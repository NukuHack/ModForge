package modforge.backend.model.attributes;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

public interface Attribute<T> {
	String getName();
	
	T getValue();
	
	Attribute<T> deepClone();
	
	Attribute<T> deepClone(T newValue);
}

@Getter
@Slf4j
@RequiredArgsConstructor
abstract class BaseAttribute<T> implements Attribute<T> {
	@NonNull
	protected final String name;
	@NonNull
	protected final T value;
	
	public String toString() {
		return name + "=" + value;
	}
	
	
	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass())
			return false;
		Attribute<?> attribute = (Attribute<?>) o;
		return Objects.equals(name, attribute.getName()) && Objects.equals(value, attribute.getValue());
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(name, value);
	}
}