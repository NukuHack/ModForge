package modforge.backend.model.attributes;

import java.util.List;

public class StringAttribute extends Attribute<String> {
	public StringAttribute(String name, String value) {
		super(name, value);
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public StringAttribute deepClone() {
		return this;
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public StringAttribute deepClone(String newValue) {
		return new StringAttribute(name, newValue);
	}
}
