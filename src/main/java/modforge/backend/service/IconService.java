package modforge.backend.service;

import modforge.Singleton;
import modforge.Util;
import modforge.backend.ModData;
import modforge.backend.model.ModItem;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

public final class IconService {
	private static final Logger log = Logger.getLogger(IconService.class.getName());
	
	private static final String TEXTURES_ROOT = "Libs/UI/Textures";
	private static final String FALLBACK_ICON = "crime_investigation_icon";
	
	private final UserService configService;
	
	
	// =====================================================================
	// Construction & lifecycle
	// =====================================================================
	
	public IconService(UserService configService) {
		this.configService = configService;
		init();
	}
	
	/**
	 * Scan a single PAK file for DDS textures under {@value #TEXTURES_ROOT} and
	 * add them to {@code target}. Returns the number of entries indexed.
	 */
	private static Map<String, byte[]> indexDdsFromPak(String pakPath) {
		final File pakFile = new File(pakPath);
		if (! pakFile.exists()) {
			log.fine("PAK not found – skipping icon scan: " + pakPath);
			return new HashMap<>();
		}
		
		final Map<String, byte[]> map = new HashMap<>();
		try (var zf = new ZipFile(pakFile)) {
			final var entries = zf.entries();
			while (entries.hasMoreElements()) {
				final var entry = entries.nextElement();
				final String name = entry.getName().replace('\\', '/');
				
				if (! name.startsWith(TEXTURES_ROOT))
					continue;
				if (! name.toLowerCase(Locale.ROOT).endsWith(".dds"))
					continue;
				
				final String stem = stemOf(name);
				if (stem.isBlank())
					continue;
				
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
	
	// =====================================================================
	// Mod icon loading
	// =====================================================================
	
	/** Extract the filename stem from a ZIP entry path (no directory, no extension). */
	private static String stemOf(String entryName) {
		int slash = entryName.lastIndexOf('/');
		String filename = slash >= 0 ? entryName.substring(slash + 1) : entryName;
		int dot = filename.lastIndexOf('.');
		return (dot > 0 ? filename.substring(0, dot) : filename).toLowerCase(Locale.ROOT);
	}
	
	// =====================================================================
	// Public API – mod-aware icon resolution
	// =====================================================================
	
	static BufferedImage convertToImage(byte[] ddsBytes) throws IOException {
		final DDSUtil.DDSImage ddsImage = DDSUtil.decodeWithInfo(ddsBytes);
		if (ddsImage == null)
			throw new IOException("Failed to decode DDS image");
		return ddsImage.toBufferedImage();
	}
	
	/**
	 * (Re-)scan IPL_GameData.pak and index every DDS file found under
	 * Libs/UI/Textures/. Safe to call again after a game-directory change.
	 * Only populates the base-game index; mod icons are loaded per-mod.
	 */
	public void init() {
		final var game = Singleton.INSTANCE.game();
		
		
		// DISABLING since it uses up over 3gigs of memory, so i will rework it
		// TODO : rework
		if (1 == 1) {
			return;
		}
		// to not get unreachable compile time exception
		
		
		final String gameDir = configService.gameDirectory;
		if (gameDir == null || gameDir.isBlank())
			return;
		loadModIconsForMod(game, false);
		game.setIcon(indexDdsFromPak(Util.icons(gameDir)));
	}
	
	/**
	 * Scan all PAK files inside Mods/<modId>/Data/ for DDS textures and store
	 * the raw bytes in mod.iconIndex. Clears any previously cached PNGs for
	 * the mod so stale data-URIs are never returned.
	 * <p/>
	 * Call this from ModService.fillCollection() after the mod's items are loaded.
	 */
	public void loadModIconsForMod(ModData mod, boolean isMod) {
		final String gameDir = configService.gameDirectory;
		
		final Path dataFolder;
		if (isMod) {
			dataFolder = Path.of(Util.modData(gameDir, mod.id));
		} else {
			dataFolder = Util.gameDataDir(gameDir);
		}
		
		if (! Files.exists(dataFolder))
			return;
		
		final Map<String, byte[]> map = new HashMap<>();
		try (var stream = Files.list(dataFolder)) {
			for (Path pakPath : stream.filter(Files::isRegularFile).filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".pak")).toList()) {
				map.putAll(indexDdsFromPak(pakPath.toString()));
			}
		} catch (IOException e) {
			log.warning("Cannot list Data folder for mod " + mod.id + ": " + e.getMessage());
		}
		mod.setIcon(map);
		int total = mod.getIcon().size();
		if (total > 0) {
			log.info(String.format("Mod '%s': indexed %d icon(s) from Data PAK(s).", mod.id, total));
		}
	}
	
	/**
	 * Return a base64 data-URI for the icon of the given mod item, resolving
	 * against mod-local icons first, then the base-game index.
	 *
	 * @param item The mod item whose icon_id / IconId attribute is used.
	 * @param mod  The mod that owns the item (maybe {@code Singleton.INSTANCE.game()}).
	 * @return the image.
	 */
	public BufferedImage getIcon(ModItem item, ModData mod) {
		if (item == null || item.getAttributes() == null)
			return null;
		
		final var iconAttr = item.getAttributes().stream().filter(a -> a.getName().equalsIgnoreCase("icon_id") || a.getName().equalsIgnoreCase("IconId")).findFirst().orElse(null);
		
		if (iconAttr == null || iconAttr.getValue() == null)
			return null;
		final String rawValue = iconAttr.getValue().toString();
		
		final boolean useFallback = rawValue.equals("0") || rawValue.equalsIgnoreCase("replaceme");
		
		final String iconId = useFallback ? FALLBACK_ICON : rawValue;
		return getBase64Icon(iconId, mod);
	}
	
	// =====================================================================
	// Private helpers
	// =====================================================================
	
	/**
	 * Convenience overload for base-game items (no per-mod index to check).
	 */
	public BufferedImage getIcon(ModItem item) {
		return getIcon(item, Singleton.INSTANCE.game());
	}
	
	/**
	 * Return a base64 data-URI for a named icon.
	 * @param iconId Icon filename without extension.
	 * @param mod    The owning mod; pass {@code Singleton.INSTANCE.game()} for game items.
	 * @return the image.
	 */
	private BufferedImage getBase64Icon(String iconId, ModData mod) {
		if (iconId == null || iconId.isBlank())
			return null;
		final String key = iconId.toLowerCase(Locale.ROOT);
		
		// 1. Mod's raw DDS index
		final byte[] modDds = mod.getIcon().get(key);
		if (modDds != null) {
			return convert(key, modDds);
		}
		
		// 2. Base-game raw DDS index
		final var game = Singleton.INSTANCE.game();
		if (mod != game) {
			final byte[] baseDds = game.getIcon().get(key);
			if (baseDds != null) {
				return convert(key, baseDds);
			}
		}
		
		log.fine("Icon not found in any index: " + iconId);
		return null;
	}
	
	/**
	 * Return true if the named icon exists in the mod's index or the base-game index.
	 */
	public boolean hasIcon(String iconId, ModData mod) {
		if (iconId == null || iconId.isBlank())
			return false;
		final String key = iconId.toLowerCase(Locale.ROOT);
		return (mod != Singleton.INSTANCE.game() && mod.getIcon().containsKey(key));
	}
	
	/**
	 * Convert raw DDS bytes to a PNG data-URI, store in {@code cache}, and return it.
	 * Returns {@code null} if conversion fails.
	 */
	private BufferedImage convert(String key, byte[] ddsBytes) {
		try {
			return convertToImage(ddsBytes);
		} catch (IOException ex) {
			log.warning("DDS→PNG conversion failed for '" + key + "': " + ex.getMessage());
			return null;
		}
	}
}