package modforge.backend.model.attributes;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BooleanAttribute extends BaseAttribute<Boolean> {
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
