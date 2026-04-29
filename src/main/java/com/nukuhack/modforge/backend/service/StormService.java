package com.nukuhack.modforge.backend.service;

import com.nukuhack.modforge.Singleton;
import com.nukuhack.modforge.backend.model.Attribute;
import com.nukuhack.modforge.backend.model.Storm;
import com.nukuhack.modforge.backend.model.Storm.StormRule;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

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
 *   <li>Scan PAK files for Storm XML entries and parse them into
 *       {@link Storm}.</li>
 *   <li>Write modified Storm items back to XML inside a mod's staging
 *       folder.</li>
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
 * <p>All tag matching is case-insensitive so files that deviate slightly in
 * casing still parse correctly.</p>
 *
 * <p>Tasks may carry their source list as either child
 * {@code <source path="..."/>} elements or as a flat {@code sources="..."}
 * attribute (older format).  Both are handled; the parser normalizes them to
 * a comma-joined {@code sources} attribute on the task node.</p>
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StormService {

    /**
     * Parse a Storm XML {@link InputStream} into an {@link Storm} item.
     * The caller is responsible for setting {@code id} and {@code path} on the
     * returned item.
     *
     * @param is The XML input stream (will NOT be closed by this method).
     * @return Populated Storm item; never {@code null}.
     */
    public static Storm parse(InputStream is) {
        try {
            var doc = Singleton.DOC_BUILDER.get().parse(is);
            return parse(doc.getDocumentElement());
        } catch (Exception ex) {
            log.warn("StormParser: failed to parse: {}", ex.getMessage());
        }
        return new Storm();
    }

    /**
     * Parse a Storm XML root {@link Element} into an
     * {@link Storm} item.
     */
    public static Storm parse(Element root) {
        var item = new Storm();

        var cat = root.getAttribute("category");
        if (!cat.isEmpty())
            item.setCategory(cat);

        parseAllSections(root, item);
        return item;
    }

    /**
     * Dispatch each direct child element of {@code root} to the appropriate
     * section parser.  Tag matching is case-insensitive.
     */
    private static void parseAllSections(Element root, Storm item) {
        forEachElement(root, section -> {
            switch (section.getTagName().toLowerCase(Locale.ROOT)) {
                case "common" -> parseCommon(section, item);
                case "tasks" -> parseTasks(section, item);
                case "customselectors" -> parseCustomSelectors(section, item);
                case "customoperations" -> parseCustomOperations(section, item);
                case "rules" -> parseRules(section, item);
            }
        });
    }

    /**
     * Parse the optional {@code <common>} section.
     *
     * <pre>{@code
     * <common>
     *   <source path="Libs/Storm/Base/base.xml"/>
     * </common>
     * }</pre>
     */
    private static void parseCommon(Element section, Storm item) {
        item.getCommonSources().addAll(parseSources(section));
    }

    private static List<String> parseSources(Element parent) {
        var list = new ArrayList<String>();
        forEachElement(parent, child -> {
            if (child.getTagName().equalsIgnoreCase("source")) {
                var path = child.getAttribute("path");
                if (path.isBlank()) path = child.getTextContent().trim();
                if (!path.isBlank()) list.add(path);
            }
        });
        return list;
    }

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
     *
     * <p>Each {@code <selector>} becomes an {@link Attribute.XmlNode} whose
     * tag is {@code "selector"}, whose attributes hold {@code name} /
     * {@code comment}, and whose children are the {@code <attribute>} nodes.</p>
     */
    private static void parseCustomSelectors(Element section, Storm item) {
        forEachElement(section, child -> item.getCustomSelectors().add(parseShallowNode(child)));
    }

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
     *
     * <p>Each {@code <operation>} becomes an {@link Attribute.XmlNode} whose
     * children are the {@code <attribute>} mod-range nodes.</p>
     */
    private static void parseCustomOperations(Element section, Storm item) {
        forEachElement(section, child -> item.getCustomOperations().add(parseShallowNode(child)));
    }

    /**
     * Parse the optional {@code <tasks>} section.
     *
     * <p>Two source formats are supported:</p>
     * <ul>
     *   <li><b>Child elements</b>:
     *       {@code <task name="Combat" class="Combat"><source path="combat.xml"/></task>}</li>
     *   <li><b>Flat attribute</b>:
     *       {@code <task name="Combat" sources="combat.xml" class="Combat"/>}</li>
     * </ul>
     *
     * <p>Both are normalized: the parser always stores paths as a
     * comma-joined {@code sources} attribute on the task node so callers
     * do not need to handle both forms.</p>
     */
    private static void parseTasks(Element section, Storm item) {
        forEachElement(section, child -> {
            if (!child.getTagName().equalsIgnoreCase("task")) return;

            var attrs = readXmlAttrs(child);

            var sourcePaths = parseSources(child);
            if (!sourcePaths.isEmpty())
                attrs.put("sources", String.join(",", sourcePaths));

            item.getTasks().add(Storm.node(child.getTagName(), attrs));
        });
    }

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
     *
     * <p>Each rule becomes a {@link StormRule} record with:</p>
     * <ul>
     *   <li>{@code attrs} — an {@link Attribute.XmlNode} carrying the rule's own
     *       XML attributes ({@code name}, {@code mode}, {@code comment}, …)</li>
     *   <li>{@code selectors} — recursively parsed selector trees</li>
     *   <li>{@code operations} — recursively parsed operation trees</li>
     * </ul>
     */
    private static void parseRules(Element section, Storm item) {
        forEachElement(section, ruleEl -> {
            if (!ruleEl.getTagName().equalsIgnoreCase("rule")) return;

            var attrsNode = Storm.node("rule", readXmlAttrs(ruleEl));
            var selectors = new ArrayList<Attribute.XmlNode>();
            var operations = new ArrayList<Attribute.XmlNode>();

            forEachElement(ruleEl, part -> {
                switch (part.getTagName().toLowerCase(Locale.ROOT)) {
                    case "selectors", "conditions" ->
                            forEachElement(part, child -> selectors.add(parseDeepNode(child)));
                    case "operations" -> forEachElement(part, child -> operations.add(parseDeepNode(child)));
                }
            });

            item.getRules().add(new StormRule(attrsNode, selectors, operations));
        });
    }

    /**
     * Parse a single element into a <em>shallow</em> {@link Attribute.XmlNode}:
     * the element's own XML attributes become the node's attributes, and its
     * direct child elements become non-recursive child nodes (each carrying
     * only their own XML attributes, no deeper nesting).
     *
     * <p>Used for {@code customSelectors} and {@code customOperations} sections
     * which are always exactly one level of children deep in practice.</p>
     */
    private static Attribute.XmlNode parseShallowNode(Element el) {
        var node = Storm.node(el.getTagName(), readXmlAttrs(el));
        forEachElement(el, child ->
                node.children().add(Storm.node(child.getTagName(), readXmlAttrs(child))));
        return node;
    }

    /**
     * Recursively parse an element and all its descendants into a
     * {@link Attribute.XmlNode} tree.
     *
     * <p>Used for rule selector and operation trees which can be arbitrarily
     * deep (e.g. {@code <and><or><not>…</not></or></and>}).</p>
     */
    private static Attribute.XmlNode parseDeepNode(Element el) {
        var node = Storm.node(el.getTagName(), readXmlAttrs(el));
        forEachElement(el, child -> node.children().add(parseDeepNode(child)));
        return node;
    }

    /**
     * Serialize an {@link Storm} item back
     * to a pretty-printed XML string that matches the on-disk format.
     */
    public static String serialize(Storm item) throws Exception {
        var doc = Singleton.DOC_BUILDER.get().newDocument();
        doc.appendChild(serialize(item, doc));
        var tf = TransformerFactory.newInstance().newTransformer();
        tf.setOutputProperty(OutputKeys.INDENT, "yes");
        var writer = new StringWriter();
        tf.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    /**
     * Serialize an {@link Storm} item into
     * a DOM {@link Element} for embedding in a larger document.
     */
    public static Element serialize(Storm item, Document doc) {
        var root = doc.createElement("storm");

        if (item.getCategory() != null)
            root.setAttribute("category", item.getCategory());

        if (!item.getCommonSources().isEmpty()) {
            var common = doc.createElement("common");
            for (var path : item.getCommonSources()) {
                var src = doc.createElement("source");
                src.setAttribute("path", path);
                common.appendChild(src);
            }
            root.appendChild(common);
        }

        if (!item.getTasks().isEmpty()) {
            var tasksEl = doc.createElement("tasks");
            for (var taskNode : item.getTasks()) {
                tasksEl.appendChild(serializeTask(doc, taskNode));
            }
            root.appendChild(tasksEl);
        }

        if (!item.getCustomSelectors().isEmpty()) {
            var cs = doc.createElement("customSelectors");
            for (var n : item.getCustomSelectors())
                cs.appendChild(serializeNode(doc, n));
            root.appendChild(cs);
        }

        if (!item.getCustomOperations().isEmpty()) {
            var co = doc.createElement("customOperations");
            for (var n : item.getCustomOperations())
                co.appendChild(serializeNode(doc, n));
            root.appendChild(co);
        }

        if (!item.getRules().isEmpty()) {
            var rulesEl = doc.createElement("rules");
            for (var rule : item.getRules())
                rulesEl.appendChild(serializeRule(doc, rule));
            root.appendChild(rulesEl);
        }

        return root;
    }

    /**
     * Serialize a task node.  The normalized {@code sources} attribute is
     * expanded back to child {@code <source path="…"/>} elements to match the
     * canonical on-disk format.
     */
    private static Element serializeTask(Document doc, Attribute.XmlNode taskNode) {
        var el = doc.createElement(taskNode.tag());

        for (var attr : taskNode.attributes()) {
            if (attr.getName().startsWith("_")) continue;

            if ("sources".equals(attr.getName())) {

                for (var rawPath : attr.getValue().toString().split(",")) {
                    var trimmed = rawPath.trim();
                    if (!trimmed.isBlank()) {
                        var src = doc.createElement("source");
                        src.setAttribute("path", trimmed);
                        el.appendChild(src);
                    }
                }
            } else {
                el.setAttribute(attr.getName(), attr.getValue().toString());
            }
        }
        return el;
    }

    /**
     * Serialize a {@link StormRule} back to a {@code <rule>} element with
     * {@code <selectors>} and {@code <operations>} child wrappers.
     */
    private static Element serializeRule(Document doc, StormRule rule) {
        var el = doc.createElement("rule");

        for (var attr : rule.attrs().attributes()) {
            if (!attr.getName().startsWith("_"))
                el.setAttribute(attr.getName(), attr.getValue().toString());
        }

        var selEl = doc.createElement("selectors");
        for (var sel : rule.selectors())
            selEl.appendChild(serializeNode(doc, sel));
        el.appendChild(selEl);

        var opsEl = doc.createElement("operations");
        for (var op : rule.operations())
            opsEl.appendChild(serializeNode(doc, op));
        el.appendChild(opsEl);

        return el;
    }

    /**
     * Recursively serialize an {@link Attribute.XmlNode} tree.
     * Attributes whose name starts with {@code "_"} are skipped (they are
     * synthetic UI-only flags and must not appear in the output XML).
     */
    private static Element serializeNode(Document doc, Attribute.XmlNode node) {
        var tag = node.tag().isBlank() ? "node" : node.tag();
        var el = doc.createElement(tag);
        for (var attr : node.attributes()) {
            if (!attr.getName().startsWith("_"))
                el.setAttribute(attr.getName(), attr.getValue().toString());
        }
        for (var child : node.children())
            el.appendChild(serializeNode(doc, child));
        return el;
    }

    /**
     * Read all XML element attributes into a new, ordered {@link LinkedHashMap}.
     */
    private static Map<String, String> readXmlAttrs(Element el) {
        var map = new LinkedHashMap<String, String>();
        var xmlAttrs = el.getAttributes();
        for (int i = 0; i < xmlAttrs.getLength(); i++) {
            var a = (Attr) xmlAttrs.item(i);
            var name = a.getName();
            if (name != null)
                map.put(name, a.getValue());
        }
        return map;
    }

    /**
     * Iterate over the direct element children of {@code parent}, invoking
     * {@code action} for each one.  Non-element nodes (text, comments) are
     * skipped.
     */
    private static void forEachElement(Element parent, Consumer<Element> action) {
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el)
                action.accept(el);
        }
    }
}