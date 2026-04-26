package com.nukuhack.modforge.backend.service;

import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.backend.ModData;
import com.nukuhack.util.IOUtil;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@NonNull
public record ModService(UserConfig userConfig, ConfigService configService, LocalService localService, ItemService itemService, IconService iconService) {
	public static final List<ModData> modCollection = new ArrayList<>();

	public ModService(@NonNull ServiceRegistry r) {
		this(r.userConfig, r.configService, r.localService, r.itemService, r.iconService);
	}

	public static ModData parseModDescription(Document doc) {
		final var m = new ModData(
				textOf(doc, "modid"),
				textOf(doc, "name"),
				textOf(doc, "description"),
				textOf(doc, "author"),
				textOf(doc, "version"),
				textOf(doc, "created_on"),
				"true".equalsIgnoreCase(textOf(doc, "modifies_level"))
		);

		var list = new LinkedList<String>();
		var versions = doc.getElementsByTagName("kcd_version");
		for (int i = 0; i < versions.getLength(); i++)
			list.add(versions.item(i).getTextContent().trim());
		m.setSupportsGameVersions(list);

		return m;
	}

	public static String textOf(Document doc, String tag) {
		var nl = doc.getElementsByTagName(tag);
		return nl.getLength() > 0 ? nl.item(0).getTextContent().trim() : "";
	}

	private static void appendText(Document doc, Element parent, String tag, String text) {
		var el = doc.createElement(tag);
		el.setTextContent(text == null ? "" : text);
		parent.appendChild(el);
	}

	/**
	 * @param gameDir The game directory path
	 * @param mod     The mod description
	 * @return boolean - succeed
	 */
	public static boolean writeModAsXml(String gameDir, ModData mod) {
		var rootPath = Util.modFolder(gameDir, mod.getId());
		var manifest = Path.of(rootPath, "mod.manifest");
		try {
			Files.createDirectories(Util.dataDir(rootPath));

			var docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			var doc = docBuilder.newDocument();

			var root = doc.createElement("kcd_mod");
			root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xsd", "http://www.w3.org/2001/XMLSchema");
			root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
			doc.appendChild(root);

			var info = doc.createElement("info");
			appendText(doc, info, "name", mod.getName());
			appendText(doc, info, "description", mod.getDescription());
			appendText(doc, info, "author", mod.getAuthor());
			appendText(doc, info, "version", mod.getModVersion());
			appendText(doc, info, "created_on", mod.getCreatedOn());
			appendText(doc, info, "modid", mod.getId());
			appendText(doc, info, "modifies_level", String.valueOf(mod.isModifiesLevel()).toLowerCase(Locale.ROOT));
			root.appendChild(info);

			var supports = doc.createElement("supports");
			for (var v : mod.getSupportsGameVersions())
				appendText(doc, supports, "kcd_version", v);
			root.appendChild(supports);

			var tf = TransformerFactory.newInstance().newTransformer();
			tf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			tf.setOutputProperty(OutputKeys.INDENT, "yes");

			var newXmlWriter = new StringWriter();
			tf.transform(new DOMSource(doc), new StreamResult(newXmlWriter));
			var newContent = newXmlWriter.toString();

			if (Files.exists(manifest)) {
				var existingContent = Files.readString(manifest, StandardCharsets.UTF_8);

				var normalizedNew = newContent.replaceAll(">\\s+<", ">\n<");
				var normalizedExisting = existingContent.replaceAll(">\\s+<", ">\n<");

				if (normalizedNew.equals(normalizedExisting)) {
					log.info("Manifest already exists with identical content: {}", manifest);
					return true;
				} else {
					log.info("Manifest exists but content differs, overwriting: {}", manifest);
				}
			}

			try (var fileWriter = new FileWriter(manifest.toFile(), StandardCharsets.UTF_8)) {
				fileWriter.write(newContent);
			}

			log.info("Manifest written: {}", manifest);
			return true;
		} catch (Exception e) {
			log.error("writeModManifest failed", Util.limitStackTrace(e, 10));
			return false;
		}
	}

	public static ModData loadMod(Path modPath) {
		var mod = loadModManifest(modPath);

		if (mod == null)
			return null;
		mod.setConfig(ConfigService.loadModConfig(mod.getId(), modPath));

		var dataPath = Util.dataDir(modPath);
		mod.setLocal(LocalService.loadLocalization(String.valueOf(modPath)));
		mod.setItems(ItemService.loadItems(dataPath));
		mod.setIcon(IconService.loadModIcons(dataPath));

		return mod;
	}

	/**
	 * Enhanced export method that packs both Data and Localization.
	 * This matches the extraction logic (one language folder -> one PAK).
	 */
	public static void exportMod(ModData mod, String gameDir) {

		/**
		 * Write items to XML files; returns the set of PAK stems that were written
		 */
		ItemService.writeModItems(mod, gameDir);
		/**
		 * Write icons to dds files; if possible from the un-decoded data, so no quality loss
		 */
		IconService.writeModIcons(mod, gameDir);
		/**
		 * Write localization XML files
		 */
		LocalService.writeModLocalization(mod, gameDir);
		/**
		 * Create one Data PAK per origin PAK stem
		 * this also paks the Icons
		 */
		createModPaks(gameDir, mod);
		/**
		 * Create Localization PAKs (one per language)
		 */
		packLocalization(gameDir, mod);

		log.info("Mod export completed: {}", mod.getId());
	}

	/**
	 * Pack each staging folder into its own PAK file and then delete the staging dir.
	 * <p/>
	 * Layout written by ItemService.writeModItems:
	 * Mods/<modId>/Data/_stage/<pakStem>/<inner-dir-structure>/
	 * <p/>
	 * Each <pakStem> becomes:
	 * Mods/<modId>/Data/<pakStem>.pak
	 * <p/>
	 * The _stage folder is removed on success.
	 */
	private static void createModPaks(String gameDir, ModData mod) {
		var id = mod.getId();
		if (mod.getItems().isEmpty()) {
			log.info("No items for mod {} – skipping PAK creation.", id);
			return;
		}

		var stageRoot = Util.modStaging(gameDir, id);
		if (!Files.exists(stageRoot)) {
			log.warn("Staging folder not found for mod {} – skipping PAK creation.", id);
			return;
		}
		var pakList = stageRoot.toFile().listFiles();
		if (pakList == null || pakList.length == 0) {
			log.warn("No data has been written {} – skipping PAK creation.", id);
			return;
		}

		var allOk = true;
		for (var stageDir : pakList) {
			if (!Files.exists(stageDir.toPath())) {
				log.info("Staging dir missing for PAK '{}' – skipping.", stageDir);
				continue;
			}
			var dataDir = Util.modData(gameDir, id);
			var destPak = Path.of(dataDir, stageDir.getName() + ".pak");
			var ok = IOUtil.packFolder(stageDir.toPath(), destPak, null, true);
			if (ok) {
				log.trace("PAK created: {}", destPak.getFileName());
			} else {
				log.warn("PAK creation failed for stem '{}'.", stageDir);
				allOk = false;
			}
		}

		IOUtil.deleteRecursively(stageRoot);

		if (allOk)
			log.trace("All PAKs created for mod {} ({} PAK(s)).", id, pakList.length);
	}

	/**
	 * Pack localization folders into PAK files.
	 * Each language folder inside Mods/<modId>/Localization/ becomes its own PAK file
	 * in the game's root Localization directory (overwriting existing ones).
	 *
	 * @param gameDir The game directory path
	 * @param mod     The mod data containing localizations
	 */
	private static void packLocalization(String gameDir, ModData mod) {
		if (mod.getLocalizations().isEmpty()) {
			log.info("No localizations to pack for mod {}", mod.getId());
			return;
		}

		var success = new AtomicBoolean(true);
		var modRoot = Util.modFolder(gameDir, mod.getId());
		var langPaks = Util.allLocPaths(modRoot).stream().map(File::new).filter(File::exists).filter(File::isDirectory).toList();
		if (langPaks.isEmpty()) {
			log.info("No Localization folder found for folder {}", modRoot);
			return;
		}

		langPaks.forEach(file -> {
			var langFolder = file.toPath();

			var destPak = Path.of(langFolder + Util.COMP_FORMAT);
			var ok = IOUtil.packFolder(langFolder, destPak, null, true);

			if (ok) {
				IOUtil.deleteRecursively(langFolder);
			} else {
				log.warn("Failed to pack localization: {}", file);
				success.set(false);
			}
		});

		success.get();
	}

	public static ModData createFromPath(Path modPath) {
		var dir = modPath.getFileName();
		var m = new ModData();
		if (dir == null) {
			m.setId("mod_" + Util.randomString(8));
			return m;
		}
		var name = dir.toString();
		m.setId(name);
		m.setName(name);
		return m;
	}

	public static ModData loadModManifest(Path modPath) {
		var manifest = modPath.resolve("mod.manifest");

		if (!Files.exists(manifest)) {
			log.warn("'mod.manifest' not found, fallback to directory name");
			return createFromPath(modPath);
		}

		try {
			var docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			var xmlDoc = docBuilder.parse(manifest.toFile());

			var mod = parseModDescription(xmlDoc);
			if (mod.getId().isBlank())
				return null;
			log.info("Successfully loaded mod manifest for: {}", mod.getId());
			return mod;
		} catch (IOException | SAXException | ParserConfigurationException e) {
			log.warn("could not parse 'mod.manifest', fallback to directory name");
			return createFromPath(modPath);
		}
	}

	public void init() {
		var gameDir = userConfig.getGameDir();
		if (gameDir.isBlank()) {
			log.warn("Game directory not configured - skipping mod collection scan.");
			return;
		}

		var modsFolder = Util.modFolder(gameDir);
		try {
			Files.createDirectories(modsFolder);
		} catch (IOException e) {
			log.error("Cannot create Mods folder: {}", e.getMessage());
			return;
		}
		var start = System.currentTimeMillis();
		modCollection.clear();
		try (var stream = Files.list(modsFolder)) {
			stream.filter(Files::isDirectory).forEach(modPath -> {
				var mod = loadMod(modPath);
				if (mod == null)
					log.warn("Cannot read mod at {}", modPath);

				if (!modCollection.contains(mod))
					modCollection.add(mod);
			});
		} catch (IOException e) {
			log.warn("Cannot list Mods folder: {}", e.getMessage());
		}
		log.info("Mod loading took: {}ms", System.currentTimeMillis() - start);
	}
}