package modforge.backend.service;

import modforge.Util;
import modforge.backend.ModData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class ModService {
	private static final Logger log = Logger.getLogger(ModService.class.getName());
	
	public final UserService userConfig;
	public final ConfigService configService;
	public final LocalService localService;
	//public final ModItemBuilder builder;
	public final ItemService itemService;
	public final IconService iconService;
	
	public List<ModData> modCollection = new ArrayList<>();
	
	public ModService(ServiceRegistry registry) {
		this.itemService = registry.itemService;
		this.iconService = registry.iconService;
		this.userConfig = registry.userConfig;
		this.localService = registry.localService;
		//this.builder = registry.builder;
		this.configService = registry.configService;
		init();
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
		
		NodeList versions = doc.getElementsByTagName("kcd_version");
		for (int i = 0; i < versions.getLength(); i++)
			m.supportsGameVersions.add(versions.item(i).getTextContent().trim());
		
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
		final String manifest = rootPath + "/mod.manifest";
		try {
			Files.createDirectories(Path.of(rootPath + "/Data"));
			Files.createDirectories(Path.of(rootPath + "/Localization"));
			
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
			for (final var v : mod.supportsGameVersions)
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
			final Path manifestPath = Path.of(manifest);
			if (Files.exists(manifestPath)) {
				final String existingContent = Files.readString(manifestPath, StandardCharsets.UTF_8);
				
				// Normalize both strings for comparison (remove whitespace differences)
				final var normalizedNew = Util.normalizeXml(newContent);
				final var normalizedExisting = Util.normalizeXml(existingContent);
				
				if (normalizedNew.equals(normalizedExisting)) {
					log.info("Manifest already exists with identical content: " + manifest);
					return true;
				} else {
					log.info("Manifest exists but content differs, overwriting: " + manifest);
				}
			}
			
			// Write the new manifest content
			try (var fileWriter = new java.io.FileWriter(manifestPath.toFile(), StandardCharsets.UTF_8)) {
				fileWriter.write(newContent);
			}
			
			log.info("Manifest written: " + manifest);
			return true;
		} catch (Exception e) {
			log.severe("writeModManifest failed: " + e.getMessage());
			return false;
		}
	}
	
	public void init() {
		final String gameDir = userConfig.gameDirectory;
		if (gameDir == null || gameDir.isBlank()) {
			log.warning("Game directory not configured - skipping mod collection scan.");
			return;
		}
		
		final Path modsFolder = Util.modFolder(gameDir);
		try {
			Files.createDirectories(modsFolder);
		} catch (IOException e) {
			log.severe("Cannot create Mods folder: " + e.getMessage());
			return;
		}
		
		modCollection.clear();
		
		try (var stream = Files.list(modsFolder)) {
			stream.filter(Files::isDirectory).forEach(modPath -> {
				try {
					fillCollection(modPath);
				} catch (Exception ex) {
					log.warning("Cannot read mod at " + modPath + ": " + ex.getMessage());
				}
			});
		} catch (IOException e) {
			log.warning("Cannot list Mods folder: " + e.getMessage());
		}
	}
	
	private void fillCollection(Path modPath) throws Exception {
		final Path manifest;
		try (var fs = Files.list(modPath)) {
			manifest = fs.filter(p -> p.getFileName().toString().contains("manifest")).findFirst().orElse(null);
		}
		if (manifest == null)
			return;
		
		final var docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		final var xmlDoc = docBuilder.parse(manifest.toFile());
		
		final var mod = parseModDescription(xmlDoc);
		
		final String gameDir = userConfig.gameDirectory;
		final var items = itemService.loadModItems(Path.of(Util.modData(gameDir, mod.id)));
		mod.setItems(items);
		log.info(String.format("XML read done items=%d", items.size()));
		loadModLocalizationsForMod(mod);
		loadModConfigForMod(mod);
		
		if (! modCollection.contains(mod))
			modCollection.add(mod);
	}
	
	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------
	
	public ModData createNewMod(String name, String description, String author, String version, String createdOn, String modId, boolean modifiesLevel, List<String> supportedVersions) {
		if (name.isBlank() || modId.isBlank()) {
			log.warning("createNewMod: required fields missing.");
			return new ModData();
		}
		if (modCollection.stream().anyMatch(mod -> mod.id.equals(modId))) {
			log.warning("createNewMod: mod ID '" + modId + "' already exists.");
			return new ModData();
		}
		final var m = new ModData();
		m.id = modId;
		m.name = name;
		m.description = description;
		m.author = author;
		m.modVersion = version;
		m.createdOn = createdOn;
		m.modifiesLevel = modifiesLevel;
		m.supportsGameVersions = new ArrayList<>(supportedVersions);
		modCollection.add(m);
		log.info("Mod '" + name + "' [" + modId + "] created.");
		return m;
	}
	
	/**
	 * Enhanced export method that packs both Data and Localization.
	 * This matches the extraction logic (one language folder -> one PAK).
	 */
	public void exportMod(ModData mod) {
		final String gameDir = userConfig.gameDirectory;
		
		// Write items to XML files; returns the set of PAK stems that were written
		itemService.writeModItems(mod);
		// Write localization XML files
		localService.writeModLocalization(mod);
		// Create one Data PAK per origin PAK stem
		createModPaks(gameDir, mod);
		// Create Localization PAKs (one per language)
		packLocalization(gameDir, mod);
		
		log.info("Mod export completed: " + mod.id);
	}
	
	/**
	 * Pack each staging folder into its own PAK file and then delete the staging dir.
	 *
	 * Layout written by ItemService.writeModItems:
	 *   Mods/<modId>/Data/_stage/<pakStem>/<inner-dir-structure>/
	 *
	 * Each <pakStem> becomes:
	 *   Mods/<modId>/Data/<pakStem>.pak
	 *
	 * The _stage folder is removed on success.
	 */
	private void createModPaks(String gameDir, ModData mod) {
		if (mod.getItems().isEmpty()) {
			log.info("No items for mod " + mod.id + " – skipping PAK creation.");
			return;
		}
		
		final Path stageRoot = Path.of(gameDir, "Mods", mod.id, "Data", "_stage");
		if (! Files.exists(stageRoot)) {
			log.warning("Staging folder not found for mod " + mod.id + " – skipping PAK creation.");
			return;
		}
		final var pakList = stageRoot.toFile().listFiles();
		if (pakList == null || pakList.length == 0) {
			log.warning("No data has been written " + mod.id + " – skipping PAK creation.");
			return;
		}
		
		boolean allOk = true;
		for (File stageDir : pakList) {
			if (! Files.exists(stageDir.toPath())) {
				log.fine("Staging dir missing for PAK '" + stageDir + "' – skipping.");
				continue;
			}
			
			Path destPak = Path.of(gameDir, "Mods", mod.id, "Data", stageDir.getName() + ".pak");
			boolean ok = Util.packFolderExcludingSelf(stageDir.toPath(), destPak);
			if (ok) {
				log.info("PAK created: " + destPak.getFileName());
			} else {
				log.warning("PAK creation failed for stem '" + stageDir + "'.");
				allOk = false;
			}
		}
		
		// Clean up the staging tree regardless of individual failures
		Util.deleteRecursively(stageRoot);
		
		if (allOk) {
			log.info("All PAKs created for mod " + mod.id + " (" + pakList.length + " PAK(s)).");
		}
	}
	
	/**
	 * Pack localization folders into PAK files.
	 * Each language folder inside Mods/<modId>/Localization/ becomes its own PAK file
	 * in the game's root Localization directory (overwriting existing ones).
	 *
	 * @param gameDir The game directory path
	 * @param mod     The mod data containing localizations
	 */
	private void packLocalization(String gameDir, ModData mod) {
		if (mod.getLocal().isEmpty()) {
			log.fine("No localizations to pack for mod " + mod.id);
			return;
		}
		
		AtomicBoolean allSuccess = new AtomicBoolean(true);
		final Path modLocalizationRoot = Path.of(gameDir, "Mods", mod.id, "Localization");
		
		if (! Files.exists(modLocalizationRoot)) {
			log.warning("Localization folder not found for mod " + mod.id);
			return;
		}
		
		try (var stream = Files.list(modLocalizationRoot)) {
			stream.filter(Files::isDirectory).forEach(langFolder -> {
				final String folderName = langFolder.getFileName().toString();
				// Expected format: "German_xml", "English_xml", etc.
				if (! folderName.endsWith("_xml")) {
					log.fine("Skipping non-localization folder: " + folderName);
					return;
				}
				
				// The PAK should be named like "German_xml.pak" and placed in the game root
				final Path destPak = Path.of(langFolder + ".pak");
				boolean success = Util.packFolder(langFolder, destPak);
				
				if (success) {
					log.info("Localization packed: " + folderName + ".pak");
					Util.deleteRecursively(langFolder);
				} else {
					log.warning("Failed to pack localization: " + folderName);
					allSuccess.set(false);
				}
			});
		} catch (IOException e) {
			log.severe("Failed to list localization folders: " + e.getMessage());
			return;
		}
		
		allSuccess.get();
	}
	
	/**
	 * Load localization data for a mod from its Localization folder structure.
	 * The mod's localization files are stored in:
	 * Mods/<modId>/Localization/<Lang>_xml/text__<modId>.xml
	 *
	 * @param mod The mod data to populate with localization entries
	 */
	public void loadModLocalizationsForMod(ModData mod) {
		final String gameDir = userConfig.gameDirectory;
		final Path modLang = Path.of(gameDir, "Mods", mod.id);
		
		if (! Files.exists(modLang)) {
			log.fine("No Localization folder found for mod " + mod.id);
			return;
		}
		mod.setLocal(localService.readLocalizationFromXml(String.valueOf(modLang), true));
	}
	
	/**
	 * Load config for a mod (mod.cfg) and store in ModData.
	 */
	public void loadModConfigForMod(ModData mod) {
		if (mod == null || mod.id == null || mod.id.isBlank())
			return;
		mod.setConfig(configService.loadModConfig(mod.id));
	}
}