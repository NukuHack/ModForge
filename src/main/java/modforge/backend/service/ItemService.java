package modforge.backend.service;

import modforge.Singleton;
import modforge.Util;
import modforge.backend.AttributeFactory;
import modforge.backend.ItemEntry;
import modforge.backend.ItemType;
import modforge.backend.ModData;
import modforge.backend.model.ModItem;
import modforge.backend.model.attributes.Attribute;
import modforge.backend.model.attributes.BuffParam;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
		final var el = doc.createElement(elementName);
		for (Attribute<?> attr : item.getAttributes()) {
			el.setAttribute(attr.getName(), serializeValue(attr));
		}
		return el;
	}
	
	private static <T> String serializeValue(Attribute<T> attr) {
		final T v = attr.getValue();
		if (v == null)
			return "";
		if (attr instanceof List<?> list) {
			if (list.isEmpty())
				return "";
			if (list.getFirst() instanceof String)
				return String.join(",", list.stream().map(Object::toString).toList());
			var sb = new StringBuilder();
			for (var f : list)
				if (f instanceof Attribute<?> a)
					sb.append(serializeValue(a)).append(',');
				else
					log.warning("found list with unsupported type: " + (list.stream().limit(20).toList()) + " type: " + f.getClass());
			return sb.toString();
		}
		return switch (v) {
			case BuffParam b -> b.toAttrString();
			case Enum<?> e -> String.valueOf(e.ordinal());
			case Boolean b -> b.toString().toLowerCase(Locale.ROOT);
			case Double d -> {
				if (Double.isInfinite(d) || Double.isNaN(d))
					yield "-1";
				long rounded = Math.round(d);
				if (Math.abs(d - rounded) < 1e-8)
					yield String.valueOf(rounded);
				yield d.toString();
			}
			default -> v.toString();
		};
	}
	
	// ==================================================================
	// WRITE OPERATIONS
	// ==================================================================
	
	static Document parseXml(InputStream is) {
		try {
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
		} catch (final ParserConfigurationException e) {
			log.warning("Could not configure the parser");
			return null;
		} catch (final SAXException e) {
			log.warning("Could not parse the file");
			return null;
		} catch (final IOException e) {
			log.warning("Could access the file");
			return null;
		}
	}
	
	// ==================================================================
	// PRIVATE HELPERS
	// ==================================================================
	
	private static void writeXml(Document doc, File outFile) throws Exception {
		final var tf = TransformerFactory.newInstance().newTransformer();
		final var writer = new StringWriter();
		tf.transform(new DOMSource(doc), new StreamResult(writer));
		Util.writeXml(writer.toString(), outFile.toPath());
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
		final var typeName = ItemEntry.forClass(item.getClass()).simpleName();
		final var stageRoot = Util.joinP(Util.modStaging(gameDir, mod.id), pakStem);
		final var targetDir = dirSuffix.isEmpty() ? stageRoot : Util.joinP(stageRoot, dirSuffix);
		final var outFile = Util.joinP(targetDir, Util.modXmlFile(typeName, mod.id));
		return outFile.toFile();
	}
	
	private static Document makeDocument(final File outFile, final ModItem item) throws Exception {
		final var factory = DocumentBuilderFactory.newInstance();
		final var docBuilder = factory.newDocumentBuilder();
		final var typeName = ItemEntry.forClass(item.getClass()).simpleName();
		final var groupName = ItemEntry.forClass(item.getClass()).parentName();
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
		
		final Document doc = makeDocument(outFile, item);
		
		writeXml(doc, outFile);
	}
	
	private static Stream<ModItem> extractItemsFromPak(final Path pakFile) {
		final Set<ModItem> items = new HashSet<>();
		try (var zf = new ZipFile(pakFile.toFile())) {
			for (final var entry : zf.stream().filter(ItemType::excludeNonEndpoints).toList()) {
				final String entryName = entry.getName().replace('\\', '/');
				try (var is = zf.getInputStream(entry)) {
					readItemsFromXml(is, pakFile.getFileName() + ":" + entryName, items);
				} catch (final Exception ex) {
					log.warning("Parse error in %s from %s: %s".formatted(entryName, pakFile.getFileName(), ex.getMessage()));
				}
			}
		} catch (IOException e) {
			log.severe("Cannot open PAK file: %s - %s".formatted(pakFile, e.getMessage()));
		}
		return items.stream();
	}
	
	private static void readItemsFromXml(final InputStream is, final String sourcePath, final Set<ModItem> sink) {
		final var doc = parseXml(is);
		if (doc == null)
			return;
		final var root = doc.getDocumentElement();
		final var tableNodes = root.getChildNodes();
		for (int i = 0; i < tableNodes.getLength(); i++) {
			if (! (tableNodes.item(i) instanceof Element tableEl))
				continue;
			
			final var items = tableEl.getChildNodes();
			for (int j = 0; j < items.getLength(); j++) {
				if (! (items.item(j) instanceof Element itemElement))
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
	
	/**
	 * Try to read all XML pak files.
	 * Returns false if the game directory is not configured.
	 */
	public void init() {
		final long start = System.currentTimeMillis();
		final String gameDir = configService.gameDirectory;
		if (gameDir == null || gameDir.isBlank())
			return;
		final var game = Singleton.INSTANCE.game();
		try {
			game.setItems(this.loadItems(Path.of(gameDir, "Data")));
			log.info(String.format("XML read done items=%d", game.getItems().size()));
		} catch (Exception ex) {
			log.severe("Game Data read failed: " + ex.getMessage());
		}
		System.out.printf("Game ItemData Load took: %dms%n", System.currentTimeMillis() - start);
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
	 * Load mod items from all PAK files in the mod's Data folder.
	 * Processes PAK files and their XML entries in parallel for maximum throughput.
	 */
	public Set<ModItem> loadItems(Path modPath) {
		if (! Files.exists(modPath)) {
			log.warning("PAK directory does not exist: " + modPath);
			return Set.of();
		}
		
		try (var stream = Files.list(modPath)) {
			final var pakFiles = stream.filter(Files::isRegularFile).filter(p -> p.toString().toLowerCase().endsWith(".pak")).filter(excludeNonDataPaks()).collect(Collectors.toSet());
			
			if (pakFiles.isEmpty()) {
				log.fine("No PAK files found in: " + modPath);
				return Set.of();
			}
			// using single stream() is fine here, it makes the load slower, but does not eat up all you cpu power - parallelStream() is too much here
			final Set<ModItem> result = pakFiles.stream().flatMap(ItemService::extractItemsFromPak).collect(Collectors.toSet());
			
			log.info("Loaded %d items from %d PAK file(s)".formatted(result.size(), pakFiles.size()));
			return result;
			
		} catch (final IOException e) {
			log.severe("Failed to list PAK folder: " + modPath + " - " + e.getMessage());
			return Set.of();
		}
	}
}