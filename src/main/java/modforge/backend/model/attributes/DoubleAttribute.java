package modforge.backend.model.attributes;

public class DoubleAttribute extends Attribute<Double> {
	public DoubleAttribute(String name, Double value) {
		super(name, value);
	}


	@Override
	@SuppressWarnings("unchecked")
	public DoubleAttribute deepClone() {
		return new DoubleAttribute(this.name, this.value);
	}
}
