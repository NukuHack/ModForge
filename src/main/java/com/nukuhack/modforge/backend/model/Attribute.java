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

@NonNull
public interface Attribute<T> {
	String INDENT = "  ";
	
	@NonNull String getName();
	
	@NonNull T getValue();
	
	@NonNull Attribute<T> deepClone();
	
	@NonNull String serialize();
	
	@NonNull Attribute<T> withValue(@NonNull T val);
	
	
	@Getter
	@Slf4j
	@NonNull
	@RequiredArgsConstructor
	abstract class BaseAttribute<T> implements Attribute<T> {
		protected final String name;
		protected final T value;
		
		@Override
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
	@NonNull
	class BooleanAttribute extends BaseAttribute<Boolean> {
		public BooleanAttribute(@NonNull String name, @NonNull Boolean value) {
			super(name, value);
		}
		
		@Override
		public @NonNull BooleanAttribute deepClone() {
			return this;
		}
		
		@Override
		public @NonNull BooleanAttribute withValue(@NonNull Boolean newValue) {
			return new BooleanAttribute(name, newValue);
		}
		
		@Override
		public @NonNull String serialize() {
			return value.toString().toLowerCase();
		}
	}
	
	@Slf4j
	@NonNull
	class DoubleAttribute extends BaseAttribute<Double> {
		public DoubleAttribute(@NonNull String name, @NonNull Double value) {
			super(name, value);
		}
		
		@Override
		public @NonNull DoubleAttribute deepClone() {
			return this;
		}
		
		@Override
		public @NonNull DoubleAttribute withValue(@NonNull Double newValue) {
			return new DoubleAttribute(name, newValue);
		}
		
		@Override
		public @NonNull String serialize() {
			if (Double.isInfinite(value) || Double.isNaN(value))
				return "1";
			long rounded = Math.round(value);
			if (Math.abs(value - rounded) < 1e-8)
				return String.valueOf(rounded);
			return value.toString();
		}
	}
	
	@Slf4j
	@NonNull
	class EnumAttribute extends BaseAttribute<Enum<?>> {
		@Getter
		@NonNull
		private final Class<? extends Enum<?>> enumType;
		
		public EnumAttribute(@NonNull String name, @NonNull Enum<?> value) {
			super(name, value);
			this.enumType = value.getDeclaringClass();
		}
		
		@Override
		public @NonNull EnumAttribute deepClone() {
			return this;
		}
		
		@Override
		public @NonNull EnumAttribute withValue(@NonNull Enum<?> newValue) {
			return new EnumAttribute(name, newValue);
		}
		
		@Override
		public @NonNull String serialize() {
			return String.valueOf(value.ordinal());
		}
	}
	
	@Slf4j
	@NonNull
	class ListAttribute<M> extends BaseAttribute<List<M>> {
		public ListAttribute(@NonNull String name, @NonNull List<M> value) {
			super(name, value);
		}

		@Override
		public @NonNull ListAttribute<M> deepClone() {
			return new ListAttribute<>(getName(), deepCloneList(value));
		}

		@Override
		public @NonNull ListAttribute<M> withValue(@NonNull List<M> newValue) {
			return new ListAttribute<>(name, deepCloneList(newValue));
		}

		@Override
		public @NonNull String serialize() {
			if (value.isEmpty())
				return "";
			return value.stream().map(item -> (item instanceof Attribute<?> a ? a.serialize() : item.toString()).trim()).filter(Predicate.not(String::isEmpty)).collect(Collectors.joining(","));
		}
	}
	
	@Slf4j
	@NonNull
	class BuffParamListAttribute extends ListAttribute<BuffParam> {
		public BuffParamListAttribute(@NonNull String name, @NonNull List<BuffParam> value) {
			super(name, value);
		}
		

		@Override
		public @NonNull BuffParamListAttribute deepClone() {
			return new BuffParamListAttribute(getName(), deepCloneList(value));
		}

		@Override
		public @NonNull BuffParamListAttribute withValue(@NonNull List<BuffParam> newValue) {
			return new BuffParamListAttribute(name, deepCloneList(newValue));
		}
		
		public static @NonNull List<BuffParam> parse(@NonNull String data) {
			return Arrays.stream(data.split(",")).map(BuffParam::fromString).filter(Objects::nonNull).toList();
		}
		
		public @NonNull String serialize() {
			return String.join(",", value.stream().map(BuffParam::serialize).toList());
		}
		
		public @NonNull String getNiceName() {
			return value.stream().map(BuffParam::beautify).collect(Collectors.joining(", "));
		}
	}
	
	@Slf4j
	@NonNull
	class StringAttribute extends BaseAttribute<String> {
		public StringAttribute(@NonNull String name, @NonNull String value) {
			super(name, value);
		}
		
		@Override
		public @NonNull StringAttribute deepClone() {
			return this;
		}
		
		@Override
		public @NonNull StringAttribute withValue(@NonNull String newValue) {
			return new StringAttribute(name, newValue);
		}
		
		@Override
		public @NonNull String serialize() {
			return value;
		}
	}
	
	@Slf4j
	@NonNull
	class UUIDAttribute extends BaseAttribute<UUID> {
		public UUIDAttribute(@NonNull String name, @NonNull UUID value) {
			super(name, value);
		}
		
		@Override
		public @NonNull UUIDAttribute deepClone() {
			return this;
		}
		
		@Override
		public @NonNull UUIDAttribute withValue(@NonNull UUID newValue) {
			return new UUIDAttribute(name, newValue);
		}
		
		@Override
		public @NonNull String serialize() {
			return value.toString();
		}
	}
	
	@Slf4j
	@NonNull
	class XmlNodeAttribute extends BaseAttribute<XmlNode> {
		public XmlNodeAttribute(@NonNull String name, @NonNull XmlNode value) {
			super(name, value);
		}
		
		@Override
		public @NonNull XmlNodeAttribute deepClone() {
			return new XmlNodeAttribute(name, deepCloneNode(value));
		}
		
		@Override
		public @NonNull XmlNodeAttribute withValue(@NonNull XmlNode newValue) {
			return new XmlNodeAttribute(name, deepCloneNode(newValue));
		}
		
		@Override
		public @NonNull String serialize() {
			return serializeNode(value, 1);
		}
		
		private @NonNull XmlNode deepCloneNode(@NonNull XmlNode node) {
			var clonedAttrs = node.attributes().stream().map(Attribute::deepClone).toList();
			var clonedChildren = node.children().stream().map(this::deepCloneNode).toList();
			return new XmlNode(node.tag(), clonedAttrs, clonedChildren);
		}
		
		private @NonNull String serializeNode(@NonNull XmlNode node, int depth) {
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
	
	@NonNull
	record XmlNode(String tag, List<Attribute> attributes, List<XmlNode> children) {
		public boolean isLeaf() {
			return children.isEmpty();
		}
		
		public int getDepth() {
			if (children.isEmpty())
				return 0;
			return 1 + children.stream().mapToInt(XmlNode::getDepth).max().orElse(0);
		}
		
		public @NonNull ModItem asItem() {
			var item = new I.EmptyImpl();
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
	@NonNull
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
		public @NonNull String serialize() {
			return this.name.getKey() + this.operation.getSymbol() + this.value;
		}
		
		public @NonNull String beautify() {
			return name.getName() + " " + operation.getSymbol() + " " + this.value;
		}
	}
}
