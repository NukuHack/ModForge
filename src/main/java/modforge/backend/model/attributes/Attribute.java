package modforge.backend.model.attributes;

import java.util.Objects;

public abstract class Attribute<T> {
	protected final String name;
	protected final T value;
	
	public Attribute(String name, T value) {
		this.name = Objects.requireNonNull(name);
		this.value = Objects.requireNonNull(value);
	}
	
	public String getName() {
		return name;
	}
	
	public T getValue() {
		return value;
	}
	
	public abstract <R extends Attribute> R deepClone();
	
	public abstract <R extends Attribute> R deepClone(T newValue);
	
	public String toString() {
		return name + "=" + value;
	}
	
	
	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass())
			return false;
		Attribute<?> attribute = (Attribute<?>) o;
		return Objects.equals(name, attribute.name) && Objects.equals(value, attribute.value);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(name, value);
	}
}