package modforge.backend.service;

import modforge.Singleton;
import modforge.Util;
import modforge.backend.ModData;
import modforge.backend.model.Language;
import modforge.backend.model.ModItem;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

public final class LocalService {
	private static final Logger log = Logger.getLogger(LocalService.class.getName());
	private static final String EXTRA = "_xml.pak";
	/**
	 * Thread-local XMLInputFactory — one pre-configured instance per thread,
	 * avoids repeated factory construction and carries the entity-size fix.
	 */
	private static final ThreadLocal<XMLInputFactory> XML_FACTORY = ThreadLocal.withInitial(() -> {
		XMLInputFactory f = XMLInputFactory.newInstance();
		f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		f.setProperty(XMLInputFactory.IS_VALIDATING, false);
		f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
		// JAXP00010003 — individual entity size
		f.setProperty("jdk.xml.maxGeneralEntitySizeLimit", 0);
		// JAXP00010004 — accumulated entity size across the whole document
		f.setProperty("jdk.xml.totalEntitySizeLimit", 0);
		return f;
	});
	private final UserService configService;
	
	// ==================================================================
	// PUBLIC API
	// ==================================================================
	
	public LocalService(UserService configService) {
		this.configService = configService;
		init();
	}
	
	/**
	 * (Re-)load from disk. Call after the user sets a new game directory.
	 */
	public void init() {
		final long start = System.currentTimeMillis();
		final String gameDir = configService.gameDirectory;
		if (gameDir == null || gameDir.isBlank())
			return;
		final var game = Singleton.INSTANCE.game();
		try {
			game.setLocal(this.loadLocalization(gameDir));
		} catch (Exception ex) {
			log.severe("Localisation read failed: " + ex.getMessage());
		}
		System.out.printf("Game Localization Load took: %dms%n", System.currentTimeMillis() - start);
	}
	
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
	
	// ------------------------------------------------------------------
	// Convenience overloads for base-game items (no per-mod map)
	// ------------------------------------------------------------------
	
	/**
	 * Resolve the lore description of {@code item} with mod-then-base fallback.
	 */
	public String getLoreDescription(ModItem item, ModData mod) {
		return resolve(item, mod, "ui_lore_desc");
	}
	
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
	
	/**
	 * Look up a raw localization key in the mod's strings, then the base game.
	 * Returns {@code null} if not found in either.
	 */
	public String resolve(String key, final ModData mod, final Language lang) {
		if (key == null || (key = key.trim()).isEmpty())
			return null;
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
	
	public String resolve(String key, ModData mod) {
		return resolve(key, mod, configService.language);
	}
	
	/**
	 * Look up a raw localization key in the base-game strings only.
	 */
	public String resolve(String key) {
		return resolve(key, Singleton.INSTANCE.game());
	}
	
	/**
	 * Read all localisation paks from the game directory.
	 * Returns: Language enum -> (string-key -> localized-value)
	 */
	public Map<Language, Map<String, String>> loadLocalization(String root) {
		// Collect all valid (language, pakPath) pairs upfront
		record LangPak(Language language, String pakPath) {
			static LangPak c(File l) {
				final var base = l.getName().replace(EXTRA, "");
				final var lang = Language.fromName(base);
				return lang != null ? new LangPak(lang, l.getPath()) : null;
			}
		}
		
		final var langPaks = Util.allLocPaths(root).stream().map(File::new).filter(File::exists).map(LangPak::c).filter(Objects::nonNull).toList();
		if (langPaks.isEmpty()) {
			log.fine("No Localization folder found for folder " + root);
			return new EnumMap<>(Language.class);
		}
		
		// Process all PAKs in parallel; each returns a (Language -> Map) contribution
		final Map<Language, Map<String, String>> result = new ConcurrentHashMap<>();
		
		langPaks.parallelStream().forEach(lp -> {
			try (final var zf = new ZipFile(lp.pakPath())) {
				// here using parallel is useless
				//  for now I removed filters, so all text data will be red, but if it's too slow for you just add a filter to the base text data for items
				zf.stream().forEach(entry -> {
					try (var is = zf.getInputStream(entry)) {
						final var parsed = parseLocalizationXml(is);
						result.computeIfAbsent(lp.language(), k -> new ConcurrentHashMap<>()).putAll(parsed);
					} catch (Exception ex) {
						log.warning("Localisation parse error (" + entry.getName() + "): " + ex.getMessage());
					}
				});
				
			} catch (Exception ex) {
				log.warning("Localisation read error (" + lp.pakPath() + "): " + ex.getMessage());
			}
		});
		
		log.info(String.format("Loaded %d languages from %d PAK file(s)", result.size(), langPaks.size()));
		// Wrap back into a plain EnumMap for the rest of the codebase
		final EnumMap<Language, Map<String, String>> out = new EnumMap<>(Language.class);
		result.forEach((lang, map) -> out.put(lang, new HashMap<>(map)));
		return out;
	}
	
	/**
	 * Parse a single localisation XML stream.
	 */
	public Map<String, String> parseLocalizationXml(InputStream is) throws Exception {
		// Pre-sized to avoid rehashing for typical file sizes
		var result = new LinkedHashMap<String, String>(1024);
		
		final var factory = XML_FACTORY.get();
		
		final var reader = factory.createXMLStreamReader(is);
		
		String key = null;
		byte cellIndex = 0;
		boolean inRow = false;
		
		while (reader.hasNext()) {
			int event = reader.next();
			
			if (event == XMLStreamConstants.END_ELEMENT && "Row".equals(reader.getLocalName()))
				inRow = false;
			if (event != XMLStreamConstants.START_ELEMENT)
				continue;
			
			switch (reader.getLocalName()) {
				case "Row":
					inRow = true;
					cellIndex = 0;
					key = null;
					break;
				case "Cell":
					if (! inRow)
						break;
					if (cellIndex++ == 1)
						break;  // skip the middle cell (index 1)
					final var text = reader.getElementText().strip();
					if (text.isEmpty())
						break;
					if (cellIndex == 1) {
						key = text;
					} else if (cellIndex == 3 && key != null) {
						result.put(key, text);
					}
					break;
			}
		}
		reader.close();
		
		return result;
	}
	
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
			
			final Path locPath = Util.locExport(gameDirectory, language, modId);
			
			try {
				final var xmlString = makeLocalizationXml(translations);
				Util.writeXml(xmlString, locPath);
				log.info("Localization written: " + locPath + " (" + translations.size() + " entries)");
			} catch (final Exception ex) {
				log.warning("Failed to write localization for " + language + ": " + ex.getMessage());
				allOk = false;
			}
		}
		return allOk;
	}
	
	public String makeLocalizationXml(Map<String, String> entries) {
		var sb = new StringBuilder();
		sb.append("<Table>\n");
		
		for (var entry : entries.entrySet()) {
			final var key = entry.getKey();
			if (! key.isBlank())
				sb.append("<Row>\n").append("<Cell>").append(Util.escapeXml(key)).append("</Cell>\n").append("<Cell/>\n").append("<Cell>").append(Util.escapeXml(entry.getValue())).append("</Cell>\n").append("</Row>\n");
		}
		
		sb.append("</Table>\n");
		return sb.toString();
	}
	
	/**
	 * Resolve one of several candidate attribute names on {@code item} to a
	 * localized string. Resolution order for each candidate key found:
	 *   1. mod's own localizations for the current language
	 *   2. base-game localizations for the current language
	 *   3. the raw attribute value (key itself) as a last resort
	 * <p/>
	 * Returns {@code null} if no candidate attribute is present on the item.
	 */
	private String resolve(ModItem item, ModData mod, String... candidates) {
		// Pull the two lang maps once – either may be null if never populated.
		final var game = Singleton.INSTANCE.game();
		final Map<String, String> modMap = (mod != game) ? mod.getLocal().get(configService.language) : new HashMap<>();
		final Map<String, String> baseMap = game.getLocal().get(configService.language);
		
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
}