package com.nukuhack.modforge.backend.service;

import lombok.NonNull;
import com.nukuhack.modforge.Singleton;
import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.backend.ModData;
import com.nukuhack.modforge.backend.model.E.Language;
import com.nukuhack.modforge.backend.model.ModItem;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipFile;

@lombok.extern.slf4j.Slf4j
public final class LocalService {
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
		// these for speed
		f.setProperty(XMLInputFactory.IS_COALESCING, true);
		if (false /*get class for "jackson-dataformat-xml"*/)
			f.setProperty("com.ctc.wstx.maxElementDepth", 5); // should be fine on 3 but left it
		else {
			// JAXP00010003 — individual entity size
			f.setProperty("jdk.xml.maxGeneralEntitySizeLimit", 0);
			// JAXP00010004 — accumulated entity size across the whole document
			f.setProperty("jdk.xml.totalEntitySizeLimit", 0);
		}
		return f;
	});
	private final UserConfig userConfig;
	
	// ==================================================================
	// PUBLIC API
	// ==================================================================
	
	public LocalService(UserConfig userConfig) {
		this.userConfig = userConfig;
	}
	
	/**
	 * Read all localisation paks from the game directory.
	 * Returns: Language enum -> (string-key -> localized-value)
	 */
	public static Map<Language, Map<String, String>> loadLocalization(String root) {
		// Collect all valid (language, pakPath) pairs upfront
		final var langPaks = Util.allLocPaths(root).stream().map(LangPak::c).filter(Objects::nonNull).filter(l -> Files.exists(Path.of(l.pakPath()))).toList();
		if (langPaks.isEmpty()) {
			log.info("No Localization folder found for folder {}", root);
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
					} catch (final Exception ex) {
						log.warn("Localisation parse error ({}): {}", entry.getName(), ex.getMessage());
					}
				});
				
			} catch (final Exception ex) {
				log.warn("Localisation read error ({}): {}", lp.pakPath(), ex.getMessage());
			}
		});
		
		log.info("Loaded {} languages from {} PAK file(s)", result.size(), langPaks.size());
		// Wrap back into a plain EnumMap for the rest of the codebase
		final EnumMap<Language, Map<String, String>> out = new EnumMap<>(Language.class);
		result.forEach((lang, map) -> out.put(lang, new HashMap<>(map)));
		return out;
	}
	
	/**
	 * Parse a single localisation XML stream.
	 */
	public static Map<String, String> parseLocalizationXml(InputStream is) throws XMLStreamException {
		// Pre-sized to avoid rehashing for typical file sizes
		var result = new LinkedHashMap<String, String>(1024);
		
		final var factory = XML_FACTORY.get();
		
		String key = null;
		byte cellIndex = 0;
		boolean inRow = false;
		final var reader = factory.createXMLStreamReader(is);
		try {
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
		} finally {
			reader.close();
		}
		
		return result;
	}
	
	/**
	 * Write per-language localization files into the mod's Localization folder.
	 * Each language gets: Localization/<Lang>_xml/text__<modId>.xml
	 */
	public static void writeModLocalization(ModData mod, String gameDir) {
		boolean ok = writeModLocalizationFiles(gameDir, mod.id, mod.getLocal());
		if (! ok) {
			log.warn("Localization write had errors for mod: {}", mod.id);
		}
	}
	
	// ------------------------------------------------------------------
	// Convenience overloads for base-game items (no per-mod map)
	// ------------------------------------------------------------------
	
	/**
	 * Write mod-specific localization files into the mod's Localization directory.
	 */
	private static boolean writeModLocalizationFiles(String gameDirectory, String modId, Map<Language, Map<String, String>> localizations) {
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
				Util.writeXml(makeLocalizationXml(translations), locPath);
				log.info("Localization written: {} ({} entries)", locPath, translations.size());
			} catch (final Exception ex) {
				log.warn("Failed to write localization for {}: {}", language, ex.getMessage());
				allOk = false;
			}
		}
		return allOk;
	}
	
	public static String makeLocalizationXml(Map<String, String> entries) {
		var sb = new StringBuilder("<Table>\n");
		for (var entry : entries.entrySet()) {
			final var key = entry.getKey();
			if (key.isBlank())
				continue;
			
			sb.append("\t<Row>\n");
			sb.append("\t\t<Cell>").append(Util.escapeXml(key)).append("</Cell>\n");
			sb.append("\t\t<Cell/>\n");
			sb.append("\t\t<Cell>").append(Util.escapeXml(entry.getValue())).append("</Cell>\n");
			sb.append("\t</Row>\n");
		}
		
		return sb.append("</Table>\n").toString();
	}
	
	/**
	 * (Re-)load from disk. Call after the user sets a new game directory.
	 */
	public void init() {
		final long start = System.currentTimeMillis();
		final String gameDir = userConfig.getGameDirectory();
		if (gameDir == null || gameDir.isBlank())
			return;
		final var game = Singleton.INSTANCE.getGame();
		try {
			game.setLocal(loadLocalization(gameDir));
		} catch (Exception ex) {
			log.error("Localisation read failed: {}", ex.getMessage());
		}
		log.info("Game Localization Load took: {}", System.currentTimeMillis() - start);
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
	
	/**
	 * Resolve the lore description of {@code item} with mod-then-base fallback.
	 */
	public String getLoreDescription(ModItem item, ModData mod) {
		return resolve(item, mod, "ui_lore_desc");
	}
	
	/** Resolve the display name of a base-game item. */
	public String getName(ModItem item) {
		return getName(item, Singleton.INSTANCE.getGame());
	}
	
	/** Resolve the description of a base-game item. */
	public String getDescription(ModItem item) {
		return getDescription(item, Singleton.INSTANCE.getGame());
	}
	
	/** Resolve the lore description of a base-game item. */
	public String getLoreDescription(ModItem item) {
		return getLoreDescription(item, Singleton.INSTANCE.getGame());
	}
	
	/**
	 * Look up a raw localization key in the mod's strings, then the base game.
	 * Returns {@code null} if not found in either.
	 */
	public String resolve(String key, final ModData mod, final Language lang) {
		if (key == null || (key = key.trim()).isEmpty())
			return null;
		final var game = Singleton.INSTANCE.getGame();
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
		return resolve(key, mod, userConfig.getLanguage());
	}
	
	/**
	 * Look up a raw localization key in the base-game strings only.
	 */
	public String resolve(String key) {
		return resolve(key, Singleton.INSTANCE.getGame());
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
		final var game = Singleton.INSTANCE.getGame();
		final Map<String, String> modMap = (mod != game) ? mod.getLocal().get(userConfig.getLanguage()) : new HashMap<>();
		final Map<String, String> baseMap = game.getLocal().get(userConfig.getLanguage());
		
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
	
	record LangPak(@NonNull Language language, @NonNull String pakPath) {
		static LangPak c(String l) {
			final var base = new File(l).getName();
			final var lang = Language.fromName(base.replace(Util.LOCALIZATION_EXTRA, ""));
			return lang != null ? new LangPak(lang, l + Util.COMP_FORMAT) : null;
		}
	}
}