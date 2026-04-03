package modforge.backend.model;

import modforge.backend.ItemType;
import modforge.backend.model.attributes.Attribute;

import java.util.*;

public abstract class BaseModItem implements ModItem {
	private final List<Attribute> attributes = new ArrayList<>();
	private final List<String> linkedIds = new ArrayList<>();
	// TODO change the ID from string to a nicer object
	private String id;
	private String path;
	
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
		return ItemType.getIdKey(this.getClass());
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
	public List<Attribute> getAttributes() {
		return this.attributes;
	}
	
	@Override
	public void setAttribute(final Collection<Attribute> attr) {
		this.attributes.clear();
		this.attributes.addAll(attr);
	}
	
	@Override
	public void addAttribute(final Attribute attr) {
		this.attributes.add(attr);
	}
	
	@Override
	public void addAttribute(final Collection<Attribute> attr) {
		this.attributes.addAll(attr);
	}
	
	@Override
	public List<String> getLinkedIds() {
		return this.linkedIds;
	}
	
	@Override
	public void setLinkedId(final Collection<String> linkedIds) {
		this.linkedIds.clear();
		this.linkedIds.addAll(linkedIds);
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
		if (o == null || getClass() != o.getClass())
			return false;
		BaseModItem that = (BaseModItem) o;
		return Objects.equals(getId(), that.getId()) && Objects.equals(getIdKey(), that.getIdKey()) && Objects.equals(getPath(), that.getPath());
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(getId(), getIdKey(), getPath(), getClass());
	}
	
	/**
	 * Helper: find the first attribute whose name (case-insensitive) contains the candidate.
	 */
	public Optional<Attribute> findAttr(final String candidate) {
		final String lo = candidate.toLowerCase(Locale.ROOT);
		return this.attributes.stream().filter(a -> a.getName().toLowerCase(Locale.ROOT).contains(lo)).findFirst();
	}
}
