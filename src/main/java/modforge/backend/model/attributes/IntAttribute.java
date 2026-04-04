package modforge.backend.model.attributes;

public class IntAttribute extends Attribute<Integer> {
	public IntAttribute(String name, Integer value) {
		super(name, value);
	}
	
	
	@Override
	@SuppressWarnings("unchecked")
	public IntAttribute deepClone() {
		return this;
	}
}
