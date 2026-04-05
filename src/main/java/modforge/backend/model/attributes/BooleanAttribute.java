package modforge.backend.model.attributes;

public non-sealed class BooleanAttribute extends BaseAttribute<Boolean> {
	public BooleanAttribute(String name, Boolean value) {
		super(name, value);
	}
	
	@Override
	public BooleanAttribute deepClone() {
		return this;
	}
	
	@Override
	public BooleanAttribute deepClone(Boolean newValue) {
		return new BooleanAttribute(name, newValue);
	}
}
