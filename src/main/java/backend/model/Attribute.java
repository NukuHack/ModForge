package backend.model;

import backend.api.IAttribute;

import java.util.ArrayList;
import java.util.List;

final class Attribute<T> implements IAttribute {
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

	public void setValue(T v) {
		this.value = v;
	}

	@Override
	@SuppressWarnings("unchecked")
	public IAttribute deepClone() {
		// For mutable values (List<BuffParam>), clone the list too
		if (value instanceof List<?> list) {
			var copy = new ArrayList<>(list);
			return new Attribute<>(name, (T) copy);
		}
		return new Attribute<>(name, value);
	}

	@Override
	public String toString() {
		return name + "=" + value;
	}
}
