package com.nukuhack.modforge.backend.service;

import com.nukuhack.modforge.backend.model.storm.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import com.nukuhack.modforge.backend.model.I.Storm;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.*;
import java.util.function.Consumer;

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
 *   <li>Scan PAK files for Storm XML entries and parse them into {@link com.nukuhack.modforge.backend.model.storm.StormData}.</li>
 *   <li>Attach parsed {@link com.nukuhack.modforge.backend.model.storm.StormData} to the matching {@link Storm} {@code ModItem}.</li>
 *   <li>Write modified {@link com.nukuhack.modforge.backend.model.storm.StormData} back to XML inside a mod's staging folder.</li>
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
 *
 *
 * Parses and serializes STORM XML files.
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
@lombok.extern.slf4j.Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StormService {
	
	final static DocumentBuilder docBuilder;
	/** Path prefix inside PAKs where Storm XML files reside. */
	private static final String STORM_PATH_PREFIX = "Libs/Storm/";
	/** Combinator selector tags that may contain nested selectors. */
	private static final Set<String> COMBINATORS = Set.of("and", "or", "not");
	
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
	
	
	// =========================================================================
	// Public API
	// =========================================================================
	
	/**
	 * Parse a Storm XML {@link InputStream} into a {@link com.nukuhack.modforge.backend.model.storm.StormData} object.
	 *
	 * @param is        The XML input stream (will NOT be closed by this method).
	 * @return Populated {@link com.nukuhack.modforge.backend.model.storm.StormData}; never {@code null}.
	 */
	public static StormData parse(InputStream is) {
		
		try {
			final Document doc = docBuilder.parse(is);
			final Element root = doc.getDocumentElement();
			
			return parse(root);
		} catch (Exception ex) {
			log.warn("StormParser: failed to parse : " + ex.getMessage());
		}
		
		return new StormData();
	}
	
	public static StormData parse(Element element) {
		final StormData data = new StormData();
		// Optional category stored as a root attribute
		final String catAttr = element.getAttribute("category");
		if (! catAttr.isEmpty())
			data.setCategory(catAttr);
		
		parseAllSections(element, data);
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
			if (! (children.item(i) instanceof Element section))
				continue;
			final String tag = section.getTagName().toLowerCase(Locale.ROOT);
			
			switch (tag) {
				case "common" -> parseCommon(section, data);
				case "tasks" -> parseTasks(section, data);
				case "customselectors" -> parseCustomSelectors(section, data);
				case "customoperations" -> parseCustomOperations(section, data);
				case "rules" -> parseRules(section, data);
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
		var list = parseSources(section);
		data.getCommonSources().addAll(list);
	}
	
	private static List<String> parseSources(Element section) {
		final List<String> list = new LinkedList<>();
		forEachElement(section, child -> {
			if (child.getTagName().equalsIgnoreCase("source")) {
				// Prefer "path" attribute; fall back to text content
				String path = child.getAttribute("path");
				if (path.isBlank())
					path = child.getTextContent().trim();
				if (! path.isBlank())
					list.add(path);
			}
		});
		return new ArrayList<>(list);
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
			forEachElement(child, attrEl -> cs.getAttributeNames().add(attrEl.getAttribute("name")));
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
			if (! child.getTagName().equalsIgnoreCase("task"))
				return;
			
			final var task = new StormTask();
			task.setName(child.getAttribute("name"));
			task.setComment(child.getAttribute("comment"));
			// "class" is a Java keyword – read via plain getAttribute
			task.setTaskClass(child.getAttribute("class"));
			
			// Sources: prefer child <source path="..."/> elements, fall back to "sources" attr
			final List<String> sourcePaths = parseSources(child);
			
			if (! sourcePaths.isEmpty()) {
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
			if (! ruleEl.getTagName().equalsIgnoreCase("rule"))
				return;
			
			final var rule = new StormRule();
			rule.setName(ruleEl.getAttribute("name"));
			rule.setComment(ruleEl.getAttribute("comment"));
			rule.setMode(ruleEl.getAttribute("mode"));
			
			forEachElement(ruleEl, part -> {
				final String partTag = part.getTagName().toLowerCase(Locale.ROOT);
				switch (partTag) {
					// Actual format uses "selectors" (also accept legacy "conditions")
					case "selectors", "conditions" ->
							forEachElement(part, child -> rule.getSelectors().add(parseSelector(child)));
					case "operations" -> forEachElement(part, child -> rule.getOperations().add(parseOperation(child)));
				}
			});
			
			data.getRules().add(rule);
		});
	}
	
	// -------------------------------------------------------------------------
	// Selectors (condition tree)
	// -------------------------------------------------------------------------
	
	/**
	 * Recursively parse a selector element.
	 *
	 * <ul>
	 *   <li>Combinator tags ({@code and}, {@code or}, {@code not}) collect
	 *       their child elements as nested selectors.</li>
	 *   <li>All other tags (e.g. {@code isMan}, {@code hasName}) are leaf
	 *       selectors; their XML attributes populate the attribute map.</li>
	 *   <li>Attribute names are normalized to lower-camel-case so that
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
		final var sel = new GenericSelector(el.getTagName());
		copyXmlAttrs(el, sel.getAttributes(), false);
		
		// Recurse into children for both combinators and any exotic nested selectors
		forEachElement(el, child -> sel.getChildren().add(parseSelector(child)));
		
		return sel;
	}
	
	// -------------------------------------------------------------------------
	// Operations
	// -------------------------------------------------------------------------
	
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
		final var op = new GenericOperation(el.getTagName());
		copyXmlAttrs(el, op.getAttributes(), false);
		
		// Recursively parse any child operations
		forEachElement(el, child -> op.getChildren().add(parseOperation(child)));
		
		return op;
	}
	
	// =========================================================================
	// Serialisation (StormData → XML string)
	// =========================================================================
	
	
	public static String serialize(StormData data) throws Exception {
		var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		var el = serialize(data, doc);
		doc.appendChild(el);
		
		final var tf = TransformerFactory.newInstance().newTransformer();
		tf.setOutputProperty(OutputKeys.INDENT, "yes");
		final var writer = new StringWriter();
		tf.transform(new DOMSource(doc), new StreamResult(writer));
		return writer.toString();
	}
	
	/**
	 * Serialize a {@link StormData} back to XML that matches the actual on-disk
	 * format: {@code <storm>} root with lowercase section tags.
	 *
	 * @return Pretty-printed XML string.
	 */
	public static Element serialize(StormData data, Document doc) {
		// Root element: <storm> (matches actual file format)
		final Element root = doc.createElement("storm");
		if (data.getCategory() != null)
			root.setAttribute("category", data.getCategory());
		
		// <common>
		if (! data.getCommonSources().isEmpty()) {
			final Element common = doc.createElement("common");
			for (var path : data.getCommonSources()) {
				final Element src = doc.createElement("source");
				src.setAttribute("path", path);
				common.appendChild(src);
			}
			root.appendChild(common);
		}
		
		// <tasks>
		if (! data.getTasks().isEmpty()) {
			final Element tasks = doc.createElement("tasks");
			for (var t : data.getTasks()) {
				final Element task = doc.createElement("task");
				if (! t.getName().isBlank())
					task.setAttribute("name", t.getName());
				if (! t.getTaskClass().isBlank())
					task.setAttribute("class", t.getTaskClass());
				if (! t.getComment().isBlank())
					task.setAttribute("comment", t.getComment());
				
				// Write sources as child elements if comma-separated, else single element
				final String sources = t.getSources();
				if (sources != null && ! sources.isBlank()) {
					for (var srcPath : sources.split(",")) {
						final String trimmed = srcPath.trim();
						if (! trimmed.isBlank()) {
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
		if (! data.getCustomSelectors().isEmpty()) {
			final Element cs = doc.createElement("customSelectors");
			for (var cSel : data.getCustomSelectors()) {
				final Element sel = doc.createElement("selector");
				sel.setAttribute("name", cSel.getName());
				if (! cSel.getComment().isBlank())
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
		if (! data.getCustomOperations().isEmpty()) {
			final Element co = doc.createElement("customOperations");
			for (var cop : data.getCustomOperations()) {
				final Element op = doc.createElement("operation");
				op.setAttribute("name", cop.getName());
				if (! cop.getMode().isBlank())
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
		if (! data.getRules().isEmpty()) {
			final Element rules = doc.createElement("rules");
			for (var r : data.getRules()) {
				rules.appendChild(serializeRule(doc, r));
			}
			root.appendChild(rules);
		}
		
		return root;
	}
	
	private static Element serializeRule(Document doc, StormRule rule) {
		final Element el = doc.createElement("rule");
		if (! rule.getName().isBlank())
			el.setAttribute("name", rule.getName());
		if (! rule.getMode().isBlank())
			el.setAttribute("mode", rule.getMode());
		if (! rule.getComment().isBlank())
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
	
	/**
	 * Copy all XML element attributes into a String map.
	 */
	private static void copyXmlAttrs(Element el, Map<String, String> target, boolean toLower) {
		final NamedNodeMap xmlAttrs = el.getAttributes();
		for (int i = 0; i < xmlAttrs.getLength(); i++) {
			final var a = (Attr) xmlAttrs.item(i);
			if (a.getName() == null)
				continue;
			target.put(toLower ? a.getName().toLowerCase() : a.getName(), a.getValue());
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
	
	/**
	 * Iterate over the direct element children of {@code parent}, calling
	 * {@code action} for each one. Non-element nodes (text, comments) are skipped.
	 */
	private static void forEachElement(Element parent, Consumer<Element> action) {
		final NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			if (children.item(i) instanceof Element el) {
				action.accept(el);
			}
		}
	}
}