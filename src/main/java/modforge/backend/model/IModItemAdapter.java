package modforge.backend.model;

import java.util.List;

public interface IModItemAdapter {
	void writeModItems(String modId, Iterable<IModItem> items);

	List<IModItem> readModItems(IDataPoint dataPoint);
}
