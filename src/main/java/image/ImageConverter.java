package image;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Core DDS → TIFF conversion logic.
 *
 * <p>Mirrors {@code ImageConverter} and the relevant parts of {@code MainWindow}
 * from the C# source.  Uses pure-Java BCn decoding ({@link BcnDecoder}) so there
 * is no native dependency (DirectXTexNet).  The output image is encoded as a
 * 16-bit-per-channel or 8-bit-per-channel TIFF depending on the source format,
 * using TwelveMonkeys ImageIO.
 */
@lombok.extern.slf4j.Slf4j
public class ImageConverter {
	
	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------
	
	/**
	 * Convert a single .dds file (with its KCD companion files) to a TIFF.
	 *
	 * @param filePath absolute path to the base .dds file
	 * @param opts     conversion options
	 */
	public static void convertImage(Path filePath, ConversionOptions opts) throws IOException {
		log.info("Converting: "+ filePath);
		
		String stem = stem(filePath);
		boolean isIdMap = stem.toLowerCase().endsWith("_id");
		
		// ── 1. Load + reassemble the split KCD DDS ───────────────────────────
		LoadedDds loaded = loadGameDds(filePath, opts);
		if (loaded == null)
			throw new IOException("Failed to load DDS: " + filePath);
		
		DdsFile dds = loaded.dds;
		DdsFile alphaDds = loaded.alphaDds;
		
		DxgiFormat fmt = dds.header.getPixelFormat();
		boolean isNormal = fmt.isNormalMap();
		boolean isSRGB = fmt.isSRGB();
		
		// ── 2. Decompress colour image to float RGBA ──────────────────────────
		float[] rgba = toFloatRgba(dds, fmt);
		
		// ── 3. Normal-map Z reconstruction ───────────────────────────────────
		if (isNormal) {
			rgba = reconstructZ(rgba, true);
		}
		
		// ── 4. Alpha / gloss handling ─────────────────────────────────────────
		if (alphaDds != null) {
			DxgiFormat aFmt = alphaDds.header.getPixelFormat();
			float[] alphaChannel = toFloatSingleChannel(alphaDds, aFmt);
			
			if (opts.separateGlossMap) {
				// Write alpha as separate _alpha.tif
				if (! opts.isOutputFolder)
					throw new IOException("Output must be a folder to separate gloss.");
				Path dir = resolveOutputDir(filePath, opts);
				Path aTif = dir.resolve(stem + "_alpha.tif");
				saveGrayscaleTiff(alphaChannel, alphaDds.header.width, alphaDds.header.height, aTif);
				log.info("  → gloss map: "+ aTif);
			} else {
				// Merge alpha channel into float RGBA
				rgba = mergeAlpha(rgba, alphaChannel);
			}
		}
		
		// ── 5. Convert float RGBA to output pixels ────────────────────────────
		int w = dds.header.width;
		int h = dds.header.height;
		BufferedImage outImage;
		
		if (isNormal) {
			// Normal maps: keep as 8-bit RGBA (R=Y, G=X, B=Z, A=1 after reconstructZ)
			outImage = floatRgbaToBufferedImage(rgba, w, h, false);
		} else if (isIdMap) {
			// ID maps: special quantisation matching C# QuantizeIDPixels
			byte[] quant = quantizeIdPixels(rgba, isSRGB);
			outImage = bytesToBufferedImageRgba(quant, w, h);
		} else {
			outImage = floatRgbaToBufferedImage(rgba, w, h, isSRGB);
		}
		
		// ── 6. Write TIFF ─────────────────────────────────────────────────────
		Path outTif;
		if (opts.isOutputFolder) {
			Path dir = resolveOutputDir(filePath, opts);
			Files.createDirectories(dir);
			outTif = dir.resolve(stem + ".tif");
		} else {
			outTif = Path.of(opts.outputPath);
			Files.createDirectories(outTif.getParent());
		}
		saveTiff(outImage, outTif);
		log.info("  → "+ outTif);
		
		// ── 7. Cleanup source files ───────────────────────────────────────────
		if (opts.deleteSourceFiles) {
			deleteSourceFiles(filePath, loaded.mipFiles, loaded.alphaMipFiles, opts.saveRawDDS && opts.isOutputFolder);
		}
	}
	
	// -------------------------------------------------------------------------
	// Batch processing (mirrors C# BatchProcessFiles + Task.WhenAll)
	// -------------------------------------------------------------------------
	
	/**
	 * Convert all .dds files found under {@code inputFolder}.
	 *
	 * @param inputFolder    folder to search
	 * @param outputFolder   root output folder
	 * @param opts           base options (outputPath / isOutputFolder are overridden per file)
	 * @param recursive      search subdirectories
	 * @return list of futures so callers can await or collect exceptions
	 */
	public static List<Future<?>> batchProcess(Path inputFolder, Path outputFolder, ConversionOptions opts, boolean recursive) throws IOException {
		
		PathMatcher matcher = inputFolder.getFileSystem().getPathMatcher("glob:**.dds");
		List<Path> ddsFiles = new ArrayList<>();
		try (var walk = recursive ? Files.walk(inputFolder) : Files.walk(inputFolder, 1)) {
			walk.filter(p -> ! Files.isDirectory(p)).filter(matcher::matches).forEach(ddsFiles::add);
		}
		
		if (ddsFiles.isEmpty())
			throw new IOException("No .dds files found in: " + inputFolder);
		
		ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
		
		List<Future<?>> futures = new ArrayList<>();
		for (Path file : ddsFiles) {
			Path destDir;
			if (recursive) {
				Path rel = inputFolder.relativize(file.getParent());
				destDir = outputFolder.resolve(rel);
			} else {
				destDir = outputFolder;
			}
			
			ConversionOptions fileOpts = new ConversionOptions().saveRawDDS(opts.saveRawDDS).separateGlossMap(opts.separateGlossMap).deleteSourceFiles(opts.deleteSourceFiles).outputPath(destDir.toString()).isOutputFolder(true);
			
			futures.add(pool.submit(() -> {
				try {
					convertImage(file, fileOpts);
				} catch (Exception e) {
					log.error("Failed to convert "+ file +" "+ e);
					throw new RuntimeException(e);
				}
				return null;
			}));
		}
		
		pool.shutdown();
		return futures;
	}
	
	/**
	 * Convenience: await all futures returned by {@link #batchProcess}.
	 * Re-throws the first exception encountered.
	 */
	public static void awaitAll(List<Future<?>> futures) throws Exception {
		for (Future<?> f : futures) {
			f.get(); // propagates ExecutionException
		}
	}
	
	// -------------------------------------------------------------------------
	// DDS loading + KCD mip-companion reassembly
	// -------------------------------------------------------------------------
	
	private static LoadedDds loadGameDds(Path base, ConversionOptions opts) throws IOException {
		
		List<byte[]> mipData = new ArrayList<>();
		List<byte[]> alphaMipData = new ArrayList<>();
		List<Path> mipFiles = new ArrayList<>();
		List<Path> alphaMipFiles = new ArrayList<>();
		
		// Collect colour mip companions (.dds.1 … .dds.63), highest first
		for (int i = 1; i < 64; i++) {
			Path p = Path.of(base + "." + i);
			if (! Files.exists(p))
				break;
			mipData.add(0, Files.readAllBytes(p)); // insert at front → lowest mip first
			mipFiles.add(p);
		}
		
		// Collect alpha mip companions (.dds.1a … .dds.63a)
		for (int i = 1; i < 64; i++) {
			Path p = Path.of(base + "." + i + "a");
			if (! Files.exists(p))
				break;
			alphaMipData.add(0, Files.readAllBytes(p));
			alphaMipFiles.add(p);
		}
		
		// Read base colour DDS
		DdsFile dds = new DdsFile(base, false);
		
		// Prepend mip data then the base DDS pixel data
		dds.data = concat(mipData, dds.data);
		
		// Validate size
		int expected = computePixelDataSize(dds.header.getPixelFormat(), dds.header.width, dds.header.height, dds.header.effectiveMipCount());
		if (dds.data.length < expected) {
			throw new IOException("Incomplete mip data for " + base + " (got " + dds.data.length + " bytes, need " + expected + ")");
		}
		
		// Optionally write raw assembled DDS
		if (opts.saveRawDDS) {
			Path dir = resolveOutputDir(base, opts);
			Files.createDirectories(dir);
			Path rawOut = dir.resolve(stem(base) + ".dds");
			dds.write(rawOut);
			log.info("  → raw DDS: "+ rawOut);
		}
		
		// Read alpha sidecar if present
		DdsFile alphaDds = null;
		Path alphaBase = Path.of(base + ".a");
		if (Files.exists(alphaBase)) {
			alphaDds = new DdsFile(alphaBase, true);
			alphaDds.data = concat(alphaMipData, alphaDds.data);
			
			int aExpected = computePixelDataSize(alphaDds.header.getPixelFormat(), alphaDds.header.width, alphaDds.header.height, alphaDds.header.effectiveMipCount());
			if (alphaDds.data.length < aExpected) {
				throw new IOException("Incomplete alpha mip data for " + alphaBase);
			}
		}
		
		return new LoadedDds(dds, alphaDds, mipFiles, alphaMipFiles);
	}
	
	/**
	 * Decompress/decode a DDS to a flat float[] in RGBA order,
	 * values in [0..1].  Only reads mip level 0.
	 */
	private static float[] toFloatRgba(DdsFile dds, DxgiFormat fmt) {
		int w = dds.header.width, h = dds.header.height;
		
		byte[] rgba8 = BcnDecoder.decompress(mip0Data(dds), w, h, fmt);
		
		// Convert RGBA8 → float RGBA [0..1]
		float[] out = new float[w * h * 4];
		for (int i = 0; i < out.length; i++) {
			out[i] = (rgba8[i] & 0xFF) / 255.0f;
		}
		return out;
	}
	
	// -------------------------------------------------------------------------
	// BCn decompression → float RGBA
	// -------------------------------------------------------------------------
	
	/**
	 * Decompress single-channel (R8) alpha DDS to float[].
	 */
	private static float[] toFloatSingleChannel(DdsFile dds, DxgiFormat fmt) {
		int w = dds.header.width, h = dds.header.height;
		byte[] rgba8 = BcnDecoder.decompress(mip0Data(dds), w, h, fmt);
		float[] out = new float[w * h];
		for (int i = 0; i < out.length; i++) {
			out[i] = (rgba8[i * 4] & 0xFF) / 255.0f; // R channel
		}
		return out;
	}
	
	/** Returns only the mip-0 pixel bytes (= last mip in the concatenated buffer). */
	private static byte[] mip0Data(DdsFile dds) {
		// The last block in data is mip 0 (highest resolution).
		// Size of mip 0 alone:
		int mip0Size = mipSize(dds.header.getPixelFormat(), dds.header.width, dds.header.height);
		int offset = dds.data.length - mip0Size;
		if (offset < 0)
			offset = 0;
		return Arrays.copyOfRange(dds.data, offset, dds.data.length);
	}
	
	/**
	 * Reconstruct the Z channel of a normal map from X and Y.
	 * Mirrors C# ReconstructZ(pixelData, pack=true).
	 */
	static float[] reconstructZ(float[] rgba, boolean pack) {
		float[] out = new float[rgba.length];
		int n = rgba.length / 4;
		for (int i = 0; i < n; i++) {
			float x = rgba[i * 4];
			float y = rgba[i * 4 + 1];
			// Remap from [0..1] to [-1..1]
			float nx = x * 2 - 1;
			float ny = y * 2 - 1;
			float nz = (float) Math.sqrt(Math.max(0, 1 - nx * nx - ny * ny));
			if (pack) {
				out[i * 4] = (float) Math.pow((ny + 1) / 2.0, 2.2); // G→R (swizzle matches C#)
				out[i * 4 + 1] = (float) Math.pow((nx + 1) / 2.0, 2.2); // R→G
				out[i * 4 + 2] = (float) Math.pow((nz + 1) / 2.0, 2.2); // Z→B
			} else {
				out[i * 4] = ny;
				out[i * 4 + 1] = nx;
				out[i * 4 + 2] = nz;
			}
			out[i * 4 + 3] = 1.0f;
		}
		return out;
	}
	
	// -------------------------------------------------------------------------
	// Pixel manipulation — mirrors C# static methods in ImageConverter
	// -------------------------------------------------------------------------
	
	/**
	 * Replace alpha channel of rgba[] with alphaChannel[].
	 * Mirrors C# MergeAlpha().
	 */
	static float[] mergeAlpha(float[] rgba, float[] alphaChannel) {
		float[] out = rgba.clone();
		for (int i = 0; i < alphaChannel.length; i++) {
			out[i * 4 + 3] = alphaChannel[i];
		}
		return out;
	}
	
	/**
	 * Quantise ID-map pixels to RGBA8, matching C# QuantizeIDPixels().
	 */
	static byte[] quantizeIdPixels(float[] rgba, boolean isSRGB) {
		int n = rgba.length / 4;
		byte[] out = new byte[n * 4];
		for (int i = 0; i < n; i++) {
			float r = rgba[i * 4], g = rgba[i * 4 + 1], b = rgba[i * 4 + 2], a = rgba[i * 4 + 3];
			if (isSRGB) {
				r = (float) Math.pow(r, 1.0 / 2.2);
				g = (float) Math.pow(g, 1.0 / 2.2);
				b = (float) Math.pow(b, 1.0 / 2.2);
				out[i * 4] = (byte) Math.ceil(r * 255);
				out[i * 4 + 1] = (byte) Math.ceil(g * 255);
				out[i * 4 + 2] = (byte) Math.ceil(b * 255);
				out[i * 4 + 3] = (byte) Math.floor(a * 255);
			} else {
				out[i * 4] = (byte) Math.floor(r * 255);
				out[i * 4 + 1] = (byte) Math.floor(g * 255);
				out[i * 4 + 2] = (byte) Math.floor(b * 255);
				out[i * 4 + 3] = (byte) Math.floor(a * 255);
			}
		}
		return out;
	}
	
	private static BufferedImage floatRgbaToBufferedImage(float[] rgba, int w, int h, boolean isSRGB) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);
		byte[] data = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
		// TYPE_4BYTE_ABGR stores bytes as A, B, G, R
		for (int i = 0; i < w * h; i++) {
			float r = rgba[i * 4], g = rgba[i * 4 + 1], b = rgba[i * 4 + 2], a = rgba[i * 4 + 3];
			if (isSRGB) {
				// LinearToSRGB: already in linear, convert to gamma 2.2 for display
				r = (float) Math.pow(Math.max(0, Math.min(1, r)), 1.0 / 2.2);
				g = (float) Math.pow(Math.max(0, Math.min(1, g)), 1.0 / 2.2);
				b = (float) Math.pow(Math.max(0, Math.min(1, b)), 1.0 / 2.2);
			}
			data[i * 4] = (byte) Math.round(a * 255);
			data[i * 4 + 1] = (byte) Math.round(b * 255);
			data[i * 4 + 2] = (byte) Math.round(g * 255);
			data[i * 4 + 3] = (byte) Math.round(r * 255);
		}
		return img;
	}
	
	// -------------------------------------------------------------------------
	// Image building
	// -------------------------------------------------------------------------
	
	private static BufferedImage bytesToBufferedImageRgba(byte[] bytes, int w, int h) {
		// bytes are already RGBA order — convert to ABGR for TYPE_4BYTE_ABGR
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);
		byte[] data = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
		for (int i = 0; i < w * h; i++) {
			data[i * 4] = bytes[i * 4 + 3]; // A
			data[i * 4 + 1] = bytes[i * 4 + 2]; // B
			data[i * 4 + 2] = bytes[i * 4 + 1]; // G
			data[i * 4 + 3] = bytes[i * 4];     // R
		}
		return img;
	}
	
	private static void saveTiff(BufferedImage img, Path out) throws IOException {
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("TIFF");
		if (! writers.hasNext())
			throw new IOException("No TIFF ImageWriter found (check classpath)");
		ImageWriter writer = writers.next();
		ImageWriteParam param = writer.getDefaultWriteParam();
		try (FileImageOutputStream fos = new FileImageOutputStream(out.toFile())) {
			writer.setOutput(fos);
			writer.write(null, new IIOImage(img, null, null), param);
		} finally {
			writer.dispose();
		}
	}
	
	// -------------------------------------------------------------------------
	// TIFF saving
	// -------------------------------------------------------------------------
	
	private static void saveGrayscaleTiff(float[] channel, int w, int h, Path out) throws IOException {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
		byte[] data = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
		for (int i = 0; i < channel.length; i++) {
			data[i] = (byte) Math.round(Math.max(0, Math.min(1, channel[i])) * 255);
		}
		saveTiff(img, out);
	}
	
	/** Compute the total pixel data size (all mips combined) in bytes. */
	static int computePixelDataSize(DxgiFormat fmt, int w, int h, int mipCount) {
		int total = 0;
		int mw = w, mh = h;
		for (int i = 0; i < mipCount; i++) {
			total += mipSize(fmt, mw, mh);
			mw = Math.max(1, mw / 2);
			mh = Math.max(1, mh / 2);
		}
		return total;
	}
	
	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------
	
	/** Compute the size in bytes of a single mip level. */
	static int mipSize(DxgiFormat fmt, int w, int h) {
		int bpp = fmt.bitsPerPixel();
		if (fmt.isCompressed()) {
			// BC formats use 4x4 pixel blocks; minimum 1 block per dimension.
			// BC1/BC4 = 4bpp = 8 bytes/block; BC2/BC3/BC5/BC6H/BC7 = 8bpp = 16 bytes/block
			int blocksW = Math.max(1, (w + 3) / 4);
			int blocksH = Math.max(1, (h + 3) / 4);
			int bytesPerBlock = (bpp == 4) ? 8 : 16;
			return blocksW * blocksH * bytesPerBlock;
		}
		return Math.max(1, w) * Math.max(1, h) * bpp / 8;
	}
	
	/** Concatenate a list of byte arrays followed by a final array. */
	private static byte[] concat(List<byte[]> parts, byte[] tail) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		for (byte[] p : parts)
			bos.write(p);
		bos.write(tail);
		return bos.toByteArray();
	}
	
	/** Return the file stem (name without extension). */
	private static String stem(Path p) {
		String name = p.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}
	
	/** Resolve the effective output directory for a given input file + options. */
	private static Path resolveOutputDir(Path inputFile, ConversionOptions opts) {
		if (opts.isOutputFolder && ! opts.outputPath.isEmpty()) {
			return Path.of(opts.outputPath);
		}
		return inputFile.getParent();
	}
	
	private static void deleteSourceFiles(Path base, List<Path> mipFiles, List<Path> alphaMipFiles, boolean keepBase) {
		
		for (Path f : mipFiles)
			tryDelete(f);
		for (Path f : alphaMipFiles)
			tryDelete(f);
		if (! keepBase) {
			tryDelete(base);
			tryDelete(Path.of(base + ".a"));
		}
	}
	
	private static void tryDelete(Path p) {
		try {
			Files.deleteIfExists(p);
		} catch (IOException e) {
			log.warn("Could not delete "+ p +" "+ e);
		}
	}
	
	private record LoadedDds(DdsFile dds, DdsFile alphaDds, List<Path> mipFiles, List<Path> alphaMipFiles) {
	}
}
