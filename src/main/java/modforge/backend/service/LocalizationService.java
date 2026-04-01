package modforge.backend.service;

import modforge.Singleton;
import modforge.backend.ModData;
import modforge.backend.model.IModItem;
import modforge.backend.model.Language;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

public final class LocalizationService {
	private static final Logger log = Logger.getLogger(LocalizationService.class.getName());
	private static final String EXTRA = "_xml.pak";
	private static final Set<String> RELEVANT_FILES = Set.of(
			"text_ui_soul.xml", "text_ui_tutorials.xml",
			"text_ui_quest.xml", "text_ui_misc.xml",
			"text_ui_minigames.xml", "text_ui_menus.xml",
			"text_ui_items.xml", "text_ui_ingame.xml"
	);

	private final UserService configService;
	private Map<Language, Map<String, String>> cachedLocalizations = new EnumMap<>(Language.class);

	public LocalizationService(UserService configService) {
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
		final String dir = configService.getCurrent().gameDirectory;
		if (dir != null && !dir.isBlank()) {
			try {
				cachedLocalizations = readLocalizationFromXml(dir);
			} catch (Exception ex) {
				log.severe("Localisation read failed: " + ex.getMessage());
				cachedLocalizations = new EnumMap<>(Language.class);
			}
		}
	}

	public String getName(IModItem item) {
		return resolve(item, "ui_name", "UIName", "name");
	}

	public String getDescription(IModItem item) {
		return resolve(item, "ui_desc", "UIInfo");
	}

	public String getLoreDescription(IModItem item) {
		return resolve(item, "ui_lore_desc");
	}

	/**
	 * Write per-language localization files into the mod's Localization folder.
	 * Each language gets: Localization/<Lang>_xml/text__<modId>.xml
	 */
	public void writeModLocalization(ModData mod) {
		final var gameDir = configService.getCurrent().gameDirectory;
		boolean ok = writeModLocalizationFiles(gameDir, mod.id, mod.localizations);
		if (ok) {
			log.info("Localization written for mod: " + mod.id);
		} else {
			log.warning("Localization write had errors for mod: " + mod.id);
		}
	}

	// ==================================================================
	// READ OPERATIONS
	// ==================================================================

	/**
	 * Read all localisation paks from the game directory.
	 * Returns: Language enum -> (string-key -> localised-value)
	 */
	public Map<Language, Map<String, String>> readLocalizationFromXml(String root) {
		final var result = new EnumMap<Language, Map<String, String>>(Language.class);

		for (final String pakPath : PathFactory.allLocPaths(root)) {
			File f = new File(pakPath);
			if (!f.exists()) continue;

			final String baseName = f.getName().replace(EXTRA, "");
			final Language language = Language.fromDisplayName(baseName);
			if (language == null) continue;

			try (final var zf = new ZipFile(f)) {
				final var it = zf.entries();
				while (it.hasMoreElements()) {
					var entry = it.nextElement();
					String name = new File(entry.getName()).getName().toLowerCase(Locale.ROOT);
					if (!RELEVANT_FILES.contains(name)) continue;

					try (var is = zf.getInputStream(entry)) {
						var parsed = parseLocalizationXml(is);
						result.computeIfAbsent(language, k -> new HashMap<>()).putAll(parsed);
					}
				}
			} catch (Exception ex) {
				log.warning("Localisation read error (" + pakPath + "): " + ex.getMessage());
			}
		}
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
			if (cells.getLength() < 3) continue;
			String key = cells.item(0).getTextContent().trim();
			String value = cells.item(2).getTextContent().trim();
			if (!key.isBlank()) result.put(key, value);
		}
		return result;
	}

	// ==================================================================
	// WRITE OPERATIONS
	// ==================================================================

	/**
	 * Write mod-specific localization files into the mod's Localization directory.
	 */
	private boolean writeModLocalizationFiles(String gameDirectory, String modId, Map<Language, Map<String, String>> localizations) {
		if (localizations == null || localizations.isEmpty()) return true;

		boolean allOk = true;
		for (var entry : localizations.entrySet()) {
			Language language = entry.getKey();
			Map<String, String> translations = entry.getValue();
			if (translations.isEmpty()) continue;

			String locFilePath = PathFactory.locExport(gameDirectory, language.getDisplayName(), modId);
			Path locPath = Path.of(locFilePath);

			try {
				Files.createDirectories(locPath.getParent());
				String xmlContent = deparseLocalizationXml(translations);
				Files.writeString(locPath, xmlContent, StandardCharsets.UTF_8);
				log.info("Localization written: " + locFilePath + " (" + translations.size() + " entries)");
			} catch (Exception ex) {
				log.warning("Failed to write localization for " + language + ": " + ex.getMessage());
				allOk = false;
			}
		}
		return allOk;
	}

	public String deparseLocalizationXml(Map<String, String> entries) throws Exception {
		var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		var table = doc.createElement("Table");
		doc.appendChild(table);

		for (var entry : entries.entrySet()) {
			var row = doc.createElement("Row");

			var keyCell = doc.createElement("Cell");
			keyCell.setTextContent(entry.getKey());
			row.appendChild(keyCell);

			var emptyCell = doc.createElement("Cell");
			row.appendChild(emptyCell);

			var valueCell = doc.createElement("Cell");
			valueCell.setTextContent(entry.getValue());
			row.appendChild(valueCell);

			table.appendChild(row);
		}

		var transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

		var writer = new java.io.StringWriter();
		transformer.transform(new DOMSource(doc), new StreamResult(writer));

		return writer.toString();
	}

	// ==================================================================
	// PRIVATE HELPERS
	// ==================================================================

	private String resolve(IModItem item, String... candidates) {
		final Language lang = Language.fromIsoCode(configService.getCurrent().language);
		final var langMap = cachedLocalizations.get(lang);

		for (String candidate : candidates) {
			String clo = candidate.toLowerCase(Locale.ROOT);
			var attr = item.getAttributes().stream()
					.filter(a -> a.getName().toLowerCase(Locale.ROOT).contains(clo))
					.findFirst().orElse(null);
			if (attr == null) continue;

			String key = String.valueOf(attr.getValue());
			if (langMap.containsKey(key)) return langMap.get(key);
			return key;
		}
		return null;
	}
}