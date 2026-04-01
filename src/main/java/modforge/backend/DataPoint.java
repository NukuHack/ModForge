package modforge.backend;

import modforge.backend.model.IDataPoint;

import java.util.Objects;

public record DataPoint(String path, String endpoint, Class<?> type) implements IDataPoint {
	public DataPoint(String path, String endpoint, Class<?> type) {
		this.path = Objects.requireNonNull(path, "path");
		this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
		this.type = Objects.requireNonNull(type, "type");
	}


	@Override
	public String toString() {
		return "DataPoint{type=" + type.getSimpleName() + ", endpoint=" + endpoint + ", path=" + path + "}";
	}
}

