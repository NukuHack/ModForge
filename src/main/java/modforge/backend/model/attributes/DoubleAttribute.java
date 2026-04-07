package modforge.backend.model.attributes;

@lombok.extern.slf4j.Slf4j
public non-sealed class DoubleAttribute extends BaseAttribute<Double> {
	public DoubleAttribute(String name, Double value) {
		super(name, value);
	}
	
	@Override
	public DoubleAttribute deepClone() {
		return this;
	}
	
	@Override
	public DoubleAttribute deepClone(Double newValue) {
		return new DoubleAttribute(name, newValue);
	}
}
