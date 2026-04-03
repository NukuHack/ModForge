package modforge.backend.model.attributes;

public abstract class Attribute<T> {
	protected final String name;
	protected T value;

	public Attribute(String name, T value) {
		this.name = name;
		this.value = value;
	}

	public String getName() {
		return name;
	}

	public Object getValue() {
		return value;
	}

	public abstract <R extends Attribute> R deepClone();

	@SuppressWarnings("unchecked")
	public void setValue(Object value) {
		this.value = (T) value;
	}

	public String toString() {
		return name + "=" + value;
	}
}