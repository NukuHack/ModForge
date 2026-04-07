package modforge.backend.model.attributes;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DoubleAttribute extends BaseAttribute<Double> {
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
