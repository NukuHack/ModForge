package modforge.backend.model;

import modforge.backend.model.attributes.IAttribute;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static modforge.Util.escapeHtml;

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


	/**
	 * Get all item details as plain text for copying
	 */
	default String details() {
		StringBuilder sb = new StringBuilder();
		sb.append("ID: ").append(this.getId()).append("\n");
		sb.append("Class: ").append(this.getClass().getSimpleName()).append("\n");
		sb.append("Path: ").append(this.getPath()).append("\n");

		// Show attributes if any
		if (!this.getAttributes().isEmpty()) {
			sb.append("\nAttributes:\n");
			for (var attr : this.getAttributes()) {
				sb.append("  • ").append(attr.getName()).append(": ").append(attr.getValue()).append("\n");
			}
		}

		// Show linked IDs if any
		if (!this.getLinkedIds().isEmpty()) {
			sb.append("\nLinked Items:\n");
			for (String linkedId : this.getLinkedIds()) {
				sb.append("  • ").append(linkedId).append("\n");
			}
		}

		return sb.toString();
	}


	/**
	 * Update the detail panel with the selected item's information
	 */
	default String detailPanel() {
		// Build detailed information about the item
		final StringBuilder details = new StringBuilder();
		details.append("<html><div style='font-family: monospace;'>");

		// Make ID clickable with a special style
		details.append("<div style='cursor: pointer; display: inline-block;' onclick='copyId()'>");
		details.append("<b style='color:#89b4fa; font-size:14px; text-decoration: underline; text-decoration-color: #89b4fa;'>")
				.append(escapeHtml(this.getId()))
				.append("</b>");
		details.append("</div>");
		details.append("<span style='color:#6c6f85; font-size: 10px; margin-left: 8px;'>(click to copy)</span>");
		details.append("<br/><br/>");
		details.append("<span style='color:#6c6f85'>");

		details.append("Class: ").append(this.getClass().getSimpleName()).append("<br/>");
		details.append("Path: ").append(escapeHtml(this.getPath())).append("<br/>");

		// Show attributes if any
		if (!this.getAttributes().isEmpty()) {
			details.append("<br/><b>Attributes:</b><br/>");
			for (var attr : this.getAttributes()) {
				details.append("• ").append(escapeHtml(attr.getName())).append(": ")
						.append(escapeHtml(String.valueOf(attr.getValue()))).append("<br/>");
			}
		}

		// Show linked IDs if any
		if (!this.getLinkedIds().isEmpty()) {
			details.append("<br/><b>Linked Items:</b><br/>");
			for (String linkedId : this.getLinkedIds()) {
				details.append("• ").append(escapeHtml(linkedId)).append("<br/>");
			}
		}

		details.append("</span></div></html>");
		return (details.toString());
	}
}