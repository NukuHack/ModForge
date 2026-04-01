package modforge.backend;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.logging.Logger;

public final class DdsConverter implements Closeable {
	private static final Logger log = Logger.getLogger(DdsConverter.class.getName());

	/**
	 * Convert a DDS input stream to a PNG byte array.
	 * <p>
	 * NOTE: Java has no built-in DDS decoder. You must add one of these to
	 * your build system:
	 * - DDSReader (lightweight, MIT): <a href="https://github.com/npedotnet/DDSReader">...</a>
	 * - JOGL's com.jogamp.opengl.util.texture.spi.DDSImage
	 * - Any AWT/ImageIO plugin that registers a DDS ImageReader SPI
	 * <p>
	 * Once you have a decoded BufferedImage, write PNG bytes like:
	 * var out = new ByteArrayOutputStream();
	 * ImageIO.write(image, "png", out);
	 * return out.toByteArray();
	 */
	public static byte[] convertToPng(InputStream ddsStream) throws IOException {
		throw new UnsupportedOperationException(
				"DDS decoding is not available in pure Java. " +
						"Add a library such as DDSReader and implement this method body.");
	}

	public static String toBase64DataUri(InputStream ddsStream) throws IOException {
		byte[] png = convertToPng(ddsStream);
		return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
	}

	@Override
	public void close() { /* stateless */ }
}
