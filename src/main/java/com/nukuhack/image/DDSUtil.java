package com.nukuhack.image;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * DDS codec — single public API for all supported formats.
 *
 * <p><b>Supported decode:</b> DXT1 / DXT3 / DXT5 / BC4 / BC5 / BC7 / uncompressed RGBA·BGRA.
 * <p><b>Supported encode:</b> DXT1 / DXT3 / DXT5 / BC5 / BC7 / uncompressed RGBA·BGRA.
 *
 * <p>All format-specific codec work is delegated to package-private helpers
 * ({@link BCUtil}) — callers never need
 * to know which codec is active.
 *
 * <p>All methods are static; this class cannot be instantiated.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DDSUtil {
	
	/**
	 * Decode a DDS file from raw bytes.
	 *
	 * @param data full DDS file bytes (including magic + header)
	 * @return decoded image, or {@code null} if the header cannot be parsed
	 */
	public static DDSImage decode(byte[] data) throws IOException {
		return decode(new ByteArrayInputStream(data));
	}
	
	/**
	 * Decode a DDS file from an {@link InputStream}.
	 *
	 * @param is stream positioned at the start of the DDS file
	 * @return decoded image, or {@code null} if the header cannot be parsed
	 */
	public static DDSImage decode(InputStream is) throws IOException {
		try (DataInputStream dis = new DataInputStream(is)) {
			return decodeInternal(dis);
		}
	}
	
	/** Compress a {@link BufferedImage} to a DXT1 DDS byte array. */
	public static byte[] encodeDXT1(BufferedImage image) throws IOException {
		DDSImage i = DDSImage.fromBufferedImage(image);
		return encodeDXT1(i.pixels, i.width, i.height);
	}
	
	/** Compress RGBA8 pixels to a DXT1 DDS byte array. */
	public static byte[] encodeDXT1(byte[] rgba, int w, int h) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(128 + blocksSize(w, h, 8));
		encodeDXT1(rgba, w, h, out);
		return out.toByteArray();
	}
	
	/** Compress RGBA8 pixels and write a DXT1 DDS directly to an {@link OutputStream}. */
	public static void encodeDXT1(byte[] rgba, int w, int h, OutputStream out) throws IOException {
		writeLegacyHeader(out, w, h, Dds.Pixel.FOURCC_DXT1, blocksSize(w, h, 8));
		out.write(compressBlocksDXT1(rgba, w, h));
	}
	
	/** Compress a {@link BufferedImage} to a DXT3 DDS byte array. */
	public static byte[] encodeDXT3(BufferedImage image) throws IOException {
		DDSImage i = DDSImage.fromBufferedImage(image);
		return encodeDXT3(i.pixels, i.width, i.height);
	}
	
	/** Compress RGBA8 pixels to a DXT3 DDS byte array. */
	public static byte[] encodeDXT3(byte[] rgba, int w, int h) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(128 + blocksSize(w, h, 16));
		encodeDXT3(rgba, w, h, out);
		return out.toByteArray();
	}
	
	/** Compress RGBA8 pixels and write a DXT3 DDS directly to an {@link OutputStream}. */
	public static void encodeDXT3(byte[] rgba, int w, int h, OutputStream out) throws IOException {
		writeLegacyHeader(out, w, h, Dds.Pixel.FOURCC_DXT3, blocksSize(w, h, 16));
		out.write(compressBlocksDXT3(rgba, w, h));
	}
	
	/** Compress a {@link BufferedImage} to a DXT5 DDS byte array. */
	public static byte[] encodeDXT5(BufferedImage image) throws IOException {
		DDSImage i = DDSImage.fromBufferedImage(image);
		return encodeDXT5(i.pixels, i.width, i.height);
	}
	
	/** Compress RGBA8 pixels to a DXT5 DDS byte array. */
	public static byte[] encodeDXT5(byte[] rgba, int w, int h) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(128 + blocksSize(w, h, 16));
		encodeDXT5(rgba, w, h, out);
		return out.toByteArray();
	}
	
	/** Compress RGBA8 pixels and write a DXT5 DDS directly to an {@link OutputStream}. */
	public static void encodeDXT5(byte[] rgba, int w, int h, OutputStream out) throws IOException {
		writeLegacyHeader(out, w, h, Dds.Pixel.FOURCC_DXT5, blocksSize(w, h, 16));
		out.write(compressBlocksDXT5(rgba, w, h));
	}
	
	/**
	 * Compress a {@link BufferedImage} to a BC5_UNORM DDS byte array (DX10 header).
	 *
	 * <p>Only the R and G channels are stored; B and A are discarded.
	 * The engine reconstructs the Z channel at runtime from X·Y.
	 */
	public static byte[] encodeBC5(BufferedImage image) throws IOException {
		DDSImage i = DDSImage.fromBufferedImage(image);
		return encodeBC5(i.pixels, i.width, i.height);
	}
	
	/**
	 * Compress RGBA8 pixels to a BC5_UNORM DDS byte array (DX10 header).
	 *
	 * <p>Only the R and G channels are stored; B and A are discarded.
	 */
	public static byte[] encodeBC5(byte[] rgba, int w, int h) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(148 + blocksSize(w, h, 16));
		encodeBC5(rgba, w, h, out);
		return out.toByteArray();
	}
	
	/** Compress RGBA8 pixels and write a BC5_UNORM DDS directly to an {@link OutputStream}. */
	public static void encodeBC5(byte[] rgba, int w, int h, OutputStream out) throws IOException {
		writeDx10Header(out, w, h, DxgiFormat.BC5_UNORM.getValue(), blocksSize(w, h, 16));
		out.write(BCUtil.compressBC5(rgba, w, h));
	}
	
	/**
	 * Compress a {@link BufferedImage} to a BC7 DDS byte array (DX10 header).
	 *
	 * <p>BC7 is the highest-quality block format and supports full RGBA.
	 * The encoder uses Mode 6 (single-subset, the best quality per block).
	 */
	public static byte[] encodeBC7(BufferedImage image) throws IOException {
		DDSImage i = DDSImage.fromBufferedImage(image);
		return encodeBC7(i.pixels, i.width, i.height);
	}
	
	/** Compress RGBA8 pixels to a BC7 DDS byte array (DX10 header). */
	public static byte[] encodeBC7(byte[] rgba, int w, int h) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(148 + blocksSize(w, h, 16));
		encodeBC7(rgba, w, h, out);
		return out.toByteArray();
	}
	
	/** Compress RGBA8 pixels and write a BC7 DDS directly to an {@link OutputStream}. */
	public static void encodeBC7(byte[] rgba, int w, int h, OutputStream out) throws IOException {
		writeDx10Header(out, w, h, DxgiFormat.BC7_UNORM.getValue(), blocksSize(w, h, 16));
		out.write(BCUtil.compressBC7(rgba, w, h));
	}
	
	/** Wrap a {@link BufferedImage} as uncompressed RGBA8 DDS (DX10 header). */
	public static byte[] encodeUncompressedRGBA(BufferedImage image) throws IOException {
		DDSImage i = DDSImage.fromBufferedImage(image);
		return encodeUncompressedRGBA(i.pixels, i.width, i.height);
	}
	
	/** Wrap RGBA8 pixels as uncompressed RGBA8 DDS (DX10 header). */
	public static byte[] encodeUncompressedRGBA(byte[] rgba, int w, int h) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(148 + rgba.length);
		encodeUncompressedRGBA(rgba, w, h, out);
		return out.toByteArray();
	}
	
	/** Wrap RGBA8 pixels and write an uncompressed RGBA DDS directly to an {@link OutputStream}. */
	public static void encodeUncompressedRGBA(byte[] rgba, int w, int h, OutputStream out) throws IOException {
		writeDx10Header(out, w, h, DxgiFormat.R8G8B8A8_UNORM.getValue(), w * h * 4);
		out.write(rgba);
	}
	
	/** Wrap a {@link BufferedImage} as uncompressed BGRA8 DDS (DX10 header). */
	public static byte[] encodeUncompressedBGRA(BufferedImage image) throws IOException {
		DDSImage i = DDSImage.fromBufferedImage(image);
		return encodeUncompressedBGRA(i.pixels, i.width, i.height);
	}
	
	/** Convert RGBA→BGRA and wrap as uncompressed BGRA8 DDS (DX10 header). */
	public static byte[] encodeUncompressedBGRA(byte[] rgba, int w, int h) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(148 + rgba.length);
		encodeUncompressedBGRA(rgba, w, h, out);
		return out.toByteArray();
	}
	
	/** Convert RGBA→BGRA and write an uncompressed BGRA DDS directly to an {@link OutputStream}. */
	public static void encodeUncompressedBGRA(byte[] rgba, int w, int h, OutputStream out) throws IOException {
		byte[] bgra = rgbaToBgra(rgba);
		writeDx10Header(out, w, h, DxgiFormat.B8G8R8A8_UNORM.getValue(), w * h * 4);
		out.write(bgra);
	}
	
	private static DDSImage decodeInternal(DataInputStream dis) throws IOException {
		// Try little-endian first (all retail DDS files), fall back to big-endian
		byte[] raw = dis.readAllBytes();
		ImgData data = parseHeader(raw);
		
		byte[] rgba = new byte[data.w * data.h * 4];
		
		switch (data.code) {
			case Dds.Pixel.FOURCC_DXT1: decompressDXT1(data, rgba);
				break;
			case Dds.Pixel.FOURCC_DXT3: decompressDXT3(data, rgba);
				break;
			case Dds.Pixel.FOURCC_DXT5: decompressDXT5(data, rgba);
				break;
			case Dds.Pixel.CODE_RAW_RGBA: System.arraycopy(data.data, 0, rgba, 0, Math.min(data.data.length, rgba.length));
				break;
			// Route all remaining BCn formats through BCUtil
			case Dds.Pixel.FOURCC_BC7: default: rgba = BCUtil.decompress(data.data, data.w, data.h, data.code);
			
		}
		return new DDSImage(data.w, data.h, rgba);
	}
	
	/**
	 * Parse DDS magic + header, read all remaining bytes into {@link ImgData}.
	 * Handles both legacy FourCC and DX10-extended headers.
	 */
	private static ImgData parseHeader(byte[] raw) throws IOException {
		ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
		
		if (buf.getInt() != Dds.DdsFile.MAGIC)
			throw new IOException("Not a DDS file (bad magic)");
		if (buf.getInt() != 124)
			throw new IOException("DDS header size invalid");
		
		buf.getInt();                       // flags
		int height = buf.getInt();
		int width = buf.getInt();
		buf.getInt();                       // pitchOrLinearSize
		buf.getInt();                       // depth
		buf.getInt();                       // mipMapCount
		buf.position(buf.position() + 44); // skip 11 reserved ints
		
		if (buf.getInt() != 32)
			throw new IOException("DDS pixel-format size invalid");
		buf.getInt();                       // pfFlags  (unused here)
		int fourCC = buf.getInt();
		// remaining pixel-format + caps fields are not needed for decode
		
		int pixelDataOffset;
		int code;
		
		if (fourCC == Dds.Pixel.FOURCC_DX10) {
			// Jump past the rest of the main header, then read the DX10 block
			buf.position(128);              // start of DX10 extended header
			int dxgi = buf.getInt();
			buf.getInt();                   // resourceDimension
			buf.getInt();                   // miscFlag
			buf.getInt();                   // arraySize
			buf.getInt();                   // miscFlags2
			pixelDataOffset = 148;          // 128 + 20
			code = DxgiFormat.toValue(dxgi);
		} else {
			pixelDataOffset = 128;
			code = DxgiFormat.fromLegacyFourcc(fourCC);
		}
		
		int dataLen = raw.length - pixelDataOffset;
		if (dataLen < 0)
			dataLen = 0;
		byte[] pixelData = new byte[dataLen];
		System.arraycopy(raw, pixelDataOffset, pixelData, 0, dataLen);
		
		int blockSize = (code == Dds.Pixel.FOURCC_DXT1) ? 8 : (code == Dds.Pixel.CODE_RAW_RGBA) ? 4 : 16;
		int expectedSize = blocksWide(width) * blocksHigh(height) * blockSize;
		return new ImgData(height, width, code, expectedSize, pixelData);
	}
	
	private static void decompressDXT1(ImgData data, byte[] out) {
		byte[] src = data.data;
		int w = data.w, h = data.h, bw = blocksWide(w);
		
		for (int by = 0, y = 0; y < h; by++, y += 4) {
			for (int bx = 0, x = 0; x < w; bx++, x += 4) {
				int bi = (by * bw + bx) * 8;
				int c0 = u16le(src, bi), c1 = u16le(src, bi + 2);
				int[] colors = expandDXT1(c0, c1);
				long bits = u32le(src, bi + 4);
				writeBlock4(out, colors, bits, x, y, w, h);
			}
		}
	}
	
	private static void decompressDXT3(ImgData data, byte[] out) {
		byte[] src = data.data;
		int w = data.w, h = data.h, bw = blocksWide(w);
		
		for (int by = 0, y = 0; y < h; by++, y += 4) {
			for (int bx = 0, x = 0; x < w; bx++, x += 4) {
				int bi = (by * bw + bx) * 16;
				long alphaBits = u64le(src, bi);
				int c0 = u16le(src, bi + 8), c1 = u16le(src, bi + 10);
				int[] colors = expandDXT3_5(c0, c1);
				long bits = u32le(src, bi + 12);
				
				for (int py = 0; py < 4 && y + py < h; py++) {
					for (int px = 0; px < 4 && x + px < w; px++) {
						int idx = py * 4 + px;
						int alpha = (int) ((alphaBits >> (idx * 4)) & 0x0F);
						alpha = (alpha << 4) | alpha;
						int color = colors[(int) ((bits >> (idx * 2)) & 0x03)];
						int oi = ((y + py) * w + x + px) * 4;
						out[oi] = (byte) ((color >> 16) & 0xFF);
						out[oi + 1] = (byte) ((color >> 8) & 0xFF);
						out[oi + 2] = (byte) (color & 0xFF);
						out[oi + 3] = (byte) alpha;
					}
				}
			}
		}
	}
	
	private static void decompressDXT5(ImgData data, byte[] out) {
		byte[] src = data.data;
		int w = data.w, h = data.h, bw = blocksWide(w);
		
		for (int by = 0, y = 0; y < h; by++, y += 4) {
			for (int bx = 0, x = 0; x < w; bx++, x += 4) {
				int bi = (by * bw + bx) * 16;
				int a0 = src[bi] & 0xFF, a1 = src[bi + 1] & 0xFF;
				long aBits = u48le(src, bi + 2);
				int c0 = u16le(src, bi + 8), c1 = u16le(src, bi + 10);
				int[] colors = expandDXT3_5(c0, c1);
				long bits = u32le(src, bi + 12);
				
				for (int py = 0; py < 4 && y + py < h; py++) {
					for (int px = 0; px < 4 && x + px < w; px++) {
						int idx = py * 4 + px;
						int alpha = alphaDXT5(a0, a1, (int) ((aBits >> (idx * 3)) & 0x07));
						int color = colors[(int) ((bits >> (idx * 2)) & 0x03)];
						int oi = ((y + py) * w + x + px) * 4;
						out[oi] = (byte) ((color >> 16) & 0xFF);
						out[oi + 1] = (byte) ((color >> 8) & 0xFF);
						out[oi + 2] = (byte) (color & 0xFF);
						out[oi + 3] = (byte) alpha;
					}
				}
			}
		}
	}
	
	private static void writeBlock4(byte[] out, int[] colors, long bits, int x, int y, int w, int h) {
		for (int py = 0; py < 4 && y + py < h; py++) {
			for (int px = 0; px < 4 && x + px < w; px++) {
				int color = colors[(int) ((bits >> ((py * 4 + px) * 2)) & 0x03)];
				int oi = ((y + py) * w + x + px) * 4;
				out[oi] = (byte) ((color >> 16) & 0xFF);
				out[oi + 1] = (byte) ((color >> 8) & 0xFF);
				out[oi + 2] = (byte) (color & 0xFF);
				out[oi + 3] = (byte) ((color >> 24) & 0xFF);
			}
		}
	}
	
	private static int[] expandDXT1(int c0, int c1) {
		int[] c = new int[4];
		c[0] = expand565(c0) | 0xFF000000;
		c[1] = expand565(c1) | 0xFF000000;
		if (c0 > c1) {
			c[2] = interp(c[0], c[1], 2, 1) | 0xFF000000;
			c[3] = interp(c[0], c[1], 1, 2) | 0xFF000000;
		} else {
			c[2] = interp(c[0], c[1], 1, 1) | 0xFF000000;
			c[3] = 0; // transparent
		}
		return c;
	}
	
	/**
	 * DXT5 and DXT3 use the same color block logic (only the alpha block differs)
	 */
	private static int[] expandDXT3_5(int c0, int c1) {
		int[] c = new int[4];
		c[0] = expand565(c0) | 0xFF000000;
		c[1] = expand565(c1) | 0xFF000000;
		c[2] = interp(c[0], c[1], 2, 1) | 0xFF000000;
		c[3] = interp(c[0], c[1], 1, 2) | 0xFF000000;
		return c;
	}
	
	private static int expand565(int c) {
		int r = ((c >> 11) & 0x1F) << 3;
		r |= r >> 5;
		int g = ((c >> 5) & 0x3F) << 2;
		g |= g >> 6;
		int b = (c & 0x1F) << 3;
		b |= b >> 5;
		return (r << 16) | (g << 8) | b;
	}
	
	private static int interp(int a, int b, int wa, int wb) {
		int t = wa + wb;
		int r = (((a >> 16) & 0xFF) * wa + ((b >> 16) & 0xFF) * wb) / t;
		int g = (((a >> 8) & 0xFF) * wa + ((b >> 8) & 0xFF) * wb) / t;
		int bl = ((a & 0xFF) * wa + (b & 0xFF) * wb) / t;
		return (r << 16) | (g << 8) | bl;
	}
	
	private static int alphaDXT5(int a0, int a1, int code) {
		if (a0 > a1)
			return switch (code) {
				case 0 -> a0;
				case 1 -> a1;
				case 2 -> (6 * a0 + a1) / 7;
				case 3 -> (5 * a0 + 2 * a1) / 7;
				case 4 -> (4 * a0 + 3 * a1) / 7;
				case 5 -> (3 * a0 + 4 * a1) / 7;
				case 6 -> (2 * a0 + 5 * a1) / 7;
				default -> (a0 + 6 * a1) / 7;
			};
		return switch (code) {
			case 0 -> a0;
			case 1 -> a1;
			case 2 -> (4 * a0 + a1) / 5;
			case 3 -> (3 * a0 + 2 * a1) / 5;
			case 4 -> (2 * a0 + 3 * a1) / 5;
			case 5 -> (a0 + 4 * a1) / 5;
			case 6 -> 0;
			default -> 255;
		};
	}
	
	private static byte[] compressBlocksDXT1(byte[] rgba, int w, int h) {
		int bw = blocksWide(w), bh = blocksHigh(h);
		byte[] out = new byte[bw * bh * 8];
		int[] block = new int[16];
		for (int by = 0; by < bh; by++)
			for (int bx = 0; bx < bw; bx++) {
				extractBlock(rgba, bx, by, w, h, block, true);
				compressDXT1Block(block, out, (by * bw + bx) * 8);
			}
		return out;
	}
	
	private static byte[] compressBlocksDXT3(byte[] rgba, int w, int h) {
		int bw = blocksWide(w), bh = blocksHigh(h);
		byte[] out = new byte[bw * bh * 16];
		int[] colors = new int[16], alphas = new int[16];
		for (int by = 0; by < bh; by++)
			for (int bx = 0; bx < bw; bx++) {
				extractBlockSplit(rgba, bx, by, w, h, colors, alphas);
				compressDXT3Block(colors, alphas, out, (by * bw + bx) * 16);
			}
		return out;
	}
	
	private static byte[] compressBlocksDXT5(byte[] rgba, int w, int h) {
		int bw = blocksWide(w), bh = blocksHigh(h);
		byte[] out = new byte[bw * bh * 16];
		int[] colors = new int[16], alphas = new int[16];
		for (int by = 0; by < bh; by++)
			for (int bx = 0; bx < bw; bx++) {
				extractBlockSplit(rgba, bx, by, w, h, colors, alphas);
				compressDXT5Block(colors, alphas, out, (by * bw + bx) * 16);
			}
		return out;
	}
	
	private static void extractBlock(byte[] rgba, int bx, int by, int w, int h, int[] out, boolean packAlpha) {
		for (int py = 0, i = 0; py < 4; py++) {
			for (int px = 0; px < 4; px++, i++) {
				int x = bx * 4 + px, y = by * 4 + py;
				if (x < w && y < h) {
					int p = (y * w + x) * 4;
					int r = rgba[p] & 0xFF, g = rgba[p + 1] & 0xFF, b = rgba[p + 2] & 0xFF, a = rgba[p + 3] & 0xFF;
					out[i] = packAlpha ? ((a << 24) | (r << 16) | (g << 8) | b) : ((r << 16) | (g << 8) | b);
				} else {
					out[i] = 0;
				}
			}
		}
	}
	
	private static void extractBlockSplit(byte[] rgba, int bx, int by, int w, int h, int[] colors, int[] alphas) {
		for (int py = 0, i = 0; py < 4; py++) {
			for (int px = 0; px < 4; px++, i++) {
				int x = bx * 4 + px, y = by * 4 + py;
				if (x < w && y < h) {
					int p = (y * w + x) * 4;
					colors[i] = ((rgba[p] & 0xFF) << 16) | ((rgba[p + 1] & 0xFF) << 8) | (rgba[p + 2] & 0xFF);
					alphas[i] = rgba[p + 3] & 0xFF;
				} else {
					colors[i] = 0;
					alphas[i] = 0;
				}
			}
		}
	}
	
	private static void compressDXT1Block(int[] colors, byte[] out, int off) {
		int minRgb = colors[0] & 0xFFFFFF, maxRgb = minRgb;
		for (int c : colors) {
			int rgb = c & 0xFFFFFF;
			if (rgb < minRgb)
				minRgb = rgb;
			if (rgb > maxRgb)
				maxRgb = rgb;
		}
		int c0 = rgbTo565(maxRgb), c1 = rgbTo565(minRgb);
		
		boolean hasAlpha = false;
		for (int c : colors)
			if (((c >> 24) & 0xFF) < 128) {
				hasAlpha = true;
				break;
			}
		
		if (! hasAlpha) {
			if (c0 <= c1) {
				int t = c0;
				c0 = c1;
				c1 = t;
			}
			if (c0 == c1 && c0 > 0)
				c1--;
		}
		
		int[] pal = expandDXT1(c0, c1);
		long bits = 0;
		for (int i = 0; i < 16; i++) {
			int best = (((colors[i] >> 24) & 0xFF) < 128 && c0 <= c1) ? 3 : nearestColor(colors[i], pal);
			bits |= (long) best << (i * 2);
		}
		out[off] = (byte) (c0 & 0xFF);
		out[off + 1] = (byte) (c0 >> 8);
		out[off + 2] = (byte) (c1 & 0xFF);
		out[off + 3] = (byte) (c1 >> 8);
		out[off + 4] = (byte) bits;
		out[off + 5] = (byte) (bits >> 8);
		out[off + 6] = (byte) (bits >> 16);
		out[off + 7] = (byte) (bits >> 24);
	}
	
	private static void compressDXT3Block(int[] colors, int[] alphas, byte[] out, int off) {
		long aBits = 0;
		for (int i = 0; i < 16; i++)
			aBits |= (long) Math.min(15, alphas[i] / 17) << (i * 4);
		for (int i = 0; i < 8; i++)
			out[off + i] = (byte) ((aBits >> (i * 8)) & 0xFF);
		int[] c0c1 = minMaxColors(colors);
		int c0 = c0c1[0], c1 = c0c1[1];
		if (c0 <= c1) {
			int t = c0;
			c0 = c1;
			c1 = t;
		}
		int[] pal = expandDXT3_5(c0, c1);
		compressColorBlock(colors, out, off, c0, c1, pal);
	}
	
	private static void compressDXT5Block(int[] colors, int[] alphas, byte[] out, int off) {
		int aMin = 255, aMax = 0;
		for (int a : alphas) {
			if (a < aMin)
				aMin = a;
			if (a > aMax)
				aMax = a;
		}
		int a0 = aMax, a1 = aMin;
		long aBits = 0;
		for (int i = 0; i < 16; i++)
			aBits |= (long) alphaCode(alphas[i], a0, a1) << (i * 3);
		out[off] = (byte) a0;
		out[off + 1] = (byte) a1;
		for (int i = 0; i < 6; i++)
			out[off + 2 + i] = (byte) ((aBits >> (i * 8)) & 0xFF);
		int[] c0c1 = minMaxColors(colors);
		int c0 = c0c1[0], c1 = c0c1[1];
		if (c0 <= c1) {
			int t = c0;
			c0 = c1;
			c1 = t;
		}
		int[] pal = expandDXT3_5(c0, c1);
		compressColorBlock(colors, out, off, c0, c1, pal);
	}
	
	private static void compressColorBlock(int[] colors, byte[] out, int off, int c0, int c1, int[] pal) {
		long bits = buildColorBits(colors, pal);
		out[off + 8] = (byte) (c0 & 0xFF);
		out[off + 9] = (byte) (c0 >> 8);
		out[off + 10] = (byte) (c1 & 0xFF);
		out[off + 11] = (byte) (c1 >> 8);
		out[off + 12] = (byte) bits;
		out[off + 13] = (byte) (bits >> 8);
		out[off + 14] = (byte) (bits >> 16);
		out[off + 15] = (byte) (bits >> 24);
	}
	
	private static int[] minMaxColors(int[] colors) {
		int mn = colors[0], mx = colors[0];
		for (int c : colors) {
			if (c < mn)
				mn = c;
			if (c > mx)
				mx = c;
		}
		return new int[] { rgbTo565(mx), rgbTo565(mn) };
	}
	
	private static long buildColorBits(int[] colors, int[] pal) {
		long bits = 0;
		for (int i = 0; i < 16; i++)
			bits |= (long) nearestColor(colors[i], pal) << (i * 2);
		return bits;
	}
	
	private static int nearestColor(int color, int[] pal) {
		int best = 0, bestD = Integer.MAX_VALUE;
		for (int j = 0; j < pal.length; j++) {
			int d = colorDist(color, pal[j]);
			if (d < bestD) {
				bestD = d;
				best = j;
			}
		}
		return best;
	}
	
	private static int alphaCode(int alpha, int a0, int a1) {
		int[] tbl = new int[8];
		if (a0 > a1) {
			tbl[0] = a0;
			tbl[1] = a1;
			for (int i = 2; i <= 7; i++)
				tbl[i] = ((8 - i) * a0 + (i - 1) * a1) / 7;
		} else {
			tbl[0] = a0;
			tbl[1] = a1;
			for (int i = 2; i <= 5; i++)
				tbl[i] = ((6 - i) * a0 + (i - 1) * a1) / 5;
			tbl[6] = 0;
			tbl[7] = 255;
		}
		return closestIndex(alpha, tbl);
	}
	
	/** Package-private: used by Bc5Util for BC4 block encoding. */
	static int closestIndex(int val, int[] table) {
		int best = 0, bestD = Integer.MAX_VALUE;
		for (int i = 0; i < table.length; i++) {
			int d = Math.abs(val - table[i]);
			if (d < bestD) {
				bestD = d;
				best = i;
			}
		}
		return best;
	}
	
	private static int rgbTo565(int rgb) {
		return (((rgb >> 16) & 0xFF) >> 3) << 11 | (((rgb >> 8) & 0xFF) >> 2) << 5 | ((rgb & 0xFF) >> 3);
	}
	
	private static int colorDist(int a, int b) {
		int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
		int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
		int db = (a & 0xFF) - (b & 0xFF);
		return dr * dr + dg * dg + db * db;
	}
	
	/**
	 * Write a standard 128-byte DDS header using a legacy FourCC (DXT1/DXT3/DXT5).
	 * Package-private so codec helpers can reuse it.
	 */
	static void writeLegacyHeader(OutputStream out, int w, int h, int fourCC, int linearSize) throws IOException {
		ByteBuffer buf = buildBaseHeader(w, h, linearSize);
		// pixel-format block
		buf.putInt(32);          // struct size
		buf.putInt(0x00000004);  // DDPF_FOURCC
		buf.putInt(fourCC);
		buf.putInt(0);
		buf.putInt(0);
		buf.putInt(0);
		buf.putInt(0);
		buf.putInt(0);
		// caps
		buf.putInt(0x00001000); // DDSCAPS_TEXTURE
		buf.putInt(0);
		buf.putInt(0);
		buf.putInt(0);
		buf.putInt(0);
		out.write(buf.array());
	}
	
	/**
	 * Write a 128-byte DDS header + 20-byte DX10 extended header.
	 * Used for BC5, BC7, and uncompressed RGBA/BGRA.
	 * Package-private so codec helpers can reuse it.
	 */
	static void writeDx10Header(OutputStream out, int w, int h, int dxgiFormat, int linearSize) throws IOException {
		writeLegacyHeader(out, w, h, Dds.Pixel.FOURCC_DX10, linearSize);
		
		// DX10 extended header (20 bytes)
		ByteBuffer ext = ByteBuffer.wrap(new byte[20]).order(ByteOrder.LITTLE_ENDIAN);
		ext.putInt(dxgiFormat);
		ext.putInt(3);  // D3D10_RESOURCE_DIMENSION_TEXTURE2D
		ext.putInt(0);  // miscFlag
		ext.putInt(1);  // arraySize
		ext.putInt(0);  // miscFlags2
		out.write(ext.array());
	}
	
	private static ByteBuffer buildBaseHeader(int w, int h, int linearSize) {
		ByteBuffer buf = ByteBuffer.wrap(new byte[128]).order(ByteOrder.LITTLE_ENDIAN);
		buf.putInt(Dds.DdsFile.MAGIC);
		buf.putInt(124);        // header size
		buf.putInt(0x00081007); // DDSD_CAPS | DDSD_HEIGHT | DDSD_WIDTH | DDSD_PIXELFORMAT | DDSD_LINEARSIZE
		buf.putInt(h);
		buf.putInt(w);
		buf.putInt(linearSize);
		buf.putInt(0);          // depth
		buf.putInt(1);          // mipMapCount
		buf.position(buf.position() + 44); // 11 reserved ints
		return buf;
	}
	
	private static byte[] rgbaToBgra(byte[] src) {
		byte[] out = new byte[src.length];
		for (int i = 0, n = src.length / 4; i < n; i++) {
			out[i * 4] = src[i * 4 + 2];  // B→R
			out[i * 4 + 1] = src[i * 4 + 1];  // G
			out[i * 4 + 2] = src[i * 4];    // R→B
			out[i * 4 + 3] = src[i * 4 + 3];  // A
		}
		return out;
	}
	
	static int blocksWide(int w) {
		return Math.max(1, (w + 3) / 4);
	}
	
	static int blocksHigh(int h) {
		return Math.max(1, (h + 3) / 4);
	}
	
	static int blocksSize(int w, int h, int bs) {
		return blocksWide(w) * blocksHigh(h) * bs;
	}
	
	private static int u16le(byte[] b, int i) {
		return (b[i] & 0xFF) | ((b[i + 1] & 0xFF) << 8);
	}
	
	private static long u32le(byte[] b, int i) {
		return (b[i] & 0xFFL) | ((b[i + 1] & 0xFFL) << 8) | ((b[i + 2] & 0xFFL) << 16) | ((b[i + 3] & 0xFFL) << 24);
	}
	
	private static long u48le(byte[] b, int i) {
		return u32le(b, i) | ((b[i + 4] & 0xFFL) << 32) | ((b[i + 5] & 0xFFL) << 40);
	}
	
	private static long u64le(byte[] b, int i) {
		return u48le(b, i) | ((b[i + 6] & 0xFFL) << 48) | ((b[i + 7] & 0xFFL) << 56);
	}
	
	/** LSB-first bit reader over a fixed byte array. Package-private for BC7Util. */
	@RequiredArgsConstructor
	static class BitReader {
		private final byte[] data;
		private int pos;
		
		int read(int n) {
			if (n == 0)
				return 0;
			int v = 0;
			for (int i = 0; i < n; i++) {
				int by = pos / 8;
				if (by >= data.length)
					break;
				v |= ((data[by] >> (pos % 8)) & 1) << i;
				pos++;
			}
			return v;
		}
	}
	
	/** LSB-first bit writer over a fixed byte array region. Package-private for BC7Util. */
	@RequiredArgsConstructor
	static class BitWriter {
		private final byte[] data;
		private int pos;
		
		BitWriter(byte[] data, int byteOffset) {
			this(data);
			this.pos = byteOffset * 8;
			Arrays.fill(data, byteOffset, byteOffset + 16, (byte) 0);
		}
		
		void write(int val, int n) {
			for (int i = 0; i < n; i++) {
				if (((val >> i) & 1) == 1)
					data[pos / 8] |= (byte) (1 << (pos % 8));
				pos++;
			}
		}
	}
	
	static class ImgData {
		final int h, w, code, length;
		final Dds.ExtendedHeader header;
		byte[] data;
		
		ImgData(int h, int w, int code, int length, byte[] data) {
			this.h = h;
			this.w = w;
			this.code = code;
			this.length = length;
			this.header = null;
			this.data = data;
		}
	}
	
	/**
	 * A decoded DDS image: raw RGBA8 pixels (4 bytes per pixel: R, G, B, A).
	 */
	public static class DDSImage {
		public final int width, height;
		public final byte[] pixels;
		
		DDSImage(int width, int height, byte[] pixels) {
			this.width = width;
			this.height = height;
			this.pixels = pixels;
		}
		
		/** Build a {@link DDSImage} from a {@link BufferedImage}. */
		public static DDSImage fromBufferedImage(BufferedImage image) {
			int w = image.getWidth(), h = image.getHeight();
			int[] rgb = image.getRGB(0, 0, w, h, null, 0, w);
			byte[] px = new byte[w * h * 4];
			for (int i = 0; i < rgb.length; i++) {
				int p = rgb[i];
				px[i * 4] = (byte) ((p >> 16) & 0xFF);
				px[i * 4 + 1] = (byte) ((p >> 8) & 0xFF);
				px[i * 4 + 2] = (byte) (p & 0xFF);
				px[i * 4 + 3] = (byte) ((p >> 24) & 0xFF);
			}
			return new DDSImage(w, h, px);
		}
		
		/** Convert this image back to a {@link BufferedImage}. */
		public BufferedImage toBufferedImage() {
			BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			int[] argb = new int[width * height];
			for (int i = 0; i < argb.length; i++) {
				int b = i * 4;
				argb[i] = ((pixels[b + 3] & 0xFF) << 24) | ((pixels[b] & 0xFF) << 16) | ((pixels[b + 1] & 0xFF) << 8) | (pixels[b + 2] & 0xFF);
			}
			img.setRGB(0, 0, width, height, argb, 0, width);
			return img;
		}
	}
}
