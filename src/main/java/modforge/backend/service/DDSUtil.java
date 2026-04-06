package modforge.backend.service;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Logger;

/**
 * DDS codec — DXT1/3/5, BC7, and uncompressed RGBA/BGRA.
 * No dependencies. All methods are static.
 */
public class DDSUtil {
	private static final Logger log = Logger.getLogger(DDSUtil.class.getName());
	
	private static final int DDS_MAGIC = 0x20534444;
	private static final int DXT1 = 0x31545844;
	private static final int DXT3 = 0x33545844;
	private static final int DXT5 = 0x35545844;
	private static final int BC7 = 0x37434200;
	private static final int DX10_4CC = 0x30315844; // "DX10" fourCC
	private static final int DXT10_RAW = 0x44315830; // internal raw marker
	
	private static final int DXGI_BC1 = 71;
	private static final int DXGI_BC2 = 74;
	private static final int DXGI_BC3 = 77;
	private static final int DXGI_BC7 = 98;
	private static final int DXGI_RGBA = 28;
	private static final int DXGI_BGRA = 87;
	
	private DDSUtil() {
	}
	
	// ── Public decode API ───────────────────────────────────────────────────
	
	/** Decode DDS bytes into a {@link DDSImage}. */
	public static DDSImage decodeWithInfo(byte[] data) throws IOException {
		return decodeWithInfo(new ByteArrayInputStream(data));
	}
	
	/** Decode a DDS stream into a {@link DDSImage}. */
	public static DDSImage decodeWithInfo(InputStream is) throws IOException {
		try (DataInputStream dis = new DataInputStream(is)) {
			return decode(dis);
		}
	}
	
	// ── Public compress API ─────────────────────────────────────────────────
	
	/** Compress a {@link BufferedImage} to DXT1 DDS bytes. */
	public static byte[] compressToDXT1(BufferedImage image) throws IOException {
		DDSImage i = DDSImage.fromBufferedImage(image);
		return compressToDXT1(i.pixels, i.width, i.height);
	}
	
	/** Compress ARGB bytes to DXT1 DDS bytes. */
	public static byte[] compressToDXT1(byte[] argb, int w, int h) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(128 + blocksSize(w, h, 8));
		compressToDXT1(argb, w, h, baos);
		return baos.toByteArray();
	}
	
	/** Compress and write DXT1 DDS directly to an {@link OutputStream}. */
	public static void compressToDXT1(byte[] argb, int w, int h, OutputStream out) throws IOException {
		writeDDSHeader(out, w, h, DXT1);
		out.write(compressBlocksDXT1(argb, w, h));
	}
	
	/** Compress a {@link BufferedImage} to DXT3 DDS bytes. */
	public static byte[] compressToDXT3(BufferedImage image) throws IOException {
		DDSImage i = DDSImage.fromBufferedImage(image);
		return compressToDXT3(i.pixels, i.width, i.height);
	}
	
	/** Compress ARGB bytes to DXT3 DDS bytes. */
	public static byte[] compressToDXT3(byte[] argb, int w, int h) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(128 + blocksSize(w, h, 16));
		compressToDXT3(argb, w, h, baos);
		return baos.toByteArray();
	}
	
	/** Compress and write DXT3 DDS directly to an {@link OutputStream}. */
	public static void compressToDXT3(byte[] argb, int w, int h, OutputStream out) throws IOException {
		writeDDSHeader(out, w, h, DXT3);
		out.write(compressBlocksDXT3(argb, w, h));
	}
	
	/** Compress a {@link BufferedImage} to DXT5 DDS bytes. */
	public static byte[] compressToDXT5(BufferedImage image) throws IOException {
		DDSImage i = DDSImage.fromBufferedImage(image);
		return compressToDXT5(i.pixels, i.width, i.height);
	}
	
	/** Compress ARGB bytes to DXT5 DDS bytes. */
	public static byte[] compressToDXT5(byte[] argb, int w, int h) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(128 + blocksSize(w, h, 16));
		compressToDXT5(argb, w, h, baos);
		return baos.toByteArray();
	}
	
	/** Compress and write DXT5 DDS directly to an {@link OutputStream}. */
	public static void compressToDXT5(byte[] argb, int w, int h, OutputStream out) throws IOException {
		writeDDSHeader(out, w, h, DXT5);
		out.write(compressBlocksDXT5(argb, w, h));
	}
	
	/** Compress a {@link BufferedImage} to BC7 DDS bytes (DX10 header). */
	public static byte[] compressToBC7(BufferedImage image) throws IOException {
		DDSImage i = DDSImage.fromBufferedImage(image);
		return compressToBC7(i.pixels, i.width, i.height);
	}
	
	/** Compress ARGB bytes to BC7 DDS bytes. */
	public static byte[] compressToBC7(byte[] argb, int w, int h) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(148 + blocksSize(w, h, 16));
		compressToBC7(argb, w, h, baos);
		return baos.toByteArray();
	}
	
	/** Compress and write BC7 DDS directly to an {@link OutputStream}. */
	public static void compressToBC7(byte[] argb, int w, int h, OutputStream out) throws IOException {
		writeDX10Header(out, w, h, blocksSize(w, h, 16), DXGI_BC7);
		out.write(BC7Util.compress(argb, w, h));
	}
	
	/** Wrap ARGB bytes in an uncompressed RGBA DDS (DX10 header). */
	public static byte[] compressToUncompressedRGBA(BufferedImage image) throws IOException {
		DDSImage i = DDSImage.fromBufferedImage(image);
		return compressToUncompressedRGBA(i.pixels, i.width, i.height);
	}
	
	/** Wrap ARGB bytes in an uncompressed RGBA DDS (DX10 header). */
	public static byte[] compressToUncompressedRGBA(byte[] argb, int w, int h) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(148 + argb.length);
		compressToUncompressedRGBA(argb, w, h, baos);
		return baos.toByteArray();
	}
	
	/** Wrap and write an uncompressed RGBA DDS directly to an {@link OutputStream}. */
	public static void compressToUncompressedRGBA(byte[] argb, int w, int h, OutputStream out) throws IOException {
		writeDX10Header(out, w, h, w * h * 4, DXGI_RGBA);
		out.write(argb);
	}
	
	/** Wrap ARGB bytes in an uncompressed BGRA DDS (DX10 header). */
	public static byte[] compressToUncompressedBGRA(BufferedImage image) throws IOException {
		DDSImage i = DDSImage.fromBufferedImage(image);
		return compressToUncompressedBGRA(i.pixels, i.width, i.height);
	}
	
	/** Wrap ARGB bytes in an uncompressed BGRA DDS (DX10 header). */
	public static byte[] compressToUncompressedBGRA(byte[] argb, int w, int h) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(148 + argb.length);
		compressToUncompressedBGRA(argb, w, h, baos);
		return baos.toByteArray();
	}
	
	/** Convert RGBA→BGRA and write an uncompressed DDS directly to an {@link OutputStream}. */
	public static void compressToUncompressedBGRA(byte[] argb, int w, int h, OutputStream out) throws IOException {
		byte[] bgra = new byte[argb.length];
		for (int i = 0, n = argb.length / 4; i < n; i++) {
			int p = i * 4;
			bgra[p] = argb[p + 2];
			bgra[p + 1] = argb[p + 1];
			bgra[p + 2] = argb[p];
			bgra[p + 3] = argb[p + 3];
		}
		writeDX10Header(out, w, h, w * h * 4, DXGI_BGRA);
		out.write(bgra);
	}
	
	// ── Decode internals ────────────────────────────────────────────────────
	
	private static DDSImage decode(DataInputStream dis) throws IOException {
		ImgData data;
		try {
			data = readHeader(dis, true);
		} catch (Exception ex) {
			log.warning("DDS header LE failed: " + ex);
			try {
				data = readHeader(dis, false);
			} catch (Exception ex2) {
				log.warning("DDS header BE failed: " + ex2);
				return null;
			}
		}
		System.out.println("header found");
		
		data.data = new byte[data.length];
		dis.readFully(data.data);
		
		byte[] rgba = new byte[data.w * data.h * 4];
		switch (data.code) {
			case DXT1 -> decompressDXT1(data, rgba);
			case DXT3 -> decompressDXT3(data, rgba);
			case DXT5 -> decompressDXT5(data, rgba);
			case BC7 -> BC7Util.decompress(data, rgba);
			case DXT10_RAW -> System.arraycopy(data.data, 0, rgba, 0, Math.min(data.data.length, rgba.length));
			default -> throw new UnsupportedOperationException("Unknown format: " + data.code);
		}
		return new DDSImage(data.w, data.h, rgba);
	}
	
	private static ImgData readHeader(DataInputStream dis, boolean littleEndian) throws IOException {
		byte[] hdr = new byte[128];
		if (dis.read(hdr) < 128)
			throw new IOException("DDS header too short");
		
		ByteBuffer buf = ByteBuffer.wrap(hdr).order(littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
		
		if (buf.getInt() != DDS_MAGIC)
			throw new IOException("Not a DDS file");
		if (buf.getInt() != 124)
			throw new IOException("DDS header size invalid");
		
		buf.getInt();                      // flags
		int height = buf.getInt();
		int width = buf.getInt();
		buf.getInt();                      // pitchOrLinearSize
		buf.getInt();                      // depth
		buf.getInt();                      // mipMapCount
		buf.position(buf.position() + 44); // skip 11 reserved ints
		
		if (buf.getInt() != 32)
			throw new IOException("Pixel format size invalid");
		buf.getInt();                      // pfFlags
		int fourCC = buf.getInt();
		// remaining pixel-format + caps fields unused
		
		int format;
		DDSHeaderDXT10 dx10 = null;
		
		if (fourCC == DXT1) {
			format = DXT1;
		} else if (fourCC == DXT3) {
			format = DXT3;
		} else if (fourCC == DXT5) {
			format = DXT5;
		} else if (fourCC == BC7) {
			format = BC7;
		} else if (fourCC == DX10_4CC) {
			byte[] ext = new byte[20];
			dis.readFully(ext);
			ByteBuffer x = ByteBuffer.wrap(ext).order(ByteOrder.LITTLE_ENDIAN);
			int dxgi = x.getInt();
			dx10 = new DDSHeaderDXT10(dxgi, x.getInt(), x.getInt(), x.getInt(), x.getInt());
			format = switch (dxgi) {
				case DXGI_BC1 -> DXT1;
				case DXGI_BC2 -> DXT3;
				case DXGI_BC3 -> DXT5;
				case DXGI_BC7 -> BC7;
				case DXGI_RGBA, DXGI_BGRA -> DXT10_RAW;
				default -> throw new IOException("Unsupported DXGI format: " + dxgi);
			};
		} else {
			throw new IOException("Unsupported DDS fourCC: 0x" + Integer.toHexString(fourCC));
		}
		
		int blockSize = (format == DXT1) ? 8 : (format == DXT10_RAW) ? 4 : 16;
		int dataSize = blocksWide(width) * blocksHigh(height) * blockSize;
		return new ImgData(height, width, format, dataSize, dx10);
	}
	
	// ── DXT decompression ───────────────────────────────────────────────────
	
	private static void decompressDXT1(ImgData data, byte[] out) {
		byte[] src = data.data;
		int w = data.w, h = data.h, bw = blocksWide(w);
		
		for (int by = 0, y = 0; y < h; by++, y += 4) {
			for (int bx = 0, x = 0; x < w; bx++, x += 4) {
				int bi = (by * bw + bx) * 8;
				int c0 = u16le(src, bi);
				int c1 = u16le(src, bi + 2);
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
				int c0 = u16le(src, bi + 8);
				int c1 = u16le(src, bi + 10);
				int[] colors = expandDXT3(c0, c1);
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
				int c0 = u16le(src, bi + 8);
				int c1 = u16le(src, bi + 10);
				int[] colors = expandDXT5(c0, c1);
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
	
	/** Write a decoded 4-color block to the output buffer. */
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
	
	// ── Color helpers ───────────────────────────────────────────────────────
	
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
	
	private static int[] expandDXT3(int c0, int c1) {
		int[] c = new int[4];
		c[0] = expand565(c0) | 0xFF000000;
		c[1] = expand565(c1) | 0xFF000000;
		c[2] = interp(c[0], c[1], 2, 1) | 0xFF000000;
		c[3] = interp(c[0], c[1], 1, 2) | 0xFF000000;
		return c;
	}
	
	private static int[] expandDXT5(int c0, int c1) {
		return expandDXT3(c0, c1);
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
	
	// ── DXT compression ─────────────────────────────────────────────────────
	
	private static byte[] compressBlocksDXT1(byte[] argb, int w, int h) {
		int bw = blocksWide(w), bh = blocksHigh(h);
		byte[] out = new byte[bw * bh * 8];
		int[] block = new int[16];
		for (int by = 0; by < bh; by++)
			for (int bx = 0; bx < bw; bx++) {
				extractBlock(argb, bx, by, w, h, block, true);
				compressDXT1Block(block, out, (by * bw + bx) * 8);
			}
		return out;
	}
	
	private static byte[] compressBlocksDXT3(byte[] argb, int w, int h) {
		int bw = blocksWide(w), bh = blocksHigh(h);
		byte[] out = new byte[bw * bh * 16];
		int[] colors = new int[16];
		int[] alphas = new int[16];
		for (int by = 0; by < bh; by++)
			for (int bx = 0; bx < bw; bx++) {
				extractBlockSplit(argb, bx, by, w, h, colors, alphas);
				compressDXT3Block(colors, alphas, out, (by * bw + bx) * 16);
			}
		return out;
	}
	
	private static byte[] compressBlocksDXT5(byte[] argb, int w, int h) {
		int bw = blocksWide(w), bh = blocksHigh(h);
		byte[] out = new byte[bw * bh * 16];
		int[] colors = new int[16];
		int[] alphas = new int[16];
		for (int by = 0; by < bh; by++)
			for (int bx = 0; bx < bw; bx++) {
				extractBlockSplit(argb, bx, by, w, h, colors, alphas);
				compressDXT5Block(colors, alphas, out, (by * bw + bx) * 16);
			}
		return out;
	}
	
	/** Extract a 4×4 block as packed ARGB ints (alpha in bits 31–24). */
	private static void extractBlock(byte[] argb, int bx, int by, int w, int h, int[] out, boolean packAlpha) {
		for (int py = 0, i = 0; py < 4; py++) {
			for (int px = 0; px < 4; px++, i++) {
				int x = bx * 4 + px, y = by * 4 + py;
				if (x < w && y < h) {
					int p = (y * w + x) * 4;
					int r = argb[p] & 0xFF, g = argb[p + 1] & 0xFF, b = argb[p + 2] & 0xFF, a = argb[p + 3] & 0xFF;
					out[i] = packAlpha ? ((a << 24) | (r << 16) | (g << 8) | b) : ((r << 16) | (g << 8) | b);
				} else {
					out[i] = 0;
				}
			}
		}
	}
	
	/** Extract a 4×4 block into separate color (RGB) and alpha arrays. */
	private static void extractBlockSplit(byte[] argb, int bx, int by, int w, int h, int[] colors, int[] alphas) {
		for (int py = 0, i = 0; py < 4; py++) {
			for (int px = 0; px < 4; px++, i++) {
				int x = bx * 4 + px, y = by * 4 + py;
				if (x < w && y < h) {
					int p = (y * w + x) * 4;
					colors[i] = ((argb[p] & 0xFF) << 16) | ((argb[p + 1] & 0xFF) << 8) | (argb[p + 2] & 0xFF);
					alphas[i] = argb[p + 3] & 0xFF;
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
		out[off + 4] = (byte) (bits & 0xFF);
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
		
		int[] pal = expandDXT3(c0, c1);
		compressColor(colors, out, off, c0, c1, pal);
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
		
		int[] pal = expandDXT5(c0, c1);
		compressColor(colors, out, off, c0, c1, pal);
	}
	
	private static void compressColor(int[] colors, byte[] out, int off, int c0, int c1, int[] pal) {
		long bits = buildColorBits(colors, pal);
		
		out[off + 8] = (byte) (c0 & 0xFF);
		out[off + 9] = (byte) (c0 >> 8);
		out[off + 10] = (byte) (c1 & 0xFF);
		out[off + 11] = (byte) (c1 >> 8);
		out[off + 12] = (byte) (bits & 0xFF);
		out[off + 13] = (byte) (bits >> 8);
		out[off + 14] = (byte) (bits >> 16);
		out[off + 15] = (byte) (bits >> 24);
	}
	
	/** Returns [c0_565, c1_565] for max/min RGB of the block. */
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
	
	/** Find the palette index (0–3) closest to {@code color}. */
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
	
	/** Find the 3-bit DXT5 alpha code closest to {@code alpha}. */
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
		int best = 0, bestD = Integer.MAX_VALUE;
		for (int i = 0; i < 8; i++) {
			int d = Math.abs(alpha - tbl[i]);
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
	
	/** RGB-only squared distance (ignores alpha). */
	private static int colorDist(int a, int b) {
		int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
		int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
		int db = (a & 0xFF) - (b & 0xFF);
		return dr * dr + dg * dg + db * db;
	}
	
	// ── DDS header writing ──────────────────────────────────────────────────
	
	/** Write a standard 128-byte DDS header for DXT1/3/5. */
	private static void writeDDSHeader(OutputStream out, int w, int h, int fourCC) throws IOException {
		byte[] hdr = new byte[128];
		ByteBuffer buf = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN);
		int blockSize = (fourCC == DXT1) ? 8 : 16;
		
		buf.putInt(DDS_MAGIC);
		buf.putInt(124);         // header size
		buf.putInt(0x00081007);  // flags: caps|height|width|pixelformat|linearsize
		buf.putInt(h);
		buf.putInt(w);
		buf.putInt(blocksWide(w) * blocksHigh(h) * blockSize); // linearSize
		buf.putInt(0);
		buf.putInt(0); // depth, mipmaps
		writeHeaderBuffer(out, hdr, buf, fourCC);
	}
	
	/** Write a 128-byte DDS header + 20-byte DX10 extension. */
	private static void writeDX10Header(OutputStream out, int w, int h, int linearSize, int dxgiFormat) throws IOException {
		byte[] hdr = new byte[128];
		ByteBuffer buf = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN);
		
		buf.putInt(DDS_MAGIC);
		buf.putInt(124);
		buf.putInt(0x00081007);
		buf.putInt(h);
		buf.putInt(w);
		buf.putInt(linearSize);
		buf.putInt(0);
		buf.putInt(0);
		writeHeaderBuffer(out, hdr, buf, DX10_4CC);
		
		byte[] ext = new byte[20];
		ByteBuffer x = ByteBuffer.wrap(ext).order(ByteOrder.LITTLE_ENDIAN);
		x.putInt(dxgiFormat);
		x.putInt(3); // D3D10_RESOURCE_DIMENSION_TEXTURE2D
		x.putInt(0);
		x.putInt(1);
		x.putInt(0);
		out.write(ext);
	}
	
	private static void writeHeaderBuffer(OutputStream out, byte[] hdr, ByteBuffer buf, int dx104cc) throws IOException {
		buf.position(buf.position() + 44);
		buf.putInt(32);
		buf.putInt(0x00000004); // DDPF_FOURCC
		buf.putInt(dx104cc);
		buf.putInt(0);
		buf.putInt(0);
		buf.putInt(0);
		buf.putInt(0);
		buf.putInt(0);
		buf.putInt(0x00001000);
		out.write(hdr);
	}
	
	// ── Bit I/O helpers ─────────────────────────────────────────────────────
	
	private static int u16le(byte[] b, int i) {
		return (b[i] & 0xFF) | ((b[i + 1] & 0xFF) << 8);
	}
	
	private static long u32le(byte[] b, int i) {
		return (b[i] & 0xFFL) | ((b[i + 1] & 0xFFL) << 8) | ((b[i + 2] & 0xFFL) << 16) | ((b[i + 3] & 0xFFL) << 24);
	}
	
	private static long u48le(byte[] b, int i) {
		return (b[i] & 0xFFL) | ((b[i + 1] & 0xFFL) << 8) | ((b[i + 2] & 0xFFL) << 16) | ((b[i + 3] & 0xFFL) << 24) | ((b[i + 4] & 0xFFL) << 32) | ((b[i + 5] & 0xFFL) << 40);
	}
	
	private static long u64le(byte[] b, int i) {
		return (b[i] & 0xFFL) | ((b[i + 1] & 0xFFL) << 8) | ((b[i + 2] & 0xFFL) << 16) | ((b[i + 3] & 0xFFL) << 24) | ((b[i + 4] & 0xFFL) << 32) | ((b[i + 5] & 0xFFL) << 40) | ((b[i + 6] & 0xFFL) << 48) | ((b[i + 7] & 0xFFL) << 56);
	}
	
	private static int blocksWide(int w) {
		return Math.max(1, (w + 3) / 4);
	}
	
	private static int blocksHigh(int h) {
		return Math.max(1, (h + 3) / 4);
	}
	
	private static int blocksSize(int w, int h, int bs) {
		return blocksWide(w) * blocksHigh(h) * bs;
	}
	
	// ── Public result type ──────────────────────────────────────────────────
	
	/** Decoded DDS image: RGBA bytes, 4 bytes per pixel (R, G, B, A order). */
	public static class DDSImage {
		public final int width, height;
		public final byte[] pixels;
		
		private DDSImage(int width, int height, byte[] pixels) {
			this.width = width;
			this.height = height;
			this.pixels = pixels;
		}
		
		/** Extract RGBA pixel data from a {@link BufferedImage}. */
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
		
		/** Convert pixels to a {@link BufferedImage}. */
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
	
	// ── Bit-level I/O used by BC7 ───────────────────────────────────────────
	
	/** LSB-first bit reader over a fixed byte array. */
	static class BitReader {
		private final byte[] data;
		private int pos;
		
		BitReader(byte[] data) {
			this.data = data;
		}
		
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
	
	/** LSB-first bit writer over a fixed byte array region. */
	static class BitWriter {
		private final byte[] data;
		private int pos;
		
		BitWriter(byte[] data, int byteOffset) {
			this.data = data;
			this.pos = byteOffset * 8;
			java.util.Arrays.fill(data, byteOffset, byteOffset + 16, (byte) 0);
		}
		
		void write(int val, int n) {
			for (int i = 0; i < n; i++) {
				if (((val >> i) & 1) == 1)
					data[pos / 8] |= (byte) (1 << (pos % 8));
				pos++;
			}
		}
	}
}


// ── Package-private support types ───────────────────────────────────────────

record DDSHeaderDXT10(int dxgiFormat, int resourceDimension, int miscFlag, int arraySize, int miscFlags2) {
}

class ImgData {
	final int h, w, code, length;
	final DDSHeaderDXT10 header;
	byte[] data;
	
	ImgData(int h, int w, int code, int length, DDSHeaderDXT10 header) {
		this.h = h;
		this.w = w;
		this.code = code;
		this.length = length;
		this.header = header;
	}
}

// ── BC7 codec ────────────────────────────────────────────────────────────────

/**
 * BC7 block decoder (all 8 modes) and Mode-6 encoder.
 * Partition tables for multi-subset modes return zeros (single-subset fallback).
 */
class BC7Util {
	
	static void decompress(ImgData data, byte[] out) {
		byte[] src = data.data;
		int w = data.w, h = data.h, bw = (w + 3) / 4;
		
		for (int by = 0; by < (h + 3) / 4; by++) {
			for (int bx = 0; bx < bw; bx++) {
				int off = (by * bw + bx) * 16;
				if (off + 16 > src.length)
					break;
				
				int[] decoded = decodeBlock(src, off);
				
				for (int py = 0; py < 4; py++) {
					for (int px = 0; px < 4; px++) {
						int x = bx * 4 + px, y = by * 4 + py;
						if (x < w && y < h) {
							int oi = (y * w + x) * 4, c = decoded[py * 4 + px];
							out[oi] = (byte) ((c >> 16) & 0xFF);
							out[oi + 1] = (byte) ((c >> 8) & 0xFF);
							out[oi + 2] = (byte) (c & 0xFF);
							out[oi + 3] = (byte) ((c >> 24) & 0xFF);
						}
					}
				}
			}
		}
	}
	
	private static int[] decodeBlock(byte[] src, int off) {
		DDSUtil.BitReader br = new DDSUtil.BitReader(java.util.Arrays.copyOfRange(src, off, off + 16));
		
		int mode = - 1;
		for (int i = 0; i < 8; i++) {
			if (br.read(1) == 1) {
				mode = i;
				break;
			}
		}
		if (mode < 0) {
			int[] e = new int[16];
			java.util.Arrays.fill(e, 0xFF000000);
			return e;
		}
		
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
			r[s][0] = expand(r[s][0], cb, pb[s][0]);
			r[s][1] = expand(r[s][1], cb, pb[s][1]);
			g[s][0] = expand(g[s][0], cb, pb[s][0]);
			g[s][1] = expand(g[s][1], cb, pb[s][1]);
			b[s][0] = expand(b[s][0], cb, pb[s][0]);
			b[s][1] = expand(b[s][1], cb, pb[s][1]);
			if (ab > 0) {
				a[s][0] = expand(a[s][0], ab, pb[s][0]);
				a[s][1] = expand(a[s][1], ab, pb[s][1]);
			} else {
				a[s][0] = a[s][1] = 255;
			}
		}
		
		int[] part = partition(ns, pi);
		int[] anchors = anchors(part, ns);
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
			int fr = interp(r[s][0], r[s][1], ci[i], icb);
			int fg = interp(g[s][0], g[s][1], ci[i], icb);
			int fb = interp(b[s][0], b[s][1], ci[i], icb);
			int fa = interp(a[s][0], a[s][1], ai[i], iab);
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
	private static int expand(int v, int bits, int pBit) {
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
	
	private static int interp(int e0, int e1, int idx, int bits) {
		final int[] W2 = { 0, 21, 43, 64 };
		final int[] W3 = { 0, 9, 18, 27, 37, 46, 55, 64 };
		final int[] W4 = { 0, 4, 9, 13, 17, 21, 26, 30, 34, 38, 43, 47, 51, 55, 60, 64 };
		int w = (bits == 2) ? W2[idx] : (bits == 3) ? W3[idx] : W4[idx];
		return ((64 - w) * e0 + w * e1 + 32) >> 6;
	}
	
	private static int[] anchors(int[] part, int ns) {
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
	
	// Multi-subset partition tables are not implemented; single-subset (all 0) is returned.
	private static int[] partition(int ns, int idx) {
		return new int[16];
	}
	
	// ── BC7 Mode-6 encoder ──────────────────────────────────────────────────
	
	static byte[] compress(byte[] argb, int w, int h) {
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
						} else
							block[i] = 0;
					}
				}
				encodeMode6(block, out, (by * bw + bx) * 16);
			}
		}
		return out;
	}
	
	private static void encodeMode6(int[] block, byte[] out, int off) {
		// Find the two most-distant colors as endpoints
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
		int e0r = ((minC >> 16) & 0xFF) >> 1, e0g = ((minC >> 8) & 0xFF) >> 1, e0b = (minC & 0xFF) >> 1, e0a = ((minC >> 24) & 0xFF) >> 1;
		int e1r = ((maxC >> 16) & 0xFF) >> 1, e1g = ((maxC >> 8) & 0xFF) >> 1, e1b = (maxC & 0xFF) >> 1, e1a = ((maxC >> 24) & 0xFF) >> 1;
		
		int e0r8 = e0r << 1, e0g8 = e0g << 1, e0b8 = e0b << 1, e0a8 = e0a << 1;
		int e1r8 = e1r << 1, e1g8 = e1g << 1, e1b8 = e1b << 1, e1a8 = e1a << 1;
		
		final int[] W4 = { 0, 4, 9, 13, 17, 21, 26, 30, 34, 38, 43, 47, 51, 55, 60, 64 };
		
		// Anchor pixel must have index < 8; swap if needed
		if (closestIdx4(block[0], e0r8, e0g8, e0b8, e0a8, e1r8, e1g8, e1b8, e1a8, W4) >= 8) {
			int t;
			for (int i = 0; i++ < 2;) {
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
		}
		
		int[] idx = new int[16];
		for (int i = 0; i < 16; i++)
			idx[i] = closestIdx4(block[i], e0r8, e0g8, e0b8, e0a8, e1r8, e1g8, e1b8, e1a8, W4);
		
		DDSUtil.BitWriter bw = new DDSUtil.BitWriter(out, off);
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
		int ca = (color >> 24) & 0xFF, cr = (color >> 16) & 0xFF, cg = (color >> 8) & 0xFF, cb = color & 0xFF;
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
		int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF), dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
		int db = (a & 0xFF) - (b & 0xFF), da = ((a >> 24) & 0xFF) - ((b >> 24) & 0xFF);
		return dr * dr + dg * dg + db * db + da * da;
	}
}