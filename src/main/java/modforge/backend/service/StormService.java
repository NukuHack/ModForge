package modforge.backend.service;

import modforge.Util;
import modforge.backend.DataPoint;
import modforge.backend.ModData;
import modforge.backend.model.I.Storm;
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
 *
 * <h2>Actual XML format (storm.xml)</h2>
 * <pre>
 * &lt;?xml version="1.0"?&gt;
 * &lt;!DOCTYPE storm SYSTEM "storm.dtd"&gt;
 * &lt;storm&gt;
 *   &lt;common&gt;
 *     &lt;source path="..."/&gt;
 *   &lt;/common&gt;
 *
 *   &lt;tasks&gt;
 *     &lt;task name="..." class="..."&gt;
 *       &lt;source path="..."/&gt;
 *     &lt;/task&gt;
 *   &lt;/tasks&gt;
 *
 *   &lt;customSelectors&gt;
 *     &lt;selector name="..." comment="..."&gt;
 *       &lt;attribute name="..."/&gt;
 *     &lt;/selector&gt;
 *   &lt;/customSelectors&gt;
 *
 *   &lt;customOperations&gt;
 *     &lt;operation name="..." mode="..."&gt;
 *       &lt;attribute stat="..." minMod="0" maxMod="1"/&gt;
 *     &lt;/operation&gt;
 *   &lt;/customOperations&gt;
 *
 *   &lt;rules&gt;
 *     &lt;rule name="..." comment="..."&gt;
 *       &lt;selectors&gt;
 *         &lt;and&gt;
 *           &lt;hasName name="foo"/&gt;
 *           &lt;or&gt;
 *             &lt;isMan/&gt;
 *             &lt;not&gt;&lt;isWoman/&gt;&lt;/not&gt;
 *           &lt;/or&gt;
 *         &lt;/and&gt;
 *       &lt;/selectors&gt;
 *       &lt;operations&gt;
 *         &lt;setUnderwear name="bar"/&gt;
 *       &lt;/operations&gt;
 *     &lt;/rule&gt;
 *   &lt;/rules&gt;
 * &lt;/storm&gt;
 * </pre>
 */
@lombok.extern.slf4j.Slf4j
public final class StormService {
	
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
			log.warn("writeStormFile: missing data or id.");
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
			log.info("Storm file written: {}", targetFile);
			return true;
		} catch (Exception e) {
			log.error("writeStormFile failed for '{}': {}", data.getId(), e.getMessage());
			return false;
		}
	}
	
	/**
	 * Scan a PAK file for Storm XML entries and return them as a map.
	 * Entries are only included when their path contains {@value #STORM_PATH_PREFIX}.
	 */
	private static Map<String, StormData> indexStormFromPakToMap(String pakPath) {
		final File pakFile = new File(pakPath);
		if (!pakFile.exists())
			return Map.of();
		
		final Map<String, StormData> result = new LinkedHashMap<>();
		
		try (var zf = new ZipFile(pakFile)) {
			final var entries = zf.entries();
			while (entries.hasMoreElements()) {
				final var entry = entries.nextElement();
				final String name = entry.getName().replace('\\', '/');
				
				if (!name.toLowerCase(Locale.ROOT).contains(STORM_PATH_PREFIX.toLowerCase(Locale.ROOT)))
					continue;
				if (!name.toLowerCase(Locale.ROOT).endsWith(Util.DATA_FORMAT))
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
					log.info("Storm parse error in {} ({}): {}", name, pakFileName, ex.getMessage());
				}
			}
		} catch (IOException ex) {
			log.error("Cannot open PAK for Storm scan: {} – {}", pakPath, ex.getMessage());
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
	static String entryToId(String entryName) {
		final int dot = entryName.lastIndexOf('.');
		return dot > 0 ? entryName.substring(0, dot) : entryName;
	}
	
	/**
	 * Derive a display category from a Storm file's entry path.
	 * e.g. {@code "Libs/Storm/Combat/melee.xml"} → {@code "Combat"}
	 * e.g. {@code "Libs/Storm/melee.xml"} → {@code null}
	 */
	static String categoryFromPath(String entryPath) {
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
	static String idToFileName(String id) {
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
		final String gameDir = userConfig.getGameDirectory();
		if (gameDir == null || gameDir.isBlank())
			return;
		
		final Path dataFolder = Util.gameDataDir(gameDir);
		if (!Files.exists(dataFolder))
			return;
		
		try (var stream = Files.list(dataFolder)) {
			stream.filter(Files::isRegularFile)
					.filter(p -> p.toString().endsWith(".pak"))
					.forEach(pak -> indexStormFromPak(pak.toString()));
		} catch (IOException e) {
			log.warn("Cannot list game Data folder for Storm scan: {}", e.getMessage());
		}
		
		log.info("StormService: indexed {} Storm file(s) from base game.", stormIndex.size());
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
		final String gameDir = userConfig.getGameDirectory();
		final Path dataFolder = Path.of(Util.modData(gameDir, mod.id));
		if (!Files.exists(dataFolder))
			return;
		
		final Map<String, StormData> modIndex = new LinkedHashMap<>();
		
		try (var stream = Files.list(dataFolder)) {
			stream.filter(Files::isRegularFile)
					.filter(p -> p.toString().endsWith(".pak"))
					.forEach(pak -> {
						Map<String, StormData> fromPak = indexStormFromPakToMap(pak.toString());
						modIndex.putAll(fromPak);
					});
		} catch (IOException e) {
			log.warn("Cannot list Data folder for Storm mod {}: {}", mod.id, e.getMessage());
		}
		
		// Attach parsed StormData to the Storm ModItems that belong to this mod
		for (var item : mod.getItems()) {
			if (!(item instanceof Storm stormItem))
				continue;
			final String itemId = stormItem.getId();
			if (itemId == null)
				continue;
			
			final StormData sd = modIndex.get(itemId);
			if (sd != null) {
				stormItem.setStormData(sd);
			}
		}
		
		log.info("StormService: loaded {} Storm file(s) for mod {}", modIndex.size(), mod.id);
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
	 */
	public StormData getById(String id) {
		return stormIndex.get(id);
	}
	
	/**
	 * Return all Storm entries that match the given category.
	 */
	public List<StormData> getByCategory(String category) {
		return stormIndex.values().stream()
					   .filter(sd -> Objects.equals(sd.getCategory(), category))
					   .toList();
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
	
	// =========================================================================
	// Inner class: StormParser
	// =========================================================================
	
	/**
	 * Parses and serialises STORM XML files.
	 *
	 * <p>The actual on-disk format uses {@code <storm>} as root and lowercase
	 * section tags: {@code <rules>}, {@code <selectors>}, {@code <operations>},
	 * {@code <tasks>}, {@code <common>}, {@code <customSelectors>},
	 * {@code <customOperations>}.  All tag matching is case-insensitive so
	 * files that deviate slightly in casing still parse correctly.</p>
	 *
	 * <p>Tasks may carry their source list as either child {@code <source path="..."/>}
	 * elements (entry-point files) or as a flat {@code sources="..."} attribute
	 * (older format).  Both are handled.</p>
	 */
	public static final class StormParser {
		
		private static final Logger log = Logger.getLogger(StormParser.class.getName());
		
		/** Combinator selector tags that may contain nested selectors. */
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
		 * @param dataPoint Source metadata stored on the result.
		 * @param id        Stable string ID (e.g. derived from the PAK entry path).
		 * @return Populated {@link StormData}; never {@code null}.
		 */
		public static StormData parse(InputStream is, DataPoint dataPoint, String id) {
			final StormData data = new StormData();
			data.setId(id);
			data.setDataPoint(dataPoint);
			
			try {
				final Document doc = buildDocument(is);
				final Element root = doc.getDocumentElement();
				
				// Optional category stored as a root attribute
				final String catAttr = root.getAttribute("category");
				if (!catAttr.isEmpty())
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
		
		/**
		 * Dispatch each direct child element of {@code root} to the appropriate
		 * section parser.  Tag matching is case-insensitive.
		 *
		 * <p>Supported section tags:
		 * {@code common}, {@code tasks}, {@code customselectors},
		 * {@code customoperations}, {@code rules}.</p>
		 */
		private static void parseAllSections(Element root, StormData data) {
			final NodeList children = root.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				if (!(children.item(i) instanceof Element section))
					continue;
				final String tag = section.getLocalName().toLowerCase(Locale.ROOT);
				
				switch (tag) {
					case "common"           -> parseCommon(section, data);
					case "tasks"            -> parseTasks(section, data);
					case "customselectors"  -> parseCustomSelectors(section, data);
					case "customoperations" -> parseCustomOperations(section, data);
					case "rules"            -> parseRules(section, data);
					// Unknown top-level sections are silently skipped.
				}
			}
		}
		
		// =========================================================================
		// Common (source list at root level)
		// =========================================================================
		
		/**
		 * Parse the optional {@code <common>} section which holds global
		 * {@code <source path="..."/>} entries.
		 *
		 * <pre>{@code
		 * <common>
		 *   <source path="Libs/Storm/Base/base.xml"/>
		 * </common>
		 * }</pre>
		 */
		private static void parseCommon(Element section, StormData data) {
			forEachElement(section, child -> {
				if (child.getLocalName().equalsIgnoreCase("source")) {
					// Prefer "path" attribute; fall back to text content
					String path = child.getAttribute("path");
					if (path.isBlank())
						path = child.getTextContent().trim();
					if (!path.isBlank())
						data.getCommonSources().add(path);
				}
			});
		}
		
		// =========================================================================
		// Custom selectors
		// =========================================================================
		
		/**
		 * Parse the optional {@code <customSelectors>} section.
		 *
		 * <pre>{@code
		 * <customSelectors>
		 *   <selector name="mySelector" comment="...">
		 *     <attribute name="someAttr"/>
		 *   </selector>
		 * </customSelectors>
		 * }</pre>
		 */
		private static void parseCustomSelectors(Element section, StormData data) {
			forEachElement(section, child -> {
				final var cs = new CustomStormSelector();
				cs.setName(child.getAttribute("name"));
				cs.setComment(child.getAttribute("comment"));
				forEachElement(child, attrEl ->
											  cs.getAttributeNames().add(attrEl.getAttribute("name")));
				data.getCustomSelectors().add(cs);
			});
		}
		
		// =========================================================================
		// Custom operations
		// =========================================================================
		
		/**
		 * Parse the optional {@code <customOperations>} section.
		 *
		 * <pre>{@code
		 * <customOperations>
		 *   <operation name="myOp" mode="add">
		 *     <attribute stat="Strength" minMod="0" maxMod="1"/>
		 *   </operation>
		 * </customOperations>
		 * }</pre>
		 */
		private static void parseCustomOperations(Element section, StormData data) {
			forEachElement(section, child -> {
				final var co = new CustomStormOperation();
				co.setName(child.getAttribute("name"));
				co.setMode(child.getAttribute("mode"));
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
		
		/**
		 * Parse the optional {@code <tasks>} section.
		 *
		 * <p>Two source formats are supported:
		 * <ul>
		 *   <li><b>Child elements</b> (entry-point storm files):
		 *       {@code <task name="Combat" class="Combat"><source path="combat.xml"/></task>}</li>
		 *   <li><b>Flat attribute</b> (older/simpler format):
		 *       {@code <task name="Combat" sources="combat.xml" class="Combat"/>}</li>
		 * </ul>
		 */
		private static void parseTasks(Element section, StormData data) {
			forEachElement(section, child -> {
				if (!child.getLocalName().equalsIgnoreCase("task"))
					return;
				
				final var task = new StormTask();
				task.setName(child.getAttribute("name"));
				task.setComment(child.getAttribute("comment"));
				// "class" is a Java keyword – read via plain getAttribute
				task.setTaskClass(child.getAttribute("class"));
				
				// Sources: prefer child <source path="..."/> elements, fall back to "sources" attr
				final List<String> sourcePaths = new ArrayList<>();
				forEachElement(child, sourceEl -> {
					if (sourceEl.getLocalName().equalsIgnoreCase("source")) {
						String path = sourceEl.getAttribute("path");
						if (path.isBlank())
							path = sourceEl.getTextContent().trim();
						if (!path.isBlank())
							sourcePaths.add(path);
					}
				});
				
				if (!sourcePaths.isEmpty()) {
					task.setSources(String.join(",", sourcePaths));
				} else {
					// Flat attribute fallback
					task.setSources(child.getAttribute("sources"));
				}
				
				data.getTasks().add(task);
			});
		}
		
		// =========================================================================
		// Rules
		// =========================================================================
		
		/**
		 * Parse the {@code <rules>} section.
		 *
		 * <pre>{@code
		 * <rules>
		 *   <rule name="myRule" comment="...">
		 *     <selectors> ... </selectors>
		 *     <operations> ... </operations>
		 *   </rule>
		 * </rules>
		 * }</pre>
		 */
		private static void parseRules(Element section, StormData data) {
			forEachElement(section, ruleEl -> {
				if (!ruleEl.getLocalName().equalsIgnoreCase("rule"))
					return;
				
				final var rule = new StormRule();
				rule.setName(ruleEl.getAttribute("name"));
				rule.setComment(ruleEl.getAttribute("comment"));
				rule.setMode(ruleEl.getAttribute("mode"));
				
				forEachElement(ruleEl, part -> {
					final String partTag = part.getLocalName().toLowerCase(Locale.ROOT);
					switch (partTag) {
						// Actual format uses "selectors" (also accept legacy "conditions")
						case "selectors", "conditions" -> parseSelectors(part, rule);
						case "operations"              -> parseOperations(part, rule);
					}
				});
				
				data.getRules().add(rule);
			});
		}
		
		// -------------------------------------------------------------------------
		// Selectors (condition tree)
		// -------------------------------------------------------------------------
		
		private static void parseSelectors(Element selectorsEl, StormRule rule) {
			forEachElement(selectorsEl, child -> rule.getSelectors().add(parseSelector(child)));
		}
		
		/**
		 * Recursively parse a selector element.
		 *
		 * <ul>
		 *   <li>Combinator tags ({@code and}, {@code or}, {@code not}) collect
		 *       their child elements as nested selectors.</li>
		 *   <li>All other tags (e.g. {@code isMan}, {@code hasName}) are leaf
		 *       selectors; their XML attributes populate the attribute map.</li>
		 *   <li>Attribute names are normalised to lower-camel-case so that
		 *       {@code Name} and {@code name} both resolve to {@code name}.</li>
		 * </ul>
		 *
		 * Example:
		 * <pre>{@code
		 * <or>
		 *   <hasName name="tvez_utopenec_1"/>
		 *   <hasName name="tvez_utopenec_2"/>
		 * </or>
		 * }</pre>
		 */
		private static GenericSelector parseSelector(Element el) {
			final String tag = el.getLocalName().toLowerCase(Locale.ROOT);
			final var sel = new GenericSelector(tag);
			
			// Copy XML attributes; normalise "Name" → "name" to handle mixed casing in files
			copyXmlAttrs(el, sel.getAttributes(), true);
			
			// Recurse into children for both combinators and any exotic nested selectors
			forEachElement(el, child -> sel.getChildren().add(parseSelector(child)));
			
			return sel;
		}
		
		// -------------------------------------------------------------------------
		// Operations
		// -------------------------------------------------------------------------
		
		private static void parseOperations(Element operationsEl, StormRule rule) {
			// TODO : check if self closing tag of operations actually work or not
			forEachElement(operationsEl, child -> rule.getOperations().add(parseOperation(child)));
		}
		
		/**
		 * Recursively parse an operation element and its children.
		 * The tag name becomes the operation name; XML attributes become the
		 * operation's attribute map. Child elements become child operations.
		 *
		 * Example:
		 * <pre>{@code
		 * <setUnderwear name="tarasMura_underwear"/>
		 * }</pre>
		 */
		private static GenericOperation parseOperation(Element el) {
			final var op = new GenericOperation(el.getLocalName());
			copyXmlAttrs(el, op.getAttributes(), false);
			
			// Recursively parse any child operations
			forEachElement(el, child -> op.getChildren().add(parseOperation(child)));
			
			return op;
		}
		
		// =========================================================================
		// Serialisation (StormData → XML string)
		// =========================================================================
		
		/**
		 * Serialize a {@link StormData} back to XML that matches the actual on-disk
		 * format: {@code <storm>} root with lowercase section tags.
		 *
		 * @return Pretty-printed XML string.
		 */
		public static String serialize(StormData data) throws Exception {
			var factory = DocumentBuilderFactory.newInstance();
			var docBuilder = factory.newDocumentBuilder();
			var doc = docBuilder.newDocument();
			
			// Root element: <storm> (matches actual file format)
			final Element root = doc.createElement("storm");
			if (data.getCategory() != null)
				root.setAttribute("category", data.getCategory());
			doc.appendChild(root);
			
			// <common>
			if (!data.getCommonSources().isEmpty()) {
				final Element common = doc.createElement("common");
				for (var path : data.getCommonSources()) {
					final Element src = doc.createElement("source");
					src.setAttribute("path", path);
					common.appendChild(src);
				}
				root.appendChild(common);
			}
			
			// <tasks>
			if (!data.getTasks().isEmpty()) {
				final Element tasks = doc.createElement("tasks");
				for (var t : data.getTasks()) {
					final Element task = doc.createElement("task");
					if (!t.getName().isBlank())
						task.setAttribute("name", t.getName());
					if (!t.getTaskClass().isBlank())
						task.setAttribute("class", t.getTaskClass());
					if (!t.getComment().isBlank())
						task.setAttribute("comment", t.getComment());
					
					// Write sources as child elements if comma-separated, else single element
					final String sources = t.getSources();
					if (sources != null && !sources.isBlank()) {
						for (var srcPath : sources.split(",")) {
							final String trimmed = srcPath.trim();
							if (!trimmed.isBlank()) {
								final Element src = doc.createElement("source");
								src.setAttribute("path", trimmed);
								task.appendChild(src);
							}
						}
					}
					tasks.appendChild(task);
				}
				root.appendChild(tasks);
			}
			
			// <customSelectors>
			if (!data.getCustomSelectors().isEmpty()) {
				final Element cs = doc.createElement("customSelectors");
				for (var cSel : data.getCustomSelectors()) {
					final Element sel = doc.createElement("selector");
					sel.setAttribute("name", cSel.getName());
					if (!cSel.getComment().isBlank())
						sel.setAttribute("comment", cSel.getComment());
					for (var attrName : cSel.getAttributeNames()) {
						final Element a = doc.createElement("attribute");
						a.setAttribute("name", attrName);
						sel.appendChild(a);
					}
					cs.appendChild(sel);
				}
				root.appendChild(cs);
			}
			
			// <customOperations>
			if (!data.getCustomOperations().isEmpty()) {
				final Element co = doc.createElement("customOperations");
				for (var cop : data.getCustomOperations()) {
					final Element op = doc.createElement("operation");
					op.setAttribute("name", cop.getName());
					if (!cop.getMode().isBlank())
						op.setAttribute("mode", cop.getMode());
					for (var ma : cop.getModAttributes()) {
						final Element a = doc.createElement("attribute");
						a.setAttribute("stat", ma.getStat());
						a.setAttribute("minMod", String.valueOf(ma.getMinMod()));
						a.setAttribute("maxMod", String.valueOf(ma.getMaxMod()));
						op.appendChild(a);
					}
					co.appendChild(op);
				}
				root.appendChild(co);
			}
			
			// <rules>
			if (!data.getRules().isEmpty()) {
				final Element rules = doc.createElement("rules");
				for (var r : data.getRules()) {
					rules.appendChild(serializeRule(doc, r));
				}
				root.appendChild(rules);
			}
			
			return docToString(doc);
		}
		
		private static Element serializeRule(Document doc, StormRule rule) {
			final Element el = doc.createElement("rule");
			if (!rule.getName().isBlank())
				el.setAttribute("name", rule.getName());
			if (!rule.getMode().isBlank())
				el.setAttribute("mode", rule.getMode());
			if (!rule.getComment().isBlank())
				el.setAttribute("comment", rule.getComment());
			
			// Always emit <selectors> and <operations> elements (even if empty),
			// to stay consistent with the source format.
			final Element selEl = doc.createElement("selectors");
			for (var sel : rule.getSelectors()) {
				selEl.appendChild(serializeSelector(doc, sel));
			}
			el.appendChild(selEl);
			
			final Element opsEl = doc.createElement("operations");
			for (var op : rule.getOperations()) {
				opsEl.appendChild(serializeOperation(doc, op));
			}
			el.appendChild(opsEl);
			
			return el;
		}
		
		/** Recursively serialize a {@link GenericSelector} (supports arbitrary depth). */
		private static Element serializeSelector(Document doc, GenericSelector sel) {
			final Element el = doc.createElement(sel.getName().isBlank() ? "selector" : sel.getName());
			sel.getAttributes().forEach(el::setAttribute);
			for (var child : sel.getChildren()) {
				el.appendChild(serializeSelector(doc, child));
			}
			return el;
		}
		
		/** Recursively serialize a {@link GenericOperation}. */
		private static Element serializeOperation(Document doc, GenericOperation op) {
			final Element el = doc.createElement(op.getName().isBlank() ? "operation" : op.getName());
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
			// Disable DTD loading to avoid network/file-not-found errors with storm.dtd
			f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			f.setFeature("http://xml.org/sax/features/validation", false);
			f.setFeature("http://xml.org/sax/features/external-general-entities", false);
			f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			return f.newDocumentBuilder().parse(is);
		}
		
		/**
		 * Copy all XML element attributes into a String map.
		 *
		 * @param normaliseCase if {@code true}, attribute names are lowercased so that
		 *                      e.g. {@code Name} and {@code name} map to the same key.
		 *                      Useful for selectors where casing varies across files.
		 *                      Pass {@code false} for operations to preserve original casing.
		 */
		private static void copyXmlAttrs(Element el, Map<String, String> target, boolean normaliseCase) {
			final NamedNodeMap xmlAttrs = el.getAttributes();
			for (int i = 0; i < xmlAttrs.getLength(); i++) {
				final var a = (Attr) xmlAttrs.item(i);
				if (a.getLocalName() == null)
					continue;
				final String key = normaliseCase
										   ? a.getLocalName().toLowerCase(Locale.ROOT)
										   : a.getLocalName();
				target.put(key, a.getValue());
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