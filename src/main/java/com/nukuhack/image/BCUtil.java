package com.nukuhack.image;

import com.nukuhack.image.DDSUtil.BitReader;
import com.nukuhack.image.DDSUtil.BitWriter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Pure-Java BCn block codec — decompressors and compressors for all formats
 * used in the KCD2 mod pipeline.
 *
 * <h3>Decompression (decode path)</h3>
 * Entry point: {@link #decompress(byte[], int, int, DxgiFormat)}.
 * Supports BC1 (DXT1), BC2 (DXT3), BC3 (DXT5), BC4, BC5, BC7.
 * Output is always 32-bit RGBA8 (R in byte 0, G in 1, B in 2, A in 3).
 *
 * <h3>Compression (encode path)</h3>
 * <ul>
 *   <li>{@link #compressBC5(byte[], int, int)} — BC5_UNORM, two-channel RG normal map</li>
 *   <li>{@link #compressBC7(byte[], int, int)} — BC7 full RGBA</li>
 * </ul>
 *
 * <p>BC5 stores two independent BC4 blocks per 4×4 tile — one for R, one for G.
 * The B and A channels are discarded; the engine reconstructs Z at runtime.
 *
 * <p>BC7 uses Mode-6 only (single-subset, full RGBA, 4-bpp).
 * Partition tables for multi-subset decode modes return zeros (single-subset
 * fallback), which is correct for the textures KCD2 actually ships.
 *
 * <p>This class is package-private on the encode side; public callers use
 * {@link DDSUtil#encodeBC5(byte[], int, int)} and
 * {@link DDSUtil#encodeBC7(byte[], int, int)}.
 *
 * <p>References:
 * <ul>
 *   <li>https://learn.microsoft.com/en-us/windows/win32/direct3d10/d3d10-graphics-programming-guide-resources-block-compression</li>
 * </ul>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BCUtil {
	
	// BC7 weight tables
	private static final int[] W2 = { 0, 21, 43, 64 };
	private static final int[] W3 = { 0, 9, 18, 27, 37, 46, 55, 64 };
	
	private static final int[] W4 = { 0, 4, 9, 13, 17, 21, 26, 30, 34, 38, 43, 47, 51, 55, 60, 64 };
	// Partition tables: 64 entries of 16 bytes each
	private static final int[][] PARTITION2 = new int[64][16];
	private static final int[][] PARTITION3 = new int[64][16];
	// Anchor index tables for second and third subsets
	private static final int[] ANCHOR_SECOND_SUBSET = new int[64];
	private static final int[] ANCHOR_THIRD_SUBSET_1 = new int[64];
	private static final int[] ANCHOR_THIRD_SUBSET_2 = new int[64];
	
	static {
		// Initialize partition tables from C++ data
		initPartition2();
		initPartition3();
		initAnchorTables();
	}
	
	/**
	 * Decompress a BCn-compressed image to raw RGBA8 pixels.
	 *
	 * @param src    compressed data (full image, mip 0)
	 * @param width  image width  (must be &gt; 0)
	 * @param height image height (must be &gt; 0)
	 * @param format the DXGI format of {@code src}
	 * @return RGBA8 byte array, length = {@code width * height * 4}
	 */
	public static byte[] decompress(byte[] src, int width, int height, DxgiFormat format) {
		return switch (format) {
			case BC1_UNORM, BC1_UNORM_SRGB -> decodeBC1(src, width, height);
			case BC2_UNORM, BC2_UNORM_SRGB -> decodeBC2(src, width, height);
			case BC3_UNORM, BC3_UNORM_SRGB -> decodeBC3(src, width, height);
			case BC4_UNORM -> decodeBC4(src, width, height, false);
			case BC4_SNORM -> decodeBC4(src, width, height, true);
			case BC5_UNORM -> decodeBC5(src, width, height, false);
			case BC5_SNORM -> decodeBC5(src, width, height, true);
			case BC7_UNORM, BC7_UNORM_SRGB -> decodeBC7(src, width, height);
			default -> throw new UnsupportedOperationException("Unsupported BCn format: " + format);
		};
	}
	
	public static byte[] decompress(byte[] src, int width, int height, int format) {
		DxgiFormat fmt = DxgiFormat.fromValue(format);
		if (fmt == null)
			throw new UnsupportedOperationException("Unknown DDS format code: 0x" + Integer.toHexString(format));
		return decompress(src, width, height, fmt);
	}
	
	private static byte[] decodeBC1(byte[] src, int w, int h) {
		byte[] dst = new byte[w * h * 4];
		ByteBuffer buf = ByteBuffer.wrap(src).order(ByteOrder.LITTLE_ENDIAN);
		int bw = (w + 3) / 4, bh = (h + 3) / 4;
		for (int by = 0; by < bh; by++) {
			for (int bx = 0; bx < bw; bx++) {
				int c0 = buf.getShort() & 0xFFFF;
				int c1 = buf.getShort() & 0xFFFF;
				int bits = buf.getInt();
				int[][] cols = bc1Colors(c0, c1);
				writeBlock(dst, w, h, bx, by, cols, bits);
			}
		}
		return dst;
	}
	
	private static int[][] bc1Colors(int c0, int c1) {
		int[][] c = new int[4][4]; // [color index][R,G,B,A]
		c[0] = rgb565ToRgba(c0, 255);
		c[1] = rgb565ToRgba(c1, 255);
		if (c0 > c1) {
			c[2] = lerp3(c[0], c[1]);
			c[3] = lerp3(c[1], c[0]);
		} else {
			c[2] = midpoint(c[0], c[1]);
			c[3] = new int[] { 0, 0, 0, 0 }; // transparent
		}
		return c;
	}
	
	private static int[] rgb565ToRgba(int v, int a) {
		int r = (v >> 11) & 0x1F;
		int g = (v >> 5) & 0x3F;
		int b = v & 0x1F;
		return new int[] { (r << 3) | (r >> 2), (g << 2) | (g >> 4), (b << 3) | (b >> 2), a };
	}
	
	private static int[] lerp3(int[] a, int[] b) {
		return new int[] { (2 * a[0] + b[0]) / 3, (2 * a[1] + b[1]) / 3, (2 * a[2] + b[2]) / 3, 255 };
	}
	
	private static int[] midpoint(int[] a, int[] b) {
		return new int[] { (a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2, 255 };
	}
	
	private static void writeBlock(byte[] dst, int w, int h, int bx, int by, int[][] cols, int bits) {
		for (int py = 0; py < 4; py++) {
			for (int px = 0; px < 4; px++) {
				int x = bx * 4 + px, y = by * 4 + py;
				if (x >= w || y >= h)
					continue;
				int idx = (bits >> (2 * (py * 4 + px))) & 0x3;
				int off = (y * w + x) * 4;
				dst[off] = (byte) cols[idx][0];
				dst[off + 1] = (byte) cols[idx][1];
				dst[off + 2] = (byte) cols[idx][2];
				dst[off + 3] = (byte) cols[idx][3];
			}
		}
	}
	
	private static byte[] decodeBC2(byte[] src, int w, int h) {
		byte[] dst = new byte[w * h * 4];
		ByteBuffer buf = ByteBuffer.wrap(src).order(ByteOrder.LITTLE_ENDIAN);
		int bw = (w + 3) / 4, bh = (h + 3) / 4;
		for (int by = 0; by < bh; by++) {
			for (int bx = 0; bx < bw; bx++) {
				long alphaBits = buf.getLong();
				int c0 = buf.getShort() & 0xFFFF;
				int c1 = buf.getShort() & 0xFFFF;
				int colorBits = buf.getInt();
				int[][] cols = bc1Colors(c0, c1);
				for (int py = 0; py < 4; py++) {
					for (int px = 0; px < 4; px++) {
						int x = bx * 4 + px, y = by * 4 + py;
						if (x >= w || y >= h)
							continue;
						int ci = (colorBits >> (2 * (py * 4 + px))) & 0x3;
						int a4 = (int) ((alphaBits >> (4 * (py * 4 + px))) & 0xF);
						int off = (y * w + x) * 4;
						dst[off] = (byte) cols[ci][0];
						dst[off + 1] = (byte) cols[ci][1];
						dst[off + 2] = (byte) cols[ci][2];
						dst[off + 3] = (byte) ((a4 << 4) | a4);
					}
				}
			}
		}
		return dst;
	}
	
	private static byte[] decodeBC3(byte[] src, int w, int h) {
		byte[] dst = new byte[w * h * 4];
		ByteBuffer buf = ByteBuffer.wrap(src).order(ByteOrder.LITTLE_ENDIAN);
		int bw = (w + 3) / 4, bh = (h + 3) / 4;
		for (int by = 0; by < bh; by++) {
			for (int bx = 0; bx < bw; bx++) {
				int[] alphas = decodeBC4Block(buf, false);
				int c0 = buf.getShort() & 0xFFFF;
				int c1 = buf.getShort() & 0xFFFF;
				int colorBits = buf.getInt();
				int[][] cols = bc1Colors(c0, c1);
				for (int py = 0; py < 4; py++) {
					for (int px = 0; px < 4; px++) {
						int x = bx * 4 + px, y = by * 4 + py;
						if (x >= w || y >= h)
							continue;
						int ci = (colorBits >> (2 * (py * 4 + px))) & 0x3;
						int off = (y * w + x) * 4;
						dst[off] = (byte) cols[ci][0];
						dst[off + 1] = (byte) cols[ci][1];
						dst[off + 2] = (byte) cols[ci][2];
						dst[off + 3] = (byte) alphas[py * 4 + px];
					}
				}
			}
		}
		return dst;
	}
	
	private static byte[] decodeBC4(byte[] src, int w, int h, boolean signed) {
		byte[] dst = new byte[w * h * 4];
		ByteBuffer buf = ByteBuffer.wrap(src).order(ByteOrder.LITTLE_ENDIAN);
		int bw = (w + 3) / 4, bh = (h + 3) / 4;
		for (int by = 0; by < bh; by++) {
			for (int bx = 0; bx < bw; bx++) {
				int[] vals = decodeBC4Block(buf, signed);
				for (int py = 0; py < 4; py++) {
					for (int px = 0; px < 4; px++) {
						int x = bx * 4 + px, y = by * 4 + py;
						if (x >= w || y >= h)
							continue;
						int v = vals[py * 4 + px];
						int off = (y * w + x) * 4;
						dst[off] = dst[off + 1] = dst[off + 2] = (byte) v;
						dst[off + 3] = (byte) 255;
					}
				}
			}
		}
		return dst;
	}
	
	/**
	 * Decode one 8-byte BC4 block into 16 [0..255] values.
	 * Used by the BC3 alpha channel, BC4, and BC5 decode paths.
	 */
	private static int[] decodeBC4Block(ByteBuffer buf, boolean signed) {
		int r0 = buf.get() & 0xFF;
		int r1 = buf.get() & 0xFF;
		long indices = 0;
		for (int i = 0; i < 6; i++)
			indices |= ((long) (buf.get() & 0xFF)) << (8 * i);
		
		int[] table = new int[8];
		if (signed) {
			int s0 = r0 > 127 ? r0 - 256 : r0;
			int s1 = r1 > 127 ? r1 - 256 : r1;
			table[0] = snormToUnorm(s0);
			table[1] = snormToUnorm(s1);
			if (s0 > s1) {
				for (int i = 2; i < 8; i++)
					table[i] = snormToUnorm(((8 - i) * s0 + (i - 1) * s1) / 7);
			} else {
				for (int i = 2; i < 6; i++)
					table[i] = snormToUnorm(((6 - i) * s0 + (i - 1) * s1) / 5);
				table[6] = snormToUnorm(- 128);
				table[7] = snormToUnorm(127);
			}
		} else {
			table[0] = r0;
			table[1] = r1;
			if (r0 > r1) {
				for (int i = 2; i < 8; i++)
					table[i] = ((8 - i) * r0 + (i - 1) * r1) / 7;
			} else {
				for (int i = 2; i < 6; i++)
					table[i] = ((6 - i) * r0 + (i - 1) * r1) / 5;
				table[6] = 0;
				table[7] = 255;
			}
		}
		
		int[] out = new int[16];
		for (int i = 0; i < 16; i++)
			out[i] = table[(int) ((indices >> (3 * i)) & 0x7)];
		return out;
	}
	
	private static int snormToUnorm(int v) {
		// Map [-128..127] → [0..255]
		int clamped = v + 128;
		if (clamped < 0)
			return 0;
		return Math.min(clamped, 255);
	}
	
	private static byte[] decodeBC5(byte[] src, int w, int h, boolean signed) {
		byte[] dst = new byte[w * h * 4];
		ByteBuffer buf = ByteBuffer.wrap(src).order(ByteOrder.LITTLE_ENDIAN);
		int bw = (w + 3) / 4, bh = (h + 3) / 4;
		for (int by = 0; by < bh; by++) {
			for (int bx = 0; bx < bw; bx++) {
				int[] rs = decodeBC4Block(buf, signed);
				int[] gs = decodeBC4Block(buf, signed);
				for (int py = 0; py < 4; py++) {
					for (int px = 0; px < 4; px++) {
						int x = bx * 4 + px, y = by * 4 + py;
						if (x >= w || y >= h)
							continue;
						int off = (y * w + x) * 4;
						dst[off] = (byte) rs[py * 4 + px];
						dst[off + 1] = (byte) gs[py * 4 + px];
						dst[off + 2] = 0;
						dst[off + 3] = (byte) 255;
					}
				}
			}
		}
		return dst;
	}
	
	private static byte[] decodeBC7(byte[] src, int w, int h) {
		byte[] dst = new byte[w * h * 4];
		int bw = (w + 3) / 4;
		int bh = (h + 3) / 4;
		for (int by = 0; by < bh; by++) {
			for (int bx = 0; bx < bw; bx++) {
				int off = (by * bw + bx) * 16;
				if (off + 16 > src.length)
					break;
				int[] decoded = decodeBC7Block(src, off);
				for (int py = 0; py < 4; py++) {
					for (int px = 0; px < 4; px++) {
						int x = bx * 4 + px;
						int y = by * 4 + py;
						if (x >= w || y >= h)
							continue;
						int c = decoded[py * 4 + px];
						int outOff = (y * w + x) * 4;
						dst[outOff] = (byte) ((c >> 16) & 0xFF); // R
						dst[outOff + 1] = (byte) ((c >> 8) & 0xFF);  // G
						dst[outOff + 2] = (byte) (c & 0xFF);         // B
						dst[outOff + 3] = (byte) ((c >> 24) & 0xFF); // A
					}
				}
			}
		}
		return dst;
	}
	
	private static int detectBC7Mode(byte firstByte) {
		// BC7 mode is determined by the first byte's bit pattern
		
		if ((firstByte & 0x01) != 0)
			return 0;  // .... ...1
		if ((firstByte & 0x02) != 0)
			return 1;  // .... ..1.
		if ((firstByte & 0x04) != 0)
			return 2;  // .... .1..
		if ((firstByte & 0x08) != 0)
			return 3;  // .... 1...
		if ((firstByte & 0x10) != 0)
			return 4;  // ...1 ....
		if ((firstByte & 0x20) != 0)
			return 5;  // ..1. ....
		if ((firstByte & 0x40) != 0)
			return 6;  // .1.. ....
		if ((firstByte & 0x80) != 0)
			return 7;  // 1... ....
		
		// For mode 8+ (theoretically invalid for BC7)
		return - 1;
	}
	
	private static int[] decodeBC7Block(byte[] src, int off) {
		BitReader br = new BitReader(Arrays.copyOfRange(src, off, off + 16));
		
		// Detect mode
		int mode = - 1;
		for (int i = 0; i < 8; i++) {
			if (br.read(1) == 1) {
				mode = i;
				break;
			}
		}
		if (mode < 0) {
			int[] e = new int[16];
			Arrays.fill(e, 0xFF000000);
			return e;
		}
		mode = detectBC7Mode((byte) (src[off] & 0xFF));
		
		// for now force it to be 6
		mode = 6;
		// TODO : make this work, check other modes, etc
		
		return switch (mode) {
			case 0, 2 -> decodeBC7Mode0_2(mode, br);
			case 1, 3, 7 -> decodeBC7Mode1_3_7(mode, br);
			case 4, 5 -> decodeBC7Mode4_5(mode, br);
			case 6 -> decodeBC7BlockMode6(mode, br);
			default -> new int[16];
		};
	}
	
	private static int[] decodeBC7Mode0_2(int mode, BitReader br) {
		int subsets = 3;
		int partBits = (mode == 0) ? 4 : 6;
		int endpointBits = (mode == 0) ? 4 : 5;
		int weightBits = (mode == 0) ? 3 : 2;
		int pbits = (mode == 0) ? 6 : 0;
		
		int partIdx = br.read(partBits);
		int[] part = PARTITION3[partIdx];
		
		// Read endpoints (R,G,B only)
		int[][][] ep = new int[subsets][2][3];
		for (int c = 0; c < 3; c++) {
			for (int s = 0; s < subsets; s++) {
				for (int e = 0; e < 2; e++) {
					ep[s][e][c] = br.read(endpointBits);
				}
			}
		}
		
		// Read P-bits
		int[] p = new int[pbits];
		for (int i = 0; i < pbits; i++)
			p[i] = br.read(1);
		
		// Read weights
		int[] weights = new int[16];
		int anchor1 = ANCHOR_THIRD_SUBSET_1[partIdx];
		int anchor2 = ANCHOR_THIRD_SUBSET_2[partIdx];
		for (int i = 0; i < 16; i++) {
			int bits = weightBits;
			if (i == 0 || i == anchor1 || i == anchor2)
				bits--;
			weights[i] = br.read(bits);
		}
		
		// Dequantize endpoints
		for (int s = 0; s < subsets; s++) {
			for (int e = 0; e < 2; e++) {
				for (int c = 0; c < 3; c++) {
					if (pbits > 0) {
						ep[s][e][c] = bc7Dequant(ep[s][e][c], p[s * 2 + e], endpointBits);
					} else {
						ep[s][e][c] = bc7Dequant(ep[s][e][c], endpointBits);
					}
				}
			}
		}
		
		// Interpolate colors
		int[][] colors = new int[subsets][1 << weightBits];
		for (int s = 0; s < subsets; s++) {
			for (int i = 0; i < (1 << weightBits); i++) {
				int r = bc7Interp(ep[s][0][0], ep[s][1][0], i, weightBits);
				int g = bc7Interp(ep[s][0][1], ep[s][1][1], i, weightBits);
				int b = bc7Interp(ep[s][0][2], ep[s][1][2], i, weightBits);
				colors[s][i] = 0xFF000000 | (r << 16) | (g << 8) | b;
			}
		}
		
		int[] out = new int[16];
		for (int i = 0; i < 16; i++) {
			out[i] = colors[part[i]][weights[i]];
		}
		return out;
	}
	
	private static int[] decodeBC7Mode1_3_7(int mode, BitReader br) {
		int subsets = 2;
		int partBits = 6;
		int endpointBits = (mode == 7) ? 5 : ((mode == 1) ? 6 : 7);
		int weightBits = (mode == 1) ? 3 : 2;
		int comps = (mode == 7) ? 4 : 3;
		int pbits = (mode == 1) ? 2 : 4;
		boolean sharedP = (mode == 1);
		
		int partIdx = br.read(partBits);
		int[] part = PARTITION2[partIdx];
		
		// Read endpoints
		int[][][] ep = new int[subsets][2][comps];
		for (int c = 0; c < comps; c++) {
			for (int s = 0; s < subsets; s++) {
				for (int e = 0; e < 2; e++) {
					ep[s][e][c] = br.read(endpointBits);
				}
			}
		}
		
		// Read P-bits
		int[] p = new int[pbits];
		for (int i = 0; i < pbits; i++)
			p[i] = br.read(1);
		
		// Read weights
		int[] weights = new int[16];
		int anchor = ANCHOR_SECOND_SUBSET[partIdx];
		for (int i = 0; i < 16; i++) {
			int bits = weightBits;
			if (i == 0 || i == anchor)
				bits--;
			weights[i] = br.read(bits);
		}
		
		// Dequantize and interpolate
		for (int s = 0; s < subsets; s++) {
			for (int e = 0; e < 2; e++) {
				for (int c = 0; c < comps; c++) {
					int pb = sharedP ? p[s] : p[s * 2 + e];
					ep[s][e][c] = bc7Dequant(ep[s][e][c], pb, endpointBits);
				}
			}
		}
		
		int[][] colors = new int[subsets][1 << weightBits];
		for (int s = 0; s < subsets; s++) {
			for (int i = 0; i < (1 << weightBits); i++) {
				int r = (comps > 0) ? bc7Interp(ep[s][0][0], ep[s][1][0], i, weightBits) : 0;
				int g = (comps > 1) ? bc7Interp(ep[s][0][1], ep[s][1][1], i, weightBits) : 0;
				int b = (comps > 2) ? bc7Interp(ep[s][0][2], ep[s][1][2], i, weightBits) : 0;
				int a = (comps > 3) ? bc7Interp(ep[s][0][3], ep[s][1][3], i, weightBits) : 255;
				colors[s][i] = (a << 24) | (r << 16) | (g << 8) | b;
			}
		}
		
		int[] out = new int[16];
		for (int i = 0; i < 16; i++) {
			out[i] = colors[part[i]][weights[i]];
		}
		return out;
	}
	
	private static int[] decodeBC7Mode4_5(int mode, BitReader br) {
		int rot = br.read(2);
		int idxMode = (mode == 4) ? br.read(1) : 0;
		
		int epBitsC = (mode == 4) ? 5 : 7;
		int epBitsA = (mode == 4) ? 6 : 8;
		int weightBitsC = 2;
		int weightBitsA = (mode == 4) ? 3 : 2;
		
		// Read endpoints
		int[][] ep = new int[2][4]; // [0/1][R,G,B,A]
		for (int c = 0; c < 3; c++) {
			for (int e = 0; e < 2; e++) {
				ep[e][c] = br.read(epBitsC);
			}
		}
		for (int e = 0; e < 2; e++) {
			ep[e][3] = br.read(epBitsA);
		}
		
		// Read weights
		int[] wC = new int[16];
		int[] wA = new int[16];
		int bits1 = idxMode == 0 ? weightBitsC : weightBitsA;
		int bits2 = idxMode == 0 ? weightBitsA : weightBitsC;
		
		for (int i = 0; i < 16; i++) {
			int bits = bits1;
			if (i == 0)
				bits--;
			(idxMode == 0 ? wA : wC)[i] = br.read(bits);
		}
		for (int i = 0; i < 16; i++) {
			int bits = bits2;
			if (i == 0)
				bits--;
			(idxMode == 0 ? wC : wA)[i] = br.read(bits);
		}
		
		// Dequantize
		for (int e = 0; e < 2; e++) {
			for (int c = 0; c < 3; c++) {
				ep[e][c] = bc7Dequant(ep[e][c], epBitsC);
			}
			ep[e][3] = bc7Dequant(ep[e][3], epBitsA);
		}
		
		// Build palettes
		int[] palC = new int[1 << bits1];
		for (int i = 0; i < palC.length; i++) {
			int r = bc7Interp(ep[0][0], ep[1][0], i, bits1);
			int g = bc7Interp(ep[0][1], ep[1][1], i, bits1);
			int b = bc7Interp(ep[0][2], ep[1][2], i, bits1);
			palC[i] = (r << 16) | (g << 8) | b;
		}
		int[] palA = new int[1 << bits2];
		for (int i = 0; i < palA.length; i++) {
			palA[i] = bc7Interp(ep[0][3], ep[1][3], i, bits2);
		}
		
		int[] out = new int[16];
		for (int i = 0; i < 16; i++) {
			int c = palC[idxMode == 0 ? wC[i] : wA[i]];
			int a = palA[idxMode == 0 ? wA[i] : wC[i]];
			out[i] = (a << 24) | c;
			// Apply component rotation
			if (rot == 1) {
				int t = a;
				a = (out[i] >> 16) & 0xFF;
				out[i] = (out[i] & 0xFF00FFFF) | (t << 16);
			} else if (rot == 2) {
				int t = a;
				a = (out[i] >> 8) & 0xFF;
				out[i] = (out[i] & 0xFFFF00FF) | (t << 8);
			} else if (rot == 3) {
				int t = a;
				a = out[i] & 0xFF;
				out[i] = (out[i] & 0xFFFFFF00) | t;
			}
		}
		return out;
	}
	
	// Helper methods
	private static int bc7Dequant(int val, int pbit, int valBits) {
		int totalBits = valBits + 1;
		val = (val << 1) | pbit;
		val <<= (8 - totalBits);
		val |= (val >> totalBits);
		return val & 0xFF;
	}
	
	private static int bc7Dequant(int val, int valBits) {
		val <<= (8 - valBits);
		val |= (val >> valBits);
		return val & 0xFF;
	}
	
	private static int bc7Interp(int e0, int e1, int idx, int bits) {
		int[] w = (bits == 2) ? W2 : (bits == 3) ? W3 : W4;
		int weight = w[idx];
		return ((64 - weight) * e0 + weight * e1 + 32) >> 6;
	}
	
	private static int[] decodeBC7BlockMode6(int mode, BitReader br) {
		// Mode 6 uses a fixed layout; we can read bits directly
		// This is a placeholder; integrate new Mode 6 decode here.
		
		final int[] nSubsets = { 3, 2, 3, 2, 1, 1, 1, 2 };
		final int[] partBits = { 4, 6, 6, 6, 0, 0, 0, 6 };
		final int[] cBits = { 4, 6, 5, 7, 5, 7, 7, 5 };
		final int[] aBits = { 0, 0, 0, 0, 6, 8, 7, 5 };
		final int[] idxBits = { 3, 3, 2, 2, 2, 2, 4, 2 };
		
		int ns = nSubsets[mode];
		int pi = br.read(partBits[mode]);
		int rot = (mode == 4 || mode == 5) ? br.read(2) : 0;
		int sel = (mode == 4) ? br.read(1) : 0;
		
		int cb = cBits[mode], ab = aBits[mode];
		int icb = (mode == 4) ? (sel == 0 ? 2 : 3) : idxBits[mode];
		int iab = (mode == 4) ? (sel == 0 ? 3 : 2) : idxBits[mode];
		
		int[][] r = new int[ns][2], g = new int[ns][2], b = new int[ns][2], a = new int[ns][2];
		for (int s = 0; s < ns; s++) {
			r[s][0] = br.read(cb);
			r[s][1] = br.read(cb);
		}
		for (int s = 0; s < ns; s++) {
			g[s][0] = br.read(cb);
			g[s][1] = br.read(cb);
		}
		for (int s = 0; s < ns; s++) {
			b[s][0] = br.read(cb);
			b[s][1] = br.read(cb);
		}
		if (ab > 0)
			for (int s = 0; s < ns; s++) {
				a[s][0] = br.read(ab);
				a[s][1] = br.read(ab);
			}
		
		int[][] pb = new int[ns][2];
		if (mode == 0 || mode == 3 || mode == 6 || mode == 7) {
			for (int s = 0; s < ns; s++) {
				pb[s][0] = br.read(1);
				pb[s][1] = br.read(1);
			}
		} else if (mode == 1) {
			for (int s = 0; s < ns; s++) {
				int p = br.read(1);
				pb[s][0] = pb[s][1] = p;
			}
		}
		
		for (int s = 0; s < ns; s++) {
			r[s][0] = bc7Expand(r[s][0], cb, pb[s][0]);
			r[s][1] = bc7Expand(r[s][1], cb, pb[s][1]);
			g[s][0] = bc7Expand(g[s][0], cb, pb[s][0]);
			g[s][1] = bc7Expand(g[s][1], cb, pb[s][1]);
			b[s][0] = bc7Expand(b[s][0], cb, pb[s][0]);
			b[s][1] = bc7Expand(b[s][1], cb, pb[s][1]);
			if (ab > 0) {
				a[s][0] = bc7Expand(a[s][0], ab, pb[s][0]);
				a[s][1] = bc7Expand(a[s][1], ab, pb[s][1]);
			} else {
				a[s][0] = a[s][1] = 255;
			}
		}
		
		int[] part = new int[16];
		int[] anchors = bc7Anchors(part, ns);
		int[] ci = new int[16], ai = new int[16];
		
		for (int i = 0; i < 16; i++)
			ci[i] = br.read(icb - (i == anchors[part[i]] ? 1 : 0));
		if (mode == 4 || mode == 5)
			for (int i = 0; i < 16; i++)
				ai[i] = br.read(iab - (i == anchors[0] ? 1 : 0));
		else
			ai = ci;
		
		int[] res = new int[16];
		for (int i = 0; i < 16; i++) {
			int s = part[i];
			int fr = bc7Interp(r[s][0], r[s][1], ci[i], icb);
			int fg = bc7Interp(g[s][0], g[s][1], ci[i], icb);
			int fb = bc7Interp(b[s][0], b[s][1], ci[i], icb);
			int fa = bc7Interp(a[s][0], a[s][1], ai[i], iab);
			if (rot == 1) {
				int t = fa;
				fa = fr;
				fr = t;
			}
			if (rot == 2) {
				int t = fa;
				fa = fg;
				fg = t;
			}
			if (rot == 3) {
				int t = fa;
				fa = fb;
				fb = t;
			}
			res[i] = (fa << 24) | (fr << 16) | (fg << 8) | fb;
		}
		return res;
	}
	
	/** Expand a value of {@code bits} (+ optional P-bit) to 8 bits. */
	private static int bc7Expand(int v, int bits, int pBit) {
		if (bits == 0)
			return 0;
		int tv = v, tb = bits;
		if (pBit >= 0 && bits < 8) {
			tv = (tv << 1) | pBit;
			tb++;
		}
		return switch (tb) {
			case 4 -> (tv << 4) | tv;
			case 5 -> (tv << 3) | (tv >> 2);
			case 6 -> (tv << 2) | (tv >> 4);
			case 7 -> (tv << 1) | (tv >> 6);
			default -> tv;
		};
	}
	
	private static int[] bc7Anchors(int[] part, int ns) {
		int[] anc = new int[ns]; // anc[0] = 0 always
		if (ns > 1)
			for (int i = 0; i < 16; i++)
				if (part[i] == 1) {
					anc[1] = i;
					break;
				}
		if (ns > 2)
			for (int i = 0; i < 16; i++)
				if (part[i] == 2) {
					anc[2] = i;
					break;
				}
		return anc;
	}
	
	/**
	 * Compress RGBA8 pixels to raw BC5_UNORM block data (no DDS header).
	 *
	 * @param rgba source pixels, 4 bytes per pixel (R, G, B, A)
	 * @param w    image width
	 * @param h    image height
	 * @return raw BC5 block bytes, length = {@code blocksWide(w) * blocksHigh(h) * 16}
	 */
	static byte[] compressBC5(byte[] rgba, int w, int h) {
		int bw = DDSUtil.blocksWide(w), bh = DDSUtil.blocksHigh(h);
		byte[] out = new byte[bw * bh * 16]; // 2 × 8-byte BC4 blocks per tile
		
		for (int by = 0; by < bh; by++) {
			for (int bx = 0; bx < bw; bx++) {
				int[] rVals = new int[16], gVals = new int[16];
				
				// Extract the R and G channels from the 4×4 block
				for (int py = 0; py < 4; py++) {
					for (int px = 0; px < 4; px++) {
						int x = Math.min(bx * 4 + px, w - 1);
						int y = Math.min(by * 4 + py, h - 1);
						int p = (y * w + x) * 4;
						rVals[py * 4 + px] = rgba[p] & 0xFF;
						gVals[py * 4 + px] = rgba[p + 1] & 0xFF;
					}
				}
				
				int blockOff = (by * bw + bx) * 16;
				encodeBC4Block(rVals, out, blockOff);      // R → first 8 bytes
				encodeBC4Block(gVals, out, blockOff + 8);  // G → second 8 bytes
			}
		}
		return out;
	}
	
	/**
	 * Encode 16 [0..255] values into one 8-byte BC4_UNORM block.
	 * Uses the 8-interpolant mode (e0 &gt; e1) for maximum precision.
	 */
	private static void encodeBC4Block(int[] vals, byte[] out, int off) {
		int mn = vals[0], mx = vals[0];
		for (int v : vals) {
			if (v < mn)
				mn = v;
			if (v > mx)
				mx = v;
		}
		
		// e0 > e1 activates the 8-interpolant mode
		out[off] = (byte) mx; // e0
		out[off + 1] = (byte) mn; // e1
		
		// Build the 3-bit lookup table for 8-interpolant mode
		int[] table = { mx, mn, (6 * mx + mn) / 7, (5 * mx + 2 * mn) / 7, (4 * mx + 3 * mn) / 7, (3 * mx + 4 * mn) / 7, (2 * mx + 5 * mn) / 7, (mx + 6 * mn) / 7 };
		
		// Assign the closest index to each of the 16 pixels
		long bits = 0;
		for (int i = 0; i < 16; i++)
			bits |= (long) DDSUtil.closestIndex(vals[i], table) << (i * 3);
		
		// Write the 6 index bytes
		for (int i = 0; i < 6; i++)
			out[off + 2 + i] = (byte) ((bits >> (8 * i)) & 0xFF);
	}
	
	static byte[] compressBC7(byte[] argb, int w, int h) {
		int bw = (w + 3) / 4, bh = (h + 3) / 4;
		byte[] out = new byte[bw * bh * 16];
		int[] block = new int[16];
		
		for (int by = 0; by < bh; by++) {
			for (int bx = 0; bx < bw; bx++) {
				for (int py = 0, i = 0; py < 4; py++) {
					for (int px = 0; px < 4; px++, i++) {
						int x = bx * 4 + px, y = by * 4 + py;
						if (x < w && y < h) {
							int p = (y * w + x) * 4;
							block[i] = ((argb[p + 3] & 0xFF) << 24) | ((argb[p] & 0xFF) << 16) | ((argb[p + 1] & 0xFF) << 8) | (argb[p + 2] & 0xFF);
						} else {
							block[i] = 0;
						}
					}
				}
				encodeBC7Mode6(block, out, (by * bw + bx) * 16);
			}
		}
		return out;
	}
	
	/**
	 * Similar to Mode 6 but:
	 * - color endpoints: 7 bits (no p-bit) -> values 0..127, expanded to 8-bit
	 * - alpha endpoints: 8 bits
	 * - rotation bits (2), index mode bit (1) similar to decoder
	 * - weights: 2 bits for color, 2 bits for alpha (or 3 for one if index mode)
	 * This is more complex; refer to BC7 specification.
	 *
	 * for now just stub it ..
	 */
	private static void encodeBC7Mode5(int[] block, byte[] out, int off) {
		throw new UnsupportedOperationException("BC7-mode5 compression is not supported yet");
	}
	
	private static void encodeBC7Mode6(int[] block, byte[] out, int off) {
		// Find the two most-distant colours as endpoints
		int minC = block[0], maxC = block[0], maxDist = - 1;
		for (int i = 0; i < 16; i++) {
			for (int j = i + 1; j < 16; j++) {
				int d = colorDist4(block[i], block[j]);
				if (d > maxDist) {
					maxDist = d;
					minC = block[i];
					maxC = block[j];
				}
			}
		}
		
		// Mode 6: 7-bit endpoints (drop LSB), P-bit = 0
		int e0r = ((minC >> 16) & 0xFF) >> 1, e0g = ((minC >> 8) & 0xFF) >> 1;
		int e0b = (minC & 0xFF) >> 1, e0a = ((minC >> 24) & 0xFF) >> 1;
		int e1r = ((maxC >> 16) & 0xFF) >> 1, e1g = ((maxC >> 8) & 0xFF) >> 1;
		int e1b = (maxC & 0xFF) >> 1, e1a = ((maxC >> 24) & 0xFF) >> 1;
		
		int e0r8 = e0r << 1, e0g8 = e0g << 1, e0b8 = e0b << 1, e0a8 = e0a << 1;
		int e1r8 = e1r << 1, e1g8 = e1g << 1, e1b8 = e1b << 1, e1a8 = e1a << 1;
		
		// Anchor pixel must have index < 8; swap endpoints if needed
		if (closestIdx4(block[0], e0r8, e0g8, e0b8, e0a8, e1r8, e1g8, e1b8, e1a8, W4) >= 8) {
			int t;
			t = e0r;
			e0r = e1r;
			e1r = t;
			t = e0g;
			e0g = e1g;
			e1g = t;
			t = e0b;
			e0b = e1b;
			e1b = t;
			t = e0a;
			e0a = e1a;
			e1a = t;
		}
		
		int[] idx = new int[16];
		for (int i = 0; i < 16; i++)
			idx[i] = closestIdx4(block[i], e0r8, e0g8, e0b8, e0a8, e1r8, e1g8, e1b8, e1a8, W4);
		
		BitWriter bw = new BitWriter(out, off);
		bw.write(0x40, 7); // mode 6 marker (bit 6 set)
		bw.write(e0r, 7);
		bw.write(e1r, 7);
		bw.write(e0g, 7);
		bw.write(e1g, 7);
		bw.write(e0b, 7);
		bw.write(e1b, 7);
		bw.write(e0a, 7);
		bw.write(e1a, 7);
		bw.write(0, 1);
		bw.write(0, 1); // P-bits
		bw.write(idx[0], 3); // anchor index (MSB implicit)
		for (int i = 1; i < 16; i++)
			bw.write(idx[i], 4);
	}
	
	private static int closestIdx4(int color, int r0, int g0, int b0, int a0, int r1, int g1, int b1, int a1, int[] W) {
		int ca = (color >> 24) & 0xFF, cr = (color >> 16) & 0xFF;
		int cg = (color >> 8) & 0xFF, cb = color & 0xFF;
		int best = 0, bestD = Integer.MAX_VALUE;
		for (int i = 0; i < 16; i++) {
			int w = W[i], iw = 64 - w;
			int da = ca - ((iw * a0 + w * a1 + 32) >> 6);
			int dr = cr - ((iw * r0 + w * r1 + 32) >> 6);
			int dg = cg - ((iw * g0 + w * g1 + 32) >> 6);
			int db2 = cb - ((iw * b0 + w * b1 + 32) >> 6);
			int d = dr * dr + dg * dg + db2 * db2 + da * da;
			if (d < bestD) {
				bestD = d;
				best = i;
			}
		}
		return best;
	}
	
	private static int colorDist4(int a, int b) {
		int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
		int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
		int db = (a & 0xFF) - (b & 0xFF);
		int da = ((a >> 24) & 0xFF) - ((b >> 24) & 0xFF);
		return dr * dr + dg * dg + db * db + da * da;
	}
	
	private static void initPartition2() {
		int[] raw = { 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 1, 0, 0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 1, 1, 1, 0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0, 0, 1, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 0, 1, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 0, 0, 0, 0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, 1, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0, 1, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 1, 0, 0, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0, 1, 1, 1, 1, 0, 1, 0, 1, 1, 0, 1, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 0, 1, 1, 0, 0, 1, 1, 1, 1, 0, 0, 1, 1, 0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 0, 1, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 1, 1, 1, 0, 0, 1, 1, 1, 1, 0, 0, 1, 1, 1, 0, 0, 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0, 1, 0, 0, 0, 0, 0, 1, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 1, 0, 0, 0, 0, 1, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1, 1, 0, 0, 0, 1, 1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 1, 1, 0, 0, 0, 1, 1, 1, 1, 0, 0, 1, 1, 0, 0, 0, 0, 1, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 0, 1, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 0, 1, 1, 0, 0, 1, 0, 0, 1, 0, 1, 1, 0, 0, 0, 1, 1, 1, 0, 0, 1, 1, 1, 0, 0, 0, 0, 1, 1, 1, 0, 0, 1, 1, 1, 0, 0, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 1, 0, 1, 1, 0, 0, 0, 1, 1, 0, 0, 1, 1, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0, 0, 1, 1, 1, 0, 0, 1, 1, 1, 0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 1, 1, 1 };
		for (int i = 0; i < 64; i++) {
			PARTITION2[i] = Arrays.copyOfRange(raw, i * 16, (i + 1) * 16);
		}
	}
	
	private static void initPartition3() {
		int[] raw = { 0, 0, 1, 1, 0, 0, 1, 1, 0, 2, 2, 1, 2, 2, 2, 2, 0, 0, 0, 1, 0, 0, 1, 1, 2, 2, 1, 1, 2, 2, 2, 1, 0, 0, 0, 0, 2, 0, 0, 1, 2, 2, 1, 1, 2, 2, 1, 1, 0, 2, 2, 2, 0, 0, 2, 2, 0, 0, 1, 1, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 2, 2, 1, 1, 2, 2, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 2, 2, 0, 0, 2, 2, 0, 0, 2, 2, 0, 0, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 2, 2, 1, 1, 2, 2, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 0, 0, 1, 2, 0, 0, 1, 2, 0, 0, 1, 2, 0, 0, 1, 2, 0, 1, 1, 2, 0, 1, 1, 2, 0, 1, 1, 2, 0, 1, 1, 2, 0, 1, 2, 2, 0, 1, 2, 2, 0, 1, 2, 2, 0, 1, 2, 2, 0, 0, 1, 1, 0, 1, 1, 2, 1, 1, 2, 2, 1, 2, 2, 2, 0, 0, 1, 1, 2, 0, 0, 1, 2, 2, 0, 0, 2, 2, 2, 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 1, 1, 2, 1, 1, 2, 2, 0, 1, 1, 1, 0, 0, 1, 1, 2, 0, 0, 1, 2, 2, 0, 0, 0, 0, 0, 0, 1, 1, 2, 2, 1, 1, 2, 2, 1, 1, 2, 2, 0, 0, 2, 2, 0, 0, 2, 2, 0, 0, 2, 2, 1, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 2, 2, 2, 0, 2, 2, 2, 0, 0, 0, 1, 0, 0, 0, 1, 2, 2, 2, 1, 2, 2, 2, 1, 0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 2, 2, 0, 1, 2, 2, 0, 0, 0, 0, 1, 1, 0, 0, 2, 2, 1, 0, 2, 2, 1, 0, 0, 1, 2, 2, 0, 1, 2, 2, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 1, 2, 0, 0, 1, 2, 1, 1, 2, 2, 2, 2, 2, 2, 0, 1, 1, 0, 1, 2, 2, 1, 1, 2, 2, 1, 0, 1, 1, 0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 2, 2, 1, 1, 2, 2, 1, 0, 0, 2, 2, 1, 1, 0, 2, 1, 1, 0, 2, 0, 0, 2, 2, 0, 1, 1, 0, 0, 1, 1, 0, 2, 0, 0, 2, 2, 2, 2, 2, 0, 0, 1, 1, 0, 1, 2, 2, 0, 1, 2, 2, 0, 0, 1, 1, 0, 0, 0, 0, 2, 0, 0, 0, 2, 2, 1, 1, 2, 2, 2, 1, 0, 0, 0, 0, 0, 0, 0, 2, 1, 1, 2, 2, 1, 2, 2, 2, 0, 2, 2, 2, 0, 0, 2, 2, 0, 0, 1, 2, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 2, 0, 0, 2, 2, 0, 2, 2, 2, 0, 1, 2, 0, 0, 1, 2, 0, 0, 1, 2, 0, 0, 1, 2, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 0, 0, 0, 0, 0, 1, 2, 0, 1, 2, 0, 1, 2, 0, 1, 2, 0, 1, 2, 0, 0, 1, 2, 0, 2, 0, 1, 2, 1, 2, 0, 1, 0, 1, 2, 0, 0, 0, 1, 1, 2, 2, 0, 0, 1, 1, 2, 2, 0, 0, 1, 1, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 0, 0, 0, 0, 1, 1, 0, 1, 0, 1, 0, 1, 0, 1, 2, 2, 2, 2, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 2, 1, 2, 1, 2, 1, 0, 0, 2, 2, 1, 1, 2, 2, 0, 0, 2, 2, 1, 1, 2, 2, 0, 0, 2, 2, 0, 0, 1, 1, 0, 0, 2, 2, 0, 0, 1, 1, 0, 2, 2, 0, 1, 2, 2, 1, 0, 2, 2, 0, 1, 2, 2, 1, 0, 1, 0, 1, 2, 2, 2, 2, 2, 2, 2, 2, 0, 1, 0, 1, 0, 0, 0, 0, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 2, 2, 2, 2, 0, 2, 2, 2, 0, 1, 1, 1, 0, 2, 2, 2, 0, 1, 1, 1, 0, 0, 0, 2, 1, 1, 1, 2, 0, 0, 0, 2, 1, 1, 1, 2, 0, 0, 0, 0, 2, 1, 1, 2, 2, 1, 1, 2, 2, 1, 1, 2, 0, 2, 2, 2, 0, 1, 1, 1, 0, 1, 1, 1, 0, 2, 2, 2, 0, 0, 0, 2, 1, 1, 1, 2, 1, 1, 1, 2, 0, 0, 0, 2, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 1, 2, 2, 1, 1, 2, 0, 1, 1, 0, 0, 1, 1, 0, 2, 2, 2, 2, 2, 2, 2, 2, 0, 0, 2, 2, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 2, 2, 0, 0, 2, 2, 1, 1, 2, 2, 1, 1, 2, 2, 0, 0, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 1, 2, 0, 0, 0, 2, 0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 1, 0, 2, 2, 2, 1, 2, 2, 2, 0, 2, 2, 2, 1, 2, 2, 2, 0, 1, 0, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 0, 1, 1, 1, 2, 0, 1, 1, 2, 2, 0, 1, 2, 2, 2, 0 };
		for (int i = 0; i < 64; i++) {
			PARTITION3[i] = Arrays.copyOfRange(raw, i * 16, (i + 1) * 16);
		}
	}
	
	private static void initAnchorTables() {
		int[] second = { 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 2, 8, 2, 2, 8, 8, 15, 2, 8, 2, 2, 8, 8, 2, 2, 15, 15, 6, 8, 2, 8, 15, 15, 2, 8, 2, 2, 2, 15, 15, 6, 6, 2, 6, 8, 15, 15, 2, 2, 15, 15, 15, 15, 15, 2, 2, 15 };
		System.arraycopy(second, 0, ANCHOR_SECOND_SUBSET, 0, 64);
		
		int[] third1 = { 3, 3, 15, 15, 8, 3, 15, 15, 8, 8, 6, 6, 6, 5, 3, 3, 3, 3, 8, 15, 3, 3, 6, 10, 5, 8, 8, 6, 8, 5, 15, 15, 8, 15, 3, 5, 6, 10, 8, 15, 15, 3, 15, 5, 15, 15, 15, 15, 3, 15, 5, 5, 5, 8, 5, 10, 5, 10, 8, 13, 15, 12, 3, 3 };
		System.arraycopy(third1, 0, ANCHOR_THIRD_SUBSET_1, 0, 64);
		
		int[] third2 = { 15, 8, 8, 3, 15, 15, 3, 8, 15, 15, 15, 15, 15, 15, 15, 8, 15, 8, 15, 3, 15, 8, 15, 8, 3, 15, 6, 10, 15, 15, 10, 8, 15, 3, 15, 10, 10, 8, 9, 10, 6, 15, 8, 15, 3, 6, 6, 8, 15, 3, 15, 15, 15, 15, 15, 15, 15, 15, 15, 15, 3, 15, 15, 8 };
		System.arraycopy(third2, 0, ANCHOR_THIRD_SUBSET_2, 0, 64);
	}
}