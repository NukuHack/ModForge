package modforge.backend.service;

import modforge.backend.ModDescription;
import modforge.backend.model.Language;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

public final class LocalizationAdapter {
	private static final Logger log = Logger.getLogger(LocalizationAdapter.class.getName());

	private static final Set<String> RELEVANT_FILES = Set.of(
			"text_ui_soul.xml", "text_ui_tutorials.xml",
			"text_ui_quest.xml", "text_ui_misc.xml",
			"text_ui_minigames.xml", "text_ui_menus.xml",
			"text_ui_items.xml", "text_ui_ingame.xml"
	);

	/**
	 * Read all localisation paks from the game directory.
	 * Returns: language-code -> (string-key -> localised-value)
	 */
	public Map<String, Map<String, String>> readLocalizationFromXml(String gameDirectory) {
		var result = new HashMap<String, Map<String, String>>();

		for (String pakPath : PathFactory.allLocPaths(gameDirectory)) {
			File f = new File(pakPath);
			if (!f.exists()) continue;

			// e.g. "German_xml.pak" -> strip "_xml.pak" -> "German"
			final String baseName = f.getName().replace("_xml.pak", "");

			// Use the enum to find the language
			final String langCode = Arrays.stream(Language.values())
					.filter(lang -> baseName.startsWith(lang.getDisplayName()))
					.map(Language::getIsoCode)
					.findFirst()
					.orElse(null);
			if (langCode == null) continue;

			try (final var zf = new ZipFile(f)) {
				final var it = zf.entries();
				while (it.hasMoreElements()) {
					var entry = it.nextElement();
					String name = new File(entry.getName()).getName()
							.toLowerCase(Locale.ROOT);
					if (!RELEVANT_FILES.contains(name)) continue;

					try (var is = zf.getInputStream(entry)) {
						var parsed = parseLocalizationXml(is);
						result.computeIfAbsent(langCode, k -> new HashMap<>())
								.putAll(parsed);
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
	 * Expected format:
	 * <pre>
	 *   &lt;Table&gt;
	 *     &lt;Row&gt;
	 *       &lt;Cell&gt;KEY&lt;/Cell&gt;
	 *       &lt;Cell/&gt;
	 *       &lt;Cell&gt;VALUE&lt;/Cell&gt;
	 *     &lt;/Row&gt;
	 *   &lt;/Table&gt;
	 * </pre>
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

	/**
	 * Write localisation for a mod (stub – extend when export is needed).
	 */
	public void writeLocalizationAsXml(String gameDirectory, ModDescription mod) {
		log.info("writeLocalizationAsXml: not yet implemented.");
	}
}
