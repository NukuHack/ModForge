package modforge.backend.service;

import modforge.Util;
import modforge.backend.DataPoint;
import modforge.backend.ModData;
import modforge.backend.model.item.Storm;
import modforge.backend.model.storm.*;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

/**
 * Manages loading, indexing, and writing STORM script files.
 *
 * <h2>File layout</h2>
 * <ul>
 *   <li>Base-game Storm files live inside PAKs under
 *       {@code Data/Libs/Storm/} (in {@code Scripts.pak} or similar).</li>
 *   <li>Mod Storm files live under
 *       {@code Mods/<modId>/Data/Libs/Storm/}.</li>
 * </ul>
 *
 * <h2>Responsibilities</h2>
 * <ol>
 *   <li>Scan PAK files for Storm XML entries and parse them into {@link StormData}.</li>
 *   <li>Attach parsed {@link StormData} to the matching {@link Storm} {@code ModItem}.</li>
 *   <li>Write modified {@link StormData} back to XML inside a mod's staging folder.</li>
 * </ol>
 */
public final class StormService {
	
	private static final Logger log = Logger.getLogger(StormService.class.getName());
	
	/** Path prefix inside PAKs where Storm XML files reside. */
	private static final String STORM_PATH_PREFIX = "Libs/Storm/";
	
	private final UserConfig userConfig;
	
	/** All parsed Storm files, keyed by their stable ID. */
	private final Map<String, StormData> stormIndex = new LinkedHashMap<>();
	
	// =========================================================================
	// Construction
	// =========================================================================
	
	public StormService(UserConfig userConfig) {
		this.userConfig = userConfig;
	}
	
	// =========================================================================
	// Public API – loading
	// =========================================================================
	
	/**
	 * Write a modified {@link StormData} into the mod's staging folder so it can
	 * later be packed into a PAK by {@link ModService}.
	 *
	 * <p>Output path:
	 * {@code Mods/<modId>/Data/_stage/<modId>/Libs/Storm/<fileName>.xml}</p>
	 *
	 * @param gameDir Game directory root.
	 * @param modId   Mod identifier.
	 * @param data    The Storm data to write.
	 * @return {@code true} on success.
	 */
	public static boolean writeStormFile(String gameDir, String modId, StormData data) {
		if (data == null || data.getId().isBlank()) {
			log.warning("writeStormFile: missing data or id.");
			return false;
		}
		
		// Derive a sensible file name from the ID (last path segment)
		final String fileName = idToFileName(data.getId());
		final Path targetDir = Util.modStormStaging(gameDir, modId);
		final Path targetFile = targetDir.resolve(fileName);
		
		try {
			Files.createDirectories(targetDir);
			final String xml = StormParser.serialize(data);
			Files.writeString(targetFile, xml, StandardCharsets.UTF_8);
			log.info("Storm file written: " + targetFile);
			return true;
		} catch (Exception e) {
			log.severe("writeStormFile failed for '" + data.getId() + "': " + e.getMessage());
			return false;
		}
	}
	
	/**
	 * Scan a PAK file for Storm XML entries and return them as a map.
	 * Entries are only included when their path contains {@value #STORM_PATH_PREFIX}.
	 */
	private static Map<String, StormData> indexStormFromPakToMap(String pakPath) {
		final File pakFile = new File(pakPath);
		if (! pakFile.exists())
			return Map.of();
		
		final Map<String, StormData> result = new LinkedHashMap<>();
		
		try (var zf = new ZipFile(pakFile)) {
			final var entries = zf.entries();
			while (entries.hasMoreElements()) {
				final var entry = entries.nextElement();
				final String name = entry.getName().replace('\\', '/');
				
				if (! name.toLowerCase(Locale.ROOT).contains(STORM_PATH_PREFIX.toLowerCase(Locale.ROOT)))
					continue;
				if (! name.toLowerCase(Locale.ROOT).endsWith(Util.DATA_FORMAT))
					continue;
				
				final String id = entryToId(name);
				final String pakFileName = pakFile.getName();
				
				// Derive category from the first path component after Libs/Storm/
				final String category = categoryFromPath(name);
				
				try (var is = zf.getInputStream(entry)) {
					final var dp = new DataPoint(pakPath + ":" + name, name, Storm.class);
					final StormData sd = StormParser.parse(is, dp, id);
					if (sd.getCategory() == null && category != null) {
						sd.setCategory(category);
					}
					result.put(id, sd);
				} catch (Exception ex) {
					log.fine("Storm parse error in " + name + " (" + pakFileName + "): " + ex.getMessage());
				}
			}
		} catch (IOException ex) {
			log.warning("Cannot open PAK for Storm scan: " + pakPath + " – " + ex.getMessage());
		}
		
		return result;
	}
	
	// =========================================================================
	// Public API – querying
	// =========================================================================
	
	/**
	 * Convert a ZIP entry path to a stable ID string.
	 * e.g. {@code "Libs/Storm/Combat/melee.xml"} → {@code "Libs/Storm/Combat/melee"}
	 */
	private static String entryToId(String entryName) {
		// Strip extension
		final int dot = entryName.lastIndexOf('.');
		return dot > 0 ? entryName.substring(0, dot) : entryName;
	}
	
	/**
	 * Derive a display category from a Storm file's entry path.
	 * e.g. {@code "Libs/Storm/Combat/melee.xml"} → {@code "Combat"}
	 * e.g. {@code "Libs/Storm/melee.xml"} → {@code null}
	 */
	private static String categoryFromPath(String entryPath) {
		final int stormIdx = entryPath.toLowerCase(Locale.ROOT).indexOf(STORM_PATH_PREFIX.toLowerCase(Locale.ROOT));
		if (stormIdx < 0)
			return null;
		final String afterStorm = entryPath.substring(stormIdx + STORM_PATH_PREFIX.length());
		final int slash = afterStorm.indexOf('/');
		return slash > 0 ? afterStorm.substring(0, slash) : null;
	}
	
	/**
	 * Convert a stable ID back to a file name.
	 * e.g. {@code "Libs/Storm/Combat/melee"} → {@code "melee.xml"}
	 */
	private static String idToFileName(String id) {
		final int slash = id.lastIndexOf('/');
		final String base = slash >= 0 ? id.substring(slash + 1) : id;
		return base.endsWith(".xml") ? base : base + ".xml";
	}
	
	/**
	 * Scan the base-game PAKs for Storm files and populate the index.
	 * Call after the game directory is set.
	 */
	public void init() {
		stormIndex.clear();
		final String gameDir = userConfig.gameDirectory;
		if (gameDir == null || gameDir.isBlank())
			return;
		
		// Storm files are typically in Scripts.pak; scan all data PAKs for robustness.
		final Path dataFolder = Util.gameDataDir(gameDir);
		if (! Files.exists(dataFolder))
			return;
		
		try (var stream = Files.list(dataFolder)) {
			stream.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".pak")).forEach(pak -> indexStormFromPak(pak.toString()));
		} catch (IOException e) {
			log.warning("Cannot list game Data folder for Storm scan: " + e.getMessage());
		}
		
		log.info("StormService: indexed " + stormIndex.size() + " Storm file(s) from base game.");
	}
	
	// =========================================================================
	// Public API – writing
	// =========================================================================
	
	/**
	 * Load Storm files for a specific mod's Data PAKs and attach the parsed
	 * {@link StormData} to matching {@link Storm} items in {@code mod}.
	 *
	 * @param mod The mod whose Data folder will be scanned.
	 */
	public void loadForMod(ModData mod) {
		final String gameDir = userConfig.gameDirectory;
		final Path dataFolder = Path.of(Util.modData(gameDir, mod.id));
		if (! Files.exists(dataFolder))
			return;
		
		final Map<String, StormData> modIndex = new LinkedHashMap<>();
		
		try (var stream = Files.list(dataFolder)) {
			stream.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".pak")).forEach(pak -> {
				Map<String, StormData> fromPak = indexStormFromPakToMap(pak.toString());
				modIndex.putAll(fromPak);
			});
		} catch (IOException e) {
			log.warning("Cannot list Data folder for Storm mod " + mod.id + ": " + e.getMessage());
		}
		
		// Attach parsed StormData to the Storm ModItems that belong to this mod
		for (var item : mod.getItems()) {
			if (! (item instanceof Storm stormItem))
				continue;
			final String itemId = stormItem.getId();
			if (itemId == null)
				continue;
			
			final StormData sd = modIndex.get(itemId);
			if (sd != null) {
				stormItem.setStormData(sd);
			}
		}
		
		log.info("StormService: loaded " + modIndex.size() + " Storm file(s) for mod " + mod.id);
	}
	
	// =========================================================================
	// Private helpers
	// =========================================================================
	
	/**
	 * Return all parsed {@link StormData} entries from the base-game index.
	 */
	public List<StormData> getAllStormData() {
		return List.copyOf(stormIndex.values());
	}
	
	/**
	 * Look up a single {@link StormData} by its ID.
	 *
	 * @param id The ID string (derived from the entry path inside the PAK).
	 * @return The matching {@link StormData}, or {@code null} if not found.
	 */
	public StormData getById(String id) {
		return stormIndex.get(id);
	}
	
	/**
	 * Return all Storm entries that match the given category.
	 * Pass {@code null} to retrieve entries with no category set ("Miscellaneous").
	 */
	public List<StormData> getByCategory(String category) {
		return stormIndex.values().stream().filter(sd -> Objects.equals(sd.getCategory(), category)).toList();
	}
	
	/**
	 * Return all distinct non-null categories present in the index.
	 */
	public Set<String> getCategories() {
		final var set = new LinkedHashSet<String>();
		stormIndex.values().forEach(sd -> {
			if (sd.getCategory() != null)
				set.add(sd.getCategory());
		});
		return Collections.unmodifiableSet(set);
	}
	
	/**
	 * Scan a PAK file for Storm XML entries and add them to {@link #stormIndex}.
	 */
	private void indexStormFromPak(String pakPath) {
		stormIndex.putAll(indexStormFromPakToMap(pakPath));
	}
	
	/**
	 * Parses a single STORM XML file ({@code .xml} inside a Storm PAK) into a
	 * {@link StormData} object.
	 *
	 * <h2>Supported file structure</h2>
	 * <pre>
	 * &lt;AISystem&gt;                          &lt;!-- or any root tag --&gt;
	 *   &lt;CustomSelectors&gt;
	 *     &lt;Selector name="..." comment="..."&gt;
	 *       &lt;Attribute name="..."/&gt;
	 *     &lt;/Selector&gt;
	 *   &lt;/CustomSelectors&gt;
	 *
	 *   &lt;CustomOperations&gt;
	 *     &lt;Operation name="..."&gt;
	 *       &lt;Attribute stat="..." minMod="0" maxMod="1"/&gt;
	 *     &lt;/Operation&gt;
	 *   &lt;/CustomOperations&gt;
	 *
	 *   &lt;Tasks&gt;
	 *     &lt;Task name="..." comment="..." sources="..." class="..."/&gt;
	 *   &lt;/Tasks&gt;
	 *
	 *   &lt;Rules&gt;
	 *     &lt;Rule name="..." comment="..."&gt;
	 *       &lt;Conditions&gt;
	 *         &lt;and&gt;
	 *           &lt;selector name="HasItem" itemId="sword_iron"/&gt;
	 *           &lt;or&gt;
	 *             &lt;selector name="IsAlive"/&gt;
	 *             &lt;not&gt;
	 *               &lt;selector name="IsDead"/&gt;
	 *             &lt;/not&gt;
	 *           &lt;/or&gt;
	 *         &lt;/and&gt;
	 *       &lt;/Conditions&gt;
	 *       &lt;Operations&gt;
	 *         &lt;setAttribute skill="Strength" value="5"&gt;
	 *           &lt;subOp foo="bar"/&gt;       &lt;!-- nested child operation --&gt;
	 *         &lt;/setAttribute&gt;
	 *       &lt;/Operations&gt;
	 *     &lt;/Rule&gt;
	 *   &lt;/Rules&gt;
	 * &lt;/AISystem&gt;
	 * </pre>
	 *
	 * <p>Element and attribute matching is case-insensitive to stay robust against
	 * minor casing differences across game versions.</p>
	 */
	public static final class StormParser {
		
		private static final Logger log = Logger.getLogger(StormParser.class.getName());
		
		// -------------------------------------------------------------------------
		// Known combinator tag names (used during recursive selector parsing)
		// -------------------------------------------------------------------------
		private static final Set<String> COMBINATORS = Set.of("and", "or", "not");
		
		private StormParser() {
		}
		
		// =========================================================================
		// Public API
		// =========================================================================
		
		/**
		 * Parse a Storm XML {@link InputStream} into a {@link StormData} object.
		 *
		 * @param is        The XML input stream (will NOT be closed by this method).
		 * @param dataPoint Source metadata (path, endpoint, type) – stored on the result.
		 * @param id        Stable string ID for this Storm file (e.g. derived from path).
		 * @return Populated {@link StormData}; never {@code null}.
		 */
		public static StormData parse(InputStream is, DataPoint dataPoint, String id) {
			final StormData data = new StormData();
			data.setId(id);
			data.setDataPoint(dataPoint);
			
			try {
				final Document doc = buildDocument(is);
				final Element root = doc.getDocumentElement();
				
				// Optional category from root attribute or derived from file name
				final String catAttr = root.getAttribute("category");
				if (! catAttr.isEmpty())
					data.setCategory(catAttr);
				
				parseAllSections(root, data);
				
			} catch (Exception ex) {
				log.warning("StormParser: failed to parse '" + id + "': " + ex.getMessage());
			}
			
			return data;
		}
		
		// =========================================================================
		// Section dispatching
		// =========================================================================
		
		private static void parseAllSections(Element root, StormData data) {
			final NodeList children = root.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				if (! (children.item(i) instanceof Element section))
					continue;
				final String tag = section.getLocalName().toLowerCase(Locale.ROOT);
				
				switch (tag) {
					case "customselectors" -> parseCustomSelectors(section, data);
					case "customoperations" -> parseCustomOperations(section, data);
					case "tasks" -> parseTasks(section, data);
					case "rules" -> parseRules(section, data);
					// Any unknown top-level section is silently skipped.
				}
			}
		}
		
		// =========================================================================
		// Custom selectors
		// =========================================================================
		
		private static void parseCustomSelectors(Element section, StormData data) {
			forEachElement(section, child -> {
				final var cs = new CustomStormSelector();
				cs.setName(child.getAttribute("name"));
				cs.setComment(child.getAttribute("comment"));
				forEachElement(child, attrEl -> cs.getAttributeNames().add(attrEl.getAttribute("name")));
				data.getCustomSelectors().add(cs);
			});
		}
		
		// =========================================================================
		// Custom operations
		// =========================================================================
		
		private static void parseCustomOperations(Element section, StormData data) {
			forEachElement(section, child -> {
				final var co = new CustomStormOperation();
				co.setName(child.getAttribute("name"));
				forEachElement(child, attrEl -> {
					final var ma = new CustomStormOperation.ModAttribute();
					ma.setStat(attrEl.getAttribute("stat"));
					ma.setMinMod(parseDouble(attrEl.getAttribute("minMod")));
					ma.setMaxMod(parseDouble(attrEl.getAttribute("maxMod")));
					co.getModAttributes().add(ma);
				});
				data.getCustomOperations().add(co);
			});
		}
		
		// =========================================================================
		// Tasks
		// =========================================================================
		
		private static void parseTasks(Element section, StormData data) {
			forEachElement(section, child -> {
				final var task = new StormTask();
				task.setName(child.getAttribute("name"));
				task.setComment(child.getAttribute("comment"));
				task.setSources(child.getAttribute("sources"));
				// "class" is a reserved word in Java – read via getAttributeNS / plain getAttribute
				task.setTaskClass(child.getAttribute("class"));
				data.getTasks().add(task);
			});
		}
		
		// =========================================================================
		// Rules
		// =========================================================================
		
		private static void parseRules(Element section, StormData data) {
			forEachElement(section, ruleEl -> {
				if (! ruleEl.getLocalName().equalsIgnoreCase("rule"))
					return;
				
				final var rule = new StormRule();
				rule.setName(ruleEl.getAttribute("name"));
				rule.setComment(ruleEl.getAttribute("comment"));
				
				forEachElement(ruleEl, part -> {
					final String partTag = part.getLocalName().toLowerCase(Locale.ROOT);
					switch (partTag) {
						case "conditions" -> parseConditions(part, rule);
						case "operations" -> parseOperations(part, rule);
					}
				});
				
				data.getRules().add(rule);
			});
		}
		
		// -------------------------------------------------------------------------
		// Conditions (selector tree)
		// -------------------------------------------------------------------------
		
		private static void parseConditions(Element conditionsEl, StormRule rule) {
			forEachElement(conditionsEl, child -> rule.getSelectors().add(parseSelector(child)));
		}
		
		/**
		 * Recursively parse a selector element.
		 *
		 * <ul>
		 *   <li>Combinator tags ({@code and}, {@code or}, {@code not}) get their
		 *       child elements recursively parsed and appended as children.</li>
		 *   <li>All other tags are treated as leaf selectors; their XML attributes
		 *       become the selector's attribute map.</li>
		 * </ul>
		 */
		private static GenericSelector parseSelector(Element el) {
			final String tag = el.getLocalName().toLowerCase(Locale.ROOT);
			final var sel = new GenericSelector(tag);
			
			// Copy XML attributes onto the selector (skips namespace-only attributes)
			copyXmlAttrs(el, sel.getAttributes());
			
			if (COMBINATORS.contains(tag)) {
				// Recurse into children
				forEachElement(el, child -> sel.getChildren().add(parseSelector(child)));
			}
			// Non-combinator leaf selectors may still have children in exotic files;
			// parse them defensively.
			else {
				forEachElement(el, child -> sel.getChildren().add(parseSelector(child)));
			}
			
			return sel;
		}
		
		// -------------------------------------------------------------------------
		// Operations
		// -------------------------------------------------------------------------
		
		private static void parseOperations(Element operationsEl, StormRule rule) {
			forEachElement(operationsEl, child -> rule.getOperations().add(parseOperation(child)));
		}
		
		/**
		 * Recursively parse an operation element and its children.
		 * The tag name becomes the operation name; XML attributes become the
		 * operation's attribute map. Child elements become child operations.
		 */
		private static GenericOperation parseOperation(Element el) {
			final var op = new GenericOperation(el.getLocalName());
			copyXmlAttrs(el, op.getAttributes());
			
			// Recursively parse any child operations
			forEachElement(el, child -> op.getChildren().add(parseOperation(child)));
			
			return op;
		}
		
		// =========================================================================
		// Serialisation (StormData → XML string)
		// =========================================================================
		
		/**
		 * Serialize a {@link StormData} back to XML that can be written into a PAK.
		 *
		 * @return Pretty-printed XML string.
		 */
		public static String serialize(StormData data) throws Exception {
			var factory = DocumentBuilderFactory.newInstance();
			var docBuilder = factory.newDocumentBuilder();
			var doc = docBuilder.newDocument();
			
			final Element root = doc.createElement("AISystem");
			if (data.getCategory() != null)
				root.setAttribute("category", data.getCategory());
			doc.appendChild(root);
			
			if (! data.getCustomSelectors().isEmpty()) {
				final Element cs = doc.createElement("CustomSelectors");
				for (var cSel : data.getCustomSelectors()) {
					final Element sel = doc.createElement("Selector");
					sel.setAttribute("name", cSel.getName());
					if (! cSel.getComment().isEmpty())
						sel.setAttribute("comment", cSel.getComment());
					for (var attrName : cSel.getAttributeNames()) {
						final Element a = doc.createElement("Attribute");
						a.setAttribute("name", attrName);
						sel.appendChild(a);
					}
					cs.appendChild(sel);
				}
				root.appendChild(cs);
			}
			
			if (! data.getCustomOperations().isEmpty()) {
				final Element co = doc.createElement("CustomOperations");
				for (var cop : data.getCustomOperations()) {
					final Element op = doc.createElement("Operation");
					op.setAttribute("name", cop.getName());
					for (var ma : cop.getModAttributes()) {
						final Element a = doc.createElement("Attribute");
						a.setAttribute("stat", ma.getStat());
						a.setAttribute("minMod", String.valueOf(ma.getMinMod()));
						a.setAttribute("maxMod", String.valueOf(ma.getMaxMod()));
						op.appendChild(a);
					}
					co.appendChild(op);
				}
				root.appendChild(co);
			}
			
			if (! data.getTasks().isEmpty()) {
				final Element tasks = doc.createElement("Tasks");
				for (var t : data.getTasks()) {
					final Element task = doc.createElement("Task");
					task.setAttribute("name", t.getName());
					task.setAttribute("comment", t.getComment());
					task.setAttribute("sources", t.getSources());
					task.setAttribute("class", t.getTaskClass());
					tasks.appendChild(task);
				}
				root.appendChild(tasks);
			}
			
			if (! data.getRules().isEmpty()) {
				final Element rules = doc.createElement("Rules");
				for (var r : data.getRules()) {
					rules.appendChild(serializeRule(doc, r));
				}
				root.appendChild(rules);
			}
			
			return docToString(doc);
		}
		
		private static Element serializeRule(Document doc, StormRule rule) {
			final Element el = doc.createElement("Rule");
			el.setAttribute("name", rule.getName());
			if (! rule.getComment().isEmpty())
				el.setAttribute("comment", rule.getComment());
			
			if (! rule.getSelectors().isEmpty()) {
				final Element cond = doc.createElement("Conditions");
				for (var sel : rule.getSelectors()) {
					cond.appendChild(serializeSelector(doc, sel));
				}
				el.appendChild(cond);
			}
			
			if (! rule.getOperations().isEmpty()) {
				final Element ops = doc.createElement("Operations");
				for (var op : rule.getOperations()) {
					ops.appendChild(serializeOperation(doc, op));
				}
				el.appendChild(ops);
			}
			
			return el;
		}
		
		/** Recursively serialize a {@link GenericSelector} (supports arbitrary depth). */
		private static Element serializeSelector(Document doc, GenericSelector sel) {
			final Element el = doc.createElement(sel.getName().isEmpty() ? "selector" : sel.getName());
			sel.getAttributes().forEach(el::setAttribute);
			for (var child : sel.getChildren()) {
				el.appendChild(serializeSelector(doc, child));
			}
			return el;
		}
		
		/** Recursively serialize a {@link GenericOperation}. */
		private static Element serializeOperation(Document doc, GenericOperation op) {
			final Element el = doc.createElement(op.getName().isEmpty() ? "operation" : op.getName());
			op.getAttributes().forEach(el::setAttribute);
			for (var child : op.getChildren()) {
				el.appendChild(serializeOperation(doc, child));
			}
			return el;
		}
		
		// =========================================================================
		// Private utilities
		// =========================================================================
		
		private static Document buildDocument(InputStream is) throws Exception {
			var f = DocumentBuilderFactory.newInstance();
			f.setNamespaceAware(true);
			f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			f.setFeature("http://xml.org/sax/features/validation", false);
			f.setFeature("http://xml.org/sax/features/external-general-entities", false);
			f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			return f.newDocumentBuilder().parse(is);
		}
		
		/** Copy all XML element attributes into a String map. */
		private static void copyXmlAttrs(Element el, Map<String, String> target) {
			final NamedNodeMap xmlAttrs = el.getAttributes();
			for (int i = 0; i < xmlAttrs.getLength(); i++) {
				final var a = (Attr) xmlAttrs.item(i);
				if (a.getLocalName() != null)
					target.put(a.getLocalName(), a.getValue());
			}
		}
		
		/** Parse a double from a string, returning {@code 0} on failure. */
		private static double parseDouble(String s) {
			if (s == null || s.isBlank())
				return 0;
			try {
				return Double.parseDouble(s);
			} catch (NumberFormatException e) {
				return 0;
			}
		}
		
		private static void forEachElement(Element parent, ElementConsumer action) {
			final NodeList children = parent.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				if (children.item(i) instanceof Element el) {
					action.accept(el);
				}
			}
		}
		
		private static String docToString(Document doc) throws Exception {
			var tf = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
			tf.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8");
			tf.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
			tf.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
			var sw = new java.io.StringWriter();
			tf.transform(new DOMSource(doc), new StreamResult(sw));
			return sw.toString();
		}
		
		/**
		 * Iterate over the direct element children of {@code parent}, calling
		 * {@code action} for each one. Non-element nodes (text, comments) are skipped.
		 */
		@FunctionalInterface
		private interface ElementConsumer {
			void accept(Element el);
		}
	}
	
}
