package modforge.backend.service;

import modforge.*;
import modforge.backend.*;
import modforge.backend.model.*;
import modforge.backend.model.attributes.IAttribute;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

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
import java.util.stream.Stream;
import java.util.zip.ZipFile;

// (LinkedHashSet is in java.util.*)

public final class ItemService {
	private static final Logger log = Logger.getLogger(ItemService.class.getName());
	private static final Set<String> BLACKLIST = Set.of("44c61b30-c20e-4267-ab01-a1e39342731e");

	private final UserService configService;
	private final ModItemBuilder builder;

	public ItemService(UserService configService, ModItemBuilder builder) {
		this.configService = configService;
		this.builder = builder;
		init();
	}

	// ==================================================================
	// READ OPERATIONS
	// ==================================================================

	/**
	 * Try to read all XML pak files.
	 * Returns false if the game directory is not configured.
	 */
	public void init() {
		final String gameDir = configService.current.gameDirectory;
		if (gameDir != null && !gameDir.isBlank()) {
			try {
				Singleton.INSTANCE.game().items = readAllItemFromXml(gameDir, false);
			} catch (Exception ex) {
				log.severe("Game Data read failed: " + ex.getMessage());
				Singleton.INSTANCE.game().items = new ArrayList<>();
			}
		}
	}

	/**
	 * Look up a mod item by ID across all loaded collections.
	 */
	public Optional<ModItem> getModItem(String id) {
		final var items = Singleton.INSTANCE.game().items;
		return Stream.of(items)
				.flatMap(Collection::stream)
				.filter(x -> id.equals(x.getId()))
				.findFirst();
	}

	public List<ModItem> readModItem(DataPoint dp) {
		final var result = new ArrayList<ModItem>();
		final String typeName = dp.type().getSimpleName().toLowerCase(Locale.ROOT);
		final File pakFile = new File(dp.path());

		if (!pakFile.exists()) {
			log.warning("Pak file not found: " + dp.path());
			return result;
		}

		try (var zf = new ZipFile(pakFile)) {
			var it = zf.entries();
			while (it.hasMoreElements()) {
				final var entry = it.nextElement();
				final String ename = entry.getName().replace('\\', '/');

				// Skip non-XML files
				if (!ename.toLowerCase().endsWith(".xml")) continue;  // Only XML files
				if (!ename.contains(dp.endpoint())) continue;

				try (var is = zf.getInputStream(entry)) {
					final Document doc = parseXml(is);
					AttributeFactory.discoverTypes(doc.getDocumentElement());

					final var nodes = doc.getElementsByTagName("*");
					for (int i = 0; i < nodes.getLength(); i++) {
						if (!(nodes.item(i) instanceof Element el)) continue;
						if (!el.getLocalName().equalsIgnoreCase(typeName)) continue;

						final ModItem item = builder.build(el);
						if (item == null) {
							log.fine("Builder returned null for <" + el.getLocalName() + ">");
							continue;
						}
						if (item.getId() == null || BLACKLIST.contains(item.getId())) continue;
						item.setPath(ename);
						result.add(item);
					}
				} catch (Exception ex) {
					log.fine("Parse error in " + ename + ": " + ex.getMessage());
				}
			}
		} catch (IOException e) {
			log.severe("Cannot open pak: " + dp.path() + " - " + e.getMessage());
		}
		return result;
	}

	// ==================================================================
	// WRITE OPERATIONS
	// ==================================================================

	/**
	 * Write all mod items to XML files, grouped by their origin PAK.
	 * Items loaded from "Weapons.pak:some/entry.xml" are staged under
	 * Mods/<modId>/Data/_stage/Weapons/<entry-dir>/
	 * so that ModService can later pack each group into a separate PAK.
	 *
	 * Items with no PAK hint (newly created) are staged under
	 * Mods/<modId>/Data/_stage/<modId>/ and end up in <modId>.pak.
	 *
	 * Returns the set of PAK names that were written (without extension),
	 * so the caller knows which staging dirs to pack.
	 */
	public Set<String> writeModItems(ModData modData) {
		if (modData.items.isEmpty()) return Set.of();
		final String gameDir = configService.current.gameDirectory;
		final Set<String> pakNames = new LinkedHashSet<>();
		for (ModItem item : modData.items) {
			String pak = writeModItem(gameDir, modData.id, item);
			if (pak != null) pakNames.add(pak);
		}
		return pakNames;
	}

	/**
	 * Write a single item to XML and return the PAK stem it belongs to
	 * (e.g. "Weapons", or the modId when there is no origin hint).
	 *
	 * Path stored on items follows one of two formats:
	 *   a) "SomePak.pak:inner/dir/entry.xml"  – came from a named PAK
	 *   b) plain filesystem path               – came from loose XML scan
	 *   c) null / blank                        – newly created item
	 */
	private String writeModItem(String gameDir, String modId, ModItem item) {
		try {
			final String typeName = item.getClass().getSimpleName().toLowerCase(Locale.ROOT);
			final String rawPath  = item.getPath() == null ? "" : item.getPath();

			// ---- Resolve PAK stem & inner directory suffix ----
			String pakStem;   // e.g. "Weapons"  or  modId
			String dirSuffix; // e.g. "Libs/Tables" or ""

			int colonIdx = rawPath.indexOf(':');
			if (colonIdx > 0) {
				// Format (a): "SomePak.pak:inner/dir/entry.xml"
				String pakFileName = rawPath.substring(0, colonIdx);       // "SomePak.pak"
				String innerEntry  = rawPath.substring(colonIdx + 1);      // "inner/dir/entry.xml"
				pakStem   = stripExtension(pakFileName);                   // "SomePak"
				int lastSlash = innerEntry.lastIndexOf('/');
				dirSuffix = lastSlash >= 0 ? innerEntry.substring(0, lastSlash) : "";
			} else {
				// Format (b/c): plain path or blank
				pakStem = modId;
				int lastSlash = rawPath.lastIndexOf('/');
				dirSuffix = lastSlash >= 0 ? rawPath.substring(0, lastSlash) : "";
			}

			// ---- Build target paths inside the staging area ----
			// Structure:  Mods/<modId>/Data/_stage/<pakStem>/<dirSuffix>/
			String stageRoot  = PathFactory.join(gameDir, "Mods", modId, "Data", "_stage", pakStem);
			String targetDir  = dirSuffix.isEmpty() ? stageRoot : PathFactory.join(stageRoot, dirSuffix);
			String targetFile = PathFactory.join(targetDir, typeName + "__" + modId + ".xml");

			Files.createDirectories(Path.of(targetDir));

			var factory    = DocumentBuilderFactory.newInstance();
			var docBuilder = factory.newDocumentBuilder();

			Document doc;
			Element  group;
			String   groupName = typeName + "s";
			File     outFile   = new File(targetFile);

			if (outFile.exists()) {
				doc   = docBuilder.parse(outFile);
				group = (Element) doc.getElementsByTagName(groupName).item(0);
				if (group == null) {
					group = doc.createElement(groupName);
					group.setAttribute("version", "1");
					doc.getDocumentElement().appendChild(group);
				}

				final Element newEl  = buildXmlElement(doc, typeName, item);
				final String idKey  = item.getIdKey();
				final String idVal  = newEl.getAttribute(idKey);

				if (idKey != null && !idKey.isBlank() && !idVal.isBlank()) {
					NodeList existing  = group.getElementsByTagName(typeName);
					boolean  replaced  = false;
					for (int i = 0; i < existing.getLength(); i++) {
						var el = (Element) existing.item(i);
						if (idVal.equals(el.getAttribute(idKey))) {
							group.replaceChild(newEl, el);
							replaced = true;
							break;
						}
					}
					if (!replaced) group.appendChild(newEl);
				} else {
					group.appendChild(newEl);
				}
			} else {
				doc = docBuilder.newDocument();
				Element root = doc.createElement("database");
				root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
				root.setAttribute("name", "barbora");
				root.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:noNamespaceSchemaLocation", "../database.xsd");
				doc.appendChild(root);

				group = doc.createElement(groupName);
				group.setAttribute("version", "1");
				root.appendChild(group);
				group.appendChild(buildXmlElement(doc, typeName, item));
			}

			var tf = TransformerFactory.newInstance().newTransformer();
			tf.setOutputProperty(OutputKeys.ENCODING, "US-ASCII");
			tf.setOutputProperty(OutputKeys.INDENT, "yes");
			tf.setOutputProperty(OutputKeys.STANDALONE, "no");
			tf.transform(new DOMSource(doc), new StreamResult(outFile));

			return pakStem;

		} catch (Exception e) {
			log.severe("writeModItem failed for " + item.getClass().getSimpleName() + ": " + e.getMessage());
			return null;
		}
	}

	/** Strip the file extension from a filename: "Weapons.pak" → "Weapons". */
	private static String stripExtension(String name) {
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	// ==================================================================
	// PRIVATE HELPERS
	// ==================================================================

	// Add this method to ItemService.java
	private List<ModItem> readModItemsFromXml(String modFolder) {
		final var result = new ArrayList<ModItem>();
		final Path modPath = Path.of(modFolder);

		if (!Files.exists(modPath))
			return result;

		try (var walk = Files.walk(modPath)) {
			walk.filter(Files::isRegularFile)
				.filter(path -> path.toString().endsWith(".xml"))
				.forEach(xmlFile -> {
					try {
						// Parse the XML file
						final Document doc = parseXml(Files.newInputStream(xmlFile));
						final Element root = doc.getDocumentElement();

						// Check each child element to find the actual table type
						NodeList children = root.getChildNodes();
						for (int i = 0; i < children.getLength(); i++) {
							var node = children.item(i);
							if (node.getNodeType() != Node.ELEMENT_NODE) continue;
							final Element tableElement = (Element) node;
							final String tableName = tableElement.getLocalName();

							// Determine the item type from the table name
							final Class<? extends ModItem> itemClass = ItemType.determineItemClassFromTableName(tableName);
							if (itemClass == null) continue;
							// Parse all items in this table
							final NodeList items = tableElement.getElementsByTagName("*");
							for (int j = 0; j < items.getLength(); j++) {
								if (items.item(j) instanceof Element itemElement) {
									// Skip the table wrapper element itself
									if (itemElement == tableElement) continue;

									AttributeFactory.discoverTypes(itemElement);
									final ModItem item = builder.build(itemElement);
									if (item != null && item.getId() != null && !BLACKLIST.contains(item.getId())) {
										item.setPath(xmlFile.toString());
										result.add(item);
									}
								}
							}

						}
					} catch (Exception ex) {
						log.warning("Parse error in " + xmlFile + ": " + ex.getMessage());
					}
				});
		} catch (IOException e) {
			log.severe("Cannot walk mod folder: " + modFolder + " - " + e.getMessage());
		}

		return result;
	}

	// Modify the existing readAllItemFromXml method
	public List<ModItem> readAllItemFromXml(String gameDir, boolean isMod) {
		long start = System.currentTimeMillis();

		if (isMod) {
			// For mods, use the new method that scans all XML files
			var result = readModItemsFromXml(gameDir);
			log.info(String.format("XML MOD !!!!!! read done in %d ms | items=%d", System.currentTimeMillis() - start, result.size()));
			return result;
		} else {
			// For game data, use the original method with known endpoints
			final var allPoints = new ArrayList<DataPoint>(30);

			ItemType.endpoints().forEach((type, eps) ->
					eps.forEach((key, pak) ->
							allPoints.add(new DataPoint(gameDir + "/" + pak, key, type))
					)
			);

			var temp = allPoints.stream()
					.map(this::readModItem)
					.flatMap(Collection::stream)
					.toList();

			log.info(String.format("XML read done in %d ms | items=%d",
					System.currentTimeMillis() - start, temp.size()));
			return temp;
		}
	}

	private static Element buildXmlElement(Document doc, String elementName, ModItem item) {
		Element el = doc.createElement(elementName);
		for (var attr : item.getAttributes()) {
			el.setAttribute(attr.getName(), serializeValue(attr));
		}
		return el;
	}

	@SuppressWarnings("unchecked")
	private static String serializeValue(IAttribute attr) {
		Object v = attr.getValue();
		if (v == null) return "";
		if ("buff_params".equals(attr.getName()) && v instanceof List<?> list) {
			return BuffParam.listToString((List<BuffParam>) list);
		}
		return switch (v) {
			case Enum<?> e -> String.valueOf(e.ordinal());
			case Boolean b -> b.toString().toLowerCase(Locale.ROOT);
			case Double d -> d == Math.floor(d) && !Double.isInfinite(d) ? String.valueOf(d.longValue()) : d.toString();
			default -> v.toString();
		};
	}

	static Document parseXml(InputStream is) throws Exception {
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
}