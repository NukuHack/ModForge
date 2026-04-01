package modforge.backend.service;

import modforge.backend.model.Language;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

public final class LocalizationAdapter {
	private static final Logger log = Logger.getLogger(LocalizationAdapter.class.getName());
	private static final String EXTRA = "_xml.pak";

	private static final Set<String> RELEVANT_FILES = Set.of(
			"text_ui_soul.xml", "text_ui_tutorials.xml",
			"text_ui_quest.xml", "text_ui_misc.xml",
			"text_ui_minigames.xml", "text_ui_menus.xml",
			"text_ui_items.xml", "text_ui_ingame.xml"
	);

	/**
	 * Read all localisation paks from the game directory.
	 * Returns: Language enum -> (string-key -> localised-value)
	 */
	public Map<Language, Map<String, String>> readLocalizationFromXml(String gameDirectory) {
		final var result = new EnumMap<Language, Map<String, String>>(Language.class);

		for (final String pakPath : PathFactory.allLocPaths(gameDirectory)) {
			File f = new File(pakPath);
			if (!f.exists()) continue;

			// e.g. "German_xml.pak" -> strip "_xml.pak" -> "German"
			final String baseName = f.getName().replace(EXTRA, "");

			// Use the enum to find the language
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


	public String deparseLocalizationXml(Map<String, String> entries) throws Exception {
		var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		var table = doc.createElement("Table");
		doc.appendChild(table);

		for (var entry : entries.entrySet()) {
			var row = doc.createElement("Row");

			// Key cell
			var keyCell = doc.createElement("Cell");
			keyCell.setTextContent(entry.getKey());
			row.appendChild(keyCell);

			// Empty cell (middle)
			var emptyCell = doc.createElement("Cell");
			row.appendChild(emptyCell);

			// Value cell
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

	public boolean writeLocalizationAsXml(String gameDirectory, Map<Language, Map<String, String>> langs) {
		AtomicBoolean allSuccessful = new AtomicBoolean(true);

		for (var entry : langs.entrySet()) {
			Language language = entry.getKey();
			Map<String, String> translations = entry.getValue();

			if (translations.isEmpty()) {
				continue;
			}

			// Group translations by their original file
			// Since we don't know which file each translation came from, we'll need to
			// read the original files to maintain the same file structure
			Map<String, Map<String, String>> fileGroups = new HashMap<>();

			// First, read existing files to understand the structure
			String pakPath = PathFactory.allLocPaths(gameDirectory)
					.stream()
					.filter(path -> path.endsWith(language.getDisplayName() + EXTRA))
					.findFirst()
					.orElse(null);

			if (pakPath != null) {
				File f = new File(pakPath);
				if (f.exists()) {
					try (var zf = new ZipFile(f)) {
						var it = zf.entries();
						while (it.hasMoreElements()) {
							var zipEntry = it.nextElement();
							String fileName = new File(zipEntry.getName()).getName().toLowerCase(Locale.ROOT);
							if (RELEVANT_FILES.contains(fileName)) {
								try (var is = zf.getInputStream(zipEntry)) {
									var parsed = parseLocalizationXml(is);
									fileGroups.put(fileName, parsed);
								}
							}
						}
					} catch (Exception ex) {
						log.warning("Failed to read existing localization file structure: " + ex.getMessage());
						allSuccessful.set(false);
						continue;
					}
				}
			}

			// If we couldn't read the original structure, we'll create a single file
			if (fileGroups.isEmpty()) {
				fileGroups.put("text_ui_items.xml", new HashMap<>());
			}

			// Update the translations in their respective groups
			// This assumes that the keys are unique across files (which they should be)
			for (var translationEntry : translations.entrySet()) {
				String key = translationEntry.getKey();
				String value = translationEntry.getValue();

				boolean found = false;
				for (var fileGroup : fileGroups.entrySet()) {
					if (fileGroup.getValue().containsKey(key)) {
						fileGroup.getValue().put(key, value);
						found = true;
						break;
					}
				}

				// If key wasn't found in any existing file, add to text_ui_items.xml
				if (!found) {
					fileGroups.get("text_ui_items.xml").put(key, value);
				}
			}

			// Create a temporary directory to build the new PAK file
			Path tempDir = null;
			try {
				tempDir = Files.createTempDirectory("localization_" + language.name());

				// Write each file
				for (var fileGroup : fileGroups.entrySet()) {
					String fileName = fileGroup.getKey();
					Map<String, String> fileEntries = fileGroup.getValue();

					if (!fileEntries.isEmpty()) {
						String xmlContent = deparseLocalizationXml(fileEntries);
						Path filePath = tempDir.resolve(fileName);
						Files.writeString(filePath, xmlContent, StandardCharsets.UTF_8);
					}
				}

				// Create the PAK file
				String pakFileName = language.getDisplayName() + EXTRA;
				Path pakFilePath = Path.of(gameDirectory, pakFileName);

				// Create the PAK file (ZIP archive)
				try (var fos = Files.newOutputStream(pakFilePath);
					 var zos = new java.util.zip.ZipOutputStream(fos)) {
					final Path relPath = tempDir;
					Files.walk(tempDir)
							.filter(Files::isRegularFile)
							.forEach(file -> {
								try {
									final String entryName = relPath.relativize(file).toString();
									var zipEntry = new java.util.zip.ZipEntry(entryName);
									zos.putNextEntry(zipEntry);
									Files.copy(file, zos);
									zos.closeEntry();
								} catch (Exception e) {
									log.warning("Failed to add file to PAK: " + e.getMessage());
									allSuccessful.set(false);
								}
							});
				}

			} catch (Exception ex) {
				log.warning("Failed to write localization for " + language + ": " + ex.getMessage());
				allSuccessful.set(false);
			} finally {
				if (tempDir != null) {
					try {
						Files.walk(tempDir)
								.sorted(Comparator.reverseOrder())
								.map(Path::toFile)
								.forEach(File::delete);
					} catch (Exception e) {
						log.warning("Failed to clean up temp directory: " + e.getMessage());
					}
				}
			}
		}

		return allSuccessful.get();
	}

	/**
	 * Write mod-specific localization files into the mod's Localization directory.
	 * Output path per language: <gameDir>/Mods/<modId>/Localization/<Lang>_xml/text__<modId>.xml
	 * This is the format KCD expects for mod localization (NOT a pak — plain XML files).
	 *
	 * @param gameDirectory Game root directory
	 * @param modId         The mod identifier
	 * @param localizations Language -> (key -> value) map (only the mod's own entries)
	 * @return true if all languages were written without error
	 */
	public boolean writeModLocalization(String gameDirectory, String modId, Map<Language, Map<String, String>> localizations) {
		if (localizations == null || localizations.isEmpty()) return true;

		boolean allOk = true;
		for (var entry : localizations.entrySet()) {
			Language language = entry.getKey();
			Map<String, String> translations = entry.getValue();
			if (translations.isEmpty()) continue;

			// KCD mod localization path: Localization/<Lang>_xml/text__<modId>.xml
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
}