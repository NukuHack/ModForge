package modforge.backend.model.attributes;

import java.util.*;

public class Attribute<T> implements IAttribute {
	private final String name;
	private T value;

	public Attribute(String name, T value) {
		this.name = name;
		this.value = value;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Object getValue() {
		return value;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void setValue(Object value) {
		this.value = (T) value;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Attribute<T> deepClone() {
		return new Attribute<>(name, value);
	}

	@Override
	public String toString() {
		return name + "=" + value;
	}
}