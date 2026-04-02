package modforge.backend;

import java.util.*;

public record DataPoint(String path, String endpoint, Class<?> type) {
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

