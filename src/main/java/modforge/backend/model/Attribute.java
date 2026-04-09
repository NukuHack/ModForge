package modforge.backend.model;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import modforge.backend.model.E.MathOperation;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public interface Attribute<T> {
	String getName();
	
	T getValue();
	
	Attribute<T> deepClone();
	
	Attribute<T> deepClone(T newValue);
	
	String serialize();
	
	
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
		
		public abstract BaseAttribute<T> deepClone();
		
		public abstract BaseAttribute<T> deepClone(T newValue);
		
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
		public BooleanAttribute deepClone(Boolean newValue) {
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
		public DoubleAttribute deepClone(Double newValue) {
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
		public EnumAttribute deepClone(Enum<?> newValue) {
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
		public ListAttribute<M> deepClone(List<M> newValue) {
			return new ListAttribute<>(name, deepCloneList(newValue));
		}
		
		@Override
		public String serialize() {
			if (value.isEmpty())
				return "";
			return value.stream().map(item -> (item instanceof Attribute<?> a ? a.serialize() : item.toString()).trim()).filter(Predicate.not(String::isEmpty)).collect(Collectors.joining(","));
		}
		
		/**
		 * Helper method to deep clone nested lists
		 */
		private List<M> deepCloneList(List<M> list) {
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
	}
	
	@Slf4j
	class BuffParamListAttribute extends ListAttribute<BuffParam> {
		public BuffParamListAttribute(String name, List<BuffParam> value) {
			super(name, value);
		}
		
		public BuffParamListAttribute(String name, String value) {
			this(name, parse(value));
		}
		
		public static List<BuffParam> parse(final String data) {
			return Arrays.stream(data.split(",")).map(BuffParam::fromString).filter(Objects::nonNull).toList();
		}
		
		public String serialize() {
			return String.join(",", value.stream().map(BuffParam::serialize).toList());
		}
	}
	
	/**
	 * Simple value object for buff parameters (stat_key op value)
	 */
	@Slf4j
	record BuffParam(@NonNull String name, @NonNull MathOperation operation, @NonNull double value) {
		private static final Pattern BUFF_PARAM_REGEX = Pattern.compile("(\\w+)([+\\-=*%<>!])([\\-+]?\\d+(?:\\.\\d+)?)");
		
		public static BuffParam fromString(String part) {
			if (part == null || (part = part.trim()).isEmpty())
				return null;
			final var m = BUFF_PARAM_REGEX.matcher(part);
			if (m.find()) {
				return new BuffParam(m.group(1), MathOperation.fromSymbol(m.group(2)), Double.parseDouble(m.group(3)));
			} else {
				return new BuffParam(part, MathOperation.SET, 1);
			}
		}
		
		/**
		 * Serialize back to the game's attribute-string format, e.g. "Strength+5,Agility-2"
		 */
		public String serialize() {
			return this.name + this.operation.getSymbol() + this.value;
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
		public StringAttribute deepClone(String newValue) {
			return new StringAttribute(name, newValue);
		}
		
		@Override
		public String serialize() {
			return value;
		}
	}
}