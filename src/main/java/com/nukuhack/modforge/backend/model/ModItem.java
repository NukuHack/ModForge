package com.nukuhack.modforge.backend.model;

import com.nukuhack.modforge.backend.ItemType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Interface - generic item...
 */
public interface ModItem {
	// ── Attribute names that hold localization keys (checked case-insensitively)
	Set<String> LANG_ATTR_HINTS = Set.of("UIName", "Desc", "UIInfo", "perk_ui_lore_desc", "perk_ui_desc", "perk_ui_name", "slot_buff_ui_name", "buff_ui_name", "buff_ui_desc");
	
	String getId();
	
	void setId(final String id);
	
	/**
	 * @return ID key for Element mapping from XML data
	 */
	default String getIdKey() {
		return ItemType.getIdKey(this.getClass());
	}
	
	String getPath();
	
	void setPath(final String path);
	
	List<Attribute> getAttributes();
	
	void setAttribute(final List<Attribute> attributes);
	
	void removeAttribute(final Attribute attr);
	
	void addAttribute(final Attribute attr);
	
	void addAttribute(final Collection<Attribute> attributes);
	
	Set<ModItem> getLinkedItems();
	
	void setLinkedItem(final Collection<ModItem> linkedItems);
	
	void addLinkedItem(final ModItem linkedItem);
	
	void addLinkedItem(final Collection<ModItem> linkedItem);
	
	Optional<Attribute> findAttr(final String candidate);
	
	default List<Attribute.StringAttribute> getLangAttributes() {
		return getAttributes().stream().filter(a -> LANG_ATTR_HINTS.contains(a.getName())).map(a -> (Attribute.StringAttribute) a).toList();
	}
	
	/**
	 * Get all item details as plain text for copying
	 */
	default String details() {
		final var sb = new StringBuilder();
		sb.append("ID: ").append(this.getId()).append("\n");
		sb.append("Class: ").append(this.getClass().getSimpleName()).append("\n");
		sb.append("Path: ").append(this.getPath()).append("\n");
		
		// Show attributes if any
		if (! this.getAttributes().isEmpty()) {
			sb.append("\nAttributes:\n");
			for (var attr : this.getAttributes()) {
				sb.append("  • ").append(attr.getName()).append(": ").append(attr.getValue()).append("\n");
			}
		}
		
		// Show linked IDs if any
		if (! this.getLinkedItems().isEmpty()) {
			sb.append("\nLinked Items:\n");
			for (var linkedItem : this.getLinkedItems()) {
				sb.append("  • ").append(linkedItem.details()).append("\n");
			}
		}
		
		return sb.toString();
	}
	
	
	@NoArgsConstructor
	@Slf4j
	abstract class BaseModItem implements ModItem {
		private final List<Attribute> attributes = new ArrayList<>();
		private Set<ModItem> linkedItems = null;
		// TODO change the ID from string to a nicer object
		// - can not do since we have id of 0 and id of -1 ... LOL
		@Getter
		@Setter
		private String id;
		@Getter
		@Setter
		private String path;
		
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
		public Set<ModItem> getLinkedItems() {
			if (this.linkedItems == null)
				this.linkedItems = new HashSet<>();
			return Collections.unmodifiableSet(this.linkedItems);
		}
		
		@Override
		public void setLinkedItem(final Collection<ModItem> linkedItems) {
			this.linkedItems = new HashSet<>(linkedItems);
		}
		
		@Override
		public void addLinkedItem(final ModItem linkedItem) {
			if (this.linkedItems == null)
				this.linkedItems = new HashSet<>();
			this.linkedItems.add(linkedItem);
		}
		
		@Override
		public void addLinkedItem(final Collection<ModItem> linkedItem) {
			if (this.linkedItems == null)
				this.linkedItems = new HashSet<>(linkedItem);
			else
				this.linkedItems.addAll(linkedItem);
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
		
		
		@Override
		public String toString() {
			return this.getClass().getName() + "{attributes=" + attributes + ", linkedItems=" + linkedItems + ", id='" + id + '\'' + ", path='" + path + '\'' + '}';
		}
	}
	
	@NoArgsConstructor
	@Slf4j
	@Getter
	@Setter
	class EmptyImpl extends BaseModItem {
		private String idKey;
	}
	
}