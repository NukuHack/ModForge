package modforge.backend.model;

import modforge.backend.model.attributes.IAttribute;

import java.util.*;

public interface ModItem {
	String getId();
	void setId(final String id);

	String getIdKey();
	void setIdKey(final String idKey);

	String getPath();
	void setPath(final String path);

	List<IAttribute> getAttributes();
	void setAttribute(final Collection<IAttribute> attributes);
	void addAttribute(final IAttribute attr);
	void addAttribute(final Collection<IAttribute> attributes);

	List<String> getLinkedIds();
	void setLinkedId(final Collection<String> linkedIds);
	void addLinkedId(final String linkedId);
	void addLinkedId(final Collection<String> linkedId);

	Optional<IAttribute> findAttr(final String candidate);
}