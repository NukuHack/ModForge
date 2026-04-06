package modforge.backend.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class DDSUtilTest extends BaseServiceTest {

	private static byte[] pngData;
	private static byte[] dxt1Data;
	private static byte[] dxt5Data;
	private static int expectedWidth;
	private static int expectedHeight;

	@BeforeAll
	static void setUp() throws IOException {
		// Load test files using classloader (works in both IDE and build)
		pngData = readResourceBytes("img/test.png");
		dxt5Data = readResourceBytes("img/test-dxt5.dds");
		dxt1Data = readResourceBytes("img/test-dxt1.dds");

		// Get expected dimensions from PNG
		BufferedImage png = ImageIO.read(new ByteArrayInputStream(pngData));
		expectedWidth = png.getWidth();
		expectedHeight = png.getHeight();
	}

	@Test
	void testDecodeDXT1() throws IOException {
		// Act
		DDSUtil.DDSImage result = DDSUtil.decodeWithInfo(dxt1Data);

		// Assert
		assertNotNull(result);
		assertEquals(expectedWidth, result.width);
		assertEquals(expectedHeight, result.height);
		assertNotNull(result.pixels);
		assertEquals(expectedWidth * expectedHeight * 4, result.pixels.length);

		// Verify pixel data is reasonable (not all zeros)
		boolean hasNonZero = false;
		for (int i = 0; i < Math.min(100, result.pixels.length); i++) {
			if (result.pixels[i] != 0) {
				hasNonZero = true;
				break;
			}
		}
		assertTrue(hasNonZero, "Pixel data should not be all zeros");
	}

	@Test
	void testDecodeDXT5() throws IOException {
		// Act
		DDSUtil.DDSImage result = DDSUtil.decodeWithInfo(dxt5Data);

		// Assert
		assertNotNull(result);
		assertEquals(expectedWidth, result.width);
		assertEquals(expectedHeight, result.height);
		assertNotNull(result.pixels);
		assertEquals(expectedWidth * expectedHeight * 4, result.pixels.length);

		// Verify pixel data is reasonable
		boolean hasNonZero = false;
		for (int i = 0; i < Math.min(100, result.pixels.length); i++) {
			if (result.pixels[i] != 0) {
				hasNonZero = true;
				break;
			}
		}
		assertTrue(hasNonZero, "Pixel data should not be all zeros");
	}

	@Test
	void testDecodeFromInputStream() throws IOException {
		// Act
		try (InputStream is = new ByteArrayInputStream(dxt1Data)) {
			DDSUtil.DDSImage result = DDSUtil.decodeWithInfo(is);

			// Assert
			assertNotNull(result);
			assertEquals(expectedWidth, result.width);
			assertEquals(expectedHeight, result.height);
		}
	}

	@Test
	void testToBufferedImage() throws IOException {
		// Arrange
		DDSUtil.DDSImage ddsImage = DDSUtil.decodeWithInfo(dxt1Data);

		// Act
		BufferedImage bufferedImage = ddsImage.toBufferedImage();

		// Assert
		assertNotNull(bufferedImage);
		assertEquals(expectedWidth, bufferedImage.getWidth());
		assertEquals(expectedHeight, bufferedImage.getHeight());
		assertEquals(BufferedImage.TYPE_INT_ARGB, bufferedImage.getType());

		// Verify we can read some pixels
		int samplePixel = bufferedImage.getRGB(0, 0);
		assertNotEquals(0, samplePixel & 0x00FFFFFF, "Pixel should have some color");
	}

	@Test
	void testRoundTripDXT1() throws IOException {
		// Arrange
		DDSUtil.DDSImage original = DDSUtil.decodeWithInfo(dxt1Data);
		BufferedImage originalImage = original.toBufferedImage();

		// Act - compress and decompress
		byte[] compressed = DDSUtil.compressToDXT1(originalImage);
		DDSUtil.DDSImage roundTripped = DDSUtil.decodeWithInfo(compressed);

		// Assert - basic properties
		assertNotNull(roundTripped);
		assertEquals(original.width, roundTripped.width);
		assertEquals(original.height, roundTripped.height);

		// Compare a few sample pixels (allow some loss due to compression)
		int samplesToCheck = Math.min(100, original.width * original.height);
		int differences = 0;
		int maxDifferences = samplesToCheck / 10; // Allow 10% pixel difference

		for (int i = 0; i < samplesToCheck; i++) {
			int origR = original.pixels[i * 4] & 0xFF;
			int origG = original.pixels[i * 4 + 1] & 0xFF;
			int origB = original.pixels[i * 4 + 2] & 0xFF;

			int roundR = roundTripped.pixels[i * 4] & 0xFF;
			int roundG = roundTripped.pixels[i * 4 + 1] & 0xFF;
			int roundB = roundTripped.pixels[i * 4 + 2] & 0xFF;

			// Allow significant color difference due to DXT compression
			int diff = Math.abs(origR - roundR) + Math.abs(origG - roundG) + Math.abs(origB - roundB);
			if (diff > 60) { // 60/765 = ~8% difference per channel
				differences++;
			}
		}

		assertTrue(differences <= maxDifferences,
				"Too many pixels differ significantly: " + differences + " of " + samplesToCheck);
	}

	@Test
	void testRoundTripDXT5() throws IOException {
		// Arrange
		DDSUtil.DDSImage original = DDSUtil.decodeWithInfo(dxt5Data);
		BufferedImage originalImage = original.toBufferedImage();

		// Act - compress and decompress
		byte[] compressed = DDSUtil.compressToDXT5(originalImage);
		DDSUtil.DDSImage roundTripped = DDSUtil.decodeWithInfo(compressed);

		// Assert
		assertNotNull(roundTripped);
		assertEquals(original.width, roundTripped.width);
		assertEquals(original.height, roundTripped.height);

		// DXT5 should preserve alpha better
		int samplesToCheck = Math.min(100, original.width * original.height);
		int alphaDifferences = 0;

		for (int i = 0; i < samplesToCheck; i++) {
			int origA = original.pixels[i * 4 + 3] & 0xFF;
			int roundA = roundTripped.pixels[i * 4 + 3] & 0xFF;

			if (Math.abs(origA - roundA) > 30) { // Allow some alpha loss
				alphaDifferences++;
			}
		}
		System.out.println("found " + alphaDifferences + " alphaDifferences in " + samplesToCheck + " samples" );
		assertTrue(alphaDifferences <= samplesToCheck / 5, "Too many alpha differences: " + alphaDifferences);
	}

	@Test
	void testWriteAllDDSFormats() throws IOException {
		// Load the original PNG
		BufferedImage originalPNG = ImageIO.read(new ByteArrayInputStream(pngData));

		// Create output directory
		Path outputDir = RESOURCES_OUTPUT;
		if (!Files.exists(outputDir)) {
			Files.createDirectories(outputDir);
		}

		// Compress to different DDS formats
		byte[] dxt1Compressed = DDSUtil.compressToDXT1(originalPNG);
		byte[] dxt3Compressed = DDSUtil.compressToDXT3(originalPNG);
		byte[] dxt5Compressed = DDSUtil.compressToDXT5(originalPNG);

		// Write the DDS files
		Path dxt1Path = outputDir.resolve("converted-dxt1.dds");
		Path dxt3Path = outputDir.resolve("converted-dxt3.dds");
		Path dxt5Path = outputDir.resolve("converted-dxt5.dds");

		Files.write(dxt1Path, dxt1Compressed);
		Files.write(dxt3Path, dxt3Compressed);
		Files.write(dxt5Path, dxt5Compressed);

		// Also write the original PNG for reference
		Path pngPath = outputDir.resolve("original.png");
		ImageIO.write(originalPNG, "png", pngPath.toFile());

		// Decode them back to PNG for easy visual comparison
		DDSUtil.DDSImage dxt1Decoded = DDSUtil.decodeWithInfo(dxt1Compressed);
		DDSUtil.DDSImage dxt3Decoded = DDSUtil.decodeWithInfo(dxt3Compressed);
		DDSUtil.DDSImage dxt5Decoded = DDSUtil.decodeWithInfo(dxt5Compressed);

		BufferedImage dxt1Image = dxt1Decoded.toBufferedImage();
		BufferedImage dxt3Image = dxt3Decoded.toBufferedImage();
		BufferedImage dxt5Image = dxt5Decoded.toBufferedImage();

		Path dxt1PNGPath = outputDir.resolve("converted-dxt1-decompressed.png");
		Path dxt3PNGPath = outputDir.resolve("converted-dxt3-decompressed.png");
		Path dxt5PNGPath = outputDir.resolve("converted-dxt5-decompressed.png");

		ImageIO.write(dxt1Image, "png", dxt1PNGPath.toFile());
		ImageIO.write(dxt3Image, "png", dxt3PNGPath.toFile());
		ImageIO.write(dxt5Image, "png", dxt5PNGPath.toFile());

		// Print file sizes for comparison
		System.out.println("=== File Size Comparison ===");
		System.out.printf("Original PNG: %d bytes%n", Files.size(pngPath));
		System.out.printf("DXT1 DDS: %d bytes (%.1f%% of PNG)%n",
				Files.size(dxt1Path), (Files.size(dxt1Path) * 100.0 / Files.size(pngPath)));
		System.out.printf("DXT3 DDS: %d bytes (%.1f%% of PNG)%n",
				Files.size(dxt3Path), (Files.size(dxt3Path) * 100.0 / Files.size(pngPath)));
		System.out.printf("DXT5 DDS: %d bytes (%.1f%% of PNG)%n",
				Files.size(dxt5Path), (Files.size(dxt5Path) * 100.0 / Files.size(pngPath)));

		// Also compare alpha differences
		System.out.println("\n=== Alpha Quality Comparison ===");
		DDSUtil.DDSImage original = DDSUtil.decodeWithInfo(dxt5Data); // Use original DXT5 as reference

		compareAlphaQuality(original, dxt1Decoded, "DXT1");
		compareAlphaQuality(original, dxt3Decoded, "DXT3");
		compareAlphaQuality(original, dxt5Decoded, "DXT5");

		System.out.println("\nFiles written to: " + outputDir.toAbsolutePath());
		System.out.println("Open the PNG files in an image editor to compare quality!");
	}

	private void compareAlphaQuality(DDSUtil.DDSImage original, DDSUtil.DDSImage converted, String formatName) {
		int totalAlphaError = 0;
		int maxAlphaError = 0;
		int samplesToCheck = Math.min(1000, original.width * original.height);

		for (int i = 0; i < samplesToCheck; i++) {
			int origA = original.pixels[i * 4 + 3] & 0xFF;
			int convA = converted.pixels[i * 4 + 3] & 0xFF;
			int error = Math.abs(origA - convA);
			totalAlphaError += error;
			if (error > maxAlphaError) maxAlphaError = error;
		}

		double avgError = totalAlphaError / (double) samplesToCheck;
		System.out.printf("%s: Avg Alpha Error = %.2f, Max Alpha Error = %d%n",
				formatName, avgError, maxAlphaError);
	}

	@Test
	void testColorDistribution() throws IOException {
		// This test verifies the decoded image has reasonable color distribution
		DDSUtil.DDSImage ddsImage = DDSUtil.decodeWithInfo(dxt1Data);

		int[] colorCounts = new int[256]; // Simple histogram of red channel

		for (int i = 0; i < ddsImage.pixels.length; i += 4) {
			int r = ddsImage.pixels[i] & 0xFF;
			colorCounts[r]++;
		}

		// Check that not all pixels are the same color
		int nonZeroChannels = 0;
		for (int count : colorCounts) {
			if (count > 0) nonZeroChannels++;
		}

		assertTrue(nonZeroChannels > 10, "Image should have more than 10 distinct red values");
	}

	@Test
	void testCompareDXT1AndDXT5() throws IOException {
		// Both DDS files should decode to similar (but not identical) images
		DDSUtil.DDSImage dxt1 = DDSUtil.decodeWithInfo(dxt1Data);
		DDSUtil.DDSImage dxt5 = DDSUtil.decodeWithInfo(dxt5Data);

		assertEquals(dxt1.width, dxt5.width);
		assertEquals(dxt1.height, dxt5.height);

		// Compare first pixel of each
		int dxt1R = dxt1.pixels[0] & 0xFF;
		int dxt1G = dxt1.pixels[1] & 0xFF;
		int dxt1B = dxt1.pixels[2] & 0xFF;

		int dxt5R = dxt5.pixels[0] & 0xFF;
		int dxt5G = dxt5.pixels[1] & 0xFF;
		int dxt5B = dxt5.pixels[2] & 0xFF;

		// Colors should be reasonably close (not wildly different)
		int diff = Math.abs(dxt1R - dxt5R) + Math.abs(dxt1G - dxt5G) + Math.abs(dxt1B - dxt5B);
		assertTrue(diff < 200, "DXT1 and DXT5 decoded images should be similar");
	}

	@Test
	void testMemoryFootprint() throws IOException {
		// Ensure we're not leaking memory or creating huge buffers
		Runtime runtime = Runtime.getRuntime();
		runtime.gc(); // Try to clean up before test

		long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

		// Decode multiple times
		for (int i = 0; i < 10; i++) {
			DDSUtil.DDSImage img = DDSUtil.decodeWithInfo(dxt1Data);
			assertNotNull(img);
		}

		runtime.gc();
		long memoryAfter = runtime.totalMemory() - runtime.freeMemory();

		// Memory growth should be minimal (less than 5MB)
		long memoryGrowth = memoryAfter - memoryBefore;
		assertTrue(memoryGrowth < 5 * 1024 * 1024,
				"Memory growth too high: " + memoryGrowth + " bytes");
	}
}