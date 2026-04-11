package com.nukuhack.modforge.backend.service;

import com.nukuhack.modforge.Singleton;
import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.backend.ItemEntry;
import com.nukuhack.modforge.backend.ItemType;
import com.nukuhack.modforge.backend.ModData;
import com.nukuhack.modforge.backend.model.ModItem;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

@Slf4j
public final class ItemService {
	
	final static DocumentBuilder docBuilder;
	private static final ThreadLocal<XMLInputFactory> XML_FACTORY = ThreadLocal.withInitial(() -> {
		XMLInputFactory f = XMLInputFactory.newInstance();
		f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		f.setProperty(XMLInputFactory.IS_VALIDATING, false);
		f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
		// these for speed
		f.setProperty(XMLInputFactory.IS_COALESCING, true);
		// JAXP00010003 — individual entity size
		f.setProperty("jdk.xml.maxGeneralEntitySizeLimit", 0);
		// JAXP00010004 — accumulated entity size across the whole document
		f.setProperty("jdk.xml.totalEntitySizeLimit", 0);
		return f;
	});
	// TODO : make this kind of data load or ... idk
	private static final Set<String> IGNORED_FILES = Set.of("scripts.pak", "animations.pak", "heads.pak", "sounds.pak", "shaders.pak");
	
	static {
		try {
			var f = DocumentBuilderFactory.newInstance();
			f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			f.setFeature("http://xml.org/sax/features/validation", false);
			f.setFeature("http://xml.org/sax/features/external-general-entities", false);
			f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			docBuilder = f.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			throw new RuntimeException(e);
		}
	}
	
	private final UserConfig userConfig;
	
	public ItemService(UserConfig userConfig) {
		this.userConfig = userConfig;
	}
	
	static Document parseXml(InputStream is) {
		try {
			// Wrap in BufferedInputStream if not already mark-supported
			if (! is.markSupported())
				is = new BufferedInputStream(is);
			
			is.mark(3);
			byte[] bom = new byte[3];
			int bytesRead = is.read(bom);
			
			// Reset to beginning regardless
			is.reset();
			
			final String encoding;
			
			// Check for BOM only if we read enough bytes
			if (bytesRead >= 3 && bom[0] == (byte) 0xEF && bom[1] == (byte) 0xBB && bom[2] == (byte) 0xBF) {
				// BOM found, skip it by reading past it
				if (is.skip(3) != 3)
					throw new IOException("Could not read the BOM correctly");
				encoding = "UTF-8";
			} else if (bytesRead >= 2 && bom[0] == (byte) 0xFE && bom[1] == (byte) 0xFF) {
				// UTF-16BE BOM
				if (is.skip(2) != 2)
					throw new IOException("Could not read the BOM correctly");
				encoding = "UTF-16BE";
			} else if (bytesRead >= 2 && bom[0] == (byte) 0xFF && bom[1] == (byte) 0xFE) {
				// UTF-16LE BOM
				if (is.skip(2) != 2)
					throw new IOException("Could not read the BOM correctly");
				encoding = "UTF-16LE";
			} else {
				encoding = "UTF-8";
			}
			
			// Use the detected encoding
			try (var reader = new BufferedReader(new InputStreamReader(is, encoding))) {
				return docBuilder.parse(new InputSource(reader));
			}
		} catch (SAXException e) {
			log.warn("Could not parse the file", e);
			return null;
		} catch (IOException e) {
			log.warn("Could not access the file", e);
			return null;
		} catch (Exception e) {
			log.warn("Unknown exception at xml parsing", e);
			return null;
		}
	}
	
	/**
	 * ---- Build target paths inside the staging area ----
	 * Structure:  <gameDir>/Mods/<modId>/Data/_stage/<pakStem>/<dirSuffix>/<type>__<mod.id>.xml
	 * <gameDir> = input
	 * <pakStem> = the pak it will be saved in without the extension (.pak)
	 * <dirSuffix> = the save path relative to pak, ie: "inner/dir/entry.xml" -> "inner/dir"
	 * <type> = got from item with {@link ItemEntry}
	 * <mod.id> = the id of the mod input
	 * @param gameDir Game Directory
	 * @param item Item you wanna save
	 * @param mod The mod the item is from
	 * @return the file, according to the Structure
	 */
	private static File getOutputFile(final String gameDir, final ModItem item, final ModData mod) {
		var rawPath = item.getPath() == null ? "" : item.getPath();
		var targetDir = getOutputFile(gameDir, rawPath, mod).getParent();
		var typeName = ModItemBuilder.group(item).fileName;
		var outFile = Util.joinP(targetDir, Util.modXmlFile(typeName, mod.id));
		return outFile.toFile();
	}
	
	/**
	 * ---- Build target paths inside the staging area ----
	 * Structure:  <gameDir>/Mods/<modId>/Data/_stage/<pakStem>/<dirSuffix>/<item_file>
	 * <gameDir> = input
	 * <pakStem> = the pak it will be saved in without the extension (.pak)
	 * <dirSuffix> = the save path relative to pak, ie: "inner/dir/entry.xml" -> "inner/dir"
	 * <item_file> = got from item with just cutting of the file name
	 * <mod.id> = the id of the mod input
	 * @param gameDir Game Directory
	 * @param itemPath Item's path you wanna save
	 * @param mod The mod the item is from
	 * @return the file, according to the Structure
	 */
	public static Path getOutputFile(final String gameDir, final String itemPath, final ModData mod) {
		// ---- Resolve PAK stem & inner directory suffix ----
		final String pakStem;   // e.g. "Weapons"  or  modId
		final String dirSuffix; // e.g. "Libs/Tables" or ""
		String fileName; // e.g. "apple.txt"
		
		int colonIdx = itemPath.indexOf(':');
		if (colonIdx > 0) {
			// Format (a): "SomePak.pak:inner/dir/entry.xml"
			final String pakFileName = itemPath.substring(0, colonIdx);       // "SomePak.pak"
			final String innerEntry = itemPath.substring(colonIdx + 1); // "inner/dir/entry.xml"
			final int dot = pakFileName.lastIndexOf('.');
			pakStem = dot > 0 ? pakFileName.substring(0, dot) : pakFileName;     // "SomePak"
			int lastSlash = innerEntry.lastIndexOf('/');      // "inner/dir"
			dirSuffix = lastSlash >= 0 ? innerEntry.substring(0, lastSlash) : "";
			fileName = innerEntry.substring(++ lastSlash);
		} else {
			// Format (b/c): plain path or blank
			pakStem = mod.id;
			int lastSlash = itemPath.lastIndexOf('/');
			dirSuffix = lastSlash >= 0 ? itemPath.substring(0, lastSlash) : "";
			fileName = itemPath.substring(++ lastSlash);
		}
		if (fileName.isBlank())
			fileName = "apple.txt";
		
		final var stageRoot = Util.joinP(Util.modStaging(gameDir, mod.id), pakStem);
		// Capitalize the L in Libs
		return dirSuffix.isEmpty() ? Util.joinP(stageRoot, fileName) : Util.joinP(stageRoot, Util.capitalStart(dirSuffix), fileName);
	}
	
	private static Document makeDocument(final File outFile, final ModItem item, final ItemEntry groupT) throws Exception {
		final Document document;
		
		var groupName = groupT.parentName;
		if (! outFile.exists()) {
			document = docBuilder.newDocument();
			var temp = document.createElement("database");
			temp.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
			temp.setAttribute("name", "barbora");
			temp.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:noNamespaceSchemaLocation", "../database.xsd");
			document.appendChild(temp);
			var group = document.createElement(groupName);
			group.setAttribute("version", groupT.getVersion());
			temp.appendChild(group);
			
			final var newEl = ModItemBuilder.create(document, item);
			newEl.ifPresent(group::appendChild);
			return document;
		}
		document = docBuilder.parse(outFile);
		var group = (Element) document.getElementsByTagName(groupName).item(0);
		if (group == null) {
			group = document.createElement(groupName);
			group.setAttribute("version", groupT.getVersion());
			document.getDocumentElement().appendChild(group);
		}
		
		var elem = ModItemBuilder.create(document, item);
		if (elem.isEmpty())
			return document;
		var newEl = elem.get();
		var idKey = item.getIdKey();
		var idVal = newEl.getAttribute(idKey);
		
		if (idVal.isBlank()) {
			group.appendChild(newEl);
			return document;
		}
		
		var existing = group.getElementsByTagName(newEl.getTagName());
		var replaced = false;
		for (int i = 0; i < existing.getLength(); i++) {
			var el = (Element) existing.item(i);
			if (idVal.equals(el.getAttribute(idKey))) {
				group.replaceChild(newEl, el);
				replaced = true;
				break;
			}
		}
		if (! replaced)
			group.appendChild(newEl);
		return document;
		
	}
	
	/**
	 * Write a single item to XML and return the PAK stem it belongs to
	 * (e.g. "Weapons", or the modId when there is no origin hint).
	 * <p>
	 * Path stored on items follows one of two formats:
	 * a) "SomePak.pak:inner/dir/entry.xml"  – came from a named PAK
	 * b) plain filesystem path               – came from loose XML scan
	 * c) null / blank                        – newly created item
	 */
	private static void writeModItem(final String gameDir, final ModData mod, final ModItem item) throws Exception {
		final File outFile = getOutputFile(gameDir, item, mod);
		Files.createDirectories(outFile.toPath().getParent());
		
		var group = ModItemBuilder.group(item);
		final Document doc;
		final String doctype;
		
		if (ModItemBuilder.HANDLER_MAP.get(group.parentName) != null) {
			doc = docBuilder.newDocument();
			var el = ModItemBuilder.create(doc, item);
			el.ifPresent(doc::appendChild);
			doctype = Util.STORM_HEADER + "\n";
		} else {
			doc = makeDocument(outFile, item, group);
			doctype = null;
		}
		
		writeXml(doc, outFile, doctype);
	}
	
	private static void writeXml(Document doc, File outFile, String doctype) throws Exception {
		final var tf = TransformerFactory.newInstance().newTransformer();
		tf.setOutputProperty(OutputKeys.INDENT, "yes");
		// We'll write the declaration + DOCTYPE ourselves
		tf.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		
		final var writer = new StringWriter();
		tf.transform(new DOMSource(doc), new StreamResult(writer));
		final String xml = doctype == null ? writer.toString() : doctype + writer;
		
		Util.writeXml(xml, outFile.toPath());
	}
	
	private static Stream<ModItem> extractItemsFromPak(final Path pakFile) {
		final Set<ModItem> items = new HashSet<>();
		try (var zf = new ZipFile(pakFile.toFile())) {
			for (final var entry : zf.stream().filter(ItemType::excludeNonEndpoints).toList()) {
				final var entryName = entry.getName().replace('\\', '/');
				try (var is = zf.getInputStream(entry)) {
					readItemsFromXml(is, pakFile.getFileName() + ":" + entryName, items);
				} catch (final Exception ex) {
					log.warn("Parse error in {} from {}", entryName, pakFile.getFileName(), ex);
				}
			}
		} catch (IOException e) {
			log.error("Cannot open PAK file: {}", pakFile, e);
		}
		return items.stream();
	}
	
	static void readItemsFromXml(final InputStream is, final String sourcePath, final Set<ModItem> sink) {
		final var doc = parseXml(is);
		if (doc == null) {
			log.debug("Parse failed for : {}", sourcePath);
			return;
		}
		final var root = doc.getDocumentElement();
		var group = root.getTagName();
		if (ModItemBuilder.HANDLER_MAP.get(group) != null) {
			log.debug("file parser for {}", group);
			final var item = ModItemBuilder.create(root);
			if (item == null || item.getId() == null)
				return;
			
			item.setPath(sourcePath);
			sink.add(item);
			return;
		}
		final var tableNodes = root.getChildNodes();
		for (int i = 0; i < tableNodes.getLength(); i++) {
			if (! (tableNodes.item(i) instanceof Element tableEl))
				continue;
			var ver = tableEl.getAttribute("version");
			int version = 1;
			try {
				version = Integer.parseInt(ver);
			} catch (NumberFormatException n) {
				log.warn("could not get version from {}, input was: '{}'", sourcePath, ver);
			}
			
			final var items = tableEl.getChildNodes();
			for (int j = 0; j < items.getLength(); j++) {
				if (! (items.item(j) instanceof Element itemElement))
					continue;
				
				final var item = ModItemBuilder.create(itemElement);
				if (item == null || item.getId() == null)
					continue;
				
				item.setPath(sourcePath);
				sink.add(item);
			}
		}
	}
	
	/**
	 * Exclude PAKs that don't contain item/table data.
	 */
	private static Predicate<Path> excludeNonDataPaks() {
		return p -> {
			if (! Files.isRegularFile(p))
				return false;
			final var name = p.getFileName().toString().toLowerCase(Locale.ROOT);
			if (! name.endsWith(".pak"))
				return false;
			boolean isIgnored = IGNORED_FILES.contains(name);
			return ! isIgnored;
		};
	}
	
	/**
	 * Load mod items from all PAK files in the mod's Data folder.
	 * Processes PAK files and their XML entries in parallel for maximum throughput.
	 */
	public static Set<ModItem> loadItems(Path modPath) {
		if (! Files.exists(modPath)) {
			log.warn("PAK directory does not exist: {}", modPath);
			return Set.of();
		}
		
		try (var stream = Files.list(modPath)) {
			final var pakFiles = stream.filter(excludeNonDataPaks()).collect(Collectors.toSet());
			
			if (pakFiles.isEmpty()) {
				log.info("No PAK files found in: {}", modPath);
				return Set.of();
			}
			// using single stream() is fine here, it makes the load slower, but does not eat up all you cpu power - parallelStream() is too much here
			final var result = pakFiles.stream().flatMap(ItemService::extractItemsFromPak).collect(Collectors.toSet());
			
			log.info("Loaded {} items from {} PAK file(s)", result.size(), pakFiles.size());
			return result;
			
		} catch (final IOException e) {
			log.error("Failed to list PAK folder: {} - {}", modPath, e.getMessage());
			return Set.of();
		}
	}
	
	/**
	 * Write all mod items to XML files, grouped by their origin PAK.
	 * Items loaded from "Weapons.pak:some/entry.xml" are staged under
	 * Mods/<modId>/Data/_stage/Weapons/<entry-dir>/
	 * so that ModService can later pack each group into a separate PAK.
	 * <p>
	 * Items with no PAK hint (newly created) are staged under
	 * Mods/<modId>/Data/_stage/<modId>/ and end up in <modId>.pak.
	 * <p>
	 * Returns the set of PAK names that were written (without extension),
	 * so the caller knows which staging dirs to pack.
	 */
	public static void writeModItems(ModData mod, String gameDir) {
		final var items = mod.getItems();
		if (items.isEmpty())
			return;
		for (final ModItem item : items) {
			try {
				writeModItem(gameDir, mod, item);
			} catch (final Exception e) {
				log.error("writeModItem failed for {}", item.getClass().getSimpleName(), e);
			}
		}
		log.debug("ModItem written to {}", Util.modStaging(gameDir, mod.id));
	}
	
	/**
	 * Try to read all XML pak files.
	 * Returns false if the game directory is not configured.
	 */
	public void init() {
		final long start = System.currentTimeMillis();
		final String gameDir = userConfig.getGameDirectory();
		if (gameDir == null || gameDir.isBlank())
			return;
		final var game = Singleton.INSTANCE.getGame();
		try {
			game.setItems(loadItems(Path.of(gameDir, "Data")));
			log.info("XML read done items={}", game.getItems().size());
		} catch (Exception ex) {
			log.error("Game Data read failed: {}", ex.getMessage());
		}
		log.info("Game ItemData Load took: {}", System.currentTimeMillis() - start);
	}
}