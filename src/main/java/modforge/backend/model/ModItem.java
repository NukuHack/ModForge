package modforge.backend.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import modforge.backend.ItemType;
import modforge.backend.model.attributes.Attribute;

import java.util.*;

public interface ModItem {
	// ── Attribute names that hold localization keys (checked case-insensitively)
	// TODO : right now you can only filter on actual items and since we don't have any item with like straight "UIName" because that si a special item, we can't filter on them
	Set<String> LANG_ATTR_HINTS = Set.of("UIName", "Desc", "UIInfo", "UiSound", "LatinName", "perk_ui_lore_desc", "perk_ui_desc", "perk_ui_name", "slot_buff_ui_name", "buff_ui_name", "buff_ui_desc");
	Set<String> LANG_FIELD_HINTS = Set.of("ui_name", "desc", "ui_info", "perk_ui_lore_desc", "perk_ui_desc", "perk_ui_name", "slot_buff_ui_name", "buff_ui_name", "buff_ui_desc");
	
	String getId();
	
	void setId(final String id);
	
	String getIdKey();
	
	String getPath();
	
	void setPath(final String path);
	
	List<Attribute> getAttributes();
	
	void setAttribute(final List<Attribute> attributes);
	
	void removeAttribute(final Attribute attr);
	
	void addAttribute(final Attribute attr);
	
	void addAttribute(final Collection<Attribute> attributes);
	
	List<ModItem> getLinkedItems();
	
	void setLinkedItem(final Collection<ModItem> linkedItems);
	
	void addLinkedItem(final ModItem linkedItem);
	
	void addLinkedItem(final Collection<ModItem> linkedItem);
	
	Optional<Attribute> findAttr(final String candidate);
	
	default List<Attribute.StringAttribute> getLangAttributes() {
		return getAttributes().stream().filter(a -> LANG_ATTR_HINTS.contains(a.getName().toLowerCase())).map(a -> (Attribute.StringAttribute) a).toList();
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
		private final List<ModItem> linkedItems = new ArrayList<>();
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
		public List<ModItem> getLinkedItems() {
			return Collections.unmodifiableList(this.linkedItems);
		}
		
		@Override
		public void setLinkedItem(final Collection<ModItem> linkedItems) {
			this.linkedItems.clear();
			this.linkedItems.addAll(linkedItems);
		}
		
		@Override
		public void addLinkedItem(final ModItem linkedItem) {
			this.linkedItems.add(linkedItem);
		}
		
		@Override
		public void addLinkedItem(final Collection<ModItem> linkedItem) {
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
	
}