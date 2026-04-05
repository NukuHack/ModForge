package modforge.backend.model.attributes;

import java.util.Objects;

public sealed interface Attribute<T> permits BaseAttribute {
	String getName();
	
	T getValue();
	
	Attribute<T> deepClone();
	
	Attribute<T> deepClone(T newValue);
}

abstract sealed class BaseAttribute<T> implements Attribute<T> permits BooleanAttribute, BuffParam, DoubleAttribute, ListAttribute, StringAttribute {
	protected final String name;
	protected final T value;
	
	public BaseAttribute(String name, T value) {
		this.name = Objects.requireNonNull(name);
		this.value = Objects.requireNonNull(value);
	}
	
	@Override
	public String getName() {
		return name;
	}
	
	@Override
	public T getValue() {
		return value;
	}
	
	public String toString() {
		return name + "=" + value;
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