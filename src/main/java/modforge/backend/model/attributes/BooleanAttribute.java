package modforge.backend.model.attributes;

public class BooleanAttribute extends Attribute<Boolean> {
	public BooleanAttribute(String name, Boolean value) {
		super(name, value);
	}
	
	
	@Override
	@SuppressWarnings("unchecked")
	public BooleanAttribute deepClone() {
		return new BooleanAttribute(this.name, this.value);
	}
}
