package backend.api;

public interface IAttribute {
	String getName();

	Object getValue();

	IAttribute deepClone();
}
