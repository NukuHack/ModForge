package modforge.backend.service;

import image.DDSUtil;
import modforge.Singleton;
import modforge.Util;
import modforge.backend.ModData;
import modforge.backend.model.ModItem;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

@lombok.extern.slf4j.Slf4j
public final class IconService {
	
	private static final String TEXTURES_ROOT = "Libs/UI/Textures";
	private static final String FALLBACK_ICON = "crime_investigation_icon";
	
	private final UserConfig userConfig;
	
	
	// =====================================================================
	// Construction & lifecycle
	// =====================================================================
	
	public IconService(UserConfig userConfig) {
		this.userConfig = userConfig;
	}
	
	/**
	 * Scan a single PAK file for DDS textures under {@value #TEXTURES_ROOT} and
	 * add them to {@code target}. Returns the number of entries indexed.
	 */
	private static Map<String, byte[]> indexDdsFromPak(String pakPath) {
		final var pakFile = Path.of(pakPath);
		
		if (! pakFile.toFile().exists()) {
			log.info("PAK not found – skipping icon scan: {}", pakPath);
			return new HashMap<>();
		}
		
		final Map<String, byte[]> map = new HashMap<>();
		try (var zf = new ZipFile(pakFile.toFile())) {
			for (final var entry : zf.stream().filter(ze -> {
				final String name = ze.getName();
				if (! name.endsWith(".dds"))
					return false;
				return name.startsWith(TEXTURES_ROOT);
			}).toList()) {
				final String name = entry.getName().replace('\\', '/');
				try (var is = zf.getInputStream(entry)) {
					final String stem = Util.stemOf(name);
					if (stem.isBlank())
						continue;
					map.put(stem, is.readAllBytes());
				} catch (Exception ex) {
					log.info("Could not read icon entry {}: {}", name, ex.getMessage());
				}
			}
		} catch (IOException ex) {
			log.error("Cannot open PAK for icon indexing ({}): {}", pakPath, ex.getMessage());
		}
		return map;
	}
	
	static BufferedImage convertToImage(byte[] ddsBytes) throws IOException {
		final DDSUtil.DDSImage ddsImage = DDSUtil.decodeWithInfo(ddsBytes);
		if (ddsImage == null)
			throw new IOException("Failed to decode DDS image");
		return ddsImage.toBufferedImage();
	}
	
	/**
	 * Convert DDS → PNG (or PNG → DDS) for a single file, a folder, or a
	 * compressed archive (.zip / .pak or any file whose magic bytes indicate
	 * a ZIP container).
	 *
	 * <ul>
	 *   <li><b>Single .dds file</b> → writes a .png next to it.</li>
	 *   <li><b>Single .png file</b> → writes a .dds next to it (reverse).</li>
	 *   <li><b>Folder</b> → recursively converts every matching file in-place
	 *       (sibling output).</li>
	 *   <li><b>Archive (.zip / .pak / ZIP magic)</b> → extracts every matching
	 *       entry, converts it, and writes the result into a new folder placed
	 *       next to the archive, preserving the internal path structure.</li>
	 * </ul>
	 *
	 * If the output file already exists it is moved to an {@code image_backup/}
	 * folder next to it with a {@code name_<8-char-timestamp>.<ext>} suffix
	 * before the new file is written.
	 *
	 * @param inputPath path to a file, folder, or archive
	 * @param toPng     {@code true}  → DDS → PNG conversion
	 *                  {@code false} → PNG → DDS conversion
	 */
	public static void convertImages(String inputPath, boolean toPng) {
		if (inputPath == null || inputPath.isBlank()) {
			log.warn("convertImages: null or blank input path – nothing to do.");
			return;
		}
		
		final Path source = Path.of(inputPath);
		if (! Files.exists(source)) {
			log.warn("convertImages: path does not exist – {}", inputPath);
			return;
		}
		
		try {
			if (Files.isDirectory(source)) {
				convertDirectory(source, source, toPng);
			} else if (Util.isZipLike(source)) {
				convertArchive(source, toPng);
			} else {
				convertSingleFile(source, source.getParent(), toPng);
			}
		} catch (IOException ex) {
			log.error("convertImages failed for '{}': {}", inputPath, ex.getMessage());
		}
	}
	
	// =====================================================================
	// Bulk conversion utilities
	// =====================================================================
	
	/** Recurse into a directory, converting every matching file. */
	private static void convertDirectory(Path dir, Path outputBase, boolean toPng) throws IOException {
		try (var stream = Files.walk(dir)) {
			final String srcExt = toPng ? ".dds" : ".png";
			stream.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(srcExt)).forEach(p -> convertSingleFile(p, p.getParent(), toPng));
		}
	}
	
	/**
	 * Extract a ZIP/PAK, convert each matching entry, and write results into
	 * {@code <archiveName>_converted/} next to the archive.
	 */
	private static void convertArchive(Path archive, boolean toPng) throws IOException {
		// e.g. MyPak.pak  →  MyPak_converted/
		final String archiveStem = Util.stemOf(archive.getFileName().toString());
		final Path outRoot = archive.getParent().resolve(archiveStem + "_converted");
		Files.createDirectories(outRoot);
		
		final String srcExt = toPng ? ".dds" : ".png";
		
		try (var zf = new ZipFile(archive.toFile())) {
			for (final var entry : zf.stream().filter(ze -> ! ze.isDirectory()).filter(ze -> ze.getName().toLowerCase(Locale.ROOT).endsWith(srcExt)).toList()) {
				
				// Preserve internal path structure inside the output folder
				final String normalised = entry.getName().replace('\\', '/');
				final Path entryOut = outRoot.resolve(normalised).getParent();
				Files.createDirectories(entryOut);
				
				final byte[] raw;
				try (var is = zf.getInputStream(entry)) {
					raw = is.readAllBytes();
				} catch (IOException ex) {
					log.info("Could not read archive entry '{}': {}", entry.getName(), ex.getMessage());
					continue;
				}
				
				// Write the raw bytes to a temp file so convertSingleFile can read it
				final String entryFilename = normalised.contains("/") ? normalised.substring(normalised.lastIndexOf('/') + 1) : normalised;
				final Path tmp = entryOut.resolve(entryFilename);
				Files.write(tmp, raw);
				convertSingleFile(tmp, entryOut, toPng);
				// Optionally delete the extracted source file after conversion:
				// Files.deleteIfExists(tmp);
			}
		}
		log.info("Archive conversion complete → {}", outRoot);
	}
	
	// ------------------------------------------------------------------ //
	//  Internal helpers                                                    //
	// ------------------------------------------------------------------ //
	
	/**
	 * Convert one file (DDS↔PNG), writing the output into {@code outputDir}.
	 * Backs up any pre-existing output file before overwriting.
	 */
	private static void convertSingleFile(Path src, Path outputDir, boolean toPng) {
		final String srcName = src.getFileName().toString();
		final String srcLower = srcName.toLowerCase(Locale.ROOT);
		final String srcExt = toPng ? ".dds" : ".png";
		final String dstExt = toPng ? ".png" : ".dds";
		
		if (! srcLower.endsWith(srcExt))
			return; // wrong type for this direction
		
		final String stem = srcName.substring(0, srcName.length() - srcExt.length());
		final Path dstPath = outputDir.resolve(stem + dstExt);
		
		try {
			backupIfExists(dstPath);
			
			if (toPng) {
				// DDS → PNG
				final byte[] ddsBytes = Files.readAllBytes(src);
				final BufferedImage img = convertToImage(ddsBytes);
				ImageIO.write(img, "png", dstPath.toFile());
				log.info("DDS→PNG: {} → {}", src, dstPath);
			} else {
				// caller gets a clear error rather than silent data loss.
				final BufferedImage img = ImageIO.read(src.toFile());
				if (img == null)
					throw new IOException("ImageIO could not read PNG: " + src);
				final byte[] ddsBytes = DDSUtil.compressToBC7(img);
				Files.write(dstPath, ddsBytes);
				log.info("PNG→DDS: {} → {}", src, dstPath);
			}
		} catch (IOException ex) {
			log.warn("Conversion failed for '{}': {}", src, ex.getMessage());
		}
	}
	
	/**
	 * If {@code path} already exists, move it to an {@code image_backup/}
	 * folder next to it, renaming to {@code stem_<8hexchars>.<ext>}.
	 * The 8-char suffix is the low 32 bits of {@link System#nanoTime()} in hex –
	 * effectively unique without any extra file-existence checks.
	 */
	private static void backupIfExists(Path path) throws IOException {
		if (! Files.exists(path))
			return;
		
		final Path backupDir = path.getParent().resolve("image_backup");
		Files.createDirectories(backupDir);
		
		final String filename = path.getFileName().toString();
		final int dot = filename.lastIndexOf('.');
		final String stem = dot > 0 ? filename.substring(0, dot) : filename;
		final String ext = dot > 0 ? filename.substring(dot) : "";
		
		final String suffix = Util.getRandomString(32);
		final Path backupPath = backupDir.resolve(stem + "_" + suffix + ext);
		
		Files.move(path, backupPath);
		log.info("Backed up existing file: {} → {}", path, backupPath);
	}
	
	/**
	 * (Re-)scan IPL_GameData.pak and index every DDS file found under
	 * Libs/UI/Textures/. Safe to call again after a game-directory change.
	 * Only populates the base-game index; mod icons are loaded per-mod.
	 */
	public void init() {
		final var game = Singleton.INSTANCE.getGame();
		
		final String gameDir = userConfig.getGameDirectory();
		if (gameDir == null || gameDir.isBlank())
			return;
		
		game.setIcon(loadModIcons(Util.icons(gameDir)));
	}
	
	/**
	 * Scan all PAK files inside Mods/<modId>/Data/ for DDS textures and store
	 * the raw bytes in mod.iconIndex. Clears any previously cached PNGs for
	 * the mod so stale data-URIs are never returned.
	 * <p/>
	 * Call this from ModService.fillCollection() after the mod's items are loaded.
	 */
	public static Map<String, byte[]> loadModIcons(Path modPath) {
		if (! Files.exists(modPath)) {
			log.warn("PAK directory does not exist: {}", modPath);
			return Map.of();
		}
		
		final Map<String, byte[]> map = new HashMap<>();
		try (var stream = Files.list(modPath)) {
			final var pakFiles = stream.filter(excludeNonIconPaks()).collect(Collectors.toSet());
			
			if (pakFiles.isEmpty()) {
				log.info("No PAK files found in: {}", modPath);
				return Map.of();
			}
			for (Path pakPath : pakFiles) {
				map.putAll(indexDdsFromPak(pakPath.toString()));
			}
		} catch (IOException e) {
			log.warn("Cannot list Data folder for path {}: {}", modPath, e.getMessage());
		}
		int total = map.size();
		if (total > 0) {
			log.info("Mod path '{}': indexed {} icon(s) from Data PAK(s).", modPath, total);
		}
		return map;
	}
	
	/**
	 * Exclude PAKs that don't contain icon data.
	 */
	private static Predicate<Path> excludeNonIconPaks() {
		return p -> {
			if (! Files.isRegularFile(p))
				return false;
			final var name = p.toString().toLowerCase(Locale.ROOT);
			return name.endsWith(".pak");
		};
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
		
		final var iconAttr = item.getAttributes().stream().filter(a -> a.getName().equals("icon_id")).findFirst().orElse(null);
		
		if (iconAttr == null || iconAttr.getValue() == null)
			return null;
		final String rawValue = iconAttr.getValue().toString();
		
		final boolean useFallback = rawValue.equals("0") || rawValue.equals("replaceme");
		
		final String iconId = useFallback ? FALLBACK_ICON : rawValue;
		return getBase64Icon(iconId, mod);
	}
	
	/**
	 * Convenience overload for base-game items (no per-mod index to check).
	 */
	public BufferedImage getIcon(ModItem item) {
		return getIcon(item, Singleton.INSTANCE.getGame());
	}
	
	/**
	 * Return a base64 data-URI for a named icon.
	 * @param iconId Icon filename without extension.
	 * @param mod    The owning mod; pass {@code Singleton.INSTANCE.game()} for game items.
	 * @return the image.
	 */
	private BufferedImage getBase64Icon(String iconId, ModData mod) {
		final String key = iconId.toLowerCase(Locale.ROOT);
		
		// 1. Mod's raw DDS index
		final byte[] modDds = mod.getIcon().get(key);
		if (modDds != null) {
			return convert(key, modDds);
		}
		
		// 2. Base-game raw DDS index
		final var game = Singleton.INSTANCE.getGame();
		if (mod != game) {
			final byte[] baseDds = game.getIcon().get(key);
			if (baseDds != null) {
				return convert(key, baseDds);
			}
		}
		
		log.info("Icon not found in any index: {}", iconId);
		return null;
	}
	
	/**
	 * Return true if the named icon exists in the mod's index or the base-game index.
	 */
	public boolean hasIcon(String iconId, ModData mod) {
		if (iconId == null || iconId.isBlank())
			return false;
		final String key = iconId.toLowerCase(Locale.ROOT);
		return (mod != Singleton.INSTANCE.getGame() && mod.getIcon().containsKey(key));
	}
	
	/**
	 * Convert raw DDS bytes to a PNG data-URI, store in {@code cache}, and return it.
	 * Returns {@code null} if conversion fails.
	 */
	private BufferedImage convert(String key, byte[] ddsBytes) {
		try {
			return convertToImage(ddsBytes);
		} catch (IOException ex) {
			log.warn("DDS→PNG conversion failed for '{}': {}", key, ex.getMessage());
			return null;
		}
	}
}