package modforge.backend.service;

import modforge.*;
import modforge.backend.*;
import modforge.backend.model.*;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.zip.*;

public final class IconService implements Closeable {
	private static final Logger log = Logger.getLogger(IconService.class.getName());

	private static final String TEXTURES_ROOT = "Libs/UI/Textures";
	private static final String FALLBACK_ICON  = "crime_investigation_icon";

	private final UserService configService;
	

	// =====================================================================
	// Construction & lifecycle
	// =====================================================================

	public IconService(UserService configService) {
		this.configService = configService;
		init();
	}

	/**
	 * (Re-)scan IPL_GameData.pak and index every DDS file found under
	 * Libs/UI/Textures/. Safe to call again after a game-directory change.
	 * Only populates the base-game index; mod icons are loaded per-mod.
	 */
	public void init() {
		var game = Singleton.INSTANCE.game();

		final String gameDir = configService.gameDirectory;
		if (gameDir == null || gameDir.isBlank()) return;
		loadModIconsForMod(game, false);
		game.setIcon(indexDdsFromPak(Util.join(gameDir, ItemType.GAMEDATA)));
	}

	// =====================================================================
	// Mod icon loading
	// =====================================================================

	/**
	 * Scan all PAK files inside Mods/<modId>/Data/ for DDS textures and store
	 * the raw bytes in mod.iconIndex. Clears any previously cached PNGs for
	 * the mod so stale data-URIs are never returned.
	 *
	 * Call this from ModService.fillCollection() after the mod's items are loaded.
	 */
	public void loadModIconsForMod(ModData mod, boolean isMod) {
		final String gameDir = configService.gameDirectory;

		final Path dataFolder;
		if (isMod) {
			dataFolder = Path.of(Util.join(gameDir, "Mods", mod.id, "Data"));
		} else {
			dataFolder = Path.of(Util.join(gameDir, "Data"));
		}

		if (!Files.exists(dataFolder)) return;

		final Map<String,byte[]> map = new HashMap<>();
		try (var stream = Files.list(dataFolder)) {
			for (Path pakPath : stream
					.filter(Files::isRegularFile)
					.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".pak"))
					.toList()) {
				map.putAll(indexDdsFromPak(pakPath.toString()));
			}
		} catch (IOException e) {
			log.warning("Cannot list Data folder for mod " + mod.id + ": " + e.getMessage());
		}
		mod.setIcon(map);
		int total = mod.getItems().size();
		if (total > 0) {
			log.info(String.format("Mod '%s': indexed %d icon(s) from Data PAK(s).", mod.id, total));
		}
	}

	// =====================================================================
	// Public API – mod-aware icon resolution
	// =====================================================================

	/**
	 * Return a base64 data-URI for the icon of the given mod item, resolving
	 * against mod-local icons first, then the base-game index.
	 *
	 * @param item The mod item whose icon_id / IconId attribute is used.
	 * @param mod  The mod that owns the item (may be {@code Singleton.INSTANCE.game()}).
	 * @return data-URI string, or {@code null} if no icon is found.
	 */
	public String getIcon(ModItem item, ModData mod) {
		if (item == null || item.getAttributes() == null) return null;

		final var iconAttr = item.getAttributes().stream()
				.filter(a -> a.getName().equalsIgnoreCase("icon_id") || a.getName().equalsIgnoreCase("IconId"))
				.findFirst().orElse(null);

		if (iconAttr == null || iconAttr.getValue() == null) return null;
		final String rawValue = iconAttr.getValue().toString();

		final boolean useFallback = rawValue.equals("0") || rawValue.equalsIgnoreCase("replaceme");

		final String iconId = useFallback ? FALLBACK_ICON : rawValue;
		return getBase64Icon(iconId, mod);
	}

	/**
	 * Convenience overload for base-game items (no per-mod index to check).
	 */
	public String getIcon(ModItem item) {
		return getIcon(item, Singleton.INSTANCE.game());
	}

	/**
	 * Return a base64 data-URI for a named icon.
	 * Resolution order:
	 *   1. mod.pngCache  (already converted this session)
	 *   2. mod.iconIndex  (raw DDS bytes in the mod's own PAKs)
	 *   3. basePngCache   (already converted base-game icon)
	 *   4. baseIconIndex  (raw DDS bytes from IPL_GameData.pak)
	 *
	 * @param iconId Icon filename without extension.
	 * @param mod    The owning mod; pass {@code Singleton.INSTANCE.game()} for game items.
	 * @return data-URI, or {@code null} if the icon is not found anywhere.
	 */
	public String getBase64Icon(String iconId, ModData mod) {
		if (iconId == null || iconId.isBlank()) return null;
		final String key = iconId.toLowerCase(Locale.ROOT);
		var game = Singleton.INSTANCE.game();

		// 1. Mod's own PNG cache (free lookup)
		if (mod != game) {
			final String cached = mod.getPng().get(key);
			if (cached != null) return cached;

			// 2. Mod's raw DDS index
			final byte[] modDds = mod.getIcon().get(key);
			if (modDds != null) {
				return convertAndCache(key, modDds, mod);
			}
		}

		// 3. Base-game PNG cache
		final String baseCached = game.getPng().get(key);
		if (baseCached != null) return baseCached;

		// 4. Base-game raw DDS index
		final byte[] baseDds = game.getIcon().get(key);
		if (baseDds != null) {
			return convertAndCache(key, baseDds, game);
		}

		log.fine("Icon not found in any index: " + iconId);
		return null;
	}

	/**
	 * Convenience overload when no per-mod index is needed.
	 */
	public String getBase64Icon(String iconId) {
		return getBase64Icon(iconId, Singleton.INSTANCE.game());
	}

	/**
	 * Return true if the named icon exists in the mod's index or the base-game index.
	 */
	public boolean hasIcon(String iconId, ModData mod) {
		if (iconId == null || iconId.isBlank()) return false;
		final String key = iconId.toLowerCase(Locale.ROOT);
		return (mod != Singleton.INSTANCE.game() && mod.getIcon().containsKey(key));
	}

	/** Return true if the named icon exists in the base-game index. */
	public boolean hasIcon(String iconId) {
		return hasIcon(iconId, Singleton.INSTANCE.game());
	}

	@Override
	public void close() { /* stateless file handles – nothing to release */ }

	// =====================================================================
	// Private helpers
	// =====================================================================

	/**
	 * Scan a single PAK file for DDS textures under {@value #TEXTURES_ROOT} and
	 * add them to {@code target}. Returns the number of entries indexed.
	 */
	private static Map<String, byte[]> indexDdsFromPak(String pakPath) {
		final File pakFile = new File(pakPath);
		if (!pakFile.exists()) {
			log.fine("PAK not found – skipping icon scan: " + pakPath);
			return new HashMap<>();
		}

		final Map<String, byte[]> map = new HashMap<>();
		try (var zf = new ZipFile(pakFile)) {
			final var entries = zf.entries();
			while (entries.hasMoreElements()) {
				final var entry = entries.nextElement();
				final String name = entry.getName().replace('\\', '/');

				if (!name.startsWith(TEXTURES_ROOT)) continue;
				if (!name.toLowerCase(Locale.ROOT).endsWith(".dds")) continue;

				final String stem = stemOf(name);
				if (stem.isBlank()) continue;

				try (var is = zf.getInputStream(entry)) {
					map.put(stem, is.readAllBytes());
				} catch (IOException ex) {
					log.fine("Could not read icon entry " + name + ": " + ex.getMessage());
				}
			}
		} catch (IOException ex) {
			log.severe("Cannot open PAK for icon indexing (" + pakPath + "): " + ex.getMessage());
		}
		return map;
	}

	/**
	 * Convert raw DDS bytes to a PNG data-URI, store in {@code cache}, and return it.
	 * Returns {@code null} if conversion fails.
	 */
	private String convertAndCache(String key, byte[] ddsBytes, ModData mod) {
		try {
			final byte[] pngData = DdsConverter.convertToPng(ddsBytes);
			final String dataUri = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(pngData);
			mod.addPng(key, dataUri);
			return dataUri;
		} catch (Exception ex) {
			log.warning("DDS→PNG conversion failed for '" + key + "': " + ex.getMessage());
			return null;
		}
	}

	/** Extract the filename stem from a ZIP entry path (no directory, no extension). */
	private static String stemOf(String entryName) {
		int slash = entryName.lastIndexOf('/');
		String filename = slash >= 0 ? entryName.substring(slash + 1) : entryName;
		int dot = filename.lastIndexOf('.');
		return (dot > 0 ? filename.substring(0, dot) : filename).toLowerCase(Locale.ROOT);
	}

	private static final class DdsConverter {

		static byte[] convertToPng(byte[] ddsBytes) throws IOException {
			final DDSUtil.DDSImage ddsImage = DDSUtil.decodeWithInfo(ddsBytes);
			if (ddsImage == null) throw new IOException("Failed to decode DDS image");
			return encodeAsPNG(ddsImage);
		}

		private static byte[] encodeAsPNG(DDSUtil.DDSImage img) throws IOException {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			baos.writeBytes("\u0089PNG\r\n\u001A\n".getBytes());
			writeChunk(baos, "IHDR", ihdr(img.width, img.height));
			writeChunk(baos, "IDAT", idat(img.pixels, img.width, img.height));
			writeChunk(baos, "IEND", new byte[0]);
			return baos.toByteArray();
		}

		private static byte[] ihdr(int w, int h) {
			return ByteBuffer.allocate(13)
				.order(ByteOrder.BIG_ENDIAN)
				.putInt(w).putInt(h)
				.put((byte) 8)   // bit depth
				.put((byte) 6)   // RGBA
				.put((byte) 0).put((byte) 0).put((byte) 0)
				.array();
		}

		private static byte[] idat(byte[] pixels, int w, int h) {
			int bytesPerRow = w * 4;
			byte[] raw = new byte[h * (bytesPerRow + 1)];
			for (int y = 0; y < h; y++) {
				raw[y * (bytesPerRow + 1)] = 0;
				System.arraycopy(pixels, y * bytesPerRow, raw, y * (bytesPerRow + 1) + 1, bytesPerRow);
			}
			Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
			deflater.setInput(raw);
			deflater.finish();
			byte[] compressed = new byte[raw.length];
			int size = deflater.deflate(compressed);
			deflater.end();
			return Arrays.copyOf(compressed, size);
		}

		private static void writeChunk(ByteArrayOutputStream baos, String type, byte[] data) throws IOException {
			ByteBuffer chunk = ByteBuffer.allocate(4 + 4 + data.length + 4)
				.order(ByteOrder.BIG_ENDIAN)
				.putInt(data.length)
				.put(type.getBytes())
				.put(data);
			var crc = new CRC32();
			crc.update(type.getBytes());
			crc.update(data);
			chunk.putInt((int) crc.getValue());
			baos.write(chunk.array());
		}
	}
}