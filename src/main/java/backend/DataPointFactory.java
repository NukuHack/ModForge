package backend;


import backend.api.IDataPoint;
import modforge.DataPoint;

final class DataPointFactory {
	private DataPointFactory() {
	}

	public static IDataPoint create(String path, String endpoint, Class<?> type) {
		return new DataPoint(path, endpoint, type);
	}
}
