package com.nukuhack.modforge.backend.service;

import com.nukuhack.modforge.backend.model.Attribute;
import com.nukuhack.modforge.backend.model.Attribute.XmlNode;
import com.nukuhack.modforge.backend.model.Attribute.XmlNodeAttribute;
import com.nukuhack.modforge.backend.model.ModItem;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static com.nukuhack.modforge.backend.service.ModItemBuilder.getAttributeFromElement;

/**
 * Builder for items whose XML structure is a tree of nested elements rather than
 * a flat list of attributes (e.g. {@code <InventoryPresets>}, {@code <BehaviorTrees>}).
 *
 * <h3>Naming convention for duplicate sibling tags</h3>
 * When multiple child elements share the same tag name (the common case —
 * many {@code <InventoryPreset>} entries inside one {@code <InventoryPresets>}),
 * each one is stored as a separate {@link XmlNodeAttribute} on the {@link ModItem}
 * with an index suffix so attribute names stay unique.
 * If a tag appears only once the plain name is used without any suffix.
 * {@link #stripIndex(String)} recovers the original tag name during serialization.
 *
 * <h3>Round-trip guarantee</h3>
 * {@link #handle(Element)} → (stored state) → {@link #handle(Document, ModItem)}
 * reconstructs the original XML exactly, including attribute order within each element.
 */
@Slf4j
public class TreeBuilder<M extends ModItem> extends ModItemBuilder.GeneralBuilder<M> {
	
	public TreeBuilder(@NonNull Class<M> type, @NonNull String idKey) {
		super(type, idKey);
	}
	
	/**
	 * Recursively parse a DOM {@link Element} into an {@link XmlNode}.
	 * <ul>
	 *   <li>XML attributes on the element → flat {@link Attribute}s in {@link XmlNode#attributes()}</li>
	 *   <li>Child elements → nested {@link XmlNode}s in {@link XmlNode#children()},
	 *       with index suffixes applied to disambiguate duplicate sibling tags</li>
	 * </ul>
	 */
	public static @NonNull XmlNode parseNode(final @NonNull Element element) {
		
		var attrs = getAttributeFromElement(element);
		
		var children = parseChildNodes(element);
		
		return new XmlNode(element.getTagName(), attrs, children);
	}
	
	/**
	 * Recursively convert an {@link XmlNode} back into a DOM {@link Element}.
	 *
	 * @param document the owner document (needed to create elements)
	 * @param tagName  the element tag — callers must already have stripped any index suffix
	 * @param node     the node to convert
	 */
	public static @NonNull Element serializeNode(final @NonNull Document document, final @NonNull String tagName, final @NonNull XmlNode node) {
		var el = document.createElement(tagName);
		
		for (var attr : node.attributes()) {
			el.setAttribute(attr.getName(), attr.serialize());
		}
		
		for (var child : node.children()) {
			var rawTag = stripIndex(child.tag());
			el.appendChild(serializeNode(document, rawTag, child));
		}
		
		return el;
	}
	
	/**
	 * Collect child elements of {@code parent} into {@link XmlNodeAttribute}s,
	 * applying index-suffix disambiguation for duplicate sibling tags.
	 * Used both by {@link #handle(Element)} (attaching to a {@link ModItem})
	 * and internally.
	 */
	private static @NonNull List<Attribute> parseChildrenAsAttributes(final @NonNull Element parent) {
		var tagSeen = new LinkedHashMap<String, Integer>();
		
		var result = new ArrayList<Attribute>();
		
		var children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			var child = children.item(i);
			if (child.getNodeType() != Node.ELEMENT_NODE || ! (child instanceof Element childEl))
				continue;
			
			var tag = childEl.getTagName();
			var count = tagSeen.merge(tag, 1, Integer::sum);
			
			result.add(new XmlNodeAttribute("__tmp__" + tag + "__" + (count - 1), parseNode(childEl)));
		}
		
		return result.stream().map(attr -> {
			
			var tmp = attr.getName();
			var last = tmp.lastIndexOf("__");
			var tag = tmp.substring(7, last);
			
			var idx = Integer.parseInt(tmp.substring(last + 2));
			var finalName = (tagSeen.get(tag) == 1) ? tag : tag + "[" + idx + "]";
			return (Attribute) new XmlNodeAttribute(finalName, ((XmlNodeAttribute) attr).getValue());
		}).toList();
	}
	
	/**
	 * Recurse into the child elements of {@code parent} and return them as
	 * a list of {@link XmlNode}s with index-suffix disambiguation.
	 * Used by {@link #parseNode(Element)} to build the children list of a node.
	 */
	private static @NonNull List<XmlNode> parseChildNodes(final @NonNull Element parent) {
		var tagSeen = new LinkedHashMap<String, Integer>();
		
		var result = new ArrayList<RawChild>();
		
		var children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			var child = children.item(i);
			if (child.getNodeType() != Node.ELEMENT_NODE || ! (child instanceof Element childEl))
				continue;
			
			var tag = childEl.getTagName();
			var idx = tagSeen.merge(tag, 1, Integer::sum) - 1;
			
			result.add(new RawChild(tag, idx, parseNode(childEl)));
		}
		
		return result.stream().map(rc -> {
			var total = tagSeen.get(rc.tag());
			var finalTag = (total == 1) ? rc.tag() : rc.tag() + "[" + rc.idx() + "]";
			
			if (finalTag.equals(rc.node().tag()))
				return rc.node();
			return new XmlNode(finalTag, rc.node().attributes(), rc.node().children());
		}).toList();
	}
	
	/**
	 * Strip a trailing {@code [N]} index suffix so the original XML tag is recovered.
	 * Examples: {@code "Foo[2]"} → {@code "Foo"},  {@code "Foo"} → {@code "Foo"}.
	 */
	public static String stripIndex(final String name) {
		final int bracket = name.indexOf('[');
		return bracket == - 1 ? name : name.substring(0, bracket);
	}
	
	/**
	 * Parse the root element of a tree-structured item.
	 * Flat XML attributes on the element are stored as ordinary {@link Attribute}s;
	 * child elements are recursively parsed into {@link XmlNodeAttribute}s.
	 */
	@Override
	public ModItem handle(final @NonNull Element element) {
		try {
			if (cons == null)
				return ModItemBuilder.fallbackBuilder.handle(element);
			
			final M item = cons.newInstance();
			item.setId(element.getAttribute(idKey));
			
			var attrs = getAttributeFromElement(element);
			
			var child = parseChildrenAsAttributes(element);
			attrs.addAll(child);
			
			item.setAttribute(attrs);
			return item;
		} catch (Exception e) {
			log.warn("TreeBuilder parse failed for {}: {}", type.getSimpleName(), e.getMessage(), e);
			return null;
		}
	}
	
	/**
	 * Reconstruct the DOM element from a previously parsed tree item.
	 * {@link XmlNodeAttribute}s are written back as child DOM elements;
	 * all other attributes are written as flat XML attributes.
	 */
	@Override
	public @NonNull Element handle(final @NonNull Document document, final @NonNull ModItem item) {
		var entry = ModItemBuilder.group(item);
		var el = document.createElement(entry.xmlObjName);
		
		for (var attr : item.getAttributes()) {
			if (attr instanceof XmlNodeAttribute nodeAttr) {
				var rawTag = stripIndex(nodeAttr.getName());
				var inner = serializeNode(document, rawTag, nodeAttr.getValue());
				el.appendChild(inner);
			} else {
				el.setAttribute(attr.getName(), attr.serialize());
			}
		}
		return el;
	}
	
	/** Tiny carrier used while building children lists before we know the total count. */
	private record RawChild(String tag, int idx, XmlNode node) {
	}
}