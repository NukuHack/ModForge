package com.nukuhack.modforge.backend.model;

import com.nukuhack.modforge.backend.model.E.MathOperation;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public interface Attribute<T> {
	String INDENT = "  ";
	
	String getName();
	
	T getValue();
	
	Attribute<T> deepClone();
	
	String serialize();
	
	Attribute<T> withValue(T val);
	
	
	@Getter
	@Slf4j
	@RequiredArgsConstructor
	abstract class BaseAttribute<T> implements Attribute<T> {
		@NonNull
		protected final String name;
		@NonNull
		protected final T value;
		
		public String toString() {
			return name + "=" + value;
		}
		
		
		/**
		 * Helper method to deep clone nested lists
		 */
		protected <M> List<M> deepCloneList(List<M> list) {
			if (list == null)
				return null;
			
			List<M> clonedList = new ArrayList<>(list.size());
			for (M e : list) {
				if (e instanceof Attribute<?> a) {
					clonedList.add((M) a.deepClone());
				} else if (e instanceof Collection<?>) {
					throw new IllegalArgumentException("No nesting inside attributes, Use ListAttribute for that");
				} else {
					clonedList.add(e);
				}
			}
			return clonedList;
		}
		
		@Override
		public boolean equals(Object o) {
			if (o == null || getClass() != o.getClass())
				return false;
			Attribute<?> attribute = (Attribute<?>) o;
			return Objects.equals(name, attribute.getName()) && Objects.equals(value, attribute.getValue());
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(name, value);
		}
	}
	
	@Slf4j
	class BooleanAttribute extends BaseAttribute<Boolean> {
		public BooleanAttribute(String name, Boolean value) {
			super(name, value);
		}
		
		@Override
		public BooleanAttribute deepClone() {
			return this;
		}
		
		@Override
		public BooleanAttribute withValue(Boolean newValue) {
			return new BooleanAttribute(name, newValue);
		}
		
		@Override
		public String serialize() {
			return value.toString().toLowerCase();
		}
	}
	
	@Slf4j
	class DoubleAttribute extends BaseAttribute<Double> {
		public DoubleAttribute(String name, Double value) {
			super(name, value);
		}
		
		@Override
		public DoubleAttribute deepClone() {
			return this;
		}
		
		@Override
		public DoubleAttribute withValue(Double newValue) {
			return new DoubleAttribute(name, newValue);
		}
		
		@Override
		public String serialize() {
			if (Double.isInfinite(value) || Double.isNaN(value))
				return "1";
			long rounded = Math.round(value);
			if (Math.abs(value - rounded) < 1e-8)
				return String.valueOf(rounded);
			return value.toString();
		}
	}
	
	@Slf4j
	class EnumAttribute extends BaseAttribute<Enum<?>> {
		@Getter
		private final Class<? extends Enum<?>> enumType;
		
		public EnumAttribute(String name, Enum<?> value) {
			super(name, value);
			this.enumType = value.getDeclaringClass();
		}
		
		@Override
		public EnumAttribute deepClone() {
			return this;
		}
		
		@Override
		public EnumAttribute withValue(Enum<?> newValue) {
			return new EnumAttribute(name, newValue);
		}
		
		@Override
		public String serialize() {
			return String.valueOf(value.ordinal());
		}
	}
	
	@Slf4j
	class ListAttribute<M> extends BaseAttribute<List<M>> {
		public ListAttribute(String name, List<M> value) {
			super(name, value);
		}

		@Override
		public ListAttribute<M> deepClone() {
			return new ListAttribute<>(getName(), deepCloneList(value));
		}

		@Override
		public ListAttribute<M> withValue(List<M> newValue) {
			return new ListAttribute<>(name, deepCloneList(newValue));
		}

		@Override
		public String serialize() {
			if (value.isEmpty())
				return "";
			return value.stream().map(item -> (item instanceof Attribute<?> a ? a.serialize() : item.toString()).trim()).filter(Predicate.not(String::isEmpty)).collect(Collectors.joining(","));
		}
	}
	
	@Slf4j
	class BuffParamListAttribute extends ListAttribute<BuffParam> {
		public BuffParamListAttribute(String name, List<BuffParam> value) {
			super(name, value);
		}
		
		public BuffParamListAttribute(String name, String value) {
			this(name, parse(value));
		}
		

		@Override
		public BuffParamListAttribute deepClone() {
			return new BuffParamListAttribute(getName(), deepCloneList(value));
		}

		@Override
		public BuffParamListAttribute withValue(List<BuffParam> newValue) {
			return new BuffParamListAttribute(name, deepCloneList(newValue));
		}
		
		public static List<BuffParam> parse(final String data) {
			return Arrays.stream(data.split(",")).map(BuffParam::fromString).filter(Objects::nonNull).toList();
		}
		
		public String serialize() {
			return String.join(",", value.stream().map(BuffParam::serialize).toList());
		}
		
		public String getNiceName() {
			return value.stream().map(BuffParam::beautify).collect(Collectors.joining(", "));
		}
	}
	
	@Slf4j
	class StringAttribute extends BaseAttribute<String> {
		public StringAttribute(String name, String value) {
			super(name, value);
		}
		
		@Override
		public StringAttribute deepClone() {
			return this;
		}
		
		@Override
		public StringAttribute withValue(String newValue) {
			return new StringAttribute(name, newValue);
		}
		
		@Override
		public String serialize() {
			return value;
		}
	}
	
	@Slf4j
	class UUIDAttribute extends BaseAttribute<UUID> {
		public UUIDAttribute(String name, UUID value) {
			super(name, value);
		}
		
		@Override
		public UUIDAttribute deepClone() {
			return this;
		}
		
		@Override
		public UUIDAttribute withValue(UUID newValue) {
			return new UUIDAttribute(name, newValue);
		}
		
		@Override
		public String serialize() {
			return value.toString();
		}
	}
	
	
	class XmlNodeAttribute extends BaseAttribute<XmlNode> {
		public XmlNodeAttribute(String name, XmlNode value) {
			super(name, value);
		}
		
		@Override
		public XmlNodeAttribute deepClone() {
			return new XmlNodeAttribute(name, deepCloneNode(value));
		}
		
		@Override
		public XmlNodeAttribute withValue(XmlNode newValue) {
			return new XmlNodeAttribute(name, deepCloneNode(newValue));
		}
		
		@Override
		public String serialize() {
			return serializeNode(value, 1);
		}
		
		private XmlNode deepCloneNode(XmlNode node) {
			var clonedAttrs = node.attributes().stream().map(Attribute::deepClone).toList();
			var clonedChildren = node.children().stream().map(this::deepCloneNode).toList();
			return new XmlNode(node.tag(), clonedAttrs, clonedChildren);
		}
		
		private String serializeNode(XmlNode node, int depth) {
			var indent = INDENT.repeat(depth);
			var sb = new StringBuilder();
			sb.append(indent).append("<").append(node.tag());
			for (var attr : node.attributes())
				sb.append(" ").append(attr.getName()).append("=\"").append(attr.serialize()).append("\"");
			if (node.isLeaf())
				return sb.append(" />").toString();
			
			sb.append(">\n");
			for (var child : node.children())
				sb.append(serializeNode(child, depth + 1)).append("\n");
			sb.append(indent).append("</").append(node.tag()).append(">");
			
			return sb.toString();
		}
	}
	
	
	record XmlNode(String tag, List<Attribute> attributes, List<XmlNode> children) {
		public boolean isLeaf() {
			return children.isEmpty();
		}
		
		public int getDepth() {
			if (children.isEmpty())
				return 0;
			return 1 + children.stream().mapToInt(XmlNode::getDepth).max().orElse(0);
		}
		
		public ModItem asItem() {
			var item = new ModItem.EmptyImpl();
			item.setId(tag);
			item.setAttribute(attributes);
			var xmlAttr = children.stream().map(c -> (Attribute) new XmlNodeAttribute(c.tag, c)).toList();
			item.addAttribute(xmlAttr);
			return item;
		}
	}
	
	/**
	 * Simple value object for buff parameters (stat_key op value)
	 */
	@Slf4j
	record BuffParam(BuffParamMap name, MathOperation operation, double value) {
		private static final Pattern BUFF_PARAM_REGEX = Pattern.compile("(\\w+)([+\\-=*%<>!])([\\-+]?\\d+(?:\\.\\d+)?)");
		
		public static BuffParam fromString(String part) {
			if (part == null || (part = part.trim()).isEmpty())
				return null;
			var m = BUFF_PARAM_REGEX.matcher(part);
			if (m.find()) {
				return new BuffParam(BuffParamMap.fromKey(m.group(1)), MathOperation.fromSymbol(m.group(2)), Double.parseDouble(m.group(3)));
			}
			return null;
		}
		
		/**
		 * Serialize back to the game's attribute-string format, e.g. "Strength+5,Agility-2"
		 */
		public String serialize() {
			return this.name.getKey() + this.operation.getSymbol() + this.value;
		}
		
		public String beautify() {
			return name.getName() + " " + operation.getSymbol() + " " + this.value;
		}
	}
}