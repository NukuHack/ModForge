package modforge.backend.service;

import modforge.Singleton;
import modforge.Util;
import modforge.backend.ItemEntry;
import modforge.backend.ItemType;
import modforge.backend.ModData;
import modforge.backend.model.ModItem;
import org.w3c.dom.*;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

@lombok.extern.slf4j.Slf4j
public final class ItemService {
	
	final static DocumentBuilder docBuilder;
	
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
	private final UserConfig userConfig;
	
	public ItemService(UserConfig userConfig) {
		this.userConfig = userConfig;
	}
	
	// ==================================================================
	// PRIVATE HELPERS
	// ==================================================================
	
	static Document parseXml(InputStream is) {
		try {
			var f = DocumentBuilderFactory.newInstance();
			f.setNamespaceAware(true);
			f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			f.setFeature("http://xml.org/sax/features/validation", false);
			f.setFeature("http://xml.org/sax/features/external-general-entities", false);
			f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			
			
			// Wrap in BufferedInputStream if not already marked-supported
			if (! is.markSupported()) {
				is = new BufferedInputStream(is);
			}
			
			// Read and skip BOM if present
			is.mark(3);
			int b1 = is.read();
			int b2 = is.read();
			int b3 = is.read();
			
			if (b1 == 0xEF && b2 == 0xBB && b3 == 0xBF) {
				// BOM found, we already skipped it, continue with stream position after BOM
				// Don't reset, just continue
			} else {
				// No BOM, reset the stream to the beginning
				is.reset();
			}
			// If BOM present, we already skipped it, continue with stream position after BOM
			
			// Use a proper encoding-aware reader
			try (var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
				var doc = f.newDocumentBuilder();
				return doc.parse(new InputSource(reader));
			} catch (final ParserConfigurationException e) {
				log.warn("Could not create document builder");
				return null;
			}
		} catch (final ParserConfigurationException e) {
			log.warn("Could not configure the parser");
			return null;
		} catch (final SAXException e) {
			log.warn("Could not parse the file");
			return null;
		} catch (final IOException e) {
			log.warn("Could not access the file");
			return null;
		}
	}
	
	private static File getOutputFile(final String gameDir, final ModItem item, final ModData mod) {
		final String rawPath = item.getPath() == null ? "" : item.getPath();
		
		// ---- Resolve PAK stem & inner directory suffix ----
		final String pakStem;   // e.g. "Weapons"  or  modId
		final String dirSuffix; // e.g. "Libs/Tables" or ""
		
		int colonIdx = rawPath.indexOf(':');
		if (colonIdx > 0) {
			// Format (a): "SomePak.pak:inner/dir/entry.xml"
			final String pakFileName = rawPath.substring(0, colonIdx);       // "SomePak.pak"
			final String innerEntry = rawPath.substring(colonIdx + 1);      // "inner/dir/entry.xml"
			final int dot = pakFileName.lastIndexOf('.');
			pakStem = dot > 0 ? pakFileName.substring(0, dot) : pakFileName;     // "SomePak"
			int lastSlash = innerEntry.lastIndexOf('/');
			dirSuffix = lastSlash >= 0 ? innerEntry.substring(0, lastSlash) : "";
		} else {
			// Format (b/c): plain path or blank
			pakStem = mod.id;
			int lastSlash = rawPath.lastIndexOf('/');
			dirSuffix = lastSlash >= 0 ? rawPath.substring(0, lastSlash) : "";
		}
		
		// ---- Build target paths inside the staging area ----
		// Structure:  <gameDir>/Mods/<modId>/Data/_stage/<pakStem>/<dirSuffix>/<type>__<mod.id>.xml
		final var typeName = ItemEntry.forClass(item.getClass()).fileName;
		final var stageRoot = Util.joinP(Util.modStaging(gameDir, mod.id), pakStem);
		final var targetDir = dirSuffix.isEmpty() ? stageRoot : Util.joinP(stageRoot, dirSuffix);
		final var outFile = Util.joinP(targetDir, Util.modXmlFile(typeName, mod.id));
		return outFile.toFile();
	}
	
	private static Document makeDocument(final File outFile, final ModItem item, final String groupName) throws Exception {
		final Document document;
		
		if (outFile.exists()) {
			document = docBuilder.parse(outFile);
			Element group = (Element) document.getElementsByTagName(groupName).item(0);
			if (group == null) {
				group = document.createElement(groupName);
				group.setAttribute("version", "1");
				document.getDocumentElement().appendChild(group);
			}
			
			final var newEl = ModItemBuilder.build(document, item);
			if (newEl == null)
				return document;
			final String idKey = item.getIdKey();
			final String idVal = newEl.getAttribute(idKey);
			
			if (! idVal.isBlank()) {
				final NodeList existing = group.getElementsByTagName(newEl.getTagName());
				boolean replaced = false;
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
			} else {
				group.appendChild(newEl);
			}
		} else {
			document = docBuilder.newDocument();
			final Element temp = document.createElement("database");
			temp.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
			temp.setAttribute("name", "barbora");
			temp.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:noNamespaceSchemaLocation", "../database.xsd");
			document.appendChild(temp);
			final Element group = document.createElement(groupName);
			group.setAttribute("version", "1");
			temp.appendChild(group);
			
			final var newEl = ModItemBuilder.build(document, item);
			group.appendChild(newEl);
		}
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
		
		final var groupName = ModItemBuilder.groupName(item);
		final Document doc;
		final String doctype;
		
		if (groupName.startsWith("storm")) {
			doc = docBuilder.newDocument();
			final Element group = doc.createElement("storm");
			group.appendChild(ModItemBuilder.build(doc, item));
			doc.appendChild(group);
			doctype = Util.STORM_HEADER+"\n";
		} else {
			doc = makeDocument(outFile, item, groupName);
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
		log.debug("entire xml so far: \n{}", xml);
		
		Util.writeXml(xml, outFile.toPath());
	}
	
	private static Stream<ModItem> extractItemsFromPak(final Path pakFile) {
		final Set<ModItem> items = new HashSet<>();
		try (var zf = new ZipFile(pakFile.toFile())) {
			for (final var entry : zf.stream().filter(ItemType::excludeNonEndpoints).toList()) {
				final String entryName = entry.getName().replace('\\', '/');
				try (var is = zf.getInputStream(entry)) {
					readItemsFromXml(is, pakFile.getFileName() + ":" + entryName, items);
				} catch (final Exception ex) {
					log.warn("Parse error in {} from {}: {}", entryName, pakFile.getFileName(), ex.getMessage());
				}
			}
		} catch (IOException e) {
			log.error("Cannot open PAK file: {} - {}", pakFile, e.getMessage());
		}
		return items.stream();
	}
	
	private static void readItemsFromXml(final InputStream is, final String sourcePath, final Set<ModItem> sink) {
		final var doc = parseXml(is);
		if (doc == null) {
			log.debug("Parse failed for : {}", sourcePath);
			return;
		}
		final var root = doc.getDocumentElement();
		if (ModItemBuilder.FILE_PARSERS.contains(root.getTagName())) {
			final ModItem item = ModItemBuilder.create(root);
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
			
			
			final var items = tableEl.getChildNodes();
			for (int j = 0; j < items.getLength(); j++) {
				if (! (items.item(j) instanceof Element itemElement))
					continue;
				
				final ModItem item = ModItemBuilder.create(itemElement);
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
			final Set<ModItem> result = pakFiles.stream().flatMap(ItemService::extractItemsFromPak).collect(Collectors.toSet());
			
			log.info("Loaded {} items from {} PAK file(s)", result.size(), pakFiles.size());
			return result;
			
		} catch (final IOException e) {
			log.error("Failed to list PAK folder: {} - {}", modPath, e.getMessage());
			return Set.of();
		}
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
	public void writeModItems(ModData modData) {
		final var items = modData.getItems();
		if (items.isEmpty())
			return;
		final String gameDir = userConfig.getGameDirectory();
		for (final ModItem item : items) {
			try {
				writeModItem(gameDir, modData, item);
			} catch (final Exception e) {
				log.error("writeModItem failed for {}: {}", item.getClass().getSimpleName(), e.getMessage());
			}
		}
	}
}