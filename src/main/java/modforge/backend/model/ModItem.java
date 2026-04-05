package modforge.backend.model;

import modforge.backend.model.attributes.Attribute;
import modforge.backend.model.attributes.StringAttribute;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ModItem {
	// ── Attribute names that hold localization keys (checked case-insensitively)
	// TODO : right now you can only filter on actual items and since we don't have any item with like straight "UIName" because that si a special item, we can't filter on them
	Set<String> LANG_ATTR_HINTS = Set.of("UIName", "Desc", "UIInfo", "UiSound", "LatinName", "perk_ui_lore_desc", "perk_ui_desc", "perk_ui_name", "slot_buff_ui_name", "buff_ui_name", "buff_ui_desc");
	
	String getId();
	
	void setId(final String id);
	
	String getIdKey();
	
	String getPath();
	
	void setPath(final String path);
	
	List<Attribute> getAttributes();
	
	void setAttribute(final List<Attribute> attributes);
	
	void addAttribute(final Attribute attr);
	
	void removeAttribute(final Attribute attr);
	
	void addAttribute(final Collection<Attribute> attributes);
	
	List<String> getLinkedIds();
	
	void setLinkedId(final Collection<String> linkedIds);
	
	void addLinkedId(final String linkedId);
	
	void addLinkedId(final Collection<String> linkedId);
	
	Optional<Attribute> findAttr(final String candidate);
	
	default List<StringAttribute> getLangAttributes() {
		return getAttributes().stream().filter(a -> LANG_ATTR_HINTS.contains(a.getName().toLowerCase())).map(a -> (StringAttribute) a).toList();
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
		if (! this.getLinkedIds().isEmpty()) {
			sb.append("\nLinked Items:\n");
			for (String linkedId : this.getLinkedIds()) {
				sb.append("  • ").append(linkedId).append("\n");
			}
		}
		
		return sb.toString();
	}
}