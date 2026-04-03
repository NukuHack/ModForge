package modforge.backend.service;

import modforge.Singleton;
import modforge.Util;
import modforge.backend.AttributeFactory;
import modforge.backend.DataPoint;
import modforge.backend.ItemType;
import modforge.backend.ModData;
import modforge.backend.model.BuffParam;
import modforge.backend.model.ModItem;
import modforge.backend.model.attributes.Attribute;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ItemService {
	private static final Logger log = Logger.getLogger(ItemService.class.getName());
	
	private final UserService configService;
	
	public ItemService(UserService configService) {
		this.configService = configService;
		init();
	}
	
	// ==================================================================
	// READ OPERATIONS
	// ==================================================================
	
	private static Element buildXmlElement(Document doc, String elementName, ModItem item) {
		Element el = doc.createElement(elementName);
		for (var attr : item.getAttributes()) {
			el.setAttribute(attr.getName(), serializeValue(attr));
		}
		return el;
	}
	
	@SuppressWarnings("unchecked")
	private static String serializeValue(Attribute attr) {
		Object v = attr.getValue();
		if (v == null)
			return "";
		if ("buff_params".equals(attr.getName()) && v instanceof List<?> list) {
			return BuffParam.listToString((List<BuffParam>) list);
		}
		return switch (v) {
			case Enum<?> e -> String.valueOf(e.ordinal());
			case Boolean b -> b.toString().toLowerCase(Locale.ROOT);
			case Double d ->
					d == Math.floor(d) && ! Double.isInfinite(d) ? String.valueOf(d.longValue()) : d.toString();
			default -> v.toString();
		};
	}
	
	// ==================================================================
	// WRITE OPERATIONS
	// ==================================================================
	
	static Document parseXml(InputStream is) throws ParserConfigurationException, IOException, SAXException {
		var f = DocumentBuilderFactory.newInstance();
		f.setNamespaceAware(true);
		f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		f.setFeature("http://xml.org/sax/features/validation", false);
		f.setFeature("http://xml.org/sax/features/external-general-entities", false);
		f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		
		// Use a proper encoding-aware reader
		try (var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
			return f.newDocumentBuilder().parse(new InputSource(reader));
		}
	}
	
	// ==================================================================
	// PRIVATE HELPERS
	// ==================================================================
	
	private static void writeXml(Document doc, File outFile) throws Exception {
		var tf = TransformerFactory.newInstance().newTransformer();
		//tf.setOutputProperty(OutputKeys.ENCODING, "US-ASCII");
		//tf.setOutputProperty(OutputKeys.INDENT, "yes");
		//tf.setOutputProperty(OutputKeys.STANDALONE, "no");
		// xml declaration appended later
		var writer = new StringWriter();
		tf.transform(new DOMSource(doc), new StreamResult(writer));
		Util.writeXml(writer.toString(), outFile.toPath());
	}
	
	private static File getOutputFile(final String gameDir, final ModItem item, final ModData mod, final String typeName) {
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
		// Structure:  Mods/<modId>/Data/_stage/<pakStem>/<dirSuffix>/
		final String stageRoot = Util.join(Util.modData(gameDir, mod.id), "_stage", pakStem);
		final String targetDir = dirSuffix.isEmpty() ? stageRoot : Util.join(stageRoot, dirSuffix);
		final String outFile = Util.join(targetDir, typeName + "__" + mod.id + ".xml");
		return new File(outFile);
	}
	
	private static Document makeDocument(final File outFile, final ModItem item, final String typeName) throws Exception {
		final var factory = DocumentBuilderFactory.newInstance();
		final var docBuilder = factory.newDocumentBuilder();
		final String groupName = typeName + "s";
		final Document doc;
		
		if (outFile.exists()) {
			doc = docBuilder.parse(outFile);
			Element group = (Element) doc.getElementsByTagName(groupName).item(0);
			if (group == null) {
				group = doc.createElement(groupName);
				group.setAttribute("version", "1");
				doc.getDocumentElement().appendChild(group);
			}
			
			final var newEl = buildXmlElement(doc, typeName, item);
			final String idKey = item.getIdKey();
			final String idVal = newEl.getAttribute(idKey).trim();
			
			if (! idVal.isBlank()) {
				final NodeList existing = group.getElementsByTagName(typeName);
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
			doc = docBuilder.newDocument();
			final var root = doc.createElement("database");
			root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
			root.setAttribute("name", "barbora");
			root.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:noNamespaceSchemaLocation", "../database.xsd");
			doc.appendChild(root);
			
			final var group = doc.createElement(groupName);
			group.setAttribute("version", "1");
			root.appendChild(group);
			group.appendChild(buildXmlElement(doc, typeName, item));
		}
		return doc;
	}
	
	/**
	 * Try to read all XML pak files.
	 * Returns false if the game directory is not configured.
	 */
	public void init() {
		final String gameDir = configService.gameDirectory;
		final var game = Singleton.INSTANCE.game();
		if (gameDir == null || gameDir.isBlank())
			return;
		try {
			game.setItems(loadModItems(Path.of(gameDir, "Data")));
			log.info(String.format("XML read done items=%d", game.getItems().size()));
		} catch (Exception ex) {
			log.severe("Game Data read failed: " + ex.getMessage());
		}
	}
	
	public Set<ModItem> readModItems(final DataPoint dp) {
		final var result = new HashSet<ModItem>();
		final File pakFile = new File(dp.path());
		
		if (! pakFile.exists()) {
			log.warning("Pak file not found: " + dp.path());
			return result;
		}
		
		try (var zf = new ZipFile(pakFile)) {
			var it = zf.entries();
			while (it.hasMoreElements()) {
				final var entry = it.nextElement();
				final String path = entry.getName().replace('\\', '/');
				final var fileName = Path.of(entry.getName()).getFileName().toString();
				
				// Skip non-XML files
				if (! fileName.endsWith("xml"))
					continue;
				if (! fileName.startsWith(dp.endpoint()))
					continue;
				
				try (var is = zf.getInputStream(entry)) {
					
					readItemsFromXml(is, pakFile.toString(), result);
				} catch (Exception ex) {
					log.fine("Parse error in " + path + ": " + ex.getMessage());
				}
			}
		} catch (IOException e) {
			log.severe("Cannot open pak: " + dp.path() + " - " + e.getMessage());
		}
		return result;
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
		final String gameDir = configService.gameDirectory;
		for (final ModItem item : items) {
			try {
				writeModItem(gameDir, modData, item);
			} catch (final Exception e) {
				log.severe("writeModItem failed for " + item.getClass().getSimpleName() + ": " + e.getMessage());
			}
		}
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
		final String typeName = item.getClass().getSimpleName().toLowerCase(Locale.ROOT);
		
		final File outFile = getOutputFile(gameDir, item, mod, typeName);
		Files.createDirectories(outFile.toPath().getParent());
		
		final Document doc = makeDocument(outFile, item, typeName);
		
		writeXml(doc, outFile);
	}
	
	/**
	 * Load mod items from all PAK files in the mod's Data folder.
	 * Processes PAK files and their XML entries in parallel for maximum throughput.
	 */
	public Set<ModItem> loadModItems(Path modPath) {
		if (! Files.exists(modPath)) {
			log.warning("PAK directory does not exist: " + modPath);
			return Set.of();
		}
		
		try (var stream = Files.list(modPath)) {
			final var pakFiles = stream
					.filter(Files::isRegularFile)
					.filter(p -> p.toString().toLowerCase().endsWith(".pak"))
					.filter(excludeNonDataPaks())
					.collect(Collectors.toSet());
			
			if (pakFiles.isEmpty()) {
				log.fine("No PAK files found in: " + modPath);
				return Set.of();
			}
			
			final Set<ModItem> result = pakFiles.parallelStream()
					.flatMap(ItemService::extractItemsFromPak)
					.collect(Collectors.toSet());
			
			log.info("Loaded %d items from %d PAK file(s)".formatted(result.size(), pakFiles.size()));
			return result;
			
		} catch (final IOException e) {
			log.severe("Failed to list PAK folder: " + modPath + " - " + e.getMessage());
			return Set.of();
		}
	}
	
	private static Stream<ModItem> extractItemsFromPak(Path pakFile) {
		final Set<ModItem> items = new HashSet<>();
		try (var zf = new ZipFile(pakFile.toFile())) {
			for (final var entry : zf.stream().filter(excludeNonEndpoints()).toList()) {
				final String entryName = entry.getName().replace('\\', '/');
				try (var is = zf.getInputStream(entry)) {
					readItemsFromXml(is, pakFile.getFileName() + ":" + entryName, items);
				} catch (Exception ex) {
					log.warning("Parse error in %s from %s: %s".formatted(entryName, pakFile.getFileName(), ex.getMessage()));
				}
			}
		} catch (IOException e) {
			log.severe("Cannot open PAK file: %s - %s".formatted(pakFile, e.getMessage()));
		}
		return items.stream();
	}
	
	private static void readItemsFromXml(final InputStream is, final String sourcePath, final Set<ModItem> sink) throws Exception {
		final var doc = parseXml(is);
		
		final var nodes = doc.getElementsByTagName("*");
		for (int i = 0; i < nodes.getLength(); i++) {
			if (! (nodes.item(i) instanceof Element tableEl) || tableEl.getNodeType() != Node.ELEMENT_NODE)
				continue;
			
			final var items = tableEl.getElementsByTagName("*");
			for (int j = 0; j < items.getLength(); j++) {
				if (! (items.item(j) instanceof Element itemElement) || itemElement == tableEl)
					continue;
				
				AttributeFactory.traverseElement(itemElement);
				final ModItem item = ModItemBuilder.build(itemElement);
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
		// TODO : make this kind of data either load or ... idk
		return p -> {
			String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
			return ! name.equals("scripts.pak") && ! name.equals("animations.pak") && ! name.equals("heads.pak") && ! name.equals("sounds.pak") && ! name.equals("shaders.pak");
		};
	}
	
	private static Predicate<ZipEntry> excludeNonEndpoints() {
		// TODO : make this kind of data either load or ... idk
		return p -> {
			final String name = p.getName().toLowerCase(Locale.ROOT);
			if (! name.endsWith(".xml"))
				return false;
			final Path path = Path.of(name);
			final String fileName = path.getFileName().toString();
			final int delimiter = fileName.indexOf("__");
			final String shortName;
			if (delimiter != -1)
				shortName = fileName.substring(0, fileName.indexOf("__"));
			else
				shortName = fileName.substring(0, fileName.lastIndexOf("."));
			return ItemType.endpointSet().contains(shortName);
		};
	}
}