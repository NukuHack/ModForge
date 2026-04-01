package modforge.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import modforge.backend.DataPointFactory;
import modforge.backend.ModCollection;
import modforge.backend.ModDescription;
import modforge.backend.model.IModItem;
import modforge.backend.model.IModItemAdapter;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static modforge.backend.Util.normalizeXml;

public final class ModService {
	private static final Logger log = Logger.getLogger(ModService.class.getName());

	private final UserConfigurationService configService;
	private final LocalizationService localizationService;
	private final IModItemAdapter adapter;
	private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

	private ModDescription currentMod = new ModDescription();

	public ModCollection modCollection = new ModCollection();
	public ModCollection externalModCollection = new ModCollection();

	public ModService(IModItemAdapter adapter,
					  UserConfigurationService configService,
					  LocalizationService localizationService) {
		this.adapter = adapter;
		this.configService = configService;
		this.localizationService = localizationService;
		initiateModCollections();
	}

	public ModDescription getCurrentMod() {
		return currentMod;
	}

	public void setCurrentMod(ModDescription m) {
		if (m != null) currentMod = m;
	}

	// ------------------------------------------------------------------
	// Collection management
	// ------------------------------------------------------------------

	public void initiateModCollections() {
		final String gameDir = configService.getCurrent().gameDirectory;
		if (gameDir == null || gameDir.isBlank()) {
			log.warning("Game directory not configured - skipping mod collection scan.");
			return;
		}

		final Path modsFolder = Path.of(gameDir, "Mods");
		try {
			Files.createDirectories(modsFolder);
		} catch (IOException e) {
			log.severe("Cannot create Mods folder: " + e.getMessage());
			return;
		}

		modCollection.clear();
		externalModCollection.clear();

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
		Optional<Path> manifest;
		try (var fs = Files.list(modPath)) {
			manifest = fs.filter(p -> p.getFileName().toString().contains("manifest"))
					.findFirst();
		}
		if (manifest.isEmpty()) return;

		var docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		var xmlDoc = docBuilder.parse(manifest.get().toFile());

		var modDesc = parseModDescription(xmlDoc);
		loadModItemsForMod(modDesc);

		if (isCreatedByUser(xmlDoc)) {
			if (modCollection.getMod(modDesc.id) == null) modCollection.addMod(modDesc);
		} else {
			if (externalModCollection.getMod(modDesc.id) == null)
				externalModCollection.addMod(modDesc);
		}
	}

	private boolean isCreatedByUser(org.w3c.dom.Document doc) {
		final String author = textOf(doc, "author");
		return !author.isBlank() && author.equals(configService.getCurrent().userName);
	}

	// ------------------------------------------------------------------
	// CRUD
	// ------------------------------------------------------------------

	public ModDescription createNewMod(String name, String description, String author,
									   String version, String createdOn, String modId,
									   boolean modifiesLevel, List<String> supportedVersions) {
		if (name == null || name.isBlank() ||
				modId == null || modId.isBlank() ||
				version == null || version.isBlank()) {
			log.warning("createNewMod: required fields missing.");
			return new ModDescription();
		}
		if (modCollection.getMod(modId) != null) {
			log.warning("createNewMod: mod ID '" + modId + "' already exists.");
			return new ModDescription();
		}
		final var m = new ModDescription();
		m.id = modId;
		m.name = name;
		m.description = description;
		m.author = author;
		m.modVersion = version;
		m.createdOn = createdOn;
		m.modifiesLevel = modifiesLevel;
		m.supportsGameVersions = new ArrayList<>(supportedVersions);
		modCollection.addMod(m);
		log.info("Mod '" + name + "' [" + modId + "] created.");
		return m;
	}

	public boolean addModItem(IModItem item) {
		if (item == null || currentMod == null) return false;
		currentMod.modItems.removeIf(x -> item.getId() != null && item.getId().equals(x.getId()));
		currentMod.modItems.add(item);
		return true;
	}

	public boolean tryGetModFromCollection(String modId) {
		var m = modCollection.getMod(modId);
		if (m == null) m = externalModCollection.getMod(modId);
		if (m == null) return false;
		this.currentMod = m;
		return true;
	}

	// ------------------------------------------------------------------
	// Export
	// ------------------------------------------------------------------

	public void exportMod(ModDescription mod) {
		String gameDir = configService.getCurrent().gameDirectory;
		writeModManifest(mod);
		localizationService.writeLocalizationAsXml(gameDir, mod);
		adapter.writeModItems(mod.id, mod.modItems);
		createModPak(
				PathFactory.modData(gameDir, mod.id),
				PathFactory.modData(gameDir, mod.id) + "/" + mod.id + ".pak"
		);
	}

	// ------------------------------------------------------------------
	// Manifest write  (mirrors C# WriteModManifest)
	// ------------------------------------------------------------------

	public boolean writeModManifest(final ModDescription mod) {
		final String gameDir = configService.getCurrent().gameDirectory;
		final String rootPath = PathFactory.modFolder(gameDir, mod.id);
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
				final String normalizedNew = normalizeXml(newContent);
				final String normalizedExisting = normalizeXml(existingContent);

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

	// ------------------------------------------------------------------
	// PAK creation
	// ------------------------------------------------------------------

	private void createModPak(String baseFolder, String pakFilePath) {
		try {
			final Path path = Path.of(pakFilePath);
			Files.deleteIfExists(path);
			Files.createDirectories(path.getParent());

			try (final var fos = new FileOutputStream(pakFilePath);
				final var zout = new ZipOutputStream(fos)) {

				Files.walk(Path.of(baseFolder))
						.filter(Files::isRegularFile)
						.forEach(file -> {
							try {
								if (file.toAbsolutePath()
										.equals(Path.of(pakFilePath).toAbsolutePath()))
									return;
								String rel = Path.of(baseFolder)
										.relativize(file).toString().replace('\\', '/');
								zout.putNextEntry(new ZipEntry(rel));
								Files.copy(file, zout);
								zout.closeEntry();
							} catch (IOException e) {
								log.warning("Cannot add to pak: " + file
										+ " - " + e.getMessage());
							}
						});
			}
			log.info("PAK created: " + pakFilePath);
		} catch (Exception e) {
			log.severe("createModPak failed: " + e.getMessage());
		}
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	private ModDescription parseModDescription(org.w3c.dom.Document doc) {
		var m = new ModDescription();
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

	private void loadModItemsForMod(ModDescription modDesc) {
		String gameDir = configService.getCurrent().gameDirectory;
		String pakFile = PathFactory.modData(gameDir, modDesc.id)
				+ "/" + modDesc.id + ".pak";

		if (!new File(pakFile).exists()) {
			log.fine("No pak found for mod " + modDesc.id);
			return;
		}

		ItemType.endpoints().forEach((type, eps) -> {
			String key = eps.keySet().iterator().next();
			int idx = key.indexOf('_');
			String epKey = idx >= 0 ? key.substring(0, idx) : key;
			var dp = DataPointFactory.create(pakFile, epKey, type);
			modDesc.modItems.addAll(adapter.readModItems(dp));
		});
	}

	private static String textOf(org.w3c.dom.Document doc, String tag) {
		var nl = doc.getElementsByTagName(tag);
		return nl.getLength() > 0 ? nl.item(0).getTextContent().trim() : "";
	}

	private static void appendText(org.w3c.dom.Document doc,
								   Element parent, String tag, String text) {
		var el = doc.createElement(tag);
		el.setTextContent(text == null ? "" : text);
		parent.appendChild(el);
	}
}
