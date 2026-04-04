package modforge.backend.model.attributes;

import java.util.Objects;

public abstract class Attribute<T> {
	protected final String name;
	protected T value;
	
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
	
	@SuppressWarnings("unchecked")
	public void setValue(Object value) {
		this.value = (T) value;
	}
	
	public abstract <R extends Attribute> R deepClone();
	
	public String toString() {
		return name + "=" + value;
	}
}