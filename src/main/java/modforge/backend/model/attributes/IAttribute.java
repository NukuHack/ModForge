package modforge.backend.model.attributes;

public interface IAttribute {
	String getName();

	Object getValue();

	IAttribute deepClone();
}
