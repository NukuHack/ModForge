package com.nukuhack.modforge.backend.service;

import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.backend.ModData;
import com.nukuhack.util.IOUtil;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public final class ModService {
	public static final List<ModData> modCollection = new ArrayList<>();
	public final UserConfig userConfig;
	public final ConfigService configService;
	public final LocalService localService;
	public final ItemService itemService;
	public final IconService iconService;
	
	public ModService(ServiceRegistry registry) {
		this.itemService = registry.itemService;
		this.iconService = registry.iconService;
		this.userConfig = registry.userConfig;
		this.localService = registry.localService;
		this.configService = registry.configService;
	}
	
	// ------------------------------------------------------------------
	// Collection management
	// ------------------------------------------------------------------
	
	public static ModData parseModDescription(Document doc) {
		final var m = new ModData();
		m.name = textOf(doc, "name");
		m.description = textOf(doc, "description");
		m.author = textOf(doc, "author");
		m.modVersion = textOf(doc, "version");
		m.createdOn = textOf(doc, "created_on");
		m.id = textOf(doc, "modid");
		m.modifiesLevel = "true".equalsIgnoreCase(textOf(doc, "modifies_level"));
		
		List<String> list = new LinkedList<>();
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
	
	// ------------------------------------------------------------------
	// CRUD
	// ------------------------------------------------------------------
	
	private static void appendText(Document doc, Element parent, String tag, String text) {
		var el = doc.createElement(tag);
		el.setTextContent(text == null ? "" : text);
		parent.appendChild(el);
	}
	
	// ------------------------------------------------------------------
	// Export
	// ------------------------------------------------------------------
	
	/**
	 * @param gameDirectory The game directory path
	 * @param mod           The mod description
	 * @return boolean - succeed
	 */
	public static boolean writeModAsXml(String gameDirectory, ModData mod) {
		final String rootPath = Util.modFolder(gameDirectory, mod.id);
		final Path manifest = Path.of(rootPath, "mod.manifest");
		try {
			Files.createDirectories(Util.dataDir(rootPath));
			
			// Create the new manifest content first
			var docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			var doc = docBuilder.newDocument();
			
			final Element root = doc.createElement("kcd_mod");
			root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xsd", "http://www.w3.org/2001/XMLSchema");
			root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
			doc.appendChild(root);
			
			final Element info = doc.createElement("info");
			appendText(doc, info, "name", mod.name);
			appendText(doc, info, "description", mod.description);
			appendText(doc, info, "author", mod.author);
			appendText(doc, info, "version", mod.modVersion);
			appendText(doc, info, "created_on", mod.createdOn);
			appendText(doc, info, "modid", mod.id);
			appendText(doc, info, "modifies_level", String.valueOf(mod.modifiesLevel).toLowerCase(Locale.ROOT));
			root.appendChild(info);
			
			final Element supports = doc.createElement("supports");
			for (final var v : mod.getSupportsGameVersions())
				appendText(doc, supports, "kcd_version", v);
			root.appendChild(supports);
			
			// Convert the new document to a string for comparison
			final var tf = TransformerFactory.newInstance().newTransformer();
			tf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			tf.setOutputProperty(OutputKeys.INDENT, "yes");
			
			var newXmlWriter = new java.io.StringWriter();
			tf.transform(new DOMSource(doc), new StreamResult(newXmlWriter));
			final String newContent = newXmlWriter.toString();
			
			// Check if the manifest already exists and has the same content
			if (Files.exists(manifest)) {
				final String existingContent = Files.readString(manifest, StandardCharsets.UTF_8);
				
				// Normalize both strings for comparison (remove whitespace differences)
				final var normalizedNew = newContent.replaceAll(">\\s+<", ">\n<");
				final var normalizedExisting = existingContent.replaceAll(">\\s+<", ">\n<");
				
				if (normalizedNew.equals(normalizedExisting)) {
					log.info("Manifest already exists with identical content: {}", manifest);
					return true;
				} else {
					log.info("Manifest exists but content differs, overwriting: {}", manifest);
				}
			}
			
			// Write the new manifest content
			try (var fileWriter = new java.io.FileWriter(manifest.toFile(), StandardCharsets.UTF_8)) {
				fileWriter.write(newContent);
			}
			
			log.info("Manifest written: {}", manifest);
			return true;
		} catch (Exception e) {
			log.error("writeModManifest failed: {}", e.getMessage());
			return false;
		}
	}
	
	public static ModData loadMod(Path modPath) {
		final var mod = loadModManifest(modPath);
		
		if (mod == null)
			return null;
		mod.setConfig(ConfigService.loadModConfig(mod.id, modPath));
		
		final Path dataPath = Util.dataDir(modPath);
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
		
		log.info("Mod export completed: {}", mod.id);
	}
	
	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------
	
	/**
	 * Pack each staging folder into its own PAK file and then delete the staging dir.
	 * <p/>
	 * Layout written by ItemService.writeModItems:
	 *   Mods/<modId>/Data/_stage/<pakStem>/<inner-dir-structure>/
	 * <p/>
	 * Each <pakStem> becomes:
	 *   Mods/<modId>/Data/<pakStem>.pak
	 * <p/>
	 * The _stage folder is removed on success.
	 */
	private static void createModPaks(String gameDir, ModData mod) {
		if (mod.getItems().isEmpty()) {
			log.info("No items for mod {} – skipping PAK creation.", mod.id);
			return;
		}
		
		final var stageRoot = Util.modStaging(gameDir, mod.id);
		if (! Files.exists(stageRoot)) {
			log.warn("Staging folder not found for mod {} – skipping PAK creation.", mod.id);
			return;
		}
		final var pakList = stageRoot.toFile().listFiles();
		if (pakList == null || pakList.length == 0) {
			log.warn("No data has been written {} – skipping PAK creation.", mod.id);
			return;
		}
		
		boolean allOk = true;
		for (var stageDir : pakList) {
			if (! Files.exists(stageDir.toPath())) {
				log.info("Staging dir missing for PAK '{}' – skipping.", stageDir);
				continue;
			}
			final var dataDir = Util.modData(gameDir, mod.id);
			final var destPak = Path.of(dataDir, stageDir.getName() + ".pak");
			final var ok = IOUtil.packFolder(stageDir.toPath(), destPak, null, true);
			if (ok) {
				log.trace("PAK created: {}", destPak.getFileName());
			} else {
				log.warn("PAK creation failed for stem '{}'.", stageDir);
				allOk = false;
			}
		}
		
		// Clean up the staging tree regardless of individual failures
		IOUtil.deleteRecursively(stageRoot);
		
		if (allOk)
			log.trace("All PAKs created for mod {} ({} PAK(s)).", mod.id, pakList.length);
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
			log.info("No localizations to pack for mod {}", mod.id);
			return;
		}
		
		final var success = new AtomicBoolean(true);
		final var modRoot = Util.modFolder(gameDir, mod.id);
		final var langPaks = Util.allLocPaths(modRoot).stream().map(File::new).filter(File::exists).filter(File::isDirectory).toList();
		if (langPaks.isEmpty()) {
			log.info("No Localization folder found for folder {}", modRoot);
			return;
		}
		
		langPaks.forEach(file -> {
			var langFolder = file.toPath();
			// The PAK should be named like "German_xml.pak" and placed in the game root
			final var destPak = Path.of(langFolder + Util.COMP_FORMAT);
			boolean ok = IOUtil.packFolder(langFolder, destPak, null, true);
			
			if (ok) {
				IOUtil.deleteRecursively(langFolder);
			} else {
				log.warn("Failed to pack localization: {}", file);
				success.set(false);
			}
		});
		
		success.get();
	}
	
	public static ModData loadModManifest(Path modPath) {
		final var manifest = modPath.resolve("mod.manifest");
		
		if (! Files.exists(manifest)) {
			log.warn("manifest not found");
			return null;
		}
		
		try {
			final var docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			final var xmlDoc = docBuilder.parse(manifest.toFile());
			
			var mod = parseModDescription(xmlDoc);
			if (mod.id.isBlank())
				return null;
			log.info("Successfully loaded mod data for: {}", mod.id);
			return mod;
		} catch (IOException | SAXException | ParserConfigurationException e) {
			log.warn("could not parse manifest");
			return null;
		}
	}
	
	public void init() {
		final String gameDir = userConfig.getGameDirectory();
		if (gameDir == null || gameDir.isBlank()) {
			log.warn("Game directory not configured - skipping mod collection scan.");
			return;
		}
		
		final Path modsFolder = Util.modFolder(gameDir);
		try {
			Files.createDirectories(modsFolder);
		} catch (IOException e) {
			log.error("Cannot create Mods folder: {}", e.getMessage());
			return;
		}
		final var start = System.currentTimeMillis();
		modCollection.clear();
		try (var stream = Files.list(modsFolder)) {
			stream.filter(Files::isDirectory).forEach(modPath -> {
				var mod = loadMod(modPath);
				if (mod == null)
					log.warn("Cannot read mod at {}", modPath);
				
				if (! modCollection.contains(mod))
					modCollection.add(mod);
			});
		} catch (IOException e) {
			log.warn("Cannot list Mods folder: {}", e.getMessage());
		}
		log.info("Mod loading took: {}ms", System.currentTimeMillis() - start);
	}
	
	public ModData createNewMod(String name, String description, String author, String version, String createdOn, String modId, boolean modifiesLevel) {
		if (name.isBlank() || modId.isBlank()) {
			log.warn("createNewMod: required fields missing.");
			return new ModData();
		}
		if (modCollection.stream().anyMatch(mod -> mod.id.equals(modId))) {
			log.warn("createNewMod: mod ID '{}' already exists.", modId);
			return new ModData();
		}
		final var m = new ModData(modId, name, description, author, version, createdOn, modifiesLevel);
		modCollection.add(m);
		log.info("Mod '{}' [{}] created.", name, modId);
		return m;
	}
}