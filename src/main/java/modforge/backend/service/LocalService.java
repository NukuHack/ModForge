package modforge.backend.service;

import modforge.Singleton;
import modforge.Util;
import modforge.backend.ModData;
import modforge.backend.model.Language;
import modforge.backend.model.ModItem;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

public final class LocalService {
	private static final Logger log = Logger.getLogger(LocalService.class.getName());
	private static final String EXTRA = "_xml.pak";
	private static final Set<String> RELEVANT_FILES = Set.of("text_ui_soul.xml", "text_ui_tutorials.xml", "text_ui_quest.xml", "text_ui_misc.xml", "text_ui_minigames.xml", "text_ui_menus.xml", "text_ui_items.xml", "text_ui_ingame.xml");
	
	private final UserService configService;
	
	public LocalService(UserService configService) {
		this.configService = configService;
		init();
	}
	
	// ==================================================================
	// PUBLIC API
	// ==================================================================
	
	/**
	 * (Re-)load from disk. Call after the user sets a new game directory.
	 */
	public void init() {
		final String dir = configService.gameDirectory;
		if (dir != null && ! dir.isBlank()) {
			try {
				Singleton.INSTANCE.game().setLocal(readLocalizationFromXml(dir, false));
			} catch (Exception ex) {
				log.severe("Localisation read failed: " + ex.getMessage());
			}
		}
	}
	
	// ------------------------------------------------------------------
	// Mod-aware getters  (check mod first, fall back to base game)
	// ------------------------------------------------------------------
	
	/**
	 * Resolve the display name of {@code item}, checking {@code mod}'s own
	 * localizations before falling back to the base-game strings.
	 */
	public String getName(ModItem item, ModData mod) {
		return resolve(item, mod, "ui_name", "UIName", "name");
	}
	
	/**
	 * Resolve the description of {@code item} with mod-then-base fallback.
	 */
	public String getDescription(ModItem item, ModData mod) {
		return resolve(item, mod, "ui_desc", "UIInfo");
	}
	
	/**
	 * Resolve the lore description of {@code item} with mod-then-base fallback.
	 */
	public String getLoreDescription(ModItem item, ModData mod) {
		return resolve(item, mod, "ui_lore_desc");
	}
	
	// ------------------------------------------------------------------
	// Convenience overloads for base-game items (no per-mod map)
	// ------------------------------------------------------------------
	
	/** Resolve the display name of a base-game item. */
	public String getName(ModItem item) {
		return getName(item, Singleton.INSTANCE.game());
	}
	
	/** Resolve the description of a base-game item. */
	public String getDescription(ModItem item) {
		return getDescription(item, Singleton.INSTANCE.game());
	}
	
	/** Resolve the lore description of a base-game item. */
	public String getLoreDescription(ModItem item) {
		return getLoreDescription(item, Singleton.INSTANCE.game());
	}
	
	// ------------------------------------------------------------------
	// Direct key look-up (useful for UI that already has a string key)
	// ------------------------------------------------------------------
	
	/**
	 * Look up a raw localization key in the mod's strings, then the base game.
	 * Returns {@code null} if not found in either.
	 */
	public String resolve(String key, ModData mod) {
		if (key == null || key.isBlank())
			return null;
		final Language lang = currentLanguage();
		final var game = Singleton.INSTANCE.game();
		// 1. Mod's own strings
		if (mod != game) {
			final var modMap = mod.getLocal().get(lang);
			if (modMap != null && modMap.containsKey(key))
				return modMap.get(key);
		}
		
		// 2. Base-game strings
		final var baseMap = game.getLocal().get(lang);
		if (baseMap != null && baseMap.containsKey(key))
			return baseMap.get(key);
		
		return null;
	}
	
	/**
	 * Look up a raw localization key in the base-game strings only.
	 */
	public String resolve(String key) {
		return resolve(key, Singleton.INSTANCE.game());
	}
	
	// ==================================================================
	// READ OPERATIONS
	// ==================================================================
	
	/**
	 * Read all localisation paks from the game directory.
	 * Returns: Language enum -> (string-key -> localised-value)
	 */
	public Map<Language, Map<String, String>> readLocalizationFromXml(String root, boolean isMod) {
		final var result = new EnumMap<Language, Map<String, String>>(Language.class);
		
		int files = 0;
		for (final String pakPath : Util.allLocPaths(root)) {
			final File f = new File(pakPath);
			if (! f.exists())
				continue;
			files++;
			final String baseName = f.getName().replace(EXTRA, "");
			final Language language = Language.fromDisplayName(baseName);
			if (language == null)
				continue;
			
			try (final var zf = new ZipFile(f)) {
				final var it = zf.entries();
				while (it.hasMoreElements()) {
					final var entry = it.nextElement();
					final String name = new File(entry.getName()).getName().toLowerCase(Locale.ROOT);
					if (! isMod && ! RELEVANT_FILES.contains(name))
						continue;
					
					try (var is = zf.getInputStream(entry)) {
						var parsed = parseLocalizationXml(is);
						result.computeIfAbsent(language, k -> new HashMap<>()).putAll(parsed);
					}
				}
			} catch (Exception ex) {
				log.warning("Localisation read error (" + pakPath + "): " + ex.getMessage());
			}
		}
		log.info(String.format("Loaded %d languages from %d PAK file(s)", result.size(), files));
		return result;
	}
	
	/**
	 * Parse a single localisation XML stream.
	 */
	public Map<String, String> parseLocalizationXml(InputStream is) throws Exception {
		var result = new LinkedHashMap<String, String>();
		var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
		var rows = doc.getElementsByTagName("Row");
		for (int i = 0; i < rows.getLength(); i++) {
			var cells = ((Element) rows.item(i)).getElementsByTagName("Cell");
			if (cells.getLength() < 3)
				continue;
			String key = cells.item(0).getTextContent().trim();
			String value = cells.item(2).getTextContent().trim();
			if (! key.isBlank())
				result.put(key, value);
		}
		return result;
	}
	
	// ==================================================================
	// WRITE OPERATIONS
	// ==================================================================
	
	/**
	 * Write per-language localization files into the mod's Localization folder.
	 * Each language gets: Localization/<Lang>_xml/text__<modId>.xml
	 */
	public void writeModLocalization(ModData mod) {
		final var gameDir = configService.gameDirectory;
		boolean ok = writeModLocalizationFiles(gameDir, mod.id, mod.getLocal());
		if (ok) {
			log.info("Localization written for mod: " + mod.id);
		} else {
			log.warning("Localization write had errors for mod: " + mod.id);
		}
	}
	
	/**
	 * Write mod-specific localization files into the mod's Localization directory.
	 */
	private boolean writeModLocalizationFiles(String gameDirectory, String modId, Map<Language, Map<String, String>> localizations) {
		if (localizations.isEmpty())
			return true;
		
		boolean allOk = true;
		for (final var entry : localizations.entrySet()) {
			final Language language = entry.getKey();
			final Map<String, String> translations = entry.getValue();
			if (translations.isEmpty())
				continue;
			
			final String locFilePath = Util.locExport(gameDirectory, language.getDisplayName(), modId);
			final Path locPath = Path.of(locFilePath);
			
			try {
				final var xmlString = makeLocalizationXml(translations);
				Util.writeXml(xmlString, locPath);
				log.info("Localization written: " + locFilePath + " (" + translations.size() + " entries)");
			} catch (final Exception ex) {
				log.warning("Failed to write localization for " + language + ": " + ex.getMessage());
				allOk = false;
			}
		}
		return allOk;
	}
	
	public String makeLocalizationXml(Map<String, String> entries) {
		var sb = new StringBuilder();
		//sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		// xml declaration appended later
		sb.append("<Table>\n");
		
		for (var entry : entries.entrySet()) {
			sb.append("<Row>\n")
				.append("<Cell>").append(Util.escapeXml(entry.getKey())).append("</Cell>\n")
				.append("<Cell/>\n")
				.append("<Cell>").append(Util.escapeXml(entry.getValue())).append("</Cell>\n")
				.append("</Row>\n");
		}
		
		sb.append("</Table>\n");
		return sb.toString();
	}
	
	// ==================================================================
	// PRIVATE HELPERS
	// ==================================================================
	
	/**
	 * Resolve one of several candidate attribute names on {@code item} to a
	 * localized string. Resolution order for each candidate key found:
	 *   1. mod's own localizations for the current language
	 *   2. base-game localizations for the current language
	 *   3. the raw attribute value (key itself) as a last resort
	 *
	 * Returns {@code null} if no candidate attribute is present on the item.
	 */
	private String resolve(ModItem item, ModData mod, String... candidates) {
		final Language lang = currentLanguage();
		// Pull the two lang maps once – either may be null if never populated.
		final var game = Singleton.INSTANCE.game();
		final Map<String, String> modMap = (mod != game) ? mod.getLocal().get(lang) : new HashMap<>();
		final Map<String, String> baseMap = game.getLocal().get(lang);
		
		for (String candidate : candidates) {
			final String clo = candidate.toLowerCase(Locale.ROOT);
			
			final var attr = item.getAttributes().stream().filter(a -> a.getName().toLowerCase(Locale.ROOT).contains(clo)).findFirst().orElse(null);
			if (attr == null)
				continue;
			
			final String key = String.valueOf(attr.getValue());
			
			// 1. Mod-local strings
			if (modMap.containsKey(key))
				return modMap.get(key);
			
			// 2. Base-game strings
			if (baseMap.containsKey(key))
				return baseMap.get(key);
			
			// 3. Raw value (the key itself) – better than null for display purposes
			return key;
		}
		return null;
	}
	
	/** Return the Language enum for the user's current language setting. */
	private Language currentLanguage() {
		return Language.fromIsoCode(configService.language);
	}
}