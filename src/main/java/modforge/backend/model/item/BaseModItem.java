package modforge.backend.model.item;

import modforge.backend.model.ModItem;
import modforge.backend.model.attributes.IAttribute;

import java.util.*;

public abstract class BaseModItem implements ModItem {
	// TODO change the ID from string to a nicer object
	private String id;
	private String idKey = "Id";
	private String path;
	private final List<IAttribute> attributes = new ArrayList<>();
	private final List<String> linkedIds = new ArrayList<>();

	@Override
	public String getId() {
		return this.id;
	}
	@Override
	public void setId(final String id) {
		this.id = id;
	}

	@Override
	public String getIdKey() {
		return this.idKey;
	}
	@Override
	public void setIdKey(final String key) {
		this.idKey = key;
	}

	public void setKey(final String id, final String key) {
		this.id = id; this.idKey = key;
	}

	@Override
	public String getPath() {
		return this.path;
	}
	@Override
	public void setPath(final String v) {
		this.path = v;
	}

	@Override
	public List<IAttribute> getAttributes() {
		return this.attributes;
	}
	@Override
	public void setAttribute(final Collection<IAttribute> attr) {
		if (this.attributes.isEmpty()) {
			this.attributes.addAll(attr);
		} else {
			this.attributes.clear();
			this.attributes.addAll(attr);
		}
	}
	@Override
	public void addAttribute(final IAttribute attr) {
		this.attributes.add(attr);
	}
	@Override
	public void addAttribute(final Collection<IAttribute> attr) {
		this.attributes.addAll(attr);
	}

	@Override
	public List<String> getLinkedIds() {
		return this.linkedIds;
	}
	@Override
	public void setLinkedId(final Collection<String> linkedIds) {
		if (this.linkedIds.isEmpty()) {
			this.linkedIds.addAll(linkedIds);
		} else {
			this.linkedIds.clear();
			this.linkedIds.addAll(linkedIds);
		}
	}
	@Override
	public void addLinkedId(final String linkedId) {
		this.linkedIds.add(linkedId);
	}
	@Override
	public void addLinkedId(final Collection<String> linkedId) {
		this.linkedIds.addAll(linkedId);
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) return false;
		BaseModItem that = (BaseModItem) o;
		return Objects.equals(id, that.id) && Objects.equals(idKey, that.idKey) && Objects.equals(path, that.path);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, idKey, path);
	}

	/**
	 * Helper: find the first attribute whose name (case-insensitive) contains the candidate.
	 */
	public Optional<IAttribute> findAttr(final String candidate) {
		final String lo = candidate.toLowerCase(Locale.ROOT);
		return this.attributes.stream()
				.filter(a -> a.getName().toLowerCase(Locale.ROOT).contains(lo))
				.findFirst();
	}
}
