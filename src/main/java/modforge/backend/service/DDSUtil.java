package modforge.backend.service;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * Minimal DDS decoder - no dependencies, no caching, just static methods.
 * Supports DXT1, DXT3, DXT5 compression formats.
 */
public class DDSUtil {
	private static final Logger log = Logger.getLogger(DDSUtil.class.getName());
	
	private static final int DX10 = 0x30315844; // "DX10"
	private static final int DXGI_FORMAT_BC1_UNORM = 71;
	private static final int DXGI_FORMAT_BC2_UNORM = 74;
	private static final int DXGI_FORMAT_BC3_UNORM = 77;
	private static final int DXGI_FORMAT_BC7_UNORM = 98;
	private static final int DXGI_FORMAT_R8G8B8A8_UNORM = 28;
	private static final int DXGI_FORMAT_B8G8R8A8_UNORM = 87;
	
	private static final int DDS_MAGIC = 0x20534444; // "DDS "
	private static final int DXT1 = 0x31545844; // "DXT1"
	private static final int DXT3 = 0x33545844; // "DXT3"
	private static final int DXT5 = 0x35545844; // "DXT5"
	private static final int DXT10 = 0x44315830; // "DXT10"
	private static final int BC7 = 0x37434200; // "BC7"
	
	private DDSUtil() {
	} // Prevent instantiation
	
	// ========== PUBLIC API ==========
	
	/**
	 * Decode and get result with metadata
	 */
	public static DDSImage decodeWithInfo(byte[] data) throws IOException {
		return decodeWithInfo(new ByteArrayInputStream(data));
	}
	
	/**
	 * Decode and get result with metadata
	 */
	public static DDSImage decodeWithInfo(InputStream is) throws IOException {
		try (DataInputStream dis = new DataInputStream(is)) {
			return decode(dis);
		}
	}
	
	// Update readHeader method to handle DXT10
	private static ImgData readHeader(DataInputStream dis, boolean isLittleEdian) throws IOException {
		// Read the entire header into a buffer (128 bytes: 4 for Magic + 124 for Header)
		byte[] headerBytes = new byte[128];
		int read = dis.read(headerBytes);
		if (read < 128)
			throw new IOException("DDS header too short");
		
		final ByteBuffer buffer;
		if (isLittleEdian) {
			buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN);
		} else {
			buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.BIG_ENDIAN);
		}
		
		// 1. Check Magic
		int magic = buffer.getInt();
		if (magic != DDS_MAGIC) {
			throw new IOException("Not a valid DDS file. Magic: 0x" + Integer.toHexString(magic));
		}
		
		// 2. Parse Header (Offsets based on MSDN spec)
		int size = buffer.getInt();
		if (size != 124)
			throw new IOException("DDS header size invalid");
		
		int flags = buffer.getInt();
		int height = buffer.getInt();
		int width = buffer.getInt();
		
		int pitchOrLinearSize = buffer.getInt();
		int depth = buffer.getInt();
		int mipMapCount = buffer.getInt();
		
		// Skip reserved fields (11 ints: offsets 20-63)
		for (int i = 0; i < 11; i++) {
			buffer.getInt();
		}
		
		// 3. Parse Pixel Format (starts at offset 76)
		int pfSize = buffer.getInt();           // offset 76
		if (pfSize != 32)
			throw new IOException("Pixel format size invalid");
		
		int pfFlags = buffer.getInt();           // offset 80
		int fourCC = buffer.getInt();            // offset 84
		
		// Skip remaining pixel format fields (offsets 88-107: 5 ints)
		for (int i = 0; i < 5; i++) {
			buffer.getInt();
		}
		// Skip DDS header remaining fields (offsets 108-127: 5 ints)
		for (int i = 0; i < 5; i++) {
			buffer.getInt();
		}
		
		// 4. Determine Format
		int format;
		DDSHeaderDXT10 dx10Header = null;
		if (fourCC == DXT1)
			format = DXT1;
		else if (fourCC == DXT3)
			format = DXT3;
		else if (fourCC == DXT5)
			format = DXT5;
		else if (fourCC == BC7)
			format = BC7;
		else if (fourCC == DX10) {
			// Read DX10 header (20 bytes)
			byte[] dx10Bytes = new byte[20];
			dis.readFully(dx10Bytes);
			ByteBuffer dx10Buffer = ByteBuffer.wrap(dx10Bytes).order(ByteOrder.LITTLE_ENDIAN);
			
			int dxgiFormat = dx10Buffer.getInt();
			int resourceDimension = dx10Buffer.getInt();
			int miscFlag = dx10Buffer.getInt();
			int arraySize = dx10Buffer.getInt();
			int miscFlags2 = dx10Buffer.getInt();
			
			dx10Header = new DDSHeaderDXT10(dxgiFormat, resourceDimension, miscFlag, arraySize, miscFlags2);
			
			// Map DXGI formats to our internal formats
			format = switch (dxgiFormat) {
				case DXGI_FORMAT_BC1_UNORM -> DXT1;
				case DXGI_FORMAT_BC2_UNORM -> DXT3;
				case DXGI_FORMAT_BC3_UNORM -> DXT5;
				case DXGI_FORMAT_BC7_UNORM -> BC7;
				case DXGI_FORMAT_R8G8B8A8_UNORM, DXGI_FORMAT_B8G8R8A8_UNORM -> DXT10;
				default -> throw new IOException("Unsupported DXGI format: " + dxgiFormat);
			};
		} else {
			throw new IOException("Unsupported DDS format: 0x" + Integer.toHexString(fourCC));
		}
		
		// 5. Calculate data size for the first mip level
		int blockSize = switch (format) {
			case DXT1 -> 8;
			case DXT10 -> 4;
			case DXT3, DXT5, BC7 -> 16;
			default -> throw new UnsupportedOperationException();
		};
		
		int blocksWide = Math.max(1, (width + 3) / 4);
		int blocksHigh = Math.max(1, (height + 3) / 4);
		int dataSize = blocksWide * blocksHigh * blockSize;
		
		return new ImgData(height, width, format, dataSize, dx10Header);
	}
	
	// Update decode method to handle DX10
	private static DDSImage decode(DataInputStream dis) throws IOException {
		
		ImgData data;
		try {
			data = readHeader(dis, true);
		} catch (final Exception ex) {
			log.warning("error in reading DDS image header " + ex);
			try {
				data = readHeader(dis, false);
			} catch (final Exception ex2) {
				log.warning("error in reading DDS image header " + ex2);
				return null;
			}
		}
		final DDSHeaderDXT10 dx10Header = data.header;
		
		// 6. Read compressed data
		byte[] compressed = new byte[data.length];
		dis.readFully(compressed);
		data.data = compressed;
		
		// Decompress
		byte[] argb = new byte[data.w * data.h * 4];
		
		if (data.code == DX10) {
			// Handle DX10 uncompressed
			decompressUncompressed(data, argb);
		} else {
			final BiConsumer<ImgData, byte[]> fn = switch (data.code) {
				case DXT1 -> DDSUtil::decompressDXT1;
				case DXT3 -> DDSUtil::decompressDXT3;
				case DXT5 -> DDSUtil::decompressDXT5;
				case BC7 -> BC7Util::decompressBC7;
				default -> throw new UnsupportedOperationException();
			};
			fn.accept(data, argb);
		}
		
		return new DDSImage(data.w, data.h, argb);
	}
	
	// ========== DXT DECOMPRESSION ==========
	
	// Add decompression for uncompressed DDS (RGBA/BGRA)
	private static void decompressUncompressed(ImgData data, byte[] out) throws IOException {
		byte[] rawData = data.data;
		int width = data.w;
		int height = data.h;
		
		// Assume RGBA format, 4 bytes per pixel
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int srcIdx = (y * width + x) * 4;
				int dstIdx = (y * width + x) * 4;
				
				// Copy as is (assuming RGBA)
				out[dstIdx] = rawData[srcIdx];     // R
				out[dstIdx + 1] = rawData[srcIdx + 1]; // G
				out[dstIdx + 2] = rawData[srcIdx + 2]; // B
				out[dstIdx + 3] = rawData[srcIdx + 3]; // A
			}
		}
	}
	
	private static void decompressDXT1(ImgData data, byte[] out) {
		byte[] blockData = data.data;
		int width = data.w;
		int height = data.h;
		int blocksWide = (width + 3) / 4;
		
		for (int y = 0; y < height; y += 4) {
			for (int x = 0; x < width; x += 4) {
				int blockIndex = ((y / 4) * blocksWide + (x / 4)) * 8;
				
				int c0 = (blockData[blockIndex] & 0xFF) | ((blockData[blockIndex + 1] & 0xFF) << 8);
				int c1 = (blockData[blockIndex + 2] & 0xFF) | ((blockData[blockIndex + 3] & 0xFF) << 8);
				
				int[] colors = expandColorsDXT1(c0, c1);
				
				long bits = ((long) (blockData[blockIndex + 7] & 0xFF) << 24) | ((blockData[blockIndex + 6] & 0xFF) << 16) | ((blockData[blockIndex + 5] & 0xFF) << 8) | (blockData[blockIndex + 4] & 0xFF);
				
				for (int py = 0; py < 4 && y + py < height; py++) {
					for (int px = 0; px < 4 && x + px < width; px++) {
						int idx = (py * 4 + px) * 2;
						int code = (int) ((bits >> idx) & 0x03);
						int color = colors[code];
						
						int outIdx = ((y + py) * width + (x + px)) * 4;
						out[outIdx] = (byte) ((color >> 16) & 0xFF); // R
						out[outIdx + 1] = (byte) ((color >> 8) & 0xFF); // G
						out[outIdx + 2] = (byte) (color & 0xFF); // B
						out[outIdx + 3] = (byte) ((color >> 24) & 0xFF); // A
					}
				}
			}
		}
	}
	
	private static void decompressDXT3(ImgData data, byte[] out) {
		byte[] blockData = data.data;
		int width = data.w;
		int height = data.h;
		int blocksWide = (width + 3) / 4;
		
		for (int y = 0; y < height; y += 4) {
			for (int x = 0; x < width; x += 4) {
				int blockIndex = ((y / 4) * blocksWide + (x / 4)) * 16;
				
				// Alpha data (64 bits, 4x4 4-bit alphas) — little-endian byte order
				long alphaBits = ((long) (blockData[blockIndex + 7] & 0xFF) << 56) | ((long) (blockData[blockIndex + 6] & 0xFF) << 48) | ((long) (blockData[blockIndex + 5] & 0xFF) << 40) | ((long) (blockData[blockIndex + 4] & 0xFF) << 32) | ((long) (blockData[blockIndex + 3] & 0xFF) << 24) | ((long) (blockData[blockIndex + 2] & 0xFF) << 16) | ((long) (blockData[blockIndex + 1] & 0xFF) << 8) | (blockData[blockIndex] & 0xFF);
				
				int c0 = (blockData[blockIndex + 8] & 0xFF) | ((blockData[blockIndex + 9] & 0xFF) << 8);
				int c1 = (blockData[blockIndex + 10] & 0xFF) | ((blockData[blockIndex + 11] & 0xFF) << 8);
				
				int[] colors = expandColorsDXT3(c0, c1);
				
				long bits = ((long) (blockData[blockIndex + 15] & 0xFF) << 24) | ((blockData[blockIndex + 14] & 0xFF) << 16) | ((blockData[blockIndex + 13] & 0xFF) << 8) | (blockData[blockIndex + 12] & 0xFF);
				
				for (int py = 0; py < 4 && y + py < height; py++) {
					for (int px = 0; px < 4 && x + px < width; px++) {
						int idx = py * 4 + px;
						int alpha = (int) ((alphaBits >> (idx * 4)) & 0x0F);
						alpha = (alpha << 4) | alpha; // Expand 4-bit to 8-bit
						
						int colorIdx = (int) ((bits >> (idx * 2)) & 0x03);
						int color = colors[colorIdx];
						
						int outIdx = ((y + py) * width + (x + px)) * 4;
						out[outIdx] = (byte) ((color >> 16) & 0xFF);
						out[outIdx + 1] = (byte) ((color >> 8) & 0xFF);
						out[outIdx + 2] = (byte) (color & 0xFF);
						out[outIdx + 3] = (byte) alpha;
					}
				}
			}
		}
	}
	
	private static void decompressDXT5(ImgData data, byte[] out) {
		byte[] blockData = data.data;
		int width = data.w;
		int height = data.h;
		int blocksWide = (width + 3) / 4;
		
		for (int y = 0; y < height; y += 4) {
			for (int x = 0; x < width; x += 4) {
				int blockIndex = ((y / 4) * blocksWide + (x / 4)) * 16;
				
				int alpha0 = blockData[blockIndex] & 0xFF;
				int alpha1 = blockData[blockIndex + 1] & 0xFF;
				
				// FIX: alpha bit-stream starts at blockIndex+2, not blockIndex+1.
				// Reading from +1 caused alpha1's byte to be included, corrupting
				// every pixel whose 3-bit code straddled a byte boundary (~every 7th/9th pixel).
				long alphaBits = ((long) (blockData[blockIndex + 7] & 0xFF) << 40) | ((long) (blockData[blockIndex + 6] & 0xFF) << 32) | ((long) (blockData[blockIndex + 5] & 0xFF) << 24) | ((long) (blockData[blockIndex + 4] & 0xFF) << 16) | ((long) (blockData[blockIndex + 3] & 0xFF) << 8) | (blockData[blockIndex + 2] & 0xFF);
				
				int c0 = (blockData[blockIndex + 8] & 0xFF) | ((blockData[blockIndex + 9] & 0xFF) << 8);
				int c1 = (blockData[blockIndex + 10] & 0xFF) | ((blockData[blockIndex + 11] & 0xFF) << 8);
				
				int[] colors = expandColorsDXT5(c0, c1);
				
				long bits = ((long) (blockData[blockIndex + 15] & 0xFF) << 24) | ((blockData[blockIndex + 14] & 0xFF) << 16) | ((blockData[blockIndex + 13] & 0xFF) << 8) | (blockData[blockIndex + 12] & 0xFF);
				
				for (int py = 0; py < 4 && y + py < height; py++) {
					for (int px = 0; px < 4 && x + px < width; px++) {
						int idx = py * 4 + px;
						int alphaCode = (int) ((alphaBits >> (idx * 3)) & 0x07);
						int alpha = getAlphaDXT5(alpha0, alpha1, alphaCode);
						
						int colorIdx = (int) ((bits >> (idx * 2)) & 0x03);
						int color = colors[colorIdx];
						
						int outIdx = ((y + py) * width + (x + px)) * 4;
						out[outIdx] = (byte) ((color >> 16) & 0xFF);
						out[outIdx + 1] = (byte) ((color >> 8) & 0xFF);
						out[outIdx + 2] = (byte) (color & 0xFF);
						out[outIdx + 3] = (byte) alpha;
					}
				}
			}
		}
	}
	
	// ========== COLOR EXPANSION HELPERS ==========
	
	private static int[] expandColorsDXT1(int c0, int c1) {
		int[] colors = new int[4];
		// FIX: alpha must be 0xFF for opaque pixels in 4-colour mode.
		// Previously colors[0]/[1] had no alpha set (0x00RRGGBB), so every
		// decoded pixel came out fully transparent.
		colors[0] = expand565(c0) | 0xFF000000;
		colors[1] = expand565(c1) | 0xFF000000;
		
		if (c0 > c1) {
			// 4-color mode — FIX: colors[2] used wrong weights (1,2 instead of 2,1)
			colors[2] = interpolate(colors[0], colors[1], 2, 1) | 0xFF000000;
			colors[3] = interpolate(colors[0], colors[1], 1, 2) | 0xFF000000;
		} else {
			// 3-color mode + transparent
			colors[2] = interpolate(colors[0], colors[1], 1, 1) | 0xFF000000;
			colors[3] = 0x00000000; // transparent
		}
		return colors;
	}
	
	private static int[] expandColorsDXT3(int c0, int c1) {
		int[] colors = new int[4];
		colors[0] = expand565(c0) | 0xFF000000;
		colors[1] = expand565(c1) | 0xFF000000;
		colors[2] = interpolate(colors[0], colors[1], 2, 1) | 0xFF000000;
		colors[3] = interpolate(colors[0], colors[1], 1, 2) | 0xFF000000;
		return colors;
	}
	
	private static int[] expandColorsDXT5(int c0, int c1) {
		return expandColorsDXT3(c0, c1);
	}
	
	private static int expand565(int c) {
		int r = ((c >> 11) & 0x1F) << 3;
		int g = ((c >> 5) & 0x3F) << 2;
		int b = (c & 0x1F) << 3;
		
		// Expand to 8-bit
		r |= (r >> 5);
		g |= (g >> 6);
		b |= (b >> 5);
		
		return (r << 16) | (g << 8) | b;
	}
	
	private static int interpolate(int a, int b, int aWeight, int bWeight) {
		int total = aWeight + bWeight;
		int r = (((a >> 16) & 0xFF) * aWeight + ((b >> 16) & 0xFF) * bWeight) / total;
		int g = (((a >> 8) & 0xFF) * aWeight + ((b >> 8) & 0xFF) * bWeight) / total;
		int bl = ((a & 0xFF) * aWeight + (b & 0xFF) * bWeight) / total;
		
		return (r << 16) | (g << 8) | bl;
	}
	
	private static int getAlphaDXT5(int alpha0, int alpha1, int code) {
		if (alpha0 > alpha1) {
			switch (code) {
				case 0: return alpha0;
				case 1: return alpha1;
				case 2: return (6 * alpha0 + alpha1) / 7;
				case 3: return (5 * alpha0 + 2 * alpha1) / 7;
				case 4: return (4 * alpha0 + 3 * alpha1) / 7;
				case 5: return (3 * alpha0 + 4 * alpha1) / 7;
				case 6: return (2 * alpha0 + 5 * alpha1) / 7;
				default: return (alpha0 + 6 * alpha1) / 7;
			}
		} else {
			switch (code) {
				case 0: return alpha0;
				case 1: return alpha1;
				case 2: return (4 * alpha0 + alpha1) / 5;
				case 3: return (3 * alpha0 + 2 * alpha1) / 5;
				case 4: return (2 * alpha0 + 3 * alpha1) / 5;
				case 5: return (alpha0 + 4 * alpha1) / 5;
				case 6: return 0;
				default: return 255;
			}
		}
	}
	
	// ========== RESULT CLASS ==========
	
	/**
	 * Compress ARGB data to DDS DXT1 format
	 */
	public static byte[] compressToDXT1(BufferedImage image) throws IOException {
		final DDSImage i = DDSImage.fromBufferedImage(image);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		compressToDXT1(i.pixels, i.width, i.height, baos);
		return baos.toByteArray();
	}
	
	
	// ========== PUBLIC API ==========
	
	/**
	 * Compress ARGB data to DDS DXT3 format (better alpha)
	 */
	public static byte[] compressToDXT3(BufferedImage image) throws IOException {
		final DDSImage i = DDSImage.fromBufferedImage(image);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		compressToDXT3(i.pixels, i.width, i.height, baos);
		return baos.toByteArray();
	}
	
	/**
	 * Compress ARGB data to DDS DXT5 format (best alpha)
	 */
	public static byte[] compressToDXT5(BufferedImage image) throws IOException {
		final DDSImage i = DDSImage.fromBufferedImage(image);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		compressToDXT5(i.pixels, i.width, i.height, baos);
		return baos.toByteArray();
	}
	
	/**
	 * Compress ARGB data to DDS DXT1 format
	 */
	public static byte[] compressToDXT1(byte[] argb, int width, int height) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		compressToDXT1(argb, width, height, baos);
		return baos.toByteArray();
	}
	
	/**
	 * Compress ARGB data to DDS DXT3 format (better alpha)
	 */
	public static byte[] compressToDXT3(byte[] argb, int width, int height) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		compressToDXT3(argb, width, height, baos);
		return baos.toByteArray();
	}
	
	/**
	 * Compress ARGB data to DDS DXT5 format (best alpha)
	 */
	public static byte[] compressToDXT5(byte[] argb, int width, int height) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		compressToDXT5(argb, width, height, baos);
		return baos.toByteArray();
	}
	
	/**
	 * Compress and write directly to OutputStream
	 */
	public static void compressToDXT1(byte[] argb, int width, int height, OutputStream out) throws IOException {
		writeDDSHeaderLe(out, width, height, 0x31545844); // DXT1
		byte[] compressed = compressBlockDXT1(argb, width, height);
		out.write(compressed);
	}
	
	public static void compressToDXT3(byte[] argb, int width, int height, OutputStream out) throws IOException {
		writeDDSHeaderLe(out, width, height, 0x33545844); // DXT3
		byte[] compressed = compressBlockDXT3(argb, width, height);
		out.write(compressed);
	}
	
	public static void compressToDXT5(byte[] argb, int width, int height, OutputStream out) throws IOException {
		writeDDSHeaderLe(out, width, height, 0x35545844); // DXT5
		byte[] compressed = compressBlockDXT5(argb, width, height);
		out.write(compressed);
	}
	
	// Add compression for uncompressed DDS (RGBA)
	public static byte[] compressToUncompressedRGBA(BufferedImage image) throws IOException {
		final DDSImage i = DDSImage.fromBufferedImage(image);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		compressToUncompressedRGBA(i.pixels, i.width, i.height, baos);
		return baos.toByteArray();
	}
	
	public static byte[] compressToUncompressedRGBA(byte[] argb, int width, int height) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		compressToUncompressedRGBA(argb, width, height, baos);
		return baos.toByteArray();
	}
	
	public static void compressToUncompressedRGBA(byte[] argb, int width, int height, OutputStream out) throws IOException {
		// Write DDS header with DX10 extension for uncompressed RGBA
		writeDDSHeaderWithDX10(out, width, height, DXGI_FORMAT_R8G8B8A8_UNORM);
		// Write raw pixel data (already in RGBA format)
		out.write(argb);
	}
	
	// Add compression for uncompressed BGRA
	public static byte[] compressToUncompressedBGRA(BufferedImage image) throws IOException {
		final DDSImage i = DDSImage.fromBufferedImage(image);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		compressToUncompressedBGRA(i.pixels, i.width, i.height, baos);
		return baos.toByteArray();
	}
	
	public static byte[] compressToUncompressedBGRA(byte[] argb, int width, int height) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		compressToUncompressedBGRA(argb, width, height, baos);
		return baos.toByteArray();
	}
	
	public static void compressToUncompressedBGRA(byte[] argb, int width, int height, OutputStream out) throws IOException {
		// Convert RGBA to BGRA
		byte[] bgra = new byte[argb.length];
		for (int i = 0; i < argb.length / 4; i++) {
			bgra[i * 4] = argb[i * 4 + 2];     // B
			bgra[i * 4 + 1] = argb[i * 4 + 1]; // G
			bgra[i * 4 + 2] = argb[i * 4];     // R
			bgra[i * 4 + 3] = argb[i * 4 + 3]; // A
		}
		
		writeDDSHeaderWithDX10(out, width, height, DXGI_FORMAT_B8G8R8A8_UNORM);
		out.write(bgra);
	}
	
	/**
	 * Compress ARGB data to DDS BC7 format
	 */
	public static byte[] compressToBC7(BufferedImage image) throws IOException {
		final DDSImage i = DDSImage.fromBufferedImage(image);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		compressToBC7(i.pixels, i.width, i.height, baos);
		return baos.toByteArray();
	}
	
	public static byte[] compressToBC7(byte[] argb, int width, int height) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		compressToBC7(argb, width, height, baos);
		return baos.toByteArray();
	}
	
	public static void compressToBC7(byte[] argb, int width, int height, OutputStream out) throws IOException {
		// Write DX10 DDS header specifically for BC7
		writeDDSHeaderWithDX10BC7(out, width, height);
		// Compress to Mode 6 BC7 blocks
		byte[] compressed = BC7Util.compressBC7(argb, width, height);
		out.write(compressed);
	}
	
	// ========== DDS HEADER ==========
	private static void writeDDSHeaderLe(OutputStream out, int width, int height, int fourCC) throws IOException {
		byte[] headerBytes = new byte[128];
		ByteBuffer buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN);
		
		buffer.putInt(0x20534444); // Magic
		buffer.putInt(124); // Header size
		buffer.putInt(0x00081007); // Flags
		buffer.putInt(height);
		buffer.putInt(width);
		
		int blockSize = (fourCC == 0x31545844) ? 8 : 16;
		int blocksWide = (width + 3) / 4;
		int blocksHigh = (height + 3) / 4;
		buffer.putInt(blocksWide * blocksHigh * blockSize);
		
		buffer.putInt(0); // Depth
		buffer.putInt(0); // Mipmaps
		
		// Reserved (11 ints)
		for (int i = 0; i < 11; i++)
			buffer.putInt(0);
		
		// Pixel format
		buffer.putInt(32); // size
		buffer.putInt(0x00000004); // DDPF_FOURCC
		buffer.putInt(fourCC);
		buffer.putInt(0); // RGB bit count
		buffer.putInt(0); // R mask
		buffer.putInt(0); // G mask
		buffer.putInt(0); // B mask
		buffer.putInt(0); // A mask
		
		// Caps
		buffer.putInt(0x00001000);
		buffer.putInt(0);
		buffer.putInt(0);
		buffer.putInt(0);
		
		// Reserved
		buffer.putInt(0);
		
		out.write(headerBytes);
	}
	
	// Add helper to write DDS header with DX10 extension
	private static void writeDDSHeaderWithDX10(OutputStream out, int width, int height, int dxgiFormat) throws IOException {
		byte[] headerBytes = new byte[128];
		ByteBuffer buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN);
		
		buffer.putInt(0x20534444); // Magic "DDS "
		buffer.putInt(124); // Header size
		buffer.putInt(0x00081007); // Flags (DDSD_CAPS | DDSD_HEIGHT | DDSD_WIDTH | DDSD_PIXELFORMAT)
		buffer.putInt(height);
		buffer.putInt(width);
		buffer.putInt(width * height * 4); // Pitch/Linear size
		buffer.putInt(0); // Depth
		buffer.putInt(0); // Mipmaps
		
		// Reserved (11 ints)
		for (int i = 0; i < 11; i++)
			buffer.putInt(0);
		
		// Pixel format
		buffer.putInt(32); // size
		buffer.putInt(0x00000004); // DDPF_FOURCC
		buffer.putInt(DX10); // FOURCC code for DX10
		buffer.putInt(0); // RGB bit count
		buffer.putInt(0); // R mask
		buffer.putInt(0); // G mask
		buffer.putInt(0); // B mask
		buffer.putInt(0); // A mask
		
		// Caps
		buffer.putInt(0x00001000); // DDSCAPS_TEXTURE
		buffer.putInt(0);
		buffer.putInt(0);
		buffer.putInt(0);
		
		// Reserved
		buffer.putInt(0);
		
		out.write(headerBytes);
		
		// Write DX10 header (20 bytes)
		byte[] dx10Bytes = new byte[20];
		ByteBuffer dx10Buffer = ByteBuffer.wrap(dx10Bytes).order(ByteOrder.LITTLE_ENDIAN);
		dx10Buffer.putInt(dxgiFormat);        // DXGI format
		dx10Buffer.putInt(3);                 // D3D10_RESOURCE_DIMENSION_TEXTURE2D
		dx10Buffer.putInt(0);                 // Misc flag
		dx10Buffer.putInt(1);                 // Array size
		dx10Buffer.putInt(0);                 // Misc flags 2
		
		out.write(dx10Bytes);
	}
	
	// BC7 requires a DX10 header with the linear size correctly set for 16-byte blocks
	private static void writeDDSHeaderWithDX10BC7(OutputStream out, int width, int height) throws IOException {
		byte[] headerBytes = new byte[128];
		ByteBuffer buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN);
		
		buffer.putInt(0x20534444); // Magic "DDS "
		buffer.putInt(124); // Header size
		buffer.putInt(0x00081007); // Flags
		buffer.putInt(height);
		buffer.putInt(width);
		
		int blocksWide = (width + 3) / 4;
		int blocksHigh = (height + 3) / 4;
		buffer.putInt(blocksWide * blocksHigh * 16); // Linear size
		
		buffer.putInt(0); // Depth
		buffer.putInt(0); // Mipmaps
		
		for (int i = 0; i < 11; i++)
			buffer.putInt(0); // Reserved
		
		buffer.putInt(32); // size
		buffer.putInt(0x00000004); // DDPF_FOURCC
		buffer.putInt(DX10); // FOURCC code for DX10
		buffer.putInt(0);
		buffer.putInt(0);
		buffer.putInt(0);
		buffer.putInt(0);
		buffer.putInt(0);
		
		buffer.putInt(0x00001000); // Caps
		buffer.putInt(0);
		buffer.putInt(0);
		buffer.putInt(0);
		buffer.putInt(0);
		
		out.write(headerBytes);
		
		// Write DX10 header (20 bytes)
		byte[] dx10Bytes = new byte[20];
		ByteBuffer dx10Buffer = ByteBuffer.wrap(dx10Bytes).order(ByteOrder.LITTLE_ENDIAN);
		dx10Buffer.putInt(DXGI_FORMAT_BC7_UNORM); // DXGI format 98
		dx10Buffer.putInt(3); // Texture2D
		dx10Buffer.putInt(0);
		dx10Buffer.putInt(1);
		dx10Buffer.putInt(0);
		
		out.write(dx10Bytes);
	}
	
	private static byte[] compressBlockDXT1(byte[] argb, int width, int height) {
		int blocksWide = (width + 3) / 4;
		int blocksHigh = (height + 3) / 4;
		byte[] result = new byte[blocksWide * blocksHigh * 8];
		
		for (int by = 0; by < blocksHigh; by++) {
			for (int bx = 0; bx < blocksWide; bx++) {
				int[] blockColors = new int[16];
				int idx = 0;
				
				for (int py = 0; py < 4; py++) {
					for (int px = 0; px < 4; px++) {
						int x = bx * 4 + px;
						int y = by * 4 + py;
						
						if (x < width && y < height) {
							int pixelIdx = (y * width + x) * 4;
							int r = argb[pixelIdx] & 0xFF;
							int g = argb[pixelIdx + 1] & 0xFF;
							int b = argb[pixelIdx + 2] & 0xFF;
							int a = argb[pixelIdx + 3] & 0xFF;
							
							blockColors[idx++] = (a << 24) | (r << 16) | (g << 8) | b;
						} else {
							blockColors[idx++] = 0x00000000;
						}
					}
				}
				
				compressDXT1Block(blockColors, result, (by * blocksWide + bx) * 8);
			}
		}
		
		return result;
	}
	
	// ========== DXT1 COMPRESSION ==========
	
	private static void compressDXT1Block(int[] colors, byte[] output, int offset) {
		// Find min and max colors by luminance over RGB (ignore alpha channel in comparison)
		int minColor = colors[0];
		int maxColor = colors[0];
		
		for (int c : colors) {
			int rgb = c & 0x00FFFFFF;
			if (rgb < (minColor & 0x00FFFFFF))
				minColor = c;
			if (rgb > (maxColor & 0x00FFFFFF))
				maxColor = c;
		}
		
		// Convert to 565
		int c0 = rgbTo565(maxColor);
		int c1 = rgbTo565(minColor);
		
		// Determine if we need 3-color or 4-color mode
		boolean hasAlpha = false;
		for (int c : colors) {
			if (((c >> 24) & 0xFF) < 128) {
				hasAlpha = true;
				break;
			}
		}
		
		if (! hasAlpha) {
			// Force 4-color mode: ensure c0 > c1 after 565 encoding
			if (c0 <= c1) {
				int temp = c0;
				c0 = c1;
				c1 = temp;
			}
			// If still equal, nudge c0 up so decoder enters 4-colour mode
			if (c0 == c1 && c0 > 0)
				c1--;
		}
		// else: 3-color + transparent mode, c0 <= c1 is fine
		
		// Generate codes
		int[] palette = expandColorsDXT1(c0, c1);
		long bits = 0;
		
		for (int i = 0; i < 16; i++) {
			int bestDist = Integer.MAX_VALUE;
			int bestIdx = 0;
			int color = colors[i];
			
			// If pixel is transparent, always map to index 3 (transparent slot)
			if (((color >> 24) & 0xFF) < 128 && c0 <= c1) {
				bestIdx = 3;
			} else {
				for (int j = 0; j < palette.length; j++) {
					int dist = colorDistance(color, palette[j]);
					if (dist < bestDist) {
						bestDist = dist;
						bestIdx = j;
					}
				}
			}
			
			bits |= (long) bestIdx << (i * 2);
		}
		
		// Write block
		output[offset] = (byte) (c0 & 0xFF);
		output[offset + 1] = (byte) ((c0 >> 8) & 0xFF);
		output[offset + 2] = (byte) (c1 & 0xFF);
		output[offset + 3] = (byte) ((c1 >> 8) & 0xFF);
		output[offset + 4] = (byte) (bits & 0xFF);
		output[offset + 5] = (byte) ((bits >> 8) & 0xFF);
		output[offset + 6] = (byte) ((bits >> 16) & 0xFF);
		output[offset + 7] = (byte) ((bits >> 24) & 0xFF);
	}
	
	private static byte[] compressBlockDXT3(byte[] argb, int width, int height) {
		int blocksWide = (width + 3) / 4;
		int blocksHigh = (height + 3) / 4;
		byte[] result = new byte[blocksWide * blocksHigh * 16];
		
		for (int by = 0; by < blocksHigh; by++) {
			for (int bx = 0; bx < blocksWide; bx++) {
				int[] blockColors = new int[16];
				int[] alphas = new int[16];
				int idx = 0;
				
				for (int py = 0; py < 4; py++) {
					for (int px = 0; px < 4; px++) {
						int x = bx * 4 + px;
						int y = by * 4 + py;
						
						if (x < width && y < height) {
							int pixelIdx = (y * width + x) * 4;
							int r = argb[pixelIdx] & 0xFF;
							int g = argb[pixelIdx + 1] & 0xFF;
							int b = argb[pixelIdx + 2] & 0xFF;
							int a = argb[pixelIdx + 3] & 0xFF;
							
							blockColors[idx] = (r << 16) | (g << 8) | b;
							alphas[idx] = a;
						} else {
							blockColors[idx] = 0;
							alphas[idx] = 0;
						}
						idx++;
					}
				}
				
				compressDXT3Block(blockColors, alphas, result, (by * blocksWide + bx) * 16);
			}
		}
		
		return result;
	}
	
	// ========== DXT3 COMPRESSION ==========
	
	private static void compressDXT3Block(int[] colors, int[] alphas, byte[] output, int offset) {
		// Alpha part - 64 bits (16 entries of 4 bits)
		long alphaBits = 0;
		for (int i = 0; i < 16; i++) {
			int alpha = Math.min(15, alphas[i] / 17); // 8-bit to 4-bit
			alphaBits |= (long) alpha << (i * 4);
		}
		
		// FIX: compare only the RGB portion (lower 24 bits), not the full int,
		// to avoid sign-extension / packed-alpha corrupting the min/max selection
		// which caused random dark pixels ("black dots") in DXT3.
		int minColor = colors[0];
		int maxColor = colors[0];
		
		for (int c : colors) {
			if (c < minColor)
				minColor = c;
			if (c > maxColor)
				maxColor = c;
		}
		
		int c0 = rgbTo565(maxColor);
		int c1 = rgbTo565(minColor);
		
		// DXT3 always uses 4-colour mode — ensure c0 > c1
		if (c0 <= c1) {
			int tmp = c0;
			c0 = c1;
			c1 = tmp;
		}
		
		int[] palette = expandColorsDXT3(c0, c1);
		long bits = 0;
		
		for (int i = 0; i < 16; i++) {
			int bestDist = Integer.MAX_VALUE;
			int bestIdx = 0;
			int color = colors[i];
			
			for (int j = 0; j < 4; j++) {
				int dist = colorDistance(color, palette[j]);
				if (dist < bestDist) {
					bestDist = dist;
					bestIdx = j;
				}
			}
			
			bits |= (long) bestIdx << (i * 2);
		}
		
		// Write block
		for (int i = 0; i < 8; i++) {
			output[offset + i] = (byte) ((alphaBits >> (i * 8)) & 0xFF);
		}
		
		output[offset + 8] = (byte) (c0 & 0xFF);
		output[offset + 9] = (byte) ((c0 >> 8) & 0xFF);
		output[offset + 10] = (byte) (c1 & 0xFF);
		output[offset + 11] = (byte) ((c1 >> 8) & 0xFF);
		output[offset + 12] = (byte) (bits & 0xFF);
		output[offset + 13] = (byte) ((bits >> 8) & 0xFF);
		output[offset + 14] = (byte) ((bits >> 16) & 0xFF);
		output[offset + 15] = (byte) ((bits >> 24) & 0xFF);
	}
	
	private static byte[] compressBlockDXT5(byte[] argb, int width, int height) {
		int blocksWide = (width + 3) / 4;
		int blocksHigh = (height + 3) / 4;
		byte[] result = new byte[blocksWide * blocksHigh * 16];
		
		for (int by = 0; by < blocksHigh; by++) {
			for (int bx = 0; bx < blocksWide; bx++) {
				int[] blockColors = new int[16];
				int[] alphas = new int[16];
				int idx = 0;
				
				for (int py = 0; py < 4; py++) {
					for (int px = 0; px < 4; px++) {
						int x = bx * 4 + px;
						int y = by * 4 + py;
						
						if (x < width && y < height) {
							int pixelIdx = (y * width + x) * 4;
							int r = argb[pixelIdx] & 0xFF;
							int g = argb[pixelIdx + 1] & 0xFF;
							int b = argb[pixelIdx + 2] & 0xFF;
							int a = argb[pixelIdx + 3] & 0xFF;
							
							blockColors[idx] = (r << 16) | (g << 8) | b;
							alphas[idx] = a;
						} else {
							blockColors[idx] = 0;
							alphas[idx] = 0;
						}
						idx++;
					}
				}
				
				compressDXT5Block(blockColors, alphas, result, (by * blocksWide + bx) * 16);
			}
		}
		
		return result;
	}
	
	// ========== DXT5 COMPRESSION ==========
	
	private static void compressDXT5Block(int[] colors, int[] alphas, byte[] output, int offset) {
		// Alpha part - find min and max alpha
		int minAlpha = 255;
		int maxAlpha = 0;
		
		for (int a : alphas) {
			if (a < minAlpha)
				minAlpha = a;
			if (a > maxAlpha)
				maxAlpha = a;
		}
		
		int alpha0 = maxAlpha;
		int alpha1 = minAlpha;
		
		// Generate alpha codes using the full 8-interpolated-value table
		long alphaBits = 0;
		for (int i = 0; i < 16; i++) {
			int code = getAlphaCodeDXT5(alphas[i], alpha0, alpha1);
			alphaBits |= (long) code << (i * 3);
		}
		
		// Color part
		int minColor = colors[0];
		int maxColor = colors[0];
		
		for (int c : colors) {
			if (c < minColor)
				minColor = c;
			if (c > maxColor)
				maxColor = c;
		}
		
		int c0 = rgbTo565(maxColor);
		int c1 = rgbTo565(minColor);
		
		// DXT5 always uses 4-colour mode — ensure c0 > c1
		if (c0 <= c1) {
			int tmp = c0;
			c0 = c1;
			c1 = tmp;
		}
		
		int[] palette = expandColorsDXT5(c0, c1);
		long bits = 0;
		
		for (int i = 0; i < 16; i++) {
			int bestDist = Integer.MAX_VALUE;
			int bestIdx = 0;
			int color = colors[i];
			
			for (int j = 0; j < 4; j++) {
				int dist = colorDistance(color, palette[j]);
				if (dist < bestDist) {
					bestDist = dist;
					bestIdx = j;
				}
			}
			
			bits |= (long) bestIdx << (i * 2);
		}
		
		// Write block
		output[offset] = (byte) alpha0;
		output[offset + 1] = (byte) alpha1;
		
		for (int i = 0; i < 6; i++) {
			output[offset + 2 + i] = (byte) ((alphaBits >> (i * 8)) & 0xFF);
		}
		
		output[offset + 8] = (byte) (c0 & 0xFF);
		output[offset + 9] = (byte) ((c0 >> 8) & 0xFF);
		output[offset + 10] = (byte) (c1 & 0xFF);
		output[offset + 11] = (byte) ((c1 >> 8) & 0xFF);
		output[offset + 12] = (byte) (bits & 0xFF);
		output[offset + 13] = (byte) ((bits >> 8) & 0xFF);
		output[offset + 14] = (byte) ((bits >> 16) & 0xFF);
		output[offset + 15] = (byte) ((bits >> 24) & 0xFF);
	}
	
	private static int rgbTo565(int rgb) {
		int r = ((rgb >> 16) & 0xFF) >> 3;
		int g = ((rgb >> 8) & 0xFF) >> 2;
		int b = (rgb & 0xFF) >> 3;
		return (r << 11) | (g << 5) | b;
	}
	
	// ========== HELPER METHODS ==========
	
	private static int colorDistance(int c1, int c2) {
		int r1 = (c1 >> 16) & 0xFF;
		int g1 = (c1 >> 8) & 0xFF;
		int b1 = c1 & 0xFF;
		int r2 = (c2 >> 16) & 0xFF;
		int g2 = (c2 >> 8) & 0xFF;
		int b2 = c2 & 0xFF;
		
		int dr = r1 - r2;
		int dg = g1 - g2;
		int db = b1 - b2;
		
		return dr * dr + dg * dg + db * db;
	}
	
	/**
	 * Find the best 3-bit alpha code (0-7) for a given alpha value
	 * against the interpolated DXT5 palette built from alpha0/alpha1.
	 * alpha0 > alpha1 → 8-value mode (codes 0-7 all interpolated).
	 */
	private static int getAlphaCodeDXT5(int alpha, int alpha0, int alpha1) {
		// Build the full 8-entry lookup table and pick the closest entry
		int[] table = new int[8];
		if (alpha0 > alpha1) {
			table[0] = alpha0;
			table[1] = alpha1;
			for (int i = 2; i <= 7; i++) {
				table[i] = ((8 - i) * alpha0 + (i - 1) * alpha1) / 7;
			}
		} else {
			table[0] = alpha0;
			table[1] = alpha1;
			for (int i = 2; i <= 5; i++) {
				table[i] = ((6 - i) * alpha0 + (i - 1) * alpha1) / 5;
			}
			table[6] = 0;
			table[7] = 255;
		}
		
		int bestCode = 0;
		int bestDist = Integer.MAX_VALUE;
		for (int i = 0; i < 8; i++) {
			int d = Math.abs(alpha - table[i]);
			if (d < bestDist) {
				bestDist = d;
				bestCode = i;
			}
		}
		return bestCode;
	}
	
	public static class DDSImage {
		public final int width;
		public final int height;
		public final byte[] pixels; // RGBA format, 4 bytes per pixel (R, G, B, A)
		
		private DDSImage(int width, int height, byte[] pixels) {
			this.width = width;
			this.height = height;
			this.pixels = pixels;
		}
		
		public static DDSImage fromBufferedImage(BufferedImage image) {
			int width = image.getWidth();
			int height = image.getHeight();
			byte[] argb = new byte[width * height * 4];
			
			int[] rgbData = image.getRGB(0, 0, width, height, null, 0, width);
			for (int i = 0; i < rgbData.length; i++) {
				int pixel = rgbData[i];
				argb[i * 4] = (byte) ((pixel >> 16) & 0xFF); // R
				argb[i * 4 + 1] = (byte) ((pixel >> 8) & 0xFF); // G
				argb[i * 4 + 2] = (byte) (pixel & 0xFF); // B
				argb[i * 4 + 3] = (byte) ((pixel >> 24) & 0xFF); // A
			}
			return new DDSImage(width, height, argb);
		}
		
		/**
		 * Convert to BufferedImage (if you have AWT available)
		 */
		public BufferedImage toBufferedImage() {
			final var img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			
			int[] argb = new int[width * height];
			for (int i = 0; i < argb.length; i++) {
				int base = i * 4;
				argb[i] = ((pixels[base + 3] & 0xFF) << 24) | ((pixels[base] & 0xFF) << 16) | ((pixels[base + 1] & 0xFF) << 8) | (pixels[base + 2] & 0xFF);
			}
			img.setRGB(0, 0, width, height, argb, 0, width);
			return img;
		}
	}
}


class BC7Util {
	public static void decompressBC7(ImgData data, byte[] out) {
		byte[] blockData = data.data;
		int width = data.w;
		int height = data.h;
		int blocksWide = (width + 3) / 4;
		
		for (int by = 0; by < (height + 3) / 4; by++) {
			for (int bx = 0; bx < (width + 3) / 4; bx++) {
				int blockOffset = (by * blocksWide + bx) * 16;
				if (blockOffset + 16 > blockData.length)
					break;
				
				byte[] block = Arrays.copyOfRange(blockData, blockOffset, blockOffset + 16);
				int[] decodedBlock = decodeBC7Block(block);
				
				for (int py = 0; py < 4; py++) {
					for (int px = 0; px < 4; px++) {
						int x = bx * 4 + px;
						int y = by * 4 + py;
						if (x < width && y < height) {
							int outIdx = (y * width + x) * 4;
							int color = decodedBlock[py * 4 + px];
							out[outIdx] = (byte) ((color >> 16) & 0xFF); // R
							out[outIdx + 1] = (byte) ((color >> 8) & 0xFF); // G
							out[outIdx + 2] = (byte) (color & 0xFF); // B
							out[outIdx + 3] = (byte) ((color >> 24) & 0xFF); // A
						}
					}
				}
			}
		}
	}
	
	private static int[] decodeBC7Block(byte[] block) {
		BitReader reader = new BitReader(block);
		
		// 1. Find the Mode
		int mode = - 1;
		for (int i = 0; i < 8; i++) {
			if (reader.read(1) == 1) {
				mode = i;
				break;
			}
		}
		
		if (mode == - 1 || mode == 8) {
			int[] err = new int[16];
			Arrays.fill(err, 0xFF000000); // Black w/ Alpha on error
			return err;
		}
		
		// Table-driven mode specs
		int[] numSubsetsTable = { 3, 2, 3, 2, 1, 1, 1, 2 };
		int[] partitionBitsTable = { 4, 6, 6, 6, 0, 0, 0, 6 };
		int[] colorBitsTable = { 4, 6, 5, 7, 5, 7, 7, 5 };
		int[] alphaBitsTable = { 0, 0, 0, 0, 6, 8, 7, 5 };
		int[] indexBitsTable = { 3, 3, 2, 2, 2, 2, 4, 2 }; // Mode 4 indexBits can be 2 or 3 (Idx Selection)
		
		int numSubsets = numSubsetsTable[mode];
		int partitionIdx = reader.read(partitionBitsTable[mode]);
		int rotation = (mode == 4 || mode == 5) ? reader.read(2) : 0;
		int idxSelection = (mode == 4) ? reader.read(1) : 0; // 0: Color uses 2 bits, 1: Color uses 3 bits
		
		int colorBits = colorBitsTable[mode];
		int alphaBits = alphaBitsTable[mode];
		int indexBitsColor = (mode == 4 && idxSelection == 0) ? 2 : (mode == 4 && idxSelection == 1) ? 3 : indexBitsTable[mode];
		int indexBitsAlpha = (mode == 4 && idxSelection == 0) ? 3 : (mode == 4 && idxSelection == 1) ? 2 : indexBitsTable[mode];
		
		// 2. Read Endpoints (Interleaved correctly per channel)
		int[][] r = new int[numSubsets][2], g = new int[numSubsets][2];
		int[][] b = new int[numSubsets][2], a = new int[numSubsets][2];
		
		for (int s = 0; s < numSubsets; s++) {
			r[s][0] = reader.read(colorBits);
			r[s][1] = reader.read(colorBits);
		}
		for (int s = 0; s < numSubsets; s++) {
			g[s][0] = reader.read(colorBits);
			g[s][1] = reader.read(colorBits);
		}
		for (int s = 0; s < numSubsets; s++) {
			b[s][0] = reader.read(colorBits);
			b[s][1] = reader.read(colorBits);
		}
		if (alphaBits > 0) {
			for (int s = 0; s < numSubsets; s++) {
				a[s][0] = reader.read(alphaBits);
				a[s][1] = reader.read(alphaBits);
			}
		}
		
		// 3. Read P-Bits
		int[][] pBits = new int[numSubsets][2];
		if (mode == 0 || mode == 3 || mode == 6 || mode == 7) {
			for (int s = 0; s < numSubsets; s++) {
				pBits[s][0] = reader.read(1);
				pBits[s][1] = (mode == 1) ? pBits[s][0] : reader.read(1); // Mode 1 shares P-bits
			}
		} else if (mode == 1) { // Shared p-bit for mode 1
			for (int s = 0; s < numSubsets; s++) {
				int p = reader.read(1);
				pBits[s][0] = p;
				pBits[s][1] = p;
			}
		}
		
		// 4. Expand Endpoints to 8-bit
		for (int s = 0; s < numSubsets; s++) {
			r[s][0] = expand(r[s][0], colorBits, pBits[s][0]);
			r[s][1] = expand(r[s][1], colorBits, pBits[s][1]);
			g[s][0] = expand(g[s][0], colorBits, pBits[s][0]);
			g[s][1] = expand(g[s][1], colorBits, pBits[s][1]);
			b[s][0] = expand(b[s][0], colorBits, pBits[s][0]);
			b[s][1] = expand(b[s][1], colorBits, pBits[s][1]);
			if (alphaBits > 0) {
				a[s][0] = expand(a[s][0], alphaBits, pBits[s][0]);
				a[s][1] = expand(a[s][1], alphaBits, pBits[s][1]);
			} else {
				a[s][0] = 255;
				a[s][1] = 255;
			}
		}
		
		// 5. Read Indices (Accounting for Anchors)
		int[] partitionTable = getBC7Partition(numSubsets, partitionIdx);
		int[] anchors = getAnchors(partitionTable, numSubsets);
		
		int[] colorIndices = new int[16];
		int[] alphaIndices = new int[16];
		
		for (int i = 0; i < 16; i++) {
			int sub = partitionTable[i];
			int bits = indexBitsColor - ((i == anchors[sub]) ? 1 : 0); // Drop MSB for anchor pixel
			colorIndices[i] = reader.read(bits);
		}
		
		if (mode == 4 || mode == 5) {
			for (int i = 0; i < 16; i++) {
				int bits = indexBitsAlpha - ((i == anchors[0]) ? 1 : 0);
				alphaIndices[i] = reader.read(bits);
			}
		} else {
			alphaIndices = colorIndices; // Shared indices for modes 0,1,2,3,6,7
		}
		
		// 6. Interpolate and Assemble Result
		int[] result = new int[16];
		for (int i = 0; i < 16; i++) {
			int s = partitionTable[i];
			int finalR = interpolate(r[s][0], r[s][1], colorIndices[i], indexBitsColor);
			int finalG = interpolate(g[s][0], g[s][1], colorIndices[i], indexBitsColor);
			int finalB = interpolate(b[s][0], b[s][1], colorIndices[i], indexBitsColor);
			int finalA = interpolate(a[s][0], a[s][1], alphaIndices[i], indexBitsAlpha);
			
			// Apply Rotation (Modes 4 and 5)
			if (rotation == 1) {
				int temp = finalA;
				finalA = finalR;
				finalR = temp;
			}
			if (rotation == 2) {
				int temp = finalA;
				finalA = finalG;
				finalG = temp;
			}
			if (rotation == 3) {
				int temp = finalA;
				finalA = finalB;
				finalB = temp;
			}
			
			result[i] = (finalA << 24) | (finalR << 16) | (finalG << 8) | finalB;
		}
		
		return result;
	}
	
	// Correctly appends P-bit as LSB before expanding to 8 bits
	private static int expand(int val, int bits, int pBit) {
		if (bits == 0)
			return 0;
		int combinedBits = val;
		int totalBits = bits;
		
		if (pBit >= 0 && bits < 8) { // If P-Bit exists, append it
			combinedBits = (combinedBits << 1) | pBit;
			totalBits += 1;
		}
		
		if (totalBits == 4)
			return (combinedBits << 4) | combinedBits;
		if (totalBits == 5)
			return (combinedBits << 3) | (combinedBits >> 2);
		if (totalBits == 6)
			return (combinedBits << 2) | (combinedBits >> 4);
		if (totalBits == 7)
			return (combinedBits << 1) | (combinedBits >> 6);
		return combinedBits;
	}
	
	// BC7 Uses strict, pre-calculated weights, not linear formulas
	private static int interpolate(int e0, int e1, int index, int indexBits) {
		int[] weights2 = { 0, 21, 43, 64 };
		int[] weights3 = { 0, 9, 18, 27, 37, 46, 55, 64 };
		int[] weights4 = { 0, 4, 9, 13, 17, 21, 26, 30, 34, 38, 43, 47, 51, 55, 60, 64 };
		
		int w = 0;
		if (indexBits == 2)
			w = weights2[index];
		if (indexBits == 3)
			w = weights3[index];
		if (indexBits == 4)
			w = weights4[index];
		
		return ((64 - w) * e0 + w * e1 + 32) >> 6;
	}
	
	private static int[] getAnchors(int[] partition, int numSubsets) {
		int[] anchors = new int[numSubsets];
		anchors[0] = 0; // Subset 0 anchor is always pixel 0
		if (numSubsets > 1) {
			for (int i = 0; i < 16; i++)
				if (partition[i] == 1) {
					anchors[1] = i;
					break;
				}
		}
		if (numSubsets > 2) {
			for (int i = 0; i < 16; i++)
				if (partition[i] == 2) {
					anchors[2] = i;
					break;
				}
		}
		return anchors;
	}
	
	// Note: To fully implement modes 0, 1, 2, 3, and 7, you need the exact 64 integer arrays
	// from the BC7 specification. This mocks it safely for single-subset blocks.
	private static int[] getBC7Partition(int numSubsets, int partitionIdx) {
		int[] partition = new int[16];
		if (numSubsets == 1)
			return partition;
		
		// TODO: Map the 64 BC7 specification partition arrays here.
		// For testing/fallback, we just set to 0.
		return partition;
	}
	
	public static byte[] compressBC7(byte[] argb, int width, int height) {
		int blocksWide = (width + 3) / 4;
		int blocksHigh = (height + 3) / 4;
		byte[] result = new byte[blocksWide * blocksHigh * 16];
		
		for (int by = 0; by < blocksHigh; by++) {
			for (int bx = 0; bx < blocksWide; bx++) {
				int[] blockColors = new int[16];
				int idx = 0;
				
				// Extract the 4x4 block
				for (int py = 0; py < 4; py++) {
					for (int px = 0; px < 4; px++) {
						int x = bx * 4 + px;
						int y = by * 4 + py;
						
						if (x < width && y < height) {
							int pixelIdx = (y * width + x) * 4;
							int r = argb[pixelIdx] & 0xFF;
							int g = argb[pixelIdx + 1] & 0xFF;
							int b = argb[pixelIdx + 2] & 0xFF;
							int a = argb[pixelIdx + 3] & 0xFF;
							blockColors[idx] = (a << 24) | (r << 16) | (g << 8) | b;
						} else {
							blockColors[idx] = 0;
						}
						idx++;
					}
				}
				
				compressBC7BlockMode6(blockColors, result, (by * blocksWide + bx) * 16);
			}
		}
		return result;
	}
	
	private static void compressBC7BlockMode6(int[] blockColors, byte[] output, int offset) {
		// 1. Find the two most extreme colors in the block (bounding box)
		int minColor = blockColors[0];
		int maxColor = blockColors[0];
		int maxDist = - 1;
		
		for (int i = 0; i < 16; i++) {
			for (int j = i + 1; j < 16; j++) {
				int dist = colorDistance(blockColors[i], blockColors[j]);
				if (dist > maxDist) {
					maxDist = dist;
					minColor = blockColors[i];
					maxColor = blockColors[j];
				}
			}
		}
		
		// 2. Mode 6 uses 7-bit endpoints.
		// We drop the LSB (>> 1). We also set the shared P-Bit to 0 for simplicity.
		int e0_a7 = ((minColor >> 24) & 0xFF) >> 1;
		int e0_r7 = ((minColor >> 16) & 0xFF) >> 1;
		int e0_g7 = ((minColor >> 8) & 0xFF) >> 1;
		int e0_b7 = (minColor & 0xFF) >> 1;
		
		int e1_a7 = ((maxColor >> 24) & 0xFF) >> 1;
		int e1_r7 = ((maxColor >> 16) & 0xFF) >> 1;
		int e1_g7 = ((maxColor >> 8) & 0xFF) >> 1;
		int e1_b7 = (maxColor & 0xFF) >> 1;
		
		// Expanded 8-bit versions to evaluate exactly what the decoder will see
		int e0_a8 = e0_a7 << 1;
		int e0_r8 = e0_r7 << 1;
		int e0_g8 = e0_g7 << 1;
		int e0_b8 = e0_b7 << 1;
		int e1_a8 = e1_a7 << 1;
		int e1_r8 = e1_r7 << 1;
		int e1_g8 = e1_g7 << 1;
		int e1_b8 = e1_b7 << 1;
		
		int[] weights4 = { 0, 4, 9, 13, 17, 21, 26, 30, 34, 38, 43, 47, 51, 55, 60, 64 };
		
		// 3. The Anchor Index Constraint
		// In BC7, the index of the first pixel (pixel 0) drops its highest bit.
		// Thus, its index MUST fall between 0 and 7. If it falls between 8 and 15, we swap the endpoints.
		int p0_idx = findClosestBC7Index(blockColors[0], e0_r8, e0_g8, e0_b8, e0_a8, e1_r8, e1_g8, e1_b8, e1_a8, weights4);
		
		if (p0_idx >= 8) {
			// Swap endpoints to invert the index (15 - index)
			int t = e0_a7;
			e0_a7 = e1_a7;
			e1_a7 = t;
			t = e0_r7;
			e0_r7 = e1_r7;
			e1_r7 = t;
			t = e0_g7;
			e0_g7 = e1_g7;
			e1_g7 = t;
			t = e0_b7;
			e0_b7 = e1_b7;
			e1_b7 = t;
			
			t = e0_a8;
			e0_a8 = e1_a8;
			e1_a8 = t;
			t = e0_r8;
			e0_r8 = e1_r8;
			e1_r8 = t;
			t = e0_g8;
			e0_g8 = e1_g8;
			e1_g8 = t;
			t = e0_b8;
			e0_b8 = e1_b8;
			e1_b8 = t;
		}
		
		// Calculate indices for all 16 pixels
		int[] indices = new int[16];
		for (int i = 0; i < 16; i++) {
			indices[i] = findClosestBC7Index(blockColors[i], e0_r8, e0_g8, e0_b8, e0_a8, e1_r8, e1_g8, e1_b8, e1_a8, weights4);
		}
		
		// 4. Pack Bits exactly to BC7 Specification
		BitWriter bw = new BitWriter(output, offset);
		
		bw.write(0x40, 7); // Mode 6 is a unary 6 (binary: 01000000)
		
		bw.write(e0_r7, 7);
		bw.write(e1_r7, 7);
		bw.write(e0_g7, 7);
		bw.write(e1_g7, 7);
		bw.write(e0_b7, 7);
		bw.write(e1_b7, 7);
		bw.write(e0_a7, 7);
		bw.write(e1_a7, 7);
		
		bw.write(0, 1);
		bw.write(0, 1); // P-Bits (set to 0)
		
		for (int i = 0; i < 16; i++) {
			if (i == 0) {
				bw.write(indices[i], 3); // Anchor drops MSB
			} else {
				bw.write(indices[i], 4);
			}
		}
	}
	
	private static int findClosestBC7Index(int color, int r0, int g0, int b0, int a0, int r1, int g1, int b1, int a1, int[] weights) {
		int a = (color >> 24) & 0xFF;
		int r = (color >> 16) & 0xFF;
		int g = (color >> 8) & 0xFF;
		int b = color & 0xFF;
		
		int bestIdx = 0;
		int bestDist = Integer.MAX_VALUE;
		
		for (int i = 0; i < 16; i++) {
			int w = weights[i];
			int ia = ((64 - w) * a0 + w * a1 + 32) >> 6;
			int ir = ((64 - w) * r0 + w * r1 + 32) >> 6;
			int ig = ((64 - w) * g0 + w * g1 + 32) >> 6;
			int ib = ((64 - w) * b0 + w * b1 + 32) >> 6;
			
			int da = a - ia;
			int dr = r - ir;
			int dg = g - ig;
			int db = b - ib;
			int dist = (dr * dr) + (dg * dg) + (db * db) + (da * da);
			
			if (dist < bestDist) {
				bestDist = dist;
				bestIdx = i;
			}
		}
		return bestIdx;
	}
	
	private static int colorDistance(int c1, int c2) {
		int a1 = (c1 >> 24) & 0xFF;
		int r1 = (c1 >> 16) & 0xFF;
		int g1 = (c1 >> 8) & 0xFF;
		int b1 = c1 & 0xFF;
		int a2 = (c2 >> 24) & 0xFF;
		int r2 = (c2 >> 16) & 0xFF;
		int g2 = (c2 >> 8) & 0xFF;
		int b2 = c2 & 0xFF;
		int dr = r1 - r2;
		int dg = g1 - g2;
		int db = b1 - b2;
		int da = a1 - a2;
		return (dr * dr) + (dg * dg) + (db * db) + (da * da);
	}
	
	// Inverse of the BitReader you currently have
	static class BitWriter {
		byte[] data;
		int bitPos;
		
		BitWriter(byte[] data, int byteOffset) {
			this.data = data;
			this.bitPos = byteOffset * 8;
			for (int i = 0; i < 16; i++) {
				data[byteOffset + i] = 0; // Clear the memory block first
			}
		}
		
		void write(int val, int numBits) {
			for (int i = 0; i < numBits; i++) {
				int bit = (val >> i) & 1;
				int byteIdx = bitPos / 8;
				if (bit == 1) {
					data[byteIdx] |= (1 << (bitPos % 8));
				}
				bitPos++;
			}
		}
	}
	
	// Stateful bit reader for traversing the 128-bit blocks safely
	static class BitReader {
		byte[] data;
		int bitPos = 0;
		
		BitReader(byte[] data) {
			this.data = data;
		}
		
		int read(int numBits) {
			if (numBits == 0)
				return 0;
			int result = 0;
			for (int i = 0; i < numBits; i++) {
				int byteIdx = bitPos / 8;
				if (byteIdx >= data.length)
					break;
				int bit = (data[byteIdx] >> (bitPos % 8)) & 1;
				result |= (bit << i);
				bitPos++;
			}
			return result;
		}
	}
}

class ImgData {
	int h;
	int w;
	int code;
	int length;
	byte[] data;
	DDSHeaderDXT10 header;
	
	public ImgData(int h, int w, int code, int length, DDSHeaderDXT10 header) {
		this.h = h;
		this.w = w;
		this.code = code;
		this.length = length;
		this.header = header;
	}
}

record DDSHeaderDXT10(int dxgiFormat, int resourceDimension, int miscFlag, int arraySize, int miscFlags2) {
}

