package modforge.backend.model.attributes;

public interface IAttribute {
	String getName();

	Object getValue();

	void setValue(Object value);

	<R extends IAttribute> R deepClone();
}
