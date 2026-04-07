package modforge.backend.model;

import lombok.Getter;
import lombok.Setter;
import modforge.backend.ItemType;
import modforge.backend.model.attributes.Attribute;

import java.util.*;


@lombok.extern.slf4j.Slf4j
public abstract class BaseModItem implements ModItem {
	private final List<Attribute> attributes = new ArrayList<>();
	private final List<String> linkedIds = new ArrayList<>();
	// TODO change the ID from string to a nicer object
	// - can not do since we have id of 0 and id of -1 ... LOL
	@Getter
	@Setter
	private String id;
	@Getter
	@Setter
	private String path;
	
	@Override
	public String getIdKey() {
		return ItemType.getIdKey(this.getClass());
	}
	
	@Override
	public List<Attribute> getAttributes() {
		return Collections.unmodifiableList(this.attributes);
	}
	
	@Override
	public void setAttribute(final List<Attribute> attr) {
		this.attributes.clear();
		this.attributes.addAll(attr);
	}
	
	@Override
	public void removeAttribute(final Attribute attr) {
		this.attributes.remove(attr);
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
		return Collections.unmodifiableList(this.linkedIds);
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
		final BaseModItem that = (BaseModItem) o;
		return Objects.equals(getId(), that.getId()) && Objects.equals(getPath(), that.getPath());
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(getId(), getPath());
	}
	
	/**
	 * Helper: find the first attribute whose name (case-insensitive) contains the candidate.
	 */
	public Optional<Attribute> findAttr(final String candidate) {
		final String lo = candidate.toLowerCase(Locale.ROOT);
		return this.attributes.stream().filter(a -> a.getName().toLowerCase(Locale.ROOT).contains(lo)).findFirst();
	}
}
