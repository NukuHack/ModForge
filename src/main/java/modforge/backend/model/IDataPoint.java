package modforge.backend.model;

public interface IDataPoint {
	String path();

	String endpoint();

	Class<?> type();
}
