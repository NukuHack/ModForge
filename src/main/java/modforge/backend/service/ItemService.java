package modforge.backend.service;

import modforge.*;
import modforge.backend.*;
import modforge.backend.model.*;
import modforge.backend.model.item.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
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
		final String gameDir = configService.getCurrent().gameDirectory;
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
	public Optional<IModItem> getModItem(String id) {
		final var items = Singleton.INSTANCE.game().items;
		return Stream.of(items)
				.flatMap(Collection::stream)
				.filter(x -> id.equals(x.getId()))
				.findFirst();
	}

	public List<IModItem> readModItem(IDataPoint dp) {
		var result = new ArrayList<IModItem>();
		String typeName = dp.type().getSimpleName().toLowerCase(Locale.ROOT);
		File pakFile = new File(dp.path());

		if (!pakFile.exists()) {
			log.warning("Pak file not found: " + dp.path());
			return result;
		}

		try (var zf = new ZipFile(pakFile)) {
			var it = zf.entries();
			while (it.hasMoreElements()) {
				var entry = it.nextElement();
				String ename = entry.getName().replace('\\', '/');

				if (ename.endsWith(".tbl")) continue;
				if (!ename.contains(dp.endpoint())) continue;

				try (var is = zf.getInputStream(entry)) {
					Document doc = parseXml(is);
					AttributeFactory.discoverTypes(doc.getDocumentElement());

					var nodes = doc.getElementsByTagName("*");
					for (int i = 0; i < nodes.getLength(); i++) {
						if (!(nodes.item(i) instanceof Element el)) continue;
						if (!el.getLocalName().equalsIgnoreCase(typeName)) continue;

						IModItem item = builder.build(el);
						if (item == null) {
							log.warning("Builder returned null for <" + el.getLocalName() + ">");
							continue;
						}
						if (item.getId() == null || BLACKLIST.contains(item.getId())) continue;
						item.setPath(ename);
						result.add(item);
					}
				} catch (Exception ex) {
					log.warning("Parse error in " + ename + ": " + ex.getMessage());
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
		final String gameDir = configService.getCurrent().gameDirectory;
		final Set<String> pakNames = new LinkedHashSet<>();
		for (IModItem item : modData.items) {
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
	private String writeModItem(String gameDir, String modId, IModItem item) {
		try {
			String typeName = item.getClass().getSimpleName().toLowerCase(Locale.ROOT);
			String rawPath  = item.getPath() == null ? "" : item.getPath();

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

				Element newEl  = buildXmlElement(doc, typeName, item);
				String  idKey  = item.getIdKey();
				String  idVal  = newEl.getAttribute(idKey);

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
				root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xsi",
						"http://www.w3.org/2001/XMLSchema-instance");
				root.setAttribute("name", "barbora");
				root.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance",
						"xsi:noNamespaceSchemaLocation", "../database.xsd");
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
			log.severe("writeModItem failed for " + item.getClass().getSimpleName()
					+ ": " + e.getMessage());
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
	private List<IModItem> readModItemsFromXml(String modFolder) {
		var result = new ArrayList<IModItem>();
		Path modPath = Path.of(modFolder);

		if (!Files.exists(modPath)) {
			return result;
		}

		try (var walk = Files.walk(modPath)) {
			walk.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".xml"))
					.forEach(xmlFile -> {
						try {
							// Parse the XML file
							Document doc = parseXml(Files.newInputStream(xmlFile));
							Element root = doc.getDocumentElement();

							// Check each child element to find the actual table type
							NodeList children = root.getChildNodes();
							for (int i = 0; i < children.getLength(); i++) {
								var node = children.item(i);
								if (node.getNodeType() != Node.ELEMENT_NODE) continue;
								Element tableElement = (Element) node;
								String tableName = tableElement.getLocalName();

								// Determine the item type from the table name
								Class<? extends IModItem> itemClass = determineItemClassFromTableName(tableName);
								if (itemClass == null) continue;
								// Parse all items in this table
								NodeList items = tableElement.getElementsByTagName("*");
								for (int j = 0; j < items.getLength(); j++) {
									if (items.item(j) instanceof Element itemElement) {
										// Skip the table wrapper element itself
										if (itemElement == tableElement) continue;

										AttributeFactory.discoverTypes(itemElement);
										IModItem item = builder.build(itemElement);
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

	// Add this helper method to determine item class from table name
	private Class<? extends IModItem> determineItemClassFromTableName(String tableName) {
		return switch (tableName.toLowerCase(Locale.ROOT)) {
			case "perks", "perk" -> Perk.class;
			case "buffs", "buff" -> Buff.class;
			case "weapons", "weapon" -> MeleeWeapon.class;
			case "armors", "armor" -> Armor.class;
			case "helmets", "helmet" -> Helmet.class;
			case "hoods", "hood" -> Hood.class;
			case "foods", "food" -> Food.class;
			case "poisons", "poison" -> Poison.class;
			case "herbs", "herb" -> Herb.class;
			case "craftingmaterials", "crafting_materials", "craftingmaterial", "crafting_material" -> CraftingMaterial.class;
			case "misctems", "miscitem", "misc" -> MiscItem.class;
			case "keys", "key" -> Key.class;
			case "money" -> Money.class;
			case "keyrings", "keyring" -> KeyRing.class;
			default -> null;
		};
	}

	// Modify the existing readAllItemFromXml method
	public List<IModItem> readAllItemFromXml(String gameDir, boolean isMod) {
		long start = System.currentTimeMillis();

		if (isMod) {
			// For mods, use the new method that scans all XML files
			var result = readModItemsFromXml(gameDir);
			log.info(String.format("XML MOD !!!!!! read done in %d ms | items=%d", System.currentTimeMillis() - start, result.size()));
			return result;
		} else {
			// For game data, use the original method with known endpoints
			final var allPoints = new ArrayList<IDataPoint>(30);

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

	private static Element buildXmlElement(Document doc, String elementName, IModItem item) {
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
		return f.newDocumentBuilder().parse(is);
	}
}