package modforge.backend.model.attributes;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StringAttribute extends BaseAttribute<String> {
	public StringAttribute(String name, String value) {
		super(name, value);
	}
	
	@Override
	public StringAttribute deepClone() {
		return this;
	}
	
	@Override
	public StringAttribute deepClone(String newValue) {
		return new StringAttribute(name, newValue);
	}
}
