package modforge.backend.model.item;

import modforge.backend.model.IAttribute;
import modforge.backend.model.IModItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public abstract class BaseModItem implements IModItem {
	private String id;
	private String idKey = "Id";
	private String path;
	private List<IAttribute> attributes = new ArrayList<>();
	private final List<String> linkedIds = new ArrayList<>();

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(String v) {
		this.id = v;
	}

	@Override
	public String getIdKey() {
		return idKey;
	}

	@Override
	public void setIdKey(String v) {
		this.idKey = v;
	}

	@Override
	public String getPath() {
		return path;
	}

	@Override
	public void setPath(String v) {
		this.path = v;
	}

	@Override
	public List<IAttribute> getAttributes() {
		return attributes;
	}

	@Override
	public void setAttributes(List<IAttribute> v) {
		this.attributes = v;
	}

	@Override
	public List<String> getLinkedIds() {
		return linkedIds;
	}

	/**
	 * Helper: find the first attribute whose name (case-insensitive) contains the candidate.
	 */
	public Optional<IAttribute> findAttr(String candidate) {
		String lo = candidate.toLowerCase(Locale.ROOT);
		return attributes.stream()
				.filter(a -> a.getName().toLowerCase(Locale.ROOT).contains(lo))
				.findFirst();
	}
}
