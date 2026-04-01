package modforge;

import backend.*;
import backend.api.*;
import backend.model.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.w3c.dom.*;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;
import java.util.stream.*;
import java.util.zip.*;

// =============================================================================
// INTERFACES
// =============================================================================

// =============================================================================
// ENUMS & VALUE TYPES
// =============================================================================

// =============================================================================
// ATTRIBUTE
// =============================================================================

// =============================================================================
// DATA POINT
// =============================================================================

public final class DataPoint implements IDataPoint {
	private final String path;
	private final String endpoint;
	private final Class<?> type;

	public DataPoint(String path, String endpoint, Class<?> type) {
		this.path = Objects.requireNonNull(path, "path");
		this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
		this.type = Objects.requireNonNull(type, "type");
	}

	@Override
	public String getPath() {
		return path;
	}

	@Override
	public String getEndpoint() {
		return endpoint;
	}

	@Override
	public Class<?> getType() {
		return type;
	}

	@Override
	public String toString() {
		return "DataPoint{type=" + type.getSimpleName() + ", endpoint=" + endpoint + ", path=" + path + "}";
	}
}

// =============================================================================
// BASE MOD ITEM - all concrete types extend this
// =============================================================================

// =============================================================================
// CONCRETE MOD ITEM TYPES
// =============================================================================

// =============================================================================
// MOD DESCRIPTION & COLLECTION
// =============================================================================

final class ModDescription {
	public String id = "";
	public String name = "";
	public String description = "";
	public String author = "";
	public String modVersion = "";
	public String createdOn = "";
	public boolean modifiesLevel = false;
	public List<String> supportsGameVersions = new ArrayList<>();
	public List<IModItem> modItems = new ArrayList<>();
	public String imagePath;
	/**
	 * Storm rules – extend as needed when Storm logic is ported.
	 */
	public List<Object> stormRules = new ArrayList<>();

	@Override
	public String toString() {
		return "Mod[" + id + "]";
	}
}

final class ModCollection extends ArrayList<ModDescription> {
	public void addMod(ModDescription m) {
		add(m);
	}

	public void removeMod(ModDescription m) {
		remove(m);
	}

	public ModDescription getMod(String id) {
		return stream().filter(m -> id.equals(m.id)).findFirst().orElse(null);
	}
}

// =============================================================================
// USER CONFIGURATION
// =============================================================================

final class UserConfiguration {
	public String gameDirectory = "";
	public String userName = "";
	public String language = "en";
}

// =============================================================================
// FACTORIES
// =============================================================================

// ---------------------------------------------------------------------------

final class PathFactory {
	private PathFactory() {
	}

	// Language pack prefixes in the same order as the C# Language enum
	private static final List<String> LANGUAGES = List.of(
			"English", "German", "French", "Spanish", "Italian",
			"Polish", "Russian", "Czech", "Hungarian", "Slovak",
			"Portuguese", "Chinese", "Japanese", "Korean"
	);

	public static String tables(String root) {
		return join(root, "Data", "Tables.pak");
	}

	public static String scripts(String root) {
		return join(root, "Data", "Scripts.pak");
	}

	public static String gameData(String root) {
		return join(root, "Data", "IPL_GameData.pak");
	}

	public static String locImport(String root, String lang) {
		return join(root, "Localization", lang + "_xml.pak");
	}

	public static String locExport(String root, String lang, String modId) {
		return join(root, "Mods", modId, "Localization", lang + "_xml", "text__" + modId + ".xml");
	}

	public static String modFolder(String root, String modId) {
		return join(root, "Mods", modId);
	}

	public static String modData(String root, String modId) {
		return join(root, "Mods", modId, "Data");
	}

	public static String stormDir(String root, String modId) {
		return join(root, "Mods", modId, "Data", "Libs", "Storm");
	}

	public static List<String> allLocPaths(String root) {
		return LANGUAGES.stream().map(l -> locImport(root, l)).collect(Collectors.toList());
	}

	/**
	 * Join path segments using forward slashes (cross-platform safe).
	 */
	public static String join(String... parts) {
		return String.join("/", parts);
	}
}

// ---------------------------------------------------------------------------

final class ToolResources {
	private ToolResources() {
	}

	public static final List<Class<? extends IModItem>> WEAPON_CLASSES =
			List.of(MeleeWeaponClass.class, MissileWeaponClass.class);

	public static final List<Class<? extends IModItem>> WEAPON_TYPES =
			List.of(MeleeWeapon.class, MissileWeapon.class, Ammo.class);

	public static final List<Class<? extends IModItem>> ARMOR_TYPES =
			List.of(Hood.class, Armor.class, Helmet.class);

	public static final List<Class<? extends IModItem>> CONSUMABLE_TYPES =
			List.of(Food.class, Poison.class);

	public static final List<Class<? extends IModItem>> CRAFTING_TYPES =
			List.of(Herb.class, CraftingMaterial.class);

	public static final List<Class<? extends IModItem>> MISC_TYPES =
			List.of(NPCTool.class, MiscItem.class, GameDocument.class, Die.class,
					ItemAlias.class, QuickSlotContainer.class, DiceBadge.class,
					PickableItem.class, Key.class, Money.class, KeyRing.class);

	private static final String TABLES = "Data/Tables.pak";
	private static final String GAMEDATA = "Data/IPL_GameData.pak";

	private static final List<String> ITEM_KEYS = List.of(
			"item", "item__alchemy", "item__aux", "item__deprecated",
			"item__dlc", "item__horse", "item__rewards", "item__system", "item__unique"
	);

	/**
	 * Returns a map of  ItemType -> (endpointKey -> pak-relative-path).
	 * Mirrors C# ToolResources.Endpoints().
	 */
	public static Map<Class<?>, Map<String, String>> endpoints() {
		var map = new LinkedHashMap<Class<?>, Map<String, String>>();

		map.put(Perk.class, orderedMapOf(
				"perk__combat", TABLES,
				"perk__hardcore", TABLES,
				"perk__kcd2", TABLES,
				"perk__dlc2", TABLES
		));

		map.put(Buff.class, orderedMapOf(
				"buff", TABLES,
				"buff__alchemy", TABLES,
				"buff__perk", TABLES,
				"buff__perk_hardcore", TABLES,
				"buff__perk_kcd1", TABLES,
				"buff__dlc", TABLES,
				"buff__dlc2_beds", TABLES,
				"buff__dlc2_others", TABLES,
				"buff__dlc2_tables", TABLES
		));

		map.put(Storm.class, orderedMapOf("storm.xml", GAMEDATA));

		for (var t : WEAPON_CLASSES) map.put(t, orderedMapOf("weapon_class", TABLES));

		var itemEps = itemEndpoints();
		for (var t : WEAPON_TYPES) map.put(t, itemEps);
		for (var t : ARMOR_TYPES) map.put(t, itemEps);
		for (var t : CONSUMABLE_TYPES) map.put(t, itemEps);
		for (var t : CRAFTING_TYPES) map.put(t, itemEps);
		for (var t : MISC_TYPES) map.put(t, itemEps);

		return Collections.unmodifiableMap(map);
	}

	private static Map<String, String> itemEndpoints() {
		var m = new LinkedHashMap<String, String>();
		ITEM_KEYS.forEach(k -> m.put(k, TABLES));
		return Collections.unmodifiableMap(m);
	}

	/**
	 * Build a String->String LinkedHashMap from alternating key/value varargs.
	 */
	private static Map<String, String> orderedMapOf(String... kvPairs) {
		if (kvPairs.length % 2 != 0) throw new IllegalArgumentException("Need even number of args");
		var m = new LinkedHashMap<String, String>();
		for (int i = 0; i < kvPairs.length; i += 2) m.put(kvPairs[i], kvPairs[i + 1]);
		return Collections.unmodifiableMap(m);
	}
}

// =============================================================================
// ATTRIBUTE FACTORY
// =============================================================================

// =============================================================================
// MOD ITEM FACTORY
// =============================================================================

final class ModItemFactory {
	private ModItemFactory() {
	}

	/**
	 * Instantiate a mod item of the given type from an XML element.
	 */
	public static IModItem create(Element element, Class<? extends IModItem> type, String path) {
		try {
			var item = type.getDeclaredConstructor().newInstance();
			item.setPath(path);

			var attrs = element.getAttributes();
			var list = new ArrayList<IAttribute>(attrs.getLength());
			for (int i = 0; i < attrs.getLength(); i++) {
				var a = (org.w3c.dom.Attr) attrs.item(i);
				list.add(AttributeFactory.create(a.getLocalName(), a.getValue()));
			}
			item.setAttributes(list);
			return item;
		} catch (Exception e) {
			throw new RuntimeException("Cannot instantiate " + type.getSimpleName(), e);
		}
	}

	/**
	 * Deep-copy a mod item, optionally changing its path.
	 */
	public static IModItem deepCopy(IModItem src, String newPath) {
		try {
			var copy = src.getClass().getDeclaredConstructor().newInstance();
			copy.setId(src.getId());
			copy.setIdKey(src.getIdKey());
			copy.setPath(newPath);
			var cloned = src.getAttributes().stream()
					.map(IAttribute::deepClone)
					.collect(Collectors.toCollection(ArrayList::new));
			copy.setAttributes(cloned);
			return copy;
		} catch (Exception e) {
			throw new RuntimeException("Deep copy failed for " + src.getClass().getSimpleName(), e);
		}
	}
}

// =============================================================================
// BUILDER
// =============================================================================

/**
 * Generic build handler: recognises elements whose local name matches the
 * simple class name (case-insensitive) and populates a configurable ID attribute.
 */
final class GenericBuildHandler<M extends BaseModItem> implements IBuildHandler {
	private final Class<M> type;
	private final String idAttrKey;

	GenericBuildHandler(Class<M> type, String idAttrKey) {
		this.type = type;
		this.idAttrKey = idAttrKey;
	}

	@Override
	public boolean isResponsible(Element el) {
		return el.getLocalName().equalsIgnoreCase(type.getSimpleName());
	}

	@Override
	public IModItem handle(Element el) {
		try {
			M item = type.getDeclaredConstructor().newInstance();
			item.setIdKey(idAttrKey);

			String idValue = el.getAttribute(idAttrKey);
			item.setId(idValue == null || idValue.isBlank() ? null : idValue);

			var xmlAttrs = el.getAttributes();
			var list = new ArrayList<IAttribute>(xmlAttrs.getLength());
			for (int i = 0; i < xmlAttrs.getLength(); i++) {
				var a = (org.w3c.dom.Attr) xmlAttrs.item(i);
				list.add(AttributeFactory.create(a.getLocalName(), a.getValue()));
			}
			item.setAttributes(list);
			return item;
		} catch (Exception e) {
			Logger.getLogger(GenericBuildHandler.class.getName())
					.warning("Handler failed for " + type.getSimpleName() + ": " + e.getMessage());
			return null;
		}
	}
}

final class ModItemBuilder {
	private static final Logger log = Logger.getLogger(ModItemBuilder.class.getName());
	private final List<IBuildHandler> handlers;

	public ModItemBuilder(List<IBuildHandler> handlers) {
		this.handlers = List.copyOf(handlers);
	}

	public IModItem build(Element element) {
		for (var h : handlers) {
			if (h.isResponsible(element)) return h.handle(element);
		}
		log.fine("No handler matched element <" + element.getLocalName() + ">");
		return null;
	}

	/**
	 * Creates the default handler list (mirrors C# ServiceConfiguration).
	 */
	public static ModItemBuilder createDefault() {
		return new ModItemBuilder(List.of(
				new GenericBuildHandler<>(Perk.class, "perk_id"),
				new GenericBuildHandler<>(Buff.class, "buff_id"),
				new GenericBuildHandler<>(MeleeWeapon.class, "Id"),
				new GenericBuildHandler<>(MissileWeapon.class, "Id"),
				new GenericBuildHandler<>(Ammo.class, "Id"),
				new GenericBuildHandler<>(MeleeWeaponClass.class, "id"),
				new GenericBuildHandler<>(MissileWeaponClass.class, "id"),
				new GenericBuildHandler<>(Hood.class, "Id"),
				new GenericBuildHandler<>(Armor.class, "Id"),
				new GenericBuildHandler<>(Helmet.class, "Id"),
				new GenericBuildHandler<>(Food.class, "Id"),
				new GenericBuildHandler<>(Poison.class, "Id"),
				new GenericBuildHandler<>(Herb.class, "Id"),
				new GenericBuildHandler<>(CraftingMaterial.class, "Id"),
				new GenericBuildHandler<>(NPCTool.class, "Id"),
				new GenericBuildHandler<>(MiscItem.class, "Id"),
				new GenericBuildHandler<>(GameDocument.class, "Id"),
				new GenericBuildHandler<>(Die.class, "Id"),
				new GenericBuildHandler<>(ItemAlias.class, "Id"),
				new GenericBuildHandler<>(QuickSlotContainer.class, "Id"),
				new GenericBuildHandler<>(DiceBadge.class, "Id"),
				new GenericBuildHandler<>(PickableItem.class, "Id"),
				new GenericBuildHandler<>(Key.class, "Id"),
				new GenericBuildHandler<>(Money.class, "Id"),
				new GenericBuildHandler<>(KeyRing.class, "Id")
		));
	}
}

// =============================================================================
// PAK READER  (mirrors C# PakReader)
// =============================================================================

final class PakReader implements Closeable {
	private static final Logger log = Logger.getLogger(PakReader.class.getName());

	private final ZipFile zip;
	/**
	 * Normalised-key (lowercase forward-slash) -> entry, for O(1) lookup.
	 */
	private final Map<String, ZipEntry> entries = new HashMap<>();

	public PakReader(String pakPath) throws IOException {
		this.zip = new ZipFile(new File(pakPath));
		var it = zip.entries();
		while (it.hasMoreElements()) {
			var e = it.nextElement();
			entries.put(normalise(e.getName()), e);
		}
	}

	private static String normalise(String s) {
		return s.replace('\\', '/').toLowerCase(Locale.ROOT);
	}

	/**
	 * Read a text file from the archive.
	 * Tries exact match first, then a suffix match (relative-path fallback).
	 */
	public String readFile(String nameOrPath) throws IOException {
		String norm = normalise(nameOrPath);
		ZipEntry entry = entries.get(norm);

		if (entry == null) {
			entry = entries.values().stream()
					.filter(e -> normalise(e.getName()).endsWith(norm))
					.findFirst().orElse(null);
		}

		if (entry == null) {
			log.warning("[PakReader] Not found in pak: " + nameOrPath);
			return null;
		}

		try (var is = zip.getInputStream(entry)) {
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/**
	 * Returns the raw XML string for a storm virtual path, or null.
	 */
	public String readStormXml(String virtualPath) throws IOException {
		return readFile(virtualPath);
	}

	public Set<String> entryNames() {
		return Collections.unmodifiableSet(entries.keySet());
	}

	@Override
	public void close() throws IOException {
		zip.close();
	}
}

// =============================================================================
// DDS CONVERTER  (stub - requires a third-party DDS decoder)
// =============================================================================

// =============================================================================
// XML ADAPTER  (mirrors C# XmlAdapter)
// =============================================================================

final class XmlAdapter implements IModItemAdapter {
	private static final Logger log = Logger.getLogger(XmlAdapter.class.getName());
	private static final Set<String> BLACKLIST =
			Set.of("44c61b30-c20e-4267-ab01-a1e39342731e");

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
		String typeName = dp.getType().getSimpleName().toLowerCase(Locale.ROOT);
		File pakFile = new File(dp.getPath());

		if (!pakFile.exists()) {
			log.warning("Pak file not found: " + dp.getPath());
			return result;
		}

		try (var zf = new ZipFile(pakFile)) {
			var it = zf.entries();
			while (it.hasMoreElements()) {
				var entry = it.nextElement();
				String ename = entry.getName().replace('\\', '/');

				if (ename.endsWith(".tbl")) continue;
				if (!ename.contains(dp.getEndpoint())) continue;

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
			log.severe("Cannot open pak: " + dp.getPath() + " - " + e.getMessage());
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
	private static Element buildXmlElement(org.w3c.dom.Document doc,
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

	private static org.w3c.dom.Document parseXml(InputStream is) throws Exception {
		var f = DocumentBuilderFactory.newInstance();
		f.setNamespaceAware(true);
		return f.newDocumentBuilder().parse(is);
	}
}

// =============================================================================
// LOCALIZATION ADAPTER  (mirrors C# LocalizationAdapter)
// =============================================================================

final class LocalizationAdapter {
	private static final Logger log = Logger.getLogger(LocalizationAdapter.class.getName());

	private static final Set<String> RELEVANT_FILES = Set.of(
			"text_ui_soul.xml", "text_ui_tutorials.xml",
			"text_ui_quest.xml", "text_ui_misc.xml",
			"text_ui_minigames.xml", "text_ui_menus.xml",
			"text_ui_items.xml", "text_ui_ingame.xml"
	);

	/**
	 * Maps pak file prefix -> ISO language code used inside the app.
	 */
	private static final Map<String, String> LANG_MAP;

	static {
		var m = new LinkedHashMap<String, String>();
		m.put("English", "en");
		m.put("German", "de");
		m.put("French", "fr");
		m.put("Spanish", "es");
		m.put("Italian", "it");
		m.put("Polish", "pl");
		m.put("Russian", "ru");
		m.put("Czech", "cs");
		m.put("Hungarian", "hu");
		m.put("Slovak", "sk");
		m.put("Portuguese", "pt");
		m.put("Chinese", "zh");
		m.put("Japanese", "ja");
		m.put("Korean", "ko");
		LANG_MAP = Collections.unmodifiableMap(m);
	}

	/**
	 * Read all localisation paks from the game directory.
	 * Returns:  language-code -> (string-key -> localised-value)
	 */
	public Map<String, Map<String, String>> readLocalizationFromXml(String gameDirectory) {
		var result = new HashMap<String, Map<String, String>>();

		for (String pakPath : PathFactory.allLocPaths(gameDirectory)) {
			File f = new File(pakPath);
			if (!f.exists()) continue;

			// e.g. "German_xml.pak" -> strip "_xml.pak" -> "German"
			String baseName = f.getName().replace("_xml.pak", "");
			String langCode = LANG_MAP.entrySet().stream()
					.filter(e -> baseName.startsWith(e.getKey()))
					.map(Map.Entry::getValue)
					.findFirst().orElse(null);
			if (langCode == null) continue;

			try (var zf = new ZipFile(f)) {
				var it = zf.entries();
				while (it.hasMoreElements()) {
					var entry = it.nextElement();
					String name = new File(entry.getName()).getName()
							.toLowerCase(Locale.ROOT);
					if (!RELEVANT_FILES.contains(name)) continue;

					try (var is = zf.getInputStream(entry)) {
						var parsed = parseLocalizationXml(is);
						result.computeIfAbsent(langCode, k -> new HashMap<>())
								.putAll(parsed);
					}
				}
			} catch (Exception ex) {
				log.warning("Localisation read error (" + pakPath + "): " + ex.getMessage());
			}
		}
		return result;
	}

	/**
	 * Parse a single localisation XML stream.
	 * Expected format:
	 * <pre>
	 *   &lt;Table&gt;
	 *     &lt;Row&gt;
	 *       &lt;Cell&gt;KEY&lt;/Cell&gt;
	 *       &lt;Cell/&gt;
	 *       &lt;Cell&gt;VALUE&lt;/Cell&gt;
	 *     &lt;/Row&gt;
	 *   &lt;/Table&gt;
	 * </pre>
	 */
	public Map<String, String> parseLocalizationXml(InputStream is) throws Exception {
		var result = new LinkedHashMap<String, String>();
		var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
		var rows = doc.getElementsByTagName("Row");
		for (int i = 0; i < rows.getLength(); i++) {
			var cells = ((Element) rows.item(i)).getElementsByTagName("Cell");
			if (cells.getLength() < 3) continue;
			String key = cells.item(0).getTextContent().trim();
			String value = cells.item(2).getTextContent().trim();
			if (!key.isBlank()) result.put(key, value);
		}
		return result;
	}

	/**
	 * Write localisation for a mod (stub – extend when export is needed).
	 */
	public void writeLocalizationAsXml(String gameDirectory, ModDescription mod) {
		log.info("writeLocalizationAsXml: not yet implemented.");
	}
}

// =============================================================================
// JSON ADAPTER  (mirrors C# JsonAdapter)
// =============================================================================

final class JsonAdapter {
	private static final Logger log = Logger.getLogger(JsonAdapter.class.getName());

	private final Path baseDir;
	private final ObjectMapper mapper;

	public JsonAdapter(String appDataDir) {
		this.baseDir = Path.of(appDataDir, "ModForge");
		this.mapper = new ObjectMapper()
				.enable(SerializationFeature.INDENT_OUTPUT)
				.activateDefaultTyping(
						BasicPolymorphicTypeValidator.builder()
								.allowIfBaseType(Object.class)
								.build(),
						ObjectMapper.DefaultTyping.NON_FINAL
				);
	}

	public List<IModItem> readFromJson(String filePath) {
		File f = new File(filePath);
		if (!f.exists()) return new ArrayList<>();
		try {
			return mapper.readValue(f,
					mapper.getTypeFactory()
							.constructCollectionType(List.class, IModItem.class));
		} catch (IOException e) {
			log.warning("JSON read failed (" + filePath + "): " + e.getMessage());
			return new ArrayList<>();
		}
	}

	public void writeAsJson(List<IModItem> items) {
		if (items == null || items.isEmpty()) return;
		String filename = items.get(0).getClass().getSimpleName()
				.toLowerCase(Locale.ROOT) + "s.json";
		Path target = baseDir.resolve(filename);
		try {
			Files.createDirectories(target.getParent());
			mapper.writeValue(target.toFile(), items);
		} catch (IOException e) {
			log.warning("JSON write failed: " + e.getMessage());
		}
	}
}

// =============================================================================
// USER CONFIGURATION SERVICE  (mirrors C# UserConfigurationService)
// =============================================================================

final class UserConfigurationService {
	private static final Logger log =
			Logger.getLogger(UserConfigurationService.class.getName());

	private final Path configFile;
	private final ObjectMapper mapper =
			new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	private UserConfiguration current;

	public UserConfigurationService() {
		// Mirror C# Environment.SpecialFolder.ApplicationData
		String appData = System.getenv("APPDATA");
		if (appData == null || appData.isBlank()) {
			appData = System.getProperty("user.home") + "/AppData/Roaming";
		}
		this.configFile = Path.of(appData, "ModForge", "userconfig.json");
		load();
	}

	public UserConfiguration getCurrent() {
		return current;
	}

	public void setCurrent(UserConfiguration c) {
		this.current = c;
	}

	private void load() {
		try {
			if (Files.exists(configFile)) {
				current = mapper.readValue(configFile.toFile(), UserConfiguration.class);
				log.info("User configuration loaded from " + configFile);
			} else {
				current = new UserConfiguration();
				log.info("No config file found - using defaults.");
			}
		} catch (Exception e) {
			log.severe("Config load error: " + e.getMessage());
			current = new UserConfiguration();
		}
	}

	public void save() {
		try {
			Files.createDirectories(configFile.getParent());
			mapper.writeValue(configFile.toFile(), current);
			log.info("User configuration saved.");
		} catch (IOException e) {
			log.severe("Config save error: " + e.getMessage());
		}
	}

	/**
	 * Write mod-load order file (mirrors C# WriteLoadout).
	 */
	public void writeLoadout(List<ModDescription> orderedMods) {
		String dir = current.gameDirectory;
		if (dir == null || dir.isBlank()) return;
		Path loadOrder = Path.of(dir, "Mods", "mod_order.txt");
		try {
			Files.deleteIfExists(loadOrder);
			var ids = orderedMods.stream()
					.map(m -> m.id)
					.collect(Collectors.toList());
			Files.write(loadOrder, ids, StandardCharsets.UTF_8);
		} catch (IOException e) {
			log.warning("Cannot write mod_order.txt: " + e.getMessage());
		}
	}
}

// =============================================================================
// LOCALIZATION SERVICE  (mirrors C# LocalizationService)
// =============================================================================

final class LocalizationService {
	private static final Logger log =
			Logger.getLogger(LocalizationService.class.getName());

	private final LocalizationAdapter adapter;
	private final UserConfigurationService configService;
	/**
	 * lang-code -> (string-key -> localised-value)
	 */
	private Map<String, Map<String, String>> localizations = new HashMap<>();

	public LocalizationService(LocalizationAdapter adapter,
							   UserConfigurationService configService) {
		this.adapter = adapter;
		this.configService = configService;
		init();
	}

	/**
	 * (Re-)load from disk. Call after the user sets a new game directory.
	 */
	public void init() {
		String dir = configService.getCurrent().gameDirectory;
		if (dir != null && !dir.isBlank()) {
			localizations = adapter.readLocalizationFromXml(dir);
		}
	}

	public String getName(IModItem item) {
		return resolve(item, "ui_name", "UIName", "name");
	}

	public String getDescription(IModItem item) {
		return resolve(item, "ui_desc", "UIInfo");
	}

	public String getLoreDescription(IModItem item) {
		return resolve(item, "ui_lore_desc");
	}

	/**
	 * Delegate read to adapter (used by XmlService).
	 */
	public Map<String, Map<String, String>> readLocalizationFromXml(String path) {
		if (path == null || path.isBlank()) return new HashMap<>();
		try {
			return adapter.readLocalizationFromXml(path);
		} catch (Exception ex) {
			log.severe("Localisation read failed: " + ex.getMessage());
			return new HashMap<>();
		}
	}

	public void writeLocalizationAsXml(String path, ModDescription mod) {
		adapter.writeLocalizationAsXml(path, mod);
	}

	// ------------------------------------------------------------------

	private String resolve(IModItem item, String... candidates) {
		String lang = configService.getCurrent().language;
		var langMap = localizations.get(lang);

		for (String candidate : candidates) {
			String clo = candidate.toLowerCase(Locale.ROOT);
			var attr = item.getAttributes().stream()
					.filter(a -> a.getName().toLowerCase(Locale.ROOT).contains(clo))
					.findFirst().orElse(null);
			if (attr == null) continue;

			String key = String.valueOf(attr.getValue());
			if (langMap != null && langMap.containsKey(key)) return langMap.get(key);
			// Fall back to the raw value stored in the attribute
			return key;
		}
		return null;
	}
}

// =============================================================================
// XML SERVICE  (mirrors C# XmlService)
// =============================================================================

final class XmlService {
	private static final Logger log = Logger.getLogger(XmlService.class.getName());

	private final IModItemAdapter adapter;
	private final LocalizationService localizationService;
	private final UserConfigurationService configService;

	public List<IModItem> perks = new ArrayList<>();
	public List<IModItem> buffs = new ArrayList<>();
	public List<IModItem> weapons = new ArrayList<>();
	public List<IModItem> armors = new ArrayList<>();
	public List<IModItem> consumables = new ArrayList<>();
	public List<IModItem> craftingMaterials = new ArrayList<>();
	public List<IModItem> miscItems = new ArrayList<>();
	public List<IModItem> weaponClasses = new ArrayList<>();

	public XmlService(IModItemAdapter adapter,
					  LocalizationService localizationService,
					  UserConfigurationService configService) {
		this.adapter = adapter;
		this.localizationService = localizationService;
		this.configService = configService;
	}

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
		return Stream.of(perks, buffs, weapons, armors,
						consumables, craftingMaterials, miscItems)
				.flatMap(Collection::stream)
				.filter(x -> id.equals(x.getId()))
				.findFirst();
	}

	// ------------------------------------------------------------------

	private void readAll(String gameDir) {
		long start = System.currentTimeMillis();

		var allPoints = new ArrayList<IDataPoint>();
		ToolResources.endpoints().forEach((type, eps) ->
				eps.forEach((key, pak) ->
						allPoints.add(DataPointFactory.create(
								gameDir + "/" + pak, key, type))));

		perks = readOf(allPoints, Perk.class);
		buffs = readOf(allPoints, Buff.class);

		ToolResources.WEAPON_CLASSES.forEach(t -> weaponClasses.addAll(readOf(allPoints, t)));
		ToolResources.WEAPON_TYPES.forEach(t -> weapons.addAll(readOf(allPoints, t)));
		ToolResources.ARMOR_TYPES.forEach(t -> armors.addAll(readOf(allPoints, t)));
		ToolResources.CONSUMABLE_TYPES.forEach(t -> consumables.addAll(readOf(allPoints, t)));
		ToolResources.CRAFTING_TYPES.forEach(t -> craftingMaterials.addAll(readOf(allPoints, t)));
		ToolResources.MISC_TYPES.forEach(t -> miscItems.addAll(readOf(allPoints, t)));

		log.info(String.format(
				"XML read done in %d ms | perks=%d buffs=%d weapons=%d armors=%d",
				System.currentTimeMillis() - start,
				perks.size(), buffs.size(), weapons.size(), armors.size()));
	}

	private List<IModItem> readOf(List<IDataPoint> all, Class<?> type) {
		return all.stream()
				.filter(dp -> dp.getType().equals(type))
				.flatMap(dp -> adapter.readModItems(dp).stream())
				.collect(Collectors.toCollection(ArrayList::new));
	}
}

// =============================================================================
// ICON SERVICE  (mirrors C# IconService)
// =============================================================================

final class IconService {
	private static final Logger log = Logger.getLogger(IconService.class.getName());

	private final UserConfigurationService configService;

	public IconService(UserConfigurationService configService) {
		this.configService = configService;
	}

	/**
	 * Return a base64 data-URI for the icon of the given mod item.
	 */
	public String getIcon(IModItem item) {
		if (item == null || item.getAttributes() == null) return null;

		var iconAttr = item.getAttributes().stream()
				.filter(a -> a.getName().equalsIgnoreCase("icon_id") ||
						a.getName().equalsIgnoreCase("IconId"))
				.findFirst().orElse(null);

		String rawValue = iconAttr == null ? null
				: String.valueOf(iconAttr.getValue());

		boolean useFallback = (rawValue == null
				|| rawValue.equals("0")
				|| rawValue.equalsIgnoreCase("replaceme"));

		String iconId = useFallback ? "crime_investigation_icon" : rawValue;
		String folder = useFallback ? null : "Icons";

		return getBase64Icon(iconId, folder);
	}

	/**
	 * Load a DDS icon from IPL_GameData.pak and return it as a base64 PNG data-URI.
	 *
	 * @param iconId         Icon filename (without extension) to search for.
	 * @param matchingFolder Sub-folder inside Libs/UI/Textures, or null.
	 * @return base64 data-URI string, or null on failure.
	 */
	public String getBase64Icon(String iconId, String matchingFolder) {
		String dir = configService.getCurrent().gameDirectory;
		if (dir == null || dir.isBlank()) {
			log.warning("Game directory not set.");
			return null;
		}

		String pakPath = PathFactory.join(dir, "Data", "IPL_GameData.pak");
		File pakFile = new File(pakPath);
		if (!pakFile.exists()) {
			log.warning("IPL_GameData.pak not found: " + pakPath);
			return null;
		}

		String targetDir = (matchingFolder == null || matchingFolder.isBlank())
				? "Libs/UI/Textures"
				: "Libs/UI/Textures/" + matchingFolder;

		try (var zf = new ZipFile(pakFile)) {
			var entry = zf.stream()
					.filter(e ->
							e.getName().toLowerCase(Locale.ROOT)
									.contains(iconId.toLowerCase(Locale.ROOT)) &&
									e.getName().contains(targetDir))
					.findFirst().orElse(null);

			if (entry == null) {
				log.warning("Icon not found in pak: " + iconId);
				return null;
			}

			try (var is = zf.getInputStream(entry)) {
				return DdsConverter.toBase64DataUri(is);
			}
		} catch (UnsupportedOperationException uoe) {
			log.warning("DDS conversion not available (add DDSReader library): "
					+ uoe.getMessage());
			return null;
		} catch (Exception ex) {
			log.severe("Icon load error (" + iconId + "): " + ex.getMessage());
			return null;
		}
	}
}

// =============================================================================
// NAVIGATION SERVICE  (mirrors C# NavigationService without Blazor)
// =============================================================================

final class NavigationService {
	private final Deque<String> back = new ArrayDeque<>();
	private final Deque<String> forward = new ArrayDeque<>();
	private String current;

	@FunctionalInterface
	public interface NavigationListener {
		void onNavigate(String uri);
	}

	private NavigationListener listener;

	public NavigationService(String initialUri) {
		this.current = initialUri;
	}

	public void setNavigationListener(NavigationListener l) {
		this.listener = l;
	}

	public boolean canGoBack() {
		return !back.isEmpty();
	}

	public boolean canGoForward() {
		return !forward.isEmpty();
	}

	public String getCurrent() {
		return current;
	}

	public void navigateTo(String uri) {
		if (current != null) back.push(current);
		forward.clear();
		current = uri;
		fire(uri);
	}

	public void goBack() {
		if (!canGoBack()) return;
		forward.push(current);
		current = back.pop();
		fire(current);
	}

	public void goForward() {
		if (!canGoForward()) return;
		back.push(current);
		current = forward.pop();
		fire(current);
	}

	private void fire(String uri) {
		if (listener != null) listener.onNavigate(uri);
	}
}

// =============================================================================
// MOD SERVICE  (mirrors C# ModService)
// =============================================================================

final class ModService {
	private static final Logger log = Logger.getLogger(ModService.class.getName());

	private final UserConfigurationService configService;
	private final LocalizationService localizationService;
	private final IModItemAdapter adapter;
	private final ObjectMapper mapper =
			new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

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

	public void clearCurrentMod() {
		currentMod = new ModDescription();
	}

	// ------------------------------------------------------------------
	// Collection management
	// ------------------------------------------------------------------

	public void initiateModCollections() {
		String gameDir = configService.getCurrent().gameDirectory;
		if (gameDir == null || gameDir.isBlank()) {
			log.warning("Game directory not configured - skipping mod collection scan.");
			return;
		}

		Path modsFolder = Path.of(gameDir, "Mods");
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
		String author = textOf(doc, "author");
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
		var m = new ModDescription();
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
		currentMod.modItems.removeIf(x ->
				item.getId() != null && item.getId().equals(x.getId()));
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

	public boolean writeModManifest(ModDescription mod) {
		if (mod == null || mod.id.isBlank() || mod.name.isBlank()) return false;
		String gameDir = configService.getCurrent().gameDirectory;
		String rootPath = PathFactory.modFolder(gameDir, mod.id);
		String manifest = rootPath + "/mod.manifest";
		try {
			Files.createDirectories(Path.of(rootPath + "/Data"));
			Files.createDirectories(Path.of(rootPath + "/Localization"));

			if (Files.exists(Path.of(manifest))) {
				log.info("Manifest already exists: " + manifest);
				return true;
			}

			var docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			var doc = docBuilder.newDocument();

			Element root = doc.createElement("kcd_mod");
			root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xsd",
					"http://www.w3.org/2001/XMLSchema");
			root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xsi",
					"http://www.w3.org/2001/XMLSchema-instance");
			doc.appendChild(root);

			Element info = doc.createElement("info");
			appendText(doc, info, "name", mod.name);
			appendText(doc, info, "description", mod.description);
			appendText(doc, info, "author", mod.author);
			appendText(doc, info, "version", mod.modVersion);
			appendText(doc, info, "created_on", mod.createdOn);
			appendText(doc, info, "modid", mod.id);
			appendText(doc, info, "modifies_level",
					String.valueOf(mod.modifiesLevel).toLowerCase(Locale.ROOT));
			root.appendChild(info);

			Element supports = doc.createElement("supports");
			for (var v : mod.supportsGameVersions)
				appendText(doc, supports, "kcd_version", v);
			root.appendChild(supports);

			var tf = TransformerFactory.newInstance().newTransformer();
			tf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			tf.setOutputProperty(OutputKeys.INDENT, "yes");
			tf.transform(new DOMSource(doc), new StreamResult(new File(manifest)));

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
			Files.deleteIfExists(Path.of(pakFilePath));
			Files.createDirectories(Path.of(pakFilePath).getParent());

			try (var fos = new FileOutputStream(pakFilePath);
				 var zout = new ZipOutputStream(fos)) {

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

		ToolResources.endpoints().forEach((type, eps) -> {
			String key = eps.keySet().iterator().next();
			int idx = key.indexOf('_');
			String epKey = idx >= 0 ? key.substring(0, idx) : key;
			var dp = DataPointFactory.create(pakFile, epKey, type);
			adapter.readModItems(dp).forEach(item -> modDesc.modItems.add(item));
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

// =============================================================================
// EXTENSIONS UTILITY  (mirrors C# Extensions static class)
// =============================================================================

final class Extensions {
	private Extensions() {
	}

	public static String readAllText(InputStream is) throws IOException {
		return new String(is.readAllBytes(), StandardCharsets.UTF_8);
	}

	public static String capitalizeFirstOnly(String s) {
		if (s == null || s.isEmpty()) return s;
		return Character.toUpperCase(s.charAt(0))
				+ s.substring(1).toLowerCase(Locale.ROOT);
	}

	public static String replaceWhiteSpace(String s) {
		if (s == null) return "";
		return String.join("_",
				s.trim().toLowerCase(Locale.ROOT).split("\\s+"));
	}

	/**
	 * Get the directory portion of a ZIP entry path,
	 * stripping any trailing XML filename.
	 * Mirrors C# ZipArchiveEntry.GetEntryPath().
	 */
	public static String getEntryPath(String fullName) {
		if (fullName == null) return "";
		String[] parts = fullName.replace('\\', '/').split("/");
		if (parts.length > 0 &&
				parts[parts.length - 1].toLowerCase(Locale.ROOT).contains("xml")) {
			var sb = new StringBuilder();
			for (int i = 0; i < parts.length - 1; i++) {
				if (i > 0) sb.append('/');
				sb.append(parts[i]);
			}
			return sb.toString();
		}
		return fullName;
	}
}

// =============================================================================
// SERVICE REGISTRY  (replaces C# IServiceCollection DI wiring)
// =============================================================================

/**
 * Wires all dependencies together in the correct order.
 * Use as the single entry-point for bootstrapping the application.
 */
final class ServiceRegistry {
	public final UserConfigurationService userConfig;
	public final LocalizationAdapter localizationAdapter;
	public final LocalizationService localizationService;
	public final ModItemBuilder builder;
	public final XmlAdapter xmlAdapter;
	public final JsonAdapter jsonAdapter;
	public final XmlService xmlService;
	public final IconService iconService;
	public final NavigationService navigationService;
	public final ModService modService;

	public ServiceRegistry() {
		this("about:blank");
	}

	public ServiceRegistry(String initialUri) {
		userConfig = new UserConfigurationService();
		localizationAdapter = new LocalizationAdapter();
		localizationService = new LocalizationService(localizationAdapter, userConfig);

		builder = ModItemBuilder.createDefault();
		xmlAdapter = new XmlAdapter(userConfig, builder);
		jsonAdapter = new JsonAdapter(resolveAppDataDir());

		xmlService = new XmlService(xmlAdapter, localizationService, userConfig);
		iconService = new IconService(userConfig);
		navigationService = new NavigationService(initialUri);
		modService = new ModService(xmlAdapter, userConfig, localizationService);
	}

	/**
	 * Convenience method: set the game directory and reload everything.
	 * Equivalent to the user browsing to their game folder in the UI.
	 */
	public void setGameDirectory(String path) {
		userConfig.getCurrent().gameDirectory = path;
		userConfig.save();
		localizationService.init();
		xmlService.tryReadXmlFiles();
		modService.initiateModCollections();

		System.out.printf(
			"Loaded: %d perks, %d buffs, %d weapons, %d armors%n",
			xmlService.perks.size(),
			xmlService.buffs.size(),
			xmlService.weapons.size(),
			xmlService.armors.size()
		);
	}

	private static String resolveAppDataDir() {
		String appData = System.getenv("APPDATA");
		return (appData != null && !appData.isBlank())
				? appData
				: System.getProperty("user.home") + "/AppData/Roaming";
	}
}
