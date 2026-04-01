package modforge.backend.service;

import modforge.backend.AttributeFactory;
import modforge.backend.model.*;
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
import java.util.zip.ZipFile;

public final class XmlAdapter implements IModItemAdapter {
	private static final Logger log = Logger.getLogger(XmlAdapter.class.getName());
	private static final Set<String> BLACKLIST = Set.of("44c61b30-c20e-4267-ab01-a1e39342731e");

	private final UserConfigurationService configService;
	private final ModItemBuilder builder;

	public XmlAdapter(UserConfigurationService configService, ModItemBuilder builder) {
		this.configService = configService;
		this.builder = builder;
	}

	// ------------------------------------------------------------------
	// READ
	// ------------------------------------------------------------------

	@Override
	public List<IModItem> readModItems(IDataPoint dp) {
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
					org.w3c.dom.Document doc = parseXml(is);
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

	// ------------------------------------------------------------------
	// WRITE
	// ------------------------------------------------------------------

	@Override
	public void writeModItems(String modId, Iterable<IModItem> items) {
		items.forEach(item -> writeModItem(modId, item));
	}

	private void writeModItem(String modId, IModItem item) {
		try {
			String typeName = item.getClass().getSimpleName().toLowerCase(Locale.ROOT);
			String gameDir = configService.getCurrent().gameDirectory;
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
				// Create brand-new file (mirrors C# new XDocument(...) block)
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

	/**
	 * Build a DOM Element from an IModItem's attribute list.
	 */
	private static Element buildXmlElement(Document doc,
										   String elementName, IModItem item) {
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
			// Prefer "5" over "5.0" for whole numbers
			return d == Math.floor(d) && !Double.isInfinite(d)
					? String.valueOf(d.longValue()) : d.toString();
		}
		return v.toString();
	}

	private static Document parseXml(InputStream is) throws Exception {
		var f = DocumentBuilderFactory.newInstance();
		f.setNamespaceAware(true);
		return f.newDocumentBuilder().parse(is);
	}
}
