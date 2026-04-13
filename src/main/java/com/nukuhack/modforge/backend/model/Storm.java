package com.nukuhack.modforge.backend.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

public class Storm {

	
	/**
	 * An operation node in a STORM rule. Like {@link GenericSelector}, operations
	 * can carry child operations, making the tree recursive.
	 *
	 * <p>Special-case flags ({@code isStat} / {@code isSpan}) are UI hints from the
	 * C# {@code RuleOperation} component and control which attribute fields are
	 * visible for {@code setAttribute} / {@code modAttribute} operations.</p>
	 */
	@Getter
	@Slf4j
	public static final class GenericOperation {
		
		/**
		 * Key-value attributes for this operation.
		 * Ordered map to preserve serialization round-trips.
		 */
		private final Map<String, String> attributes = new LinkedHashMap<>();
		/**
		 * Nested child operations. Not all operations have children, but the model
		 * supports arbitrary depth.
		 */
		private final List<GenericOperation> children = new ArrayList<>();
		/** XML tag name of this operation, e.g. {@code "setAttribute"}, {@code "addExp"}. */
		private String name = "";
		/**
		 * UI-only flag: when {@code true} the operation targets a Stat, otherwise a Skill.
		 * Only meaningful for {@code setAttribute} / {@code modAttribute}.
		 */
		@Setter
		private boolean isStat = false;
		
		/**
		 * UI-only flag: when {@code true} the operation uses a span (min/max range),
		 * otherwise a fixed value.
		 */
		@Setter
		private boolean isSpan = false;
		
		// -------------------------------------------------------------------------
		// Construction helpers
		// -------------------------------------------------------------------------
		
		public GenericOperation() {
		}
		
		public GenericOperation(String name) {
			this.name = name;
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
		
		// -------------------------------------------------------------------------
		// Deep-copy
		// -------------------------------------------------------------------------
		
		public GenericOperation deepCopy() {
			var copy = new GenericOperation(this.name);
			copy.attributes.putAll(this.attributes);
			copy.isStat = this.isStat;
			copy.isSpan = this.isSpan;
			for (var child : this.children) {
				copy.children.add(child.deepCopy());
			}
			return copy;
		}
		
		@Override
		public String toString() {
			return "GenericOperation{name='" + name + "', attrs=" + attributes.size() + ", children=" + children.size() + "}";
		}
	}
	
	/**
	 * A custom selector declared at the top of a STORM file
	 * (i.e. a named selector template, not a usage instance).
	 */
	@Getter
	@Slf4j
	public static final class CustomStormSelector {
		
		/** Attribute names defined by this custom selector. */
		private final List<String> attributeNames = new ArrayList<>();
		private String name = "";
		private String comment = "";
		
		public void setName(String name) {
			this.name = name == null ? "" : name;
		}
		
		public void setComment(String comment) {
			this.comment = comment == null ? "" : comment;
		}
		
		@Override
		public String toString() {
			return "CustomStormSelector{name='" + name + "'}";
		}
	}
	
	
	/**
	 * A custom operation declared at the top of a STORM file.
	 *
	 * <p>Custom operations can expose "mod attributes" that describe how
	 * a stat can be modified (min/max mod values).</p>
	 *
	 * <p>Example XML:</p>
	 * <pre>{@code
	 * <customOperations>
	 *   <operation name="myOp" mode="add">
	 *     <attribute stat="Strength" minMod="0" maxMod="1"/>
	 *   </operation>
	 * </customOperations>
	 * }</pre>
	 */
	@Getter
	@Slf4j
	public static final class CustomStormOperation {
		
		/** Attribute ranges exposed by this operation. */
		private final List<ModAttribute> modAttributes = new ArrayList<>();
		
		private String name = "";
		
		/**
		 * Optional mode string from the {@code mode} XML attribute (e.g. {@code "add"},
		 * {@code "set"}). Empty string when absent.
		 */
		private String mode = "";
		
		// -------------------------------------------------------------------------
		
		public void setName(String name) {
			this.name = name == null ? "" : name;
		}
		
		public void setMode(String mode) {
			this.mode = mode == null ? "" : mode;
		}
		
		@Override
		public String toString() {
			return "CustomStormOperation{name='" + name + "', mode='" + mode + "', modAttrs=" + modAttributes.size() + "}";
		}
		
		// -------------------------------------------------------------------------
		// Nested type
		// -------------------------------------------------------------------------
		
		/**
		 * Describes the numeric range of a modifiable attribute within a custom operation.
		 */
		@Getter
		public static final class ModAttribute {
			private String stat = "";
			@Setter
			private double minMod = 0;
			@Setter
			private double maxMod = 0;
			
			public void setStat(String stat) {
				this.stat = stat == null ? "" : stat;
			}
			
			@Override
			public String toString() {
				return "ModAttribute{stat='" + stat + "', range=[" + minMod + "," + maxMod + "]}";
			}
		}
	}
	
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
	public static final class GenericSelector {
		
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
	
	
	/**
	 * The fully-parsed content of a single {@code .storm} / Storm XML file.
	 *
	 * <p>Mirrors the C# {@code StormDto} class while dropping Blazor-specific
	 * rendering concerns.</p>
	 *
	 * <p>Structure of a Storm file (simplified):</p>
	 * <pre>
	 * &lt;storm&gt;
	 *   &lt;common&gt;
	 *     &lt;source path="..."/&gt;
	 *   &lt;/common&gt;
	 *   &lt;customSelectors&gt; ... &lt;/customSelectors&gt;
	 *   &lt;customOperations&gt; ... &lt;/customOperations&gt;
	 *   &lt;tasks&gt; ... &lt;/tasks&gt;
	 *   &lt;rules&gt;
	 *     &lt;rule name="..." comment="..."&gt;
	 *       &lt;selectors&gt;
	 *         &lt;and&gt;
	 *           &lt;selector .../&gt;
	 *           &lt;or&gt;
	 *             &lt;selector .../&gt;
	 *             &lt;not&gt;&lt;selector .../&gt;&lt;/not&gt;
	 *           &lt;/or&gt;
	 *         &lt;/and&gt;
	 *       &lt;/selectors&gt;
	 *       &lt;operations&gt;
	 *         &lt;setAttribute ...&gt;
	 *           &lt;subOp .../&gt;
	 *         &lt;/setAttribute&gt;
	 *       &lt;/operations&gt;
	 *     &lt;/rule&gt;
	 *   &lt;/rules&gt;
	 * &lt;/storm&gt;
	 * </pre>
	 */
	@Getter
	@Slf4j
	public static final class StormData {
		
		private final List<String> commonSources = new ArrayList<>();
		private final List<CustomStormSelector> customSelectors = new ArrayList<>();
		private final List<CustomStormOperation> customOperations = new ArrayList<>();
		private final List<StormTask> tasks = new ArrayList<>();
		private final List<StormRule> rules = new ArrayList<>();
		
		/** Stable identifier derived from the file path (used for navigation). */
		@Setter
		private String id = "";
		
		/**
		 * Optional category grouping (derived from the file path or a {@code category}
		 * attribute on the root element). {@code null} means "Miscellaneous".
		 */
		@Setter
		private String category = null;
		
		@Override
		public String toString() {
			return String.format("StormData{id='%s', category=%s, commonSources=%d, rules=%d, tasks=%d}", id, category, commonSources.size(), rules.size(), tasks.size());
		}
	}
	
	/**
	 * A single rule inside a STORM file.
	 *
	 * <p>A rule bundles a list of {@link GenericSelector selectors} (the "when" side)
	 * with a list of {@link GenericOperation operations} (the "then" side).</p>
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
	 */
	@Getter
	@Slf4j
	public static final class StormRule {
		
		private final List<GenericSelector> selectors = new ArrayList<>();
		private final List<GenericOperation> operations = new ArrayList<>();
		
		private String name = "";
		private String comment = "";
		
		/**
		 * Optional mode string from the {@code mode} XML attribute.
		 * Empty string when absent.
		 */
		private String mode = "";
		
		// -------------------------------------------------------------------------
		// Accessors
		// -------------------------------------------------------------------------
		
		public void setName(String name) {
			this.name = name == null ? "" : name;
		}
		
		public void setComment(String comment) {
			this.comment = comment == null ? "" : comment;
		}
		
		public void setMode(String mode) {
			this.mode = mode == null ? "" : mode;
		}
		
		@Override
		public String toString() {
			return "StormRule{name='" + name + "', mode='" + mode + "', selectors=" + selectors.size() + ", operations=" + operations.size() + "}";
		}
	}
	
	/**
	 * A task declaration inside a STORM file.
	 */
	@ToString
	@Getter
	@Slf4j
	@NoArgsConstructor
	public static final class StormTask {
		
		private String name = "";
		private String comment = "";
		private String sources = "";
		/** The scripting class name wired to this task. */
		private String taskClass = "";
		
		// -------------------------------------------------------------------------
		// Accessors
		// -------------------------------------------------------------------------
		
		public void setName(String name) {
			this.name = name == null ? "" : name;
		}
		
		public void setComment(String comment) {
			this.comment = comment == null ? "" : comment;
		}
		
		public void setSources(String sources) {
			this.sources = sources == null ? "" : sources;
		}
		
		public void setTaskClass(String taskClass) {
			this.taskClass = taskClass == null ? "" : taskClass;
		}
	}
	
}
