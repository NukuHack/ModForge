package modforge.backend.model;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import modforge.backend.model.E.MathOperation;

import java.util.*;
import java.util.regex.Pattern;

public interface Attribute<T> {
	String getName();
	
	T getValue();
	
	Attribute<T> deepClone();
	
	Attribute<T> deepClone(T newValue);
	
	
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
	}
	
	/**
		 * Simple value object for buff parameters (stat_key op value)
		 */
	record BuffParam(String name, MathOperation operation, double value) {
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
			 * Parse the compact game format:  "Strength+5,Agility-2.5,Charisma=10"
			 */
			public static List<BuffParam> parse(final String data) {
				return Arrays.stream(data.split(",")).map(BuffParam::fromString).filter(Objects::nonNull).toList();
			}
			
			/**
			 * Serialize back to the game's attribute-string format, e.g. "Strength+5,Agility-2"
			 */
			public String toAttrString() {
				return this.name + this.operation.getSymbol() + this.value;
			}
			
			static class BuffParams extends ListAttribute<BuffParam> {
				public BuffParams(String name, List<BuffParam> value) {
					super(name, value);
				}
				
				public static BuffParams parse(String name, String value) {
					return new BuffParams(name, BuffParam.parse(value));
				}
			}
		}
	
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
	}
	
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
	}
	
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
	}
}