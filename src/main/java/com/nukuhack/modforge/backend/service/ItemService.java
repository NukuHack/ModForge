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
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

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
public record ItemService(UserConfig userConfig) {

	public static final Set<String> IGNORED_FILES = Set.of("scripts.pak", "animations.pak", "heads.pak", "sounds.pak", "shaders.pak");
	public static final Set<String> LOGGED = new HashSet<>();
	
	static Document parseXml(InputStream is) {
		try {
			
			if (! is.markSupported())
				is = new BufferedInputStream(is);
			
			is.mark(3);
			byte[] bom = new byte[3];
			int bytesRead = is.read(bom);
			
			is.reset();
			
			final String encoding;
			
			if (bytesRead >= 3 && bom[0] == (byte) 0xEF && bom[1] == (byte) 0xBB && bom[2] == (byte) 0xBF) {
				
				if (is.skip(3) != 3)
					throw new IOException("Could not read the BOM correctly");
				encoding = "UTF-8";
			} else if (bytesRead >= 2 && bom[0] == (byte) 0xFE && bom[1] == (byte) 0xFF) {
				
				if (is.skip(2) != 2)
					throw new IOException("Could not read the BOM correctly");
				encoding = "UTF-16BE";
			} else if (bytesRead >= 2 && bom[0] == (byte) 0xFF && bom[1] == (byte) 0xFE) {
				
				if (is.skip(2) != 2)
					throw new IOException("Could not read the BOM correctly");
				encoding = "UTF-16LE";
			} else {
				encoding = "UTF-8";
			}
			
			try (var reader = new BufferedReader(new InputStreamReader(is, encoding))) {
				return Singleton.DOC_BUILDER.get().parse(new InputSource(reader));
			}
		} catch (SAXException e) {
			log.warn("Could not parse the file", Util.limitStackTrace(e, 10));
			return null;
		} catch (IOException e) {
			log.warn("Could not access the file", Util.limitStackTrace(e, 10));
			return null;
		} catch (Exception e) {
			log.warn("Unknown exception at xml parsing", Util.limitStackTrace(e, 10));
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
	private static File getOutputFile(String gameDir, ModItem item, ModData mod, boolean base) {
		var rawPath = item.getPath();
		var targetDir = getOutputFile(gameDir, rawPath, mod).getParent();
		var typeName = ! base ? ItemEntry.to(item).fileName : Path.of(rawPath).getFileName().toString();
		var outFile = Util.joinP(targetDir, Util.modXmlFile(typeName, mod.getId(), base));
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
	public static Path getOutputFile(String gameDir, String itemPath, ModData mod) {

		String pakStem;
		String dirSuffix;
		String fileName;
		
		int colonIdx = itemPath.indexOf(':');
		if (colonIdx > 0) {
			var pakFileName = itemPath.substring(0, colonIdx);
			var innerEntry = itemPath.substring(colonIdx + 1);
			
			var dot = pakFileName.lastIndexOf('.');
			pakStem = dot > 0 ? pakFileName.substring(0, dot) : pakFileName;
			var lastSlash = innerEntry.lastIndexOf('/');
			
			dirSuffix = lastSlash >= 0 ? innerEntry.substring(0, lastSlash) : "";
			fileName = innerEntry.substring(++ lastSlash);
		} else {
			pakStem = mod.getId();
			var lastSlash = itemPath.lastIndexOf('/');
			dirSuffix = lastSlash >= 0 ? itemPath.substring(0, lastSlash) : "";
			fileName = itemPath.substring(++ lastSlash);
		}
		if (fileName.isBlank())
			fileName = "apple.txt";
		
		var stageRoot = Util.joinP(Util.modStaging(gameDir, mod.getId()), pakStem);
		
		return dirSuffix.isEmpty() ? Util.joinP(stageRoot, fileName) : Util.joinP(stageRoot, Util.capitalStart(dirSuffix), fileName);
	}
	
	private static Document makeDocument(File outFile, ModItem item, ItemEntry groupT) throws Exception {
		final Document document;
		
		var groupName = groupT.parentName;
		if (! outFile.exists()) {
			document = Singleton.DOC_BUILDER.get().newDocument();
			var temp = document.createElement("database");
			temp.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
			temp.setAttribute("name", "barbora");
			temp.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:noNamespaceSchemaLocation", "../database.xsd");
			document.appendChild(temp);
			var group = document.createElement(groupName);
			group.setAttribute("version", groupT.getVersion());
			temp.appendChild(group);
			
			var newEl = ModItemBuilder.create(document, item);
			newEl.ifPresent(group::appendChild);
			return document;
		}
		document = Singleton.DOC_BUILDER.get().parse(outFile);
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
	private static void writeModItem(String gameDir, ModData mod, ModItem item) throws Exception {
		File outFile;
		var group = ItemEntry.to(item);
		final Document doc;
		String doctype;
		
		if (ModItemBuilder.HANDLER_MAP.get(group.parentName) != null) {
			outFile = getOutputFile(gameDir, item, mod, true);
			Files.createDirectories(outFile.toPath().getParent());
			doc = Singleton.DOC_BUILDER.get().newDocument();
			var el = ModItemBuilder.create(doc, item);
			el.ifPresent(doc::appendChild);
			doctype = Util.STORM_HEADER + "\n";
		} else {
			outFile = getOutputFile(gameDir, item, mod, false);
			Files.createDirectories(outFile.toPath().getParent());
			doc = makeDocument(outFile, item, group);
			doctype = null;
		}
		
		writeXml(doc, outFile, doctype);
	}
	
	private static void writeXml(Document doc, File outFile, String doctype) throws Exception {
		var tf = TransformerFactory.newInstance().newTransformer();
		tf.setOutputProperty(OutputKeys.INDENT, "yes");
		tf.setOutputProperty("{http://xml.apache.org/xalan}indent-amount", "2");
		
		tf.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		
		var writer = new StringWriter();
		tf.transform(new DOMSource(doc), new StreamResult(writer));
		var xml = doctype == null ? writer.toString() : doctype + writer;
		
		Util.writeXml(xml, outFile.toPath());
	}
	
	private static Stream<ModItem> extractItemsFromPak(Path pakFile) {
		var items = new HashSet<ModItem>();
		try (var zf = new ZipFile(pakFile.toFile())) {
			for (var entry : zf.stream().filter(ItemType::excludeNonEndpoints).toList()) {
				var entryName = entry.getName().replace('\\', '/');
				try (var is = zf.getInputStream(entry)) {
					readItemsFromXml(is, pakFile.getFileName() + ":" + entryName, items);
				} catch (Exception ex) {
					log.warn("Parse error in {} from {}", entryName, pakFile.getFileName(), ex);
				}
			}
		} catch (IOException e) {
			log.error("Cannot open PAK file: {}", pakFile, e);
		}
		return items.stream();
	}
	
	static void readItemsFromXml(InputStream is, String sourcePath, Set<ModItem> sink) {
		var doc = parseXml(is);
		if (doc == null) {
			log.debug("Parse failed for : {}", sourcePath);
			return;
		}
		var root = doc.getDocumentElement();
		var group = root.getTagName();
		if (ModItemBuilder.HANDLER_MAP.get(group) != null) {
			var item = ModItemBuilder.create(root);
			if (item == null)
				return;
			
			item.setPath(sourcePath);
			sink.add(item);
			return;
		}
		var tableNodes = root.getChildNodes();
		for (int i = 0; i < tableNodes.getLength(); i++) {
			if (! (tableNodes.item(i) instanceof Element tableEl))
				continue;
			var ver = tableEl.getAttribute("version");
			int version = 1;
			try {
				version = Integer.parseInt(ver);
			} catch (NumberFormatException ignored) {
			}
			
			var items = tableEl.getChildNodes();
			for (int j = 0; j < items.getLength(); j++) {
				if (! (items.item(j) instanceof Element itemElement))
					continue;

				var elementName = itemElement.getTagName();
				var item = ModItemBuilder.create(itemElement);
				if (item == null) {
					if (!LOGGED.contains(elementName)) {
						LOGGED.add(elementName);
						log.info("No creater matched element <{}> in {}", elementName, sourcePath);
					}
					continue;
				}
				
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
			var name = p.getFileName().toString().toLowerCase(Locale.ROOT);
			if (! name.endsWith(".pak"))
				return false;
			var isIgnored = IGNORED_FILES.contains(name);
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
			var pakFiles = stream.filter(excludeNonDataPaks()).collect(Collectors.toSet());
			
			if (pakFiles.isEmpty()) {
				log.info("No PAK files found in: {}", modPath);
				return Set.of();
			}
			
			var result = pakFiles.stream().flatMap(ItemService::extractItemsFromPak).collect(Collectors.toSet());
			
			log.info("Loaded {} items from {} PAK file(s)", result.size(), pakFiles.size());
			return result;
			
		} catch (IOException e) {
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
		var items = mod.getItems();
		if (items.isEmpty())
			return;
		for (var item : items) {
			try {
				writeModItem(gameDir, mod, item);
			} catch (Exception e) {
				log.error("writeModItem failed for {}", item.getClass().getSimpleName(), e);
			}
		}
		log.debug("ModItem written to {}", Util.modStaging(gameDir, mod.getId()));
	}
	
	/**
	 * Try to read all XML pak files.
	 * Returns false if the game directory is not configured.
	 */
	public void init() {
		var start = System.currentTimeMillis();
		var gameDir = userConfig.getGameDir();
		if (gameDir.isBlank())
			return;
		var game = Singleton.getGame();
		try {
			game.setItems(loadItems(Path.of(gameDir, "Data")));
			log.info("XML read done items={}", game.getItems().size());
		} catch (Exception ex) {
			log.error("Game Data read failed: {}", ex.getMessage());
		}
		log.info("Game ItemData Load took: {}", System.currentTimeMillis() - start);
	}
}