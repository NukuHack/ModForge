package modforge.backend.model.attributes;

public class DoubleAttribute extends Attribute<Double> {
	public DoubleAttribute(String name, Double value) {
		super(name, value);
	}
	
	
	@Override
	@SuppressWarnings("unchecked")
	public DoubleAttribute deepClone() {
		return this;
	}
}
