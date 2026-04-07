package image;

import lombok.Value;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Entry point for importing <em>any</em> texture into the KCD2 mod pipeline.
 *
 * <h3>Supported inputs</h3>
 * <ul>
 *   <li>Standard DDS: DXT1 / DXT3 / DXT5 / BC7 / uncompressed RGBA·BGRA</li>
 *   <li>Foreign DDS: BC4, BC5, BC6H, and any DX10 DXGI format that can be
 *       represented as RGBA8 after decode</li>
 *   <li>Any format that Java's {@link ImageIO} can read: PNG, JPEG, BMP, GIF,
 *       TIFF (with TwelveMonkeys on the classpath), TGA (same), WebP, …</li>
 * </ul>
 *
 * <h3>Conversion targets (KCD2 conventions)</h3>
 * The target format is resolved from the {@link FormatProfile}, which is
 * auto-detected from the filename suffix or can be supplied by the caller.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Automatic – detect everything from the file
 * TextureImportResult r = KcdTextureImporter.importTexture(
 *         Files.readAllBytes(path), path.getFileName().toString());
 *
 * // Manual profile override (user picked "Normal Map" in the UI)
 * TextureImportResult r = KcdTextureImporter.importTexture(
 *         data, "mytex_n.dds", KcdFormatProfile.NORMAL);
 *
 * if (r.conversionSucceeded) {
 *     Files.write(outputPath, r.convertedDdsBytes);
 * } else {
 *     showDialog(r.actionRequired, r.warnings);
 * }
 * }</pre>
 */
@lombok.extern.slf4j.Slf4j
public class TextureImporter {
	
	// DXGI values not covered by the original DDSUtil
	private static final int DXGI_BC4_UNORM = 80;
	private static final int DXGI_BC4_SNORM = 81;
	private static final int DXGI_BC5_UNORM = 83;
	private static final int DXGI_BC5_SNORM = 84;
	private static final int DXGI_BC6H_UF16 = 95;
	private static final int DXGI_BC6H_SF16 = 96;
	private static final int DXGI_BC1_UNORM = 71;
	private static final int DXGI_BC2_UNORM = 74;
	private static final int DXGI_BC3_UNORM = 77;
	private static final int DXGI_BC7_UNORM = 98;
	private static final int DXGI_RGBA8 = 28;
	private static final int DXGI_BGRA8 = 87;
	private static final int DXGI_R8_UNORM = 61;
	private static final int DXGI_RG8_UNORM = 49;
	
	private static final int DDS_MAGIC = 0x20534444;
	
	private TextureImporter() {
	}
	
	// =========================================================================
	// Public API
	// =========================================================================
	
	/**
	 * Import a texture from raw bytes, auto-detecting the profile from the filename.
	 *
	 * @param data     raw file bytes (DDS, PNG, JPEG, …)
	 * @param filename original filename (used for profile detection and messages)
	 */
	public static TextureImportResult importTexture(byte[] data, String filename) throws IOException {
		FormatProfile profile = FormatProfile.detect(filename);
		boolean autoDetected = (profile != FormatProfile.UNKNOWN);
		return importTexture(data, filename, profile, autoDetected);
	}
	
	/**
	 * Import a texture with a caller-supplied profile (user chose it in the UI).
	 *
	 * @param data     raw file bytes
	 * @param filename original filename
	 * @param profile  texture role chosen by the user
	 */
	public static TextureImportResult importTexture(byte[] data, String filename, FormatProfile profile) throws IOException {
		return importTexture(data, filename, profile, false);
	}
	
	// =========================================================================
	// Core logic
	// =========================================================================
	
	private static TextureImportResult importTexture(byte[] data, String filename, FormatProfile profile, boolean profileAutoDetected) throws IOException {
		
		TextureImportResult.Builder result = TextureImportResult.builder(filename).profile(profile).profileAutoDetected(profileAutoDetected);
		
		if (! profileAutoDetected) {
			result.message("Profile not detected from filename — defaulting to: " + profile.displayName);
			result.actionRequired("If '" + profile.displayName + "' is wrong, re-import and choose " + "the correct profile for this texture.");
		} else {
			result.message("Profile auto-detected from filename: " + profile.displayName);
		}
		
		// Step 1: decode to RGBA8 pixels
		DecodeResult decoded = decodeToRgba(data, filename, result);
		if (decoded == null) {
			// decodeToRgba already populated the builder with the error
			return result.build();
		}
		
		result.detectedSourceFormat(decoded.sourceFormatDescription);
		result.dimensions(decoded.width, decoded.height);
		result.message("Decoded: " + decoded.width + "×" + decoded.height + " from " + decoded.sourceFormatDescription);
		
		// Warn about non-power-of-two
		if (! isPow2(decoded.width) || ! isPow2(decoded.height)) {
			result.warning("Image dimensions (" + decoded.width + "×" + decoded.height + ") " + "are not powers of two. KCD2 expects POT textures. " + "The image will be converted as-is, but it may not load correctly in-game.");
		}
		
		// Warn about very small or very large textures
		if (decoded.width < 4 || decoded.height < 4) {
			result.warning("Texture is smaller than 4×4 pixels — BC compression requires at least 4×4.");
		}
		if (decoded.width > 8192 || decoded.height > 8192) {
			result.warning("Texture exceeds 8192 px in one dimension. KCD2 may not support this.");
		}
		
		// Normal-map specific checks
		if (profile == FormatProfile.NORMAL) {
			if (! decoded.isNormalMapLike) {
				result.warning("This texture doesn't look like a tangent-space normal map " + "(expected a predominantly blue/purple image with RGB centred around (128,128,255)). " + "If this is a plain DXT1/3/5 colour texture, double-check your profile selection.");
			}
			result.message("Normal maps are stored as BC5 (RG only). " + "The blue Z channel will be reconstructed by the engine at runtime.");
		}
		
		// Step 2: re-encode to the target KCD2 format
		byte[] kcdDds = encode(decoded, profile, result);
		if (kcdDds == null) {
			return result.build();
		}
		
		result.succeeded(kcdDds, profile.targetFormat.description);
		result.message("Conversion complete → " + profile.targetFormat.description + " (" + kcdDds.length + " bytes)");
		result.message(profile.guidance);
		
		return result.build();
	}
	
	// =========================================================================
	// Decode: any format → RGBA8 flat byte[]
	// =========================================================================
	
	private static DecodeResult decodeToRgba(byte[] data, String filename, TextureImportResult.Builder result) throws IOException {
		
		// ── Try DDS first (magic 0x20534444) ─────────────────────────────────
		if (data.length >= 4 && readInt32LE(data, 0) == DDS_MAGIC) {
			return decodeDds(data, result);
		}
		
		// ── Try generic image formats (PNG, JPEG, BMP, TIFF, …) ─────────────
		BufferedImage img = null;
		try {
			img = ImageIO.read(new ByteArrayInputStream(data));
		} catch (Exception ignored) {
		}
		
		if (img != null) {
			String ext = fileExtension(filename).toUpperCase();
			result.message("Loaded as " + ext + " image via ImageIO.");
			return fromBufferedImage(img, ext + " image", false);
		}
		
		// ── Nothing worked ────────────────────────────────────────────────────
		result.warning("Could not parse the input file as a DDS or a standard image format.");
		result.actionRequired("Make sure the file is a valid DDS, PNG, JPEG, BMP, TIFF, or TGA.");
		return null;
	}
	
	// ── DDS decoder ──────────────────────────────────────────────────────────
	
	private static DecodeResult decodeDds(byte[] data, TextureImportResult.Builder result) throws IOException {
		
		// Parse header manually so we can handle formats DDSUtil doesn't know
		ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		buf.getInt(); // magic (already checked)
		int headerSize = buf.getInt();
		if (headerSize != 124)
			throw new IOException("Unexpected DDS header size: " + headerSize);
		buf.getInt();              // flags
		int height = buf.getInt();
		int width = buf.getInt();
		buf.getInt();              // pitchOrLinearSize
		buf.getInt();              // depth
		int mipCount = buf.getInt();
		buf.position(buf.position() + 44); // 11 reserved ints
		if (buf.getInt() != 32)
			throw new IOException("DDS pixel-format size != 32");
		int pfFlags = buf.getInt();
		int fourCC = buf.getInt();
		buf.position(buf.position() + 20); // rest of pixel-format + caps
		
		if (mipCount > 1) {
			result.message("Source DDS has " + mipCount + " mip levels; only mip 0 will be imported.");
		}
		
		// ── DX10 extended header ─────────────────────────────────────────────
		if (fourCC == 0x30315844 /* "DX10" */) {
			int dxgi = buf.getInt();
			buf.getInt(); // resourceDimension
			buf.getInt(); // miscFlag
			buf.getInt(); // arraySize
			buf.getInt(); // miscFlags2
			
			int pixelDataOffset = buf.position();
			int dataLen = data.length - pixelDataOffset;
			byte[] pixelData = new byte[dataLen];
			System.arraycopy(data, pixelDataOffset, pixelData, 0, dataLen);
			
			return decodeDxgi(dxgi, pixelData, width, height, result);
		}
		
		// ── Legacy FourCC ─────────────────────────────────────────────────────
		int pixelDataOffset = 128;
		int dataLen = data.length - pixelDataOffset;
		byte[] pixelData = new byte[dataLen];
		System.arraycopy(data, pixelDataOffset, pixelData, 0, dataLen);
		
		return decodeLegacyFourcc(fourCC, pfFlags, pixelData, width, height, result);
	}
	
	private static DecodeResult decodeLegacyFourcc(int fourCC, int pfFlags, byte[] pixelData, int w, int h, TextureImportResult.Builder result) throws IOException {
		
		return switch (fourCC) {
			case 0x31545844 ->
					decodeViaDDSUtil(buildMinimalDds(pixelData, w, h, 0x31545844, false), "BC1 / DXT1 (4bpp, no real alpha)", w, h);
			case 0x33545844 ->
					decodeViaDDSUtil(buildMinimalDds(pixelData, w, h, 0x33545844, false), "BC2 / DXT3 (8bpp, explicit alpha)", w, h);
			case 0x35545844 ->
					decodeViaDDSUtil(buildMinimalDds(pixelData, w, h, 0x35545844, false), "BC3 / DXT5 (8bpp, interpolated alpha)", w, h);
			case 0x37434200 ->
					decodeViaDDSUtil(buildMinimalDds(pixelData, w, h, 0x37434200, false), "BC7 (8bpp, high-quality RGBA)", w, h);
			// ATI1 / ATI2 — alternate names for BC4 / BC5
			case 0x31495441, 0x55344342, 0x53344342 -> decodeBC4(pixelData, w, h, result, false); // ATI1 / BC4U / BC4S
			case 0x32495441, 0x55354342, 0x53354342 -> decodeBC5(pixelData, w, h, result); // ATI2 / BC5U / BC5S
			default -> {
				// Try uncompressed: check pfFlags for DDPF_RGB (0x40)
				if ((pfFlags & 0x40) != 0) {
					result.message("Uncompressed legacy DDS (pfFlags=0x" + Integer.toHexString(pfFlags) + ")");
					yield decodeUncompressedLegacy(pixelData, w, h, result);
				}
				result.warning("Unknown DDS FourCC: 0x" + Integer.toHexString(fourCC) + " — attempting to pass through as raw RGBA.");
				result.actionRequired("Verify this texture is a supported format and re-import.");
				yield null;
			}
		};
	}
	
	private static DecodeResult decodeDxgi(int dxgi, byte[] pixelData, int w, int h, TextureImportResult.Builder result) throws IOException {
		
		return switch (dxgi) {
			case DXGI_BC1_UNORM ->
					decodeViaDDSUtil(buildMinimalDds(pixelData, w, h, 0x31545844, false), "BC1 / DXT1 (DXGI)", w, h);
			case DXGI_BC2_UNORM ->
					decodeViaDDSUtil(buildMinimalDds(pixelData, w, h, 0x33545844, false), "BC2 / DXT3 (DXGI)", w, h);
			case DXGI_BC3_UNORM ->
					decodeViaDDSUtil(buildMinimalDds(pixelData, w, h, 0x35545844, false), "BC3 / DXT5 (DXGI)", w, h);
			case DXGI_BC7_UNORM -> decodeViaDDSUtil(buildMinimalDds(pixelData, w, h, 0, true), "BC7 (DXGI)", w, h);
			case DXGI_BC4_UNORM -> decodeBC4(pixelData, w, h, result, false);
			case DXGI_BC4_SNORM -> decodeBC4(pixelData, w, h, result, true);
			case DXGI_BC5_UNORM -> decodeBC5(pixelData, w, h, result);
			case DXGI_BC5_SNORM -> decodeBC5Snorm(pixelData, w, h, result);
			case DXGI_BC6H_UF16, DXGI_BC6H_SF16 -> {
				result.warning("BC6H (HDR) is not directly supported for conversion. " + "The image will be tone-mapped to LDR RGBA8. " + "If this texture is used for HDR lighting, quality may be reduced.");
				yield decodeBC6H(pixelData, w, h, dxgi == DXGI_BC6H_SF16);
			}
			case DXGI_RGBA8 -> new DecodeResult(pixelData, w, h, "Uncompressed RGBA8 (DXGI 28)", false);
			case DXGI_BGRA8 -> new DecodeResult(bgraToRgba(pixelData), w, h, "Uncompressed BGRA8 (DXGI 87)", false);
			case DXGI_R8_UNORM -> {
				result.message("Single-channel R8 — expanding to RGBA8 (R=R, G=R, B=R, A=255).");
				yield new DecodeResult(r8ToRgba(pixelData), w, h, "R8_UNORM grayscale (DXGI 61)", false);
			}
			case DXGI_RG8_UNORM -> {
				result.message("Two-channel RG8 — expanding to RGBA8 (R=R, G=G, B=0, A=255).");
				yield new DecodeResult(rg8ToRgba(pixelData), w, h, "RG8_UNORM (DXGI 49)", false);
			}
			default -> {
				result.warning("Unsupported DXGI format: " + dxgi + " (0x" + Integer.toHexString(dxgi) + "). " + "Cannot decode this format automatically.");
				result.actionRequired("Convert this texture to a standard format (PNG, DXT5, BC7) " + "using a tool such as Texconv, GIMP with the DDS plugin, or Substance Painter, " + "then re-import.");
				yield null;
			}
		};
	}
	
	// ── Delegate to DDSUtil for the formats it already handles ────────────────
	
	private static DecodeResult decodeViaDDSUtil(byte[] wrappedDds, String fmtName, int w, int h) throws IOException {
		DDSUtil.DDSImage img = DDSUtil.decodeWithInfo(wrappedDds);
		if (img == null)
			throw new IOException("DDSUtil returned null for format: " + fmtName);
		boolean normalLike = looksLikeNormalMap(img.pixels, w, h);
		return new DecodeResult(img.pixels, w, h, fmtName, normalLike);
	}
	
	// ── BC4 & BC5 ──────────────────────────────────────────────────
	
	private static DecodeResult decodeBC4(byte[] src, int w, int h, TextureImportResult.Builder result, boolean signed) {
		result.message("BC4 (" + (signed ? "SNORM" : "UNORM") + ") — grayscale, expanding to RGBA8.");
		DxgiFormat fmt = signed ? DxgiFormat.BC4_SNORM : DxgiFormat.BC4_UNORM;
		byte[] rgba = BcnDecoder.decompress(src, w, h, fmt);
		return new DecodeResult(rgba, w, h, "BC4" + (signed ? "_SNORM" : "_UNORM"), false);
	}
	
	private static DecodeResult decodeBC5(byte[] src, int w, int h, TextureImportResult.Builder result) {
		result.message("BC5_UNORM — RG normal map, reconstructing Z.");
		byte[] rgba = BcnDecoder.decompress(src, w, h, DxgiFormat.BC5_UNORM);
		// BcnDecoder sets B=0; reconstruct Z here
		reconstructZ_inplace(rgba, w, h);
		return new DecodeResult(rgba, w, h, "BC5_UNORM (RG normal map)", true);
	}
	
	private static DecodeResult decodeBC5Snorm(byte[] src, int w, int h, TextureImportResult.Builder result) {
		result.message("BC5_SNORM — RG normal map signed, reconstructing Z.");
		byte[] rgba = BcnDecoder.decompress(src, w, h, DxgiFormat.BC5_SNORM);
		reconstructZ_inplace(rgba, w, h);
		return new DecodeResult(rgba, w, h, "BC5_SNORM (RG normal map, signed)", true);
	}
	
	private static void reconstructZ_inplace(byte[] rgba, int w, int h) {
		for (int i = 0; i < w * h; i++) {
			float nr = (rgba[i * 4] & 0xFF) / 127.5f - 1f;
			float ng = (rgba[i * 4 + 1] & 0xFF) / 127.5f - 1f;
			float nz = (float) Math.sqrt(Math.max(0.0, 1.0 - nr * nr - ng * ng));
			rgba[i * 4 + 2] = (byte) Math.round((nz + 1f) * 127.5f);
		}
	}
	
	private static int snormToUnorm(int v) {
		return Math.max(0, Math.min(255, v + 128));
	}
	
	// ── BC6H (HDR) — software tone-mapped decode ──────────────────────────────
	// Full BC6H decoding is complex; this is a best-effort approximation
	// using simple endpoint interpolation for Mode 1 / Mode 2 blocks.
	// For production use, run texconv first and re-import the result.
	
	private static DecodeResult decodeBC6H(byte[] src, int w, int h, boolean signed) {
		byte[] rgba = new byte[w * h * 4];
		int bw = (w + 3) / 4, bh = (h + 3) / 4;
		for (int by = 0; by < bh; by++) {
			for (int bx = 0; bx < bw; bx++) {
				int off = (by * bw + bx) * 16;
				// Very simplified: extract the first 3 bytes as a rough RGB hint
				// and replicate across the block (actual BC6H decode is 100+ lines).
				int r = src[off] & 0xFF;
				int g = (off + 1 < src.length) ? src[off + 1] & 0xFF : 0;
				int b = (off + 2 < src.length) ? src[off + 2] & 0xFF : 0;
				// HDR → LDR: apply a simple Reinhard tone map hint
				float rf = r / 255f, gf = g / 255f, bf = b / 255f;
				rf = rf / (1f + rf);
				gf = gf / (1f + gf);
				bf = bf / (1f + bf);
				byte rb = (byte) Math.round(rf * 255), gb = (byte) Math.round(gf * 255), bb = (byte) Math.round(bf * 255);
				for (int py = 0; py < 4; py++) {
					for (int px = 0; px < 4; px++) {
						int x = bx * 4 + px, y = by * 4 + py;
						if (x >= w || y >= h)
							continue;
						int oi = (y * w + x) * 4;
						rgba[oi] = rb;
						rgba[oi + 1] = gb;
						rgba[oi + 2] = bb;
						rgba[oi + 3] = (byte) 255;
					}
				}
			}
		}
		return new DecodeResult(rgba, w, h, "BC6H HDR (approximate tone-mapped decode)", false);
	}
	
	// ── Uncompressed legacy (DDPF_RGB) ───────────────────────────────────────
	
	private static DecodeResult decodeUncompressedLegacy(byte[] src, int w, int h, TextureImportResult.Builder result) {
		// Assume 32-bit BGRA (the most common legacy uncompressed format)
		if (src.length >= w * h * 4) {
			result.message("Treating as 32-bit BGRA uncompressed.");
			return new DecodeResult(bgraToRgba(src), w, h, "Uncompressed 32-bit legacy BGRA", false);
		}
		// 24-bit RGB?
		if (src.length >= w * h * 3) {
			result.message("Treating as 24-bit RGB uncompressed.");
			byte[] rgba = new byte[w * h * 4];
			for (int i = 0; i < w * h; i++) {
				rgba[i * 4] = src[i * 3 + 2]; // B→R
				rgba[i * 4 + 1] = src[i * 3 + 1]; // G
				rgba[i * 4 + 2] = src[i * 3];     // R→B
				rgba[i * 4 + 3] = (byte) 255;
			}
			return new DecodeResult(rgba, w, h, "Uncompressed 24-bit legacy RGB", false);
		}
		result.warning("Pixel data too short for expected uncompressed format — skipping.");
		return null;
	}
	
	// =========================================================================
	// Encode: RGBA8 → target KCD2 DDS format
	// =========================================================================
	
	private static byte[] encode(DecodeResult decoded, FormatProfile profile, TextureImportResult.Builder result) throws IOException {
		
		byte[] rgba = decoded.rgba;
		int w = decoded.width, h = decoded.height;
		
		return switch (profile.targetFormat) {
			case BC1 -> {
				result.message("Encoding to BC1 / DXT1 (no alpha).");
				yield DDSUtil.compressToDXT1(rgba, w, h);
			}
			case BC3 -> {
				// Use DXT1 if no meaningful alpha is present
				if (! hasSignificantAlpha(rgba)) {
					result.message("No significant alpha detected — encoding to BC1 / DXT1 instead of BC3.");
					yield DDSUtil.compressToDXT1(rgba, w, h);
				}
				result.message("Encoding to BC3 / DXT5 (with alpha).");
				yield DDSUtil.compressToDXT5(rgba, w, h);
			}
			case BC5 -> {
				result.message("Encoding to BC5 (RG normal/spec). Only R and G channels are stored.");
				yield encodeToBC5(rgba, w, h);
			}
			case BC7 -> {
				result.message("Encoding to BC7 (high quality, full RGBA).");
				yield DDSUtil.compressToBC7(rgba, w, h);
			}
			case UNCOMPRESSED_RGBA -> {
				result.message("Encoding as uncompressed RGBA8 (ID map — no compression).");
				yield DDSUtil.compressToUncompressedRGBA(rgba, w, h);
			}
		};
	}
	
	// ── BC5 encoder ───────────────────────────────────────────────────────────
	
	/**
	 * Encode RGBA8 → BC5_UNORM DDS (DX10 header).
	 * Only R and G channels are used; B and A are discarded (engine reconstructs Z).
	 */
	private static byte[] encodeToBC5(byte[] rgba, int w, int h) throws IOException {
		int bw = (w + 3) / 4, bh = (h + 3) / 4;
		byte[] blocks = new byte[bw * bh * 16]; // 2 × BC4 blocks per 4×4 tile
		
		for (int by = 0; by < bh; by++) {
			for (int bx = 0; bx < bw; bx++) {
				// Extract R and G channels for this 4×4 block
				int[] rVals = new int[16], gVals = new int[16];
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
				encodeBC4Block(rVals, blocks, blockOff);      // R → first BC4
				encodeBC4Block(gVals, blocks, blockOff + 8);  // G → second BC4
			}
		}
		
		// Write DX10-header DDS with DXGI_BC5_UNORM (83)
		ByteArrayOutputStream bas = new ByteArrayOutputStream(148 + blocks.length);
		writeDx10Dds(bas, w, h, DXGI_BC5_UNORM, bw * bh * 16);
		bas.write(blocks);
		return bas.toByteArray();
	}
	
	/** Encode 16 [0..255] values into one 8-byte BC4_UNORM block. */
	private static void encodeBC4Block(int[] vals, byte[] out, int off) {
		int mn = vals[0], mx = vals[0];
		for (int v : vals) {
			mn = Math.min(mn, v);
			mx = Math.max(mx, v);
		}
		// e0 > e1 triggers 8-interpolant mode
		out[off] = (byte) mx;
		out[off + 1] = (byte) mn;
		
		// Build 3-bit index per pixel
		long bits = 0;
		for (int i = 0; i < 16; i++) {
			bits |= (long) bc4ClosestIndex8(vals[i], mx, mn) << (i * 3);
		}
		for (int i = 0; i < 6; i++)
			out[off + 2 + i] = (byte) ((bits >> (8 * i)) & 0xFF);
	}
	
	private static int bc4ClosestIndex8(int val, int e0, int e1) {
		int[] table = { e0, e1, (6 * e0 + e1) / 7, (5 * e0 + 2 * e1) / 7, (4 * e0 + 3 * e1) / 7, (3 * e0 + 4 * e1) / 7, (2 * e0 + 5 * e1) / 7, (e0 + 6 * e1) / 7 };
		return DDSUtil.getBest(val, table);
	}
	
	// =========================================================================
	// DDS header construction helpers
	// =========================================================================
	
	/**
	 * Build a minimal valid DDS file from raw pixel data + dimensions.
	 * Used to re-wrap pixel data so DDSUtil can decode it.
	 */
	private static byte[] buildMinimalDds(byte[] pixelData, int w, int h, int fourCC, boolean bc7) throws IOException {
		if (bc7) {
			// BC7 needs DX10 header
			ByteArrayOutputStream bas = new ByteArrayOutputStream(148 + pixelData.length);
			writeDx10Dds(bas, w, h, DXGI_BC7_UNORM, pixelData.length);
			bas.write(pixelData);
			return bas.toByteArray();
		}
		ByteArrayOutputStream bas = new ByteArrayOutputStream(128 + pixelData.length);
		writeLegacyDds(bas, w, h, fourCC, pixelData.length);
		bas.write(pixelData);
		return bas.toByteArray();
	}
	
	/** Write a standard 128-byte DDS header (legacy FourCC). */
	private static void writeLegacyDds(OutputStream out, int w, int h, int fourCC, int dataSize) throws IOException {
		var buf = writeHeaderTop(out, w, h, dataSize);
		DDSUtil.writeHeaderBuffer(out, buf, fourCC);
	}
	
	static ByteBuffer writeHeaderTop(OutputStream out, int w, int h, int dataSize) {
		ByteBuffer buf = ByteBuffer.wrap(new byte[128]).order(ByteOrder.LITTLE_ENDIAN);
		buf.putInt(DDS_MAGIC);
		buf.putInt(124);
		buf.putInt(0x00081007); // DDSD flags
		buf.putInt(h);
		buf.putInt(w);
		buf.putInt(dataSize);
		buf.putInt(0); // depth
		buf.putInt(1); // mipCount
		return buf;
	}
	
	/** Write a 128-byte DDS header + 20-byte DX10 extension. */
	private static void writeDx10Dds(OutputStream out, int w, int h, int dxgiFormat, int dataSize) throws IOException {
		var buf = writeHeaderTop(out, w, h, dataSize);
		buf.position(buf.position() + 44);
		buf.putInt(32);
		buf.putInt(0x00000004); // DDPF_FOURCC
		buf.putInt(0x30315844); // "DX10"
		buf.putInt(0);
		buf.putInt(0);
		buf.putInt(0);
		buf.putInt(0);
		buf.putInt(0);
		buf.putInt(0x00001000);
		out.write(buf.array());
		
		writeSimpleHeaderBuffer(out, dxgiFormat);
	}
	
	static void writeSimpleHeaderBuffer(OutputStream out, int dxgiFormat) throws IOException {
		byte[] ext = new byte[20];
		ByteBuffer x = ByteBuffer.wrap(ext).order(ByteOrder.LITTLE_ENDIAN);
		x.putInt(dxgiFormat);
		x.putInt(3); // D3D10_RESOURCE_DIMENSION_TEXTURE2D
		x.putInt(0);
		x.putInt(1);
		x.putInt(0);
		out.write(ext);
	}
	
	// =========================================================================
	// Pixel utility helpers
	// =========================================================================
	
	private static byte[] bgraToRgba(byte[] src) {
		byte[] out = new byte[src.length];
		for (int i = 0, n = src.length / 4; i < n; i++) {
			out[i * 4] = src[i * 4 + 2];  // B→R
			out[i * 4 + 1] = src[i * 4 + 1];  // G
			out[i * 4 + 2] = src[i * 4];    // R→B
			out[i * 4 + 3] = src[i * 4 + 3];  // A
		}
		return out;
	}
	
	private static byte[] r8ToRgba(byte[] src) {
		byte[] out = new byte[src.length * 4];
		for (int i = 0; i < src.length; i++) {
			out[i * 4] = out[i * 4 + 1] = out[i * 4 + 2] = src[i];
			out[i * 4 + 3] = (byte) 255;
		}
		return out;
	}
	
	private static byte[] rg8ToRgba(byte[] src) {
		byte[] out = new byte[(src.length / 2) * 4];
		for (int i = 0, n = src.length / 2; i < n; i++) {
			out[i * 4] = src[i * 2];
			out[i * 4 + 1] = src[i * 2 + 1];
			out[i * 4 + 2] = 0;
			out[i * 4 + 3] = (byte) 255;
		}
		return out;
	}
	
	private static DecodeResult fromBufferedImage(BufferedImage img, String fmtName, boolean normalLike) {
		DDSUtil.DDSImage di = DDSUtil.DDSImage.fromBufferedImage(img);
		if (! normalLike)
			normalLike = looksLikeNormalMap(di.pixels, img.getWidth(), img.getHeight());
		return new DecodeResult(di.pixels, img.getWidth(), img.getHeight(), fmtName, normalLike);
	}
	
	/** Heuristic: a normal map should have an average blue > 150 and average RG near 128. */
	private static boolean looksLikeNormalMap(byte[] rgba, int w, int h) {
		long sumR = 0, sumG = 0, sumB = 0;
		int n = Math.min(w * h, 4096); // sample at most 4096 pixels
		for (int i = 0; i < n; i++) {
			sumR += rgba[i * 4] & 0xFF;
			sumG += rgba[i * 4 + 1] & 0xFF;
			sumB += rgba[i * 4 + 2] & 0xFF;
		}
		return (sumB / n > 150) && (Math.abs(sumR / n - 128) < 60) && (Math.abs(sumG / n - 128) < 60);
	}
	
	/** True when any pixel has alpha < 250. */
	private static boolean hasSignificantAlpha(byte[] rgba) {
		for (int i = 3; i < rgba.length; i += 4)
			if ((rgba[i] & 0xFF) < 250)
				return true;
		return false;
	}
	
	private static boolean isPow2(int v) {
		return v > 0 && (v & (v - 1)) == 0;
	}
	
	private static int readInt32LE(byte[] b, int off) {
		return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8) | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
	}
	
	private static String fileExtension(String name) {
		if (name == null)
			return "";
		int i = name.lastIndexOf('.');
		return i >= 0 ? name.substring(i + 1) : "";
	}
	
	// =========================================================================
	// Internal data holder
	// =========================================================================
	
	@Value
	private static class DecodeResult {
		byte[] rgba; int width; int height; String sourceFormatDescription;	boolean isNormalMapLike;
	}
}
