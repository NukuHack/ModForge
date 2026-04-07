package image;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Pure-Java software BCn block decompressor.
 *
 * Supports BC1 (DXT1), BC2 (DXT3), BC3 (DXT5), BC4, BC5 — the formats
 * that appear in KCD textures. Output is always 32-bit RGBA (ABGR in Java
 * BufferedImage terms: R in byte 0, G in 1, B in 2, A in 3).
 *
 * References:
 *   - https://learn.microsoft.com/en-us/windows/win32/direct3d10/d3d10-graphics-programming-guide-resources-block-compression
 */
@lombok.extern.slf4j.Slf4j
public class BcnDecoder {
	
	private BcnDecoder() {
	}
	
	// -------------------------------------------------------------------------
	// Public entry point
	// -------------------------------------------------------------------------
	
	/**
	 * Decompress a BCn-compressed image to raw RGBA8 pixels.
	 *
	 * @param src    compressed data (full image, mip 0)
	 * @param width  image width  (must be > 0)
	 * @param height image height (must be > 0)
	 * @param format the DXGI format of src
	 * @return RGBA8 byte array, length = width * height * 4
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
			default -> throw new UnsupportedOperationException("Unsupported BCn format: " + format);
		};
	}
	
	// -------------------------------------------------------------------------
	// BC1 (DXT1) — 4 bpp, RGB + optional 1-bit alpha
	// -------------------------------------------------------------------------
	
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
		int r = ((v >> 11) & 0x1F);
		int g = ((v >> 5) & 0x3F);
		int b = (v & 0x1F);
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
	
	// -------------------------------------------------------------------------
	// BC2 (DXT3) — explicit 4-bit alpha + BC1 color
	// -------------------------------------------------------------------------
	
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
	
	// -------------------------------------------------------------------------
	// BC3 (DXT5) — BC4 alpha block + BC1 color
	// -------------------------------------------------------------------------
	
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
	
	// -------------------------------------------------------------------------
	// BC4 — single channel (R)
	// -------------------------------------------------------------------------
	
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
	
	/** Decode one 8-byte BC4 block into 16 [0..255] values. */
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
		if (clamped < 0) return 0;
		if (clamped > 255) return 255;
		return clamped;
	}
	
	// -------------------------------------------------------------------------
	// BC5 — two channels (RG), used for normal maps
	// -------------------------------------------------------------------------
	
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
}
