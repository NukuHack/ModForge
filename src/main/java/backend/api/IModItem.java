package backend.api;

import java.util.List;

public interface IModItem {
	String getId();

	void setId(String id);

	String getIdKey();

	void setIdKey(String idKey);

	String getPath();

	void setPath(String path);

	List<IAttribute> getAttributes();

	void setAttributes(List<IAttribute> attributes);

	List<String> getLinkedIds();
}
