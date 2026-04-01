package backend.api;

public interface IDataPoint {
	String getPath();

	String getEndpoint();

	Class<?> getType();
}
