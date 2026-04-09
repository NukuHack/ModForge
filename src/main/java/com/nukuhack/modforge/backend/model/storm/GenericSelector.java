package com.nukuhack.modforge.backend.model.storm;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * A selector node in a STORM rule. Selectors can be plain selectors
 * (with named attributes) or logical combinators: {@code and}, {@code or}, {@code not}.
 *
 * <p>Combinators are recursive – each child is itself a {@code GenericSelector},
 * so the tree can be arbitrarily deep (the "layered at least 3 times" requirement).</p>
 *
 * <pre>
 * &lt;and&gt;
 *   &lt;selector name="HasItem" itemId="sword_iron"/&gt;
 *   &lt;or&gt;
 *     &lt;selector name="IsAlive"/&gt;
 *     &lt;not&gt;
 *       &lt;selector name="IsDead"/&gt;
 *     &lt;/not&gt;
 *   &lt;/or&gt;
 * &lt;/and&gt;
 * </pre>
 */
@Getter
@Slf4j
public final class GenericSelector {
	
	/**
	 * XML attributes on this selector node (e.g. {@code name="HasItem"}, {@code itemId="sword_iron"}).
	 * Ordered to preserve round-trip fidelity.
	 */
	private final Map<String, String> attributes = new LinkedHashMap<>();
	/**
	 * Child selectors – only populated for combinator nodes ({@code and}/{@code or}/{@code not}).
	 * Never {@code null}; use {@link #isLeaf()} to test.
	 */
	private final List<GenericSelector> children = new ArrayList<>();
	/** Tag name, e.g. {@code "selector"}, {@code "and"}, {@code "or"}, {@code "not"}. */
	private String name = "";
	
	// -------------------------------------------------------------------------
	// Construction helpers
	// -------------------------------------------------------------------------
	
	public GenericSelector() {
	}
	
	public GenericSelector(String name) {
		this.name = name;
	}
	
	/** Factory for a combinator node with the supplied children pre-added. */
	public static GenericSelector combinator(String type, GenericSelector... kids) {
		var s = new GenericSelector(type);
		Collections.addAll(s.children, kids);
		return s;
	}
	
	// -------------------------------------------------------------------------
	// Accessors
	// -------------------------------------------------------------------------
	
	public void setName(String name) {
		this.name = name == null ? "" : name;
	}
	
	public void putAttribute(String key, String value) {
		attributes.put(key, value == null ? "" : value);
	}
	
	/** Returns {@code true} if this node is a logical combinator ({@code and/or/not}). */
	public boolean isCombinator() {
		return "and".equals(name) || "or".equals(name) || "not".equals(name);
	}
	
	/** Returns {@code true} when this node has no children (a leaf selector). */
	public boolean isLeaf() {
		return children.isEmpty();
	}
	
	// -------------------------------------------------------------------------
	// Deep-copy
	// -------------------------------------------------------------------------
	
	public GenericSelector deepCopy() {
		var copy = new GenericSelector(this.name);
		copy.attributes.putAll(this.attributes);
		for (var child : this.children) {
			copy.children.add(child.deepCopy());
		}
		return copy;
	}
	
	@Override
	public String toString() {
		return "GenericSelector{name='" + name + "', attrs=" + attributes.size() + ", children=" + children.size() + "}";
	}
}
