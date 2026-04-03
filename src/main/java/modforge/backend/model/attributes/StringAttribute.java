package modforge.backend.model.attributes;

public class StringAttribute extends Attribute<String> {
	public StringAttribute(String name, String value) {
		super(name, value);
	}
	
	
	@Override
	@SuppressWarnings("unchecked")
	public StringAttribute deepClone() {
		return new StringAttribute(this.name, this.value);
	}
}
