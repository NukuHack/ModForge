package modforge.backend.service;

import modforge.backend.model.ModItem;

import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.zip.*;

public final class IconService implements Closeable {
	private static final Logger log = Logger.getLogger(IconService.class.getName());

	private static final String TEXTURES_ROOT = "Libs/UI/Textures";
	private static final String FALLBACK_ICON  = "crime_investigation_icon";

	private final UserService configService;

	/**
	 * Index built during init(): iconId (lowercase, no extension) → raw DDS bytes.
	 * Holding raw DDS bytes is far cheaper than decoded pixels; typical DDS icons
	 * are 16–128 KB, vs the uncompressed RGBA which can be 4–16× larger.
	 */
	private final Map<String, byte[]> iconIndex = new HashMap<>();

	/**
	 * Lazy PNG cache: iconId → "data:image/png;base64,…" string.
	 * Populated on first access; subsequent calls are a pure map lookup.
	 */
	private final Map<String, String> pngCache = new HashMap<>();

	// =====================================================================
	// Construction & lifecycle
	// =====================================================================

	public IconService(UserService configService) {
		this.configService = configService;
		init();
	}

	/**
	 * (Re-)scan IPL_GameData.pak and index every DDS file found under
	 * Libs/UI/Textures/.  Safe to call again after a game-directory change.
	 */
	public void init() {
		iconIndex.clear();
		pngCache.clear();

		final String dir = configService.current.gameDirectory;
		if (dir == null || dir.isBlank()) return;

		final String pakPath = PathFactory.join(dir, "Data", "IPL_GameData.pak");
		final File   pakFile = new File(pakPath);
		if (!pakFile.exists()) {
			log.warning("IPL_GameData.pak not found – icon index empty: " + pakPath);
			return;
		}

		final long start = System.currentTimeMillis();
		int indexed = 0;

		try (var zf = new ZipFile(pakFile)) {
			final var entries = zf.entries();
			while (entries.hasMoreElements()) {
				final var entry = entries.nextElement();
				final String name = entry.getName().replace('\\', '/');

				// Only texture entries
				if (!name.startsWith(TEXTURES_ROOT)) continue;
				if (!name.toLowerCase(Locale.ROOT).endsWith(".dds")) continue;

				// Key = filename stem, lowercase  (e.g. "sword_icon")
				final String stem = stemOf(name);
				if (stem.isBlank()) continue;

				try (var is = zf.getInputStream(entry)) {
					iconIndex.put(stem, is.readAllBytes());
					indexed++;
				} catch (IOException ex) {
					log.fine("Could not read icon entry " + name + ": " + ex.getMessage());
				}
			}
		} catch (IOException ex) {
			log.severe("Cannot open IPL_GameData.pak for icon indexing: " + ex.getMessage());
		}

		log.info(String.format("Icon index built in %d ms | entries=%d",
				System.currentTimeMillis() - start, indexed));
	}

	// =====================================================================
	// Public API
	// =====================================================================

	/**
	 * Return true if an icon with this id is present in the index.
	 * O(1), no I/O.
	 */
	public boolean hasIcon(String iconId) {
		if (iconId == null || iconId.isBlank()) return false;
		return iconIndex.containsKey(iconId.toLowerCase(Locale.ROOT));
	}

	/**
	 * Return a base64 data-URI for the icon of the given mod item.
	 * Result is cached after the first conversion.
	 */
	public String getIcon(ModItem item) {
		if (item == null || item.getAttributes() == null) return null;

		final var iconAttr = item.getAttributes().stream()
				.filter(a -> a.getName().equalsIgnoreCase("icon_id") || a.getName().equalsIgnoreCase("IconId"))
				.findFirst().orElse(null);

		if (iconAttr == null || iconAttr.getValue() == null) return null;
		final String rawValue = iconAttr.getValue().toString();

		final boolean useFallback = rawValue.equals("0") || rawValue.equalsIgnoreCase("replaceme");

		final String iconId = useFallback ? FALLBACK_ICON : rawValue;
		return getBase64Icon(iconId);
	}

	/**
	 * Return a base64 data-URI for a named icon.
	 * DDS → PNG conversion is performed at most once per icon; subsequent
	 * calls return the cached string immediately.
	 *
	 * @param iconId Icon filename (without extension).
	 * @return base64 data-URI, or {@code null} if not found or conversion failed.
	 */
	public String getBase64Icon(String iconId) {
		if (iconId == null || iconId.isBlank()) return null;

		final String key = iconId.toLowerCase(Locale.ROOT);

		// 1. Already converted?
		final String cached = pngCache.get(key);
		if (cached != null) return cached;

		// 2. Is it in the raw index?
		final byte[] ddsBytes = iconIndex.get(key);
		if (ddsBytes == null) {
			log.fine("Icon not in index: " + iconId);
			return null;
		}

		// 3. Convert DDS → PNG → base64, then cache.
		try {
			final byte[] pngData = DdsConverter.convertToPng(ddsBytes);
			final String dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(pngData);
			pngCache.put(key, dataUri);
			return dataUri;
		} catch (Exception ex) {
			log.warning("DDS→PNG conversion failed for '" + iconId + "': " + ex.getMessage());
			return null;
		}
	}

	@Override
	public void close() { /* stateless file handles – nothing to release */ }

	// =====================================================================
	// Private helpers
	// =====================================================================

	/** Extract the filename stem from a ZIP entry path (no directory, no extension). */
	private static String stemOf(String entryName) {
		int slash = entryName.lastIndexOf('/');
		String filename = slash >= 0 ? entryName.substring(slash + 1) : entryName;
		int dot = filename.lastIndexOf('.');
		return (dot > 0 ? filename.substring(0, dot) : filename).toLowerCase(Locale.ROOT);
	}

	// =====================================================================
	// DDS → PNG converter using DDSUtil (BC7 support)
	// =====================================================================

// =====================================================================
// DDS → PNG converter using DDSUtil (BC7 support)
// =====================================================================

	private static final class DdsConverter {

		/**
		 * Convert DDS bytes to PNG bytes using DDSUtil.
		 * Supports DXT1, DXT3, DXT5, and BC7 formats.
		 */
		static byte[] convertToPng(byte[] ddsBytes) throws IOException {
			final DDSUtil.DDSImage ddsImage = DDSUtil.decodeWithInfo(ddsBytes);
			if (ddsImage == null) {
				throw new IOException("Failed to decode DDS image");
			}
			return encodeAsPNG(ddsImage);
		}

		/**
		 * Encode a DDSImage (from DDSUtil) as PNG bytes.
		 */
		private static byte[] encodeAsPNG(DDSUtil.DDSImage img) throws IOException {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			// PNG signature
			baos.writeBytes("\u0089PNG\r\n\u001A\n".getBytes());
			// Write chunks
			writeChunk(baos, "IHDR", ihdr(img.width, img.height));
			writeChunk(baos, "IDAT", idat(img.pixels, img.width, img.height));
			writeChunk(baos, "IEND", new byte[0]);

			return baos.toByteArray();
		}

		private static byte[] ihdr(int w, int h) {
			return ByteBuffer.allocate(13)
					.order(ByteOrder.BIG_ENDIAN)
					.putInt(w)
					.putInt(h)
					.put((byte) 8)   // bit depth
					.put((byte) 6)   // color type (RGBA)
					.put((byte) 0)   // compression method
					.put((byte) 0)   // filter method
					.put((byte) 0)   // interlace method
					.array();
		}

		private static byte[] idat(byte[] pixels, int w, int h) {
			// Build raw scanlines with filter byte (0 = none)
			int bytesPerRow = w * 4;
			byte[] raw = new byte[h * (bytesPerRow + 1)];

			for (int y = 0; y < h; y++) {
				raw[y * (bytesPerRow + 1)] = 0; // filter byte
				System.arraycopy(pixels, y * bytesPerRow, raw, y * (bytesPerRow + 1) + 1, bytesPerRow);
			}

			// Compress with zlib
			Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
			deflater.setInput(raw);
			deflater.finish();

			byte[] compressed = new byte[raw.length];
			int compressedSize = deflater.deflate(compressed);
			deflater.end();

			return Arrays.copyOf(compressed, compressedSize);
		}

		private static void writeChunk(ByteArrayOutputStream baos, String type, byte[] data) throws IOException {
			ByteBuffer chunk = ByteBuffer.allocate(4 + 4 + data.length + 4)
					.order(ByteOrder.BIG_ENDIAN)
					.putInt(data.length)                    // length
					.put(type.getBytes())                   // type
					.put(data);                             // data

			// CRC32 of type + data
			var crc = new CRC32();
			crc.update(type.getBytes());
			crc.update(data);

			chunk.putInt((int) crc.getValue());         // CRC

			baos.write(chunk.array());
		}
	}
}