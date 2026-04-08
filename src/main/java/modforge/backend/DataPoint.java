package modforge.backend;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record DataPoint(@NonNull String path, @NonNull String endpoint, @NonNull Class<?> type) {
	@Override
	public String toString() {
		return "DataPoint{type=" + type.getSimpleName() + ", endpoint=" + endpoint + ", path=" + path + "}";
	}
}