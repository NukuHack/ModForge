package modforge.backend.service;

import modforge.Singleton;
import modforge.backend.AttributeFactory;
import modforge.backend.DataPointFactory;
import modforge.backend.ModData;
import modforge.backend.model.*;
import modforge.backend.model.item.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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

public final class ItemService {
	private static final Logger log = Logger.getLogger(ItemService.class.getName());
	private static final Set<String> BLACKLIST = Set.of("44c61b30-c20e-4267-ab01-a1e39342731e");

	private final UserService configService;
	private final ModItemBuilder builder;

	public ItemService(UserService configService, ModItemBuilder builder) {
		this.configService = configService;
		this.builder = builder;
	}

	// ==================================================================
	// READ OPERATIONS
	// ==================================================================

	/**
	 * Try to read all XML pak files.
	 * Returns false if the game directory is not configured.
	 */
	public boolean tryReadXmlFiles() {
		String dir = configService.getCurrent().gameDirectory;
		if (dir == null || dir.isBlank()) {
			log.warning("Game directory is not configured.");
			return false;
		}
		readAll(dir);
		return true;
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

	public List<IModItem> readModItems(List<IDataPoint> dp) {
		return dp.stream().map(this::readModItem).flatMap(Collection::stream).toList();
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

	public void writeModItems(ModData modData) {
		if (modData.items.isEmpty()) return;
		final var gameDir = configService.getCurrent().gameDirectory;
		modData.items.forEach(item -> writeModItem(gameDir, modData.id, item));
	}

	private void writeModItem(String gameDir, String modId, IModItem item) {
		try {
			String typeName = item.getClass().getSimpleName().toLowerCase(Locale.ROOT);
			String itemPath = item.getPath() == null ? "" : item.getPath();
			int lastSlash = itemPath.lastIndexOf('/');
			String dirSuffix = lastSlash >= 0 ? itemPath.substring(0, lastSlash) : "";

			String targetDir = PathFactory.join(gameDir, "Mods", modId, "Data", dirSuffix);
			String targetFile = PathFactory.join(targetDir, typeName + "__" + modId + ".xml");

			Files.createDirectories(Path.of(targetDir));

			var factory = DocumentBuilderFactory.newInstance();
			var docBuilder = factory.newDocumentBuilder();

			Document doc;
			Element group;
			String groupName = typeName + "s";
			String elementName = typeName;
			File outFile = new File(targetFile);

			if (outFile.exists()) {
				doc = docBuilder.parse(outFile);
				group = (Element) doc.getElementsByTagName(groupName).item(0);
				if (group == null) {
					group = doc.createElement(groupName);
					group.setAttribute("version", "1");
					doc.getDocumentElement().appendChild(group);
				}

				Element newEl = buildXmlElement(doc, elementName, item);
				String idKey = item.getIdKey();
				String idVal = newEl.getAttribute(idKey);

				if (idKey != null && !idKey.isBlank() && !idVal.isBlank()) {
					NodeList existing = group.getElementsByTagName(elementName);
					boolean replaced = false;
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
				group.appendChild(buildXmlElement(doc, elementName, item));
			}

			var tf = TransformerFactory.newInstance().newTransformer();
			tf.setOutputProperty(OutputKeys.ENCODING, "US-ASCII");
			tf.setOutputProperty(OutputKeys.INDENT, "yes");
			tf.setOutputProperty(OutputKeys.STANDALONE, "no");
			tf.transform(new DOMSource(doc), new StreamResult(outFile));

		} catch (Exception e) {
			log.severe("writeModItem failed for " + item.getClass().getSimpleName()
					+ ": " + e.getMessage());
		}
	}

	// ==================================================================
	// PRIVATE HELPERS
	// ==================================================================

	private void readAll(String gameDir) {
		long start = System.currentTimeMillis();

		final var allPoints = new ArrayList<IDataPoint>();
		ItemType.endpoints().forEach((type, eps) ->
				eps.forEach((key, pak) ->
						allPoints.add(DataPointFactory.create(gameDir + "/" + pak, key, type))));
		final var items = Singleton.INSTANCE.game().items;
		items.addAll(readModItems(allPoints));

		log.info(String.format("XML read done in %d ms | items=%d",
				System.currentTimeMillis() - start, items.size()));
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
		if (v instanceof Enum<?> e) {
			return String.valueOf(e.ordinal());
		}
		if (v instanceof Boolean b) {
			return b.toString().toLowerCase(Locale.ROOT);
		}
		if (v instanceof Double d) {
			return d == Math.floor(d) && !Double.isInfinite(d) ? String.valueOf(d.longValue()) : d.toString();
		}
		return v.toString();
	}

	private static Document parseXml(InputStream is) throws Exception {
		var f = DocumentBuilderFactory.newInstance();
		f.setNamespaceAware(true);
		return f.newDocumentBuilder().parse(is);
	}
}