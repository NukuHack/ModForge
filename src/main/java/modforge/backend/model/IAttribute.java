package modforge.backend.model;

public interface IAttribute {
	String getName();

	Object getValue();

	IAttribute deepClone();
}
