package modforge.backend;


import modforge.backend.model.IDataPoint;

public final class DataPointFactory {
	private DataPointFactory() {
	}

	public static IDataPoint create(String path, String endpoint, Class<?> type) {
		return new DataPoint(path, endpoint, type);
	}
}
