package modforge.backend.model.attributes;

import lombok.Getter;

import java.util.Objects;

public sealed interface Attribute<T> permits BaseAttribute {
	String getName();
	
	T getValue();
	
	Attribute<T> deepClone();
	
	Attribute<T> deepClone(T newValue);
}

@lombok.extern.slf4j.Slf4j
abstract sealed class BaseAttribute<T> implements Attribute<T> permits BooleanAttribute, BuffParam, DoubleAttribute, ListAttribute, StringAttribute {
	@Getter
	protected final String name;
	@Getter
	protected final T value;
	
	public BaseAttribute(String name, T value) {
		this.name = Objects.requireNonNull(name);
		this.value = Objects.requireNonNull(value);
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