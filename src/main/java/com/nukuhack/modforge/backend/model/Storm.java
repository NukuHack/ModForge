package com.nukuhack.modforge.backend.model;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * All Storm-specific model types now live here as lightweight value types.
 *
 * <h2>UI-only flags</h2>
 * <p>The old {@code isStat} / {@code isSpan} flags that lived on
 * {@code GenericOperation} are stored as synthetic attributes with
 * underscore-prefixed keys ({@code "_isStat"}, {@code "_isSpan"}) on the
 * operation {@link Attribute.XmlNode}.  The serializer in
 * {@link com.nukuhack.modforge.backend.service.StormService} skips any
 * attribute whose name starts with {@code "_"}, so they never reach the
 * output XML.</p>
 *
 * {@code ModItem} representation of a Storm script entry.
 *
 * <p>The five parsed Storm sections live directly on this item rather than
 * inside a separate {@code StormData} wrapper object.  The wrapper added no
 * value: its {@code id} / {@code category} fields already belong to the item
 * layer ({@code id} via {@link ModItem}, {@code category} as a plain field
 * here), and its lists are just lists.</p>
 *
 * <h2>Section fields</h2>
 * <ul>
 *   <li>{@link #commonSources} — raw path strings from
 *       {@code <common><source path="…"/></common>}.  A plain
 *       {@code List<String>} because there is genuinely nothing node-like
 *       about a bare path.</li>
 *   <li>{@link #customSelectors}, {@link #customOperations}, {@link #tasks}
 *       — each entry is an {@link Attribute.XmlNode} whose tag is the XML
 *       element name and whose {@link Attribute.StringAttribute} children
 *       carry the XML attribute key-value pairs.  Nested child elements
 *       become child nodes.</li>
 *   <li>{@link #rules} — each entry is a {@link Storm.StormRule} record that
 *       keeps the rule's own attributes, its selector tree, and its operation
 *       tree in three clearly typed fields rather than burying them in wrapper
 *       child nodes.</li>
 * </ul>
 *
 * <h2>Relation to the base-class attribute list</h2>
 * <p>The inherited {@link ModItem#getAttributes()} list is still populated by
 * {@code ModItemBuilder} for the normal item-list display pipeline (search,
 * filter, etc.).  The Storm-specific fields above are populated separately by
 * {@link com.nukuhack.modforge.backend.service.StormService} after the PAK
 * scan and are orthogonal to the flat attribute list.</p>
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@Slf4j
public class Storm extends ModItem {

    /**
     * Global source paths from {@code <common><source path="…"/></common>}.
     */
    private final List<String> commonSources = new ArrayList<>();
    /**
     * Custom selectors declared in {@code <customSelectors>}.
     * Each node's tag is {@code "selector"}; its attributes hold
     * {@code name} / {@code comment}; child nodes represent
     * {@code <attribute name="…"/>} entries.
     */
    private final List<Attribute.XmlNode> customSelectors = new ArrayList<>();
    /**
     * Custom operations declared in {@code <customOperations>}.
     * Each node's tag is {@code "operation"}; its attributes hold
     * {@code name} / {@code mode}; child nodes represent
     * {@code <attribute stat="…" minMod="…" maxMod="…"/>} entries.
     */
    private final List<Attribute.XmlNode> customOperations = new ArrayList<>();
    /**
     * Tasks declared in {@code <tasks>}.
     * Each node's tag is {@code "task"}; its attributes hold
     * {@code name}, {@code class}, {@code comment}, and {@code sources}
     * (a comma-joined path list, normalized from child {@code <source>}
     * elements by the parser).
     */
    private final List<Attribute.XmlNode> tasks = new ArrayList<>();
    /**
     * Rules declared in {@code <rules>}.
     * Each {@link Storm.StormRule} record holds the rule's own XML
     * attributes plus its selector and operation trees as separate
     * {@link Attribute.XmlNode} lists.
     */
    private final List<com.nukuhack.modforge.backend.model.Storm.StormRule> rules = new ArrayList<>();
    /**
     * Optional category grouping derived from the {@code category} attribute
     * on the root {@code <storm>} element.  {@code null} means "Miscellaneous".
     */
    private String category = null;

    /**
     * Build an {@link Attribute.XmlNode} from a tag name and an ordered map of
     * XML attributes.  The returned node has a mutable children list so the
     * parser can append to it.
     */
    public static Attribute.XmlNode node(String tag, Map<String, String> attrs) {
        var attrList = new ArrayList<Attribute>();
        attrs.forEach((k, v) -> attrList.add(new Attribute.StringAttribute(k, v)));
        return new Attribute.XmlNode(tag, attrList, new ArrayList<>());
    }

    /**
     * Build a leaf {@link Attribute.XmlNode} with no attributes and no children.
     */
    public static Attribute.XmlNode node(String tag) {
        return new Attribute.XmlNode(tag, new ArrayList<>(), new ArrayList<>());
    }

    /**
     * Read the {@code _isStat} synthetic flag stored on an operation node.
     * Defaults to {@code false} when absent.
     */
    public static boolean isStatOp(Attribute.XmlNode opNode) {
        return opNode.attributes().stream()
                .filter(a -> "_isStat".equals(a.getName()))
                .map(a -> Boolean.parseBoolean(a.getValue().toString()))
                .findFirst().orElse(false);
    }

    /**
     * Read the {@code _isSpan} synthetic flag stored on an operation node.
     * Defaults to {@code false} when absent.
     */
    public static boolean isSpanOp(Attribute.XmlNode opNode) {
        return opNode.attributes().stream()
                .filter(a -> "_isSpan".equals(a.getName()))
                .map(a -> Boolean.parseBoolean(a.getValue().toString()))
                .findFirst().orElse(false);
    }

    /**
     * Returns {@code true} when no Storm sections have been populated yet.
     */
    public boolean isStormLoaded() {
        return !rules.isEmpty() || !tasks.isEmpty() || !customSelectors.isEmpty() || !customOperations.isEmpty() || !commonSources.isEmpty();
    }

    /**
     * A single rule inside a STORM file.
     *
     * <p>Kept as a dedicated type (rather than a plain {@link Attribute.XmlNode})
     * because it carries two structurally distinct child lists — selectors and
     * operations — along with its own named attributes.  Merging them into one
     * node would require callers to locate wrapper children by tag-name
     * convention rather than by field access.</p>
     *
     * <p>The rule's own XML attributes ({@code name}, {@code mode},
     * {@code comment}, …) are stored in {@link #attrs} as an
     * {@link Attribute.XmlNode} with tag {@code "rule"} and an empty children
     * list — the children lists are {@link #selectors} and {@link #operations}
     * instead.</p>
     *
     * <p>Example XML:</p>
     * <pre>{@code
     * <rule name="underwear_tarasMura_taras" mode="..." comment="...">
     *   <selectors>
     *     <hasName name="ksta_taras"/>
     *   </selectors>
     *   <operations>
     *     <setUnderwear name="tarasMura_underwear"/>
     *   </operations>
     * </rule>
     * }</pre>
     *
     * @param attrs      The rule's own XML attributes (name, mode, comment, …),
     *                   stored in an {@link Attribute.XmlNode} with tag
     *                   {@code "rule"}.  Never {@code null}.
     * @param selectors  Parsed selector trees (recursively nested
     *                   {@link Attribute.XmlNode}s).  Never {@code null}.
     * @param operations Parsed operation trees (recursively nested
     *                   {@link Attribute.XmlNode}s).  Never {@code null}.
     */
    @NonNull
    public record StormRule(
            Attribute.XmlNode attrs,
            List<Attribute.XmlNode> selectors,
            List<Attribute.XmlNode> operations
    ) {

        /**
         * Convenience accessor — equivalent to {@code attrs().tag()}.
         */
        public String name() {
            return attrValue("name");
        }

        public String mode() {
            return attrValue("mode");
        }

        public String comment() {
            return attrValue("comment");
        }

        private String attrValue(String key) {
            return attrs.attributes().stream()
                    .filter(a -> key.equals(a.getName()))
                    .map(a -> a.getValue().toString())
                    .findFirst().orElse("");
        }

        @Override
        public String toString() {
            return "StormRule{name='" + name() + "', mode='" + mode()
                    + "', selectors=" + selectors.size()
                    + ", operations=" + operations.size() + "}";
        }
    }
}