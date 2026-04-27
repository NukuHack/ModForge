package com.nukuhack.modforge.backend.service;

import com.nukuhack.modforge.Main;
import com.nukuhack.modforge.Singleton;
import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.backend.ModData;
import com.nukuhack.modforge.backend.model.E;
import com.nukuhack.modforge.backend.model.E.Language;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static com.nukuhack.modforge.backend.model.Attribute.INDENT;

@Slf4j
@NonNull
public record LocalService(UserConfig userConfig) {
	
	/**
	 * Read all localisation paks from the game directory.
	 * Returns: Language enum -> (string-key -> localized-value)
	 */
	public static Map<Language, Map<String, String>> loadLocalization(String root) {
		
		var langPaks = Util.allLocPaths(root).stream().map(LangPak::c).filter(Objects::nonNull).filter(l -> Files.exists(Path.of(l.pakPath()))).toList();
		if (langPaks.isEmpty()) {
			log.info("No Localization folder found for folder {}", root);
			return new EnumMap<>(Language.class);
		}
		
		var result = new ConcurrentHashMap<Language, Map<String, String>>();
		
		langPaks.parallelStream().forEach(lp -> {
			try (var zf = new ZipFile(lp.pakPath())) {
				
				zf.stream().forEach(entry -> {
					try (var is = zf.getInputStream(entry)) {
						var parsed = parseLocalizationXml(is);
						result.computeIfAbsent(lp.language(), k -> new ConcurrentHashMap<>()).putAll(parsed);
					} catch (Exception ex) {
						log.warn("Localisation parse error ({}): {}", entry.getName(), ex.getMessage());
					}
				});
				
			} catch (Exception ex) {
				log.warn("Localisation read error ({}): {}", lp.pakPath(), ex.getMessage());
			}
		});
		
		log.info("Loaded {} languages from {} PAK file(s)", result.size(), langPaks.size());
		
		var out = new EnumMap<Language, Map<String, String>>(Language.class);
		result.forEach((lang, map) -> out.put(lang, new HashMap<>(map)));
		return out;
	}
	
	/**
	 * Parse a single localisation XML stream.
	 */
	public static Map<String, String> parseLocalizationXml(InputStream is) throws XMLStreamException {
		
		var result = new LinkedHashMap<String, String>(1024);
		
		var factory = Singleton.XML_FACTORY.get();
		
		String key = null;
		var cellIndex = 0;
		var inRow = false;
		var reader = factory.createXMLStreamReader(is);
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
							break;
						
						var text = reader.getElementText().strip();
						if (text.isEmpty())
							break;
						
						if (cellIndex == 1)
							key = text;
						
						else if (cellIndex == 3 && key != null)
							result.put(key, text);
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
		boolean ok = writeModLocalizationFiles(gameDir, mod.getId(), mod.getLocalizations());
		if (! ok) {
			log.warn("Localization write had errors for mod: {}", mod.getId());
		}
	}
	
	/**
	 * Write mod-specific localization files into the mod's Localization directory.
	 */
	private static boolean writeModLocalizationFiles(String gameDir, String modId, Map<Language, Map<String, String>> localizations) {
		if (localizations.isEmpty())
			return true;
		
		boolean allOk = true;
		for (var entry : localizations.entrySet()) {
			var language = entry.getKey();
			var translations = entry.getValue();
			if (translations.isEmpty())
				continue;
			
			var locPath = Util.locExport(gameDir, language, modId);
			
			try {
				Util.writeXml(makeLocalizationXml(translations), locPath);
				log.info("Localization written: {} ({} entries)", locPath, translations.size());
			} catch (Exception ex) {
				log.warn("Failed to write localization for {}: {}", language, ex.getMessage());
				allOk = false;
			}
		}
		return allOk;
	}
	
	public static String makeLocalizationXml(Map<String, String> entries) {
		var sb = new StringBuilder("<Table>\n");
		for (var entry : entries.entrySet()) {
			var key = entry.getKey();
			if (key.isBlank())
				continue;
			
			sb.append(INDENT).append("<Row>\n");
			sb.append(INDENT).append(INDENT).append("<Cell>").append(Util.escapeXml(key)).append("</Cell>\n");
			sb.append(INDENT).append(INDENT).append("<Cell>").append(Util.escapeXml(key)).append("</Cell>\n");
			sb.append(INDENT).append(INDENT).append("<Cell>").append(Util.escapeXml(entry.getValue())).append("</Cell>\n");
			sb.append(INDENT).append("</Row>\n");
		}
		
		return sb.append("</Table>\n").toString();
	}
	
	/**
	 * (Re-)load from disk. Call after the user sets a new game directory.
	 */
	public void init() {
		var start = System.currentTimeMillis();
		var gameDir = userConfig.getGameDir();
		if (gameDir.isBlank())
			return;
		var game = Singleton.getGame();
		try {
			game.setLocal(loadLocalization(gameDir));
		} catch (Exception ex) {
			log.error("Localisation read failed: {}", ex.getMessage());
		}
		log.info("Game Localization Load took: {}", System.currentTimeMillis() - start);
	}

	/**
	 * Bulk version of {@link LocalService#resolve(ModData, Language, String)}
	 */
	private List<String> resolve(ModData mod, Language lang, List<String> candidates) {
		return candidates.stream().map(c -> resolve(mod, lang, c)).toList();
	}
	/**
	 * Look up a raw localization key in the mod's strings, then the base game.
	 * Returns {@code null} if not found in either.
	 */
	public String resolve(ModData mod, Language lang, String key) {
		if (key == null || (key = key.trim()).isEmpty())
			return null;
		var game = Singleton.getGame();
		
		if (mod != game) {
			var modMap = mod.getLang(lang);
			if (modMap.containsKey(key))
				return modMap.get(key);
		}
		
		var baseMap = game.getLang(lang);
		if (baseMap.containsKey(key))
			return baseMap.get(key);
		
		return null;
	}
	
	public String resolve(ModData mod, String key) {
		return resolve(mod, userConfig.getLanguage(), key);
	}
	
	/**
	 * Look up a raw localization key in the base-game strings only.
	 */
	public String resolve(String key) {
		return resolve(Singleton.getGame(), key);
	}
	
	record LangPak(@NonNull Language language, @NonNull String pakPath) {
		static LangPak c(String l) {
			var base = new File(l).getName();
			var lang = Language.fromName(base.replace(Util.LOCALIZATION_EXTRA, ""));
			return lang != null ? new LangPak(lang, l + Util.COMP_FORMAT) : null;
		}
	}
	
	public static void loadUILocalizations() {
		var zipPath = "local.zip";
		var zipStream = Main.class.getClassLoader().getResourceAsStream(zipPath);
		
		if (zipStream == null) {
			log.warn("ZIP file not found: {}", zipPath);
			return;
		}
		
		var langMap = new EnumMap<E.Language, Map<String, String>>(E.Language.class);
		try (var zis = new ZipInputStream(zipStream)) {
			ZipEntry entry;
			
			while ((entry = zis.getNextEntry()) != null) {
				if (entry.isDirectory()) {
					continue;
				}
				
				var entryName = entry.getName();
				var name = entryName.substring(entryName.lastIndexOf("/") + 1);
				var langCode = name.substring(0, name.length() - 4).toUpperCase();
				var map = langMap.computeIfAbsent(E.Language.fromName(langCode), l -> new LinkedHashMap<>());
				
				try (var nonClosingStream = new NonClosingInputStream(zis)) {
					map.putAll(LocalService.parseLocalizationXml(nonClosingStream));
				}
				
				zis.closeEntry();
			}
		} catch (IOException e) {
			log.error("Failed to read ZIP from resources", Util.limitStackTrace(e, 10));
		} catch (XMLStreamException e) {
			throw new RuntimeException(e);
		}
		Arrays.stream(Language.values()).forEach(l -> langMap.putIfAbsent(l, new HashMap<>()));
		Singleton.getLangMap().putAll(langMap);
	}
}

/**
 * Wraps an InputStream but ignores close() calls.
 * This allows the XML parser to "close" its stream without closing the underlying ZIP stream.
 */
class NonClosingInputStream extends FilterInputStream {
	protected NonClosingInputStream(InputStream in) {
		super(in);
	}
	
	@Override
	public void close() {
	}
}