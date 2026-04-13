package com.nukuhack.modforge.backend.model;

import com.nukuhack.modforge.backend.ItemType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Interface - generic item...
 */
public interface ModItem {
	// ── Attribute names that hold localization keys (checked case-insensitively)
	Set<String> LANG_ATTR_HINTS = Set.of("UIName", "Desc", "UIInfo", "perk_ui_lore_desc", "perk_ui_desc", "perk_ui_name", "slot_buff_ui_name", "buff_ui_name", "buff_ui_desc");
	
	@NonNull String getId();
	
	void setId(final @NonNull String id);
	
	/**
	 * @return ID key for Element mapping from XML data
	 */
	default @NonNull String getIdKey() {
		return ItemType.getIdKey(this.getClass());
	}
	
	@NonNull String getPath();
	
	void setPath(final @NonNull String path);
	
	@NonNull List<Attribute> getAttributes();
	
	void setAttribute(final @NonNull Collection<Attribute> attributes);
	
	void removeAttribute(final @NonNull Attribute attr);
	
	void addAttribute(final @NonNull Attribute attr);
	
	void addAttribute(final @NonNull Collection<Attribute> attributes);
	
	@NonNull Optional<Attribute> findAttr(final @NonNull String candidate);
	
	default @NonNull List<Attribute.StringAttribute> getLangAttributes() {
		return getAttributes().stream().filter(a -> LANG_ATTR_HINTS.contains(a.getName())).map(a -> (Attribute.StringAttribute) a).toList();
	}
	
	/**
	 * Get all item details as plain text for copying
	 */
	default @NonNull String details() {
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
		
		return sb.toString();
	}
	
	
	@Slf4j
	@Setter
	@NonNull
	@NoArgsConstructor
	abstract class BaseModItem implements ModItem {
		private final List<Attribute> attributes = new ArrayList<>();
		// TODO change the ID from string to a nicer object
		// - can not do since we have id of 0 and id of -1 ... LOL
		@Getter
		private String id;
		@Getter
		private String path;
		
		@Override
		public @NonNull List<Attribute> getAttributes() {
			return Collections.unmodifiableList(this.attributes);
		}
		
		@Override
		public void setAttribute(final @NonNull Collection<Attribute> attr) {
			this.attributes.clear();
			this.attributes.addAll(attr);
		}
		
		@Override
		public void removeAttribute(final @NonNull Attribute attr) {
			this.attributes.remove(attr);
		}
		
		@Override
		public void addAttribute(final @NonNull  Attribute attr) {
			this.attributes.add(attr);
		}
		
		@Override
		public void addAttribute(final @NonNull  Collection<Attribute> attr) {
			this.attributes.addAll(attr);
		}
		
		/**
		 * Helper: find the first attribute whose name (case-insensitive) contains the candidate.
		 */
		public @NonNull Optional<Attribute> findAttr(final @NonNull  String candidate) {
			final String lo = candidate.toLowerCase(Locale.ROOT);
			return this.attributes.stream().filter(a -> a.getName().toLowerCase(Locale.ROOT).contains(lo)).findFirst();
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
		
		@Override
		public String toString() {
			return this.getClass().getName() + "{attributes=" + attributes + ", id='" + id + '\'' + ", path='" + path + '\'' + '}';
		}
	}
}