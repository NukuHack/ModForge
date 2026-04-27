package com.nukuhack.modforge.backend.model;

import com.nukuhack.modforge.backend.ItemType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Base class representing a generic mod item.
 *
 * <p>Holds a typed attribute list, a path (optionally prefixed with a namespace
 * separated by {@code :}), and an identifier. Attribute look-ups are
 * case-insensitive name-contains matches unless stated otherwise.
 */
@Slf4j
@Setter
@NoArgsConstructor
public abstract class ModItem {

	/**
	 * Attribute names (checked case-insensitively) whose values are
	 * localization keys rather than raw display strings.
	 */
	public static final Set<String> LANG_ATTR_HINTS = Set.of(
			"UIName", "Desc", "UIInfo",
			"perk_ui_lore_desc", "perk_ui_desc", "perk_ui_name",
			"slot_buff_ui_name", "buff_ui_name", "buff_ui_desc"
	);

	// ── Fields ────────────────────────────────────────────────────────────────

	private final List<Attribute> attributes = new ArrayList<>();

	// TODO: migrate from String to a richer ID type once the data model allows it —
	//       current data contains numeric IDs (0, -1) as well as plain string names.
	@Getter
	private String id;

	@Getter
	private String path;

	// ── ID key ────────────────────────────────────────────────────────────────

	/**
	 * Returns the XML element mapping key for this item's concrete type.
	 */
	public @NonNull String getIdKey() {
		return ItemType.getIdKey(this.getClass());
	}

	// ── Path helpers ──────────────────────────────────────────────────────────

	/**
	 * Returns the prefix portion of the path (everything before the first
	 * {@code :}), or an empty string when no prefix is present.
	 */
	public @NonNull String getPathPrefix() {
		var colon = path.indexOf(':');
		return colon >= 0 ? path.substring(0, colon) : "";
	}

	/**
	 * Returns the path without its prefix (everything after the first
	 * {@code :}), or the full path when no prefix is present.
	 */
	public @NonNull String getPathWithoutPrefix() {
		var colon = path.indexOf(':');
		return colon >= 0 ? path.substring(colon + 1) : path;
	}

	/** Returns {@code true} when the path contains a {@code :} separator. */
	public boolean hasPathPrefix() {
		return path.contains(":");
	}

	/**
	 * Replaces an existing prefix in the path, or prepends one if none is
	 * present yet.
	 *
	 * @param prefix the new prefix, without a trailing colon
	 */
	public void setPathPrefix(@NonNull String prefix) {
		var colon = path.indexOf(':');
		path = colon >= 0
				? prefix + path.substring(colon)   // replace
				: prefix + ":" + path;              // prepend
	}

	/** Removes the prefix from the path if one is present. */
	public void removePathPrefix() {
		path = getPathWithoutPrefix();
	}

	// ── Attribute accessors ───────────────────────────────────────────────────

	/** Returns an unmodifiable view of all attributes. */
	public @NonNull List<Attribute> getAttributes() {
		return Collections.unmodifiableList(attributes);
	}

	/** Replaces all attributes with the given collection. */
	public void setAttribute(@NonNull Collection<Attribute> attrs) {
		attributes.clear();
		attributes.addAll(attrs);
	}

	/** Appends a single attribute. */
	public void addAttribute(@NonNull Attribute attr) {
		attributes.add(attr);
	}

	/** Appends all attributes in the given collection. */
	public void addAttribute(@NonNull Collection<Attribute> attrs) {
		attributes.addAll(attrs);
	}

	/** Removes a specific attribute instance. */
	public void removeAttribute(@NonNull Attribute attr) {
		attributes.remove(attr);
	}

	/**
	 * Removes all attributes whose name matches {@code name}
	 * (case-insensitive, exact match).
	 */
	public void removeAttributeByName(@NonNull String name) {
		var lo = name.toLowerCase(Locale.ROOT);
		attributes.removeIf(a -> a.getName().toLowerCase(Locale.ROOT).equals(lo));
	}

	// ── Attribute queries ─────────────────────────────────────────────────────

	/**
	 * Returns the first attribute whose name (case-insensitive) <em>contains</em>
	 * {@code candidate}, or {@link Optional#empty()} when none matches.
	 */
	public @NonNull Optional<Attribute> findAttr(@NonNull String candidate) {
		var lo = candidate.toLowerCase(Locale.ROOT);
		return attributes.stream()
				.filter(a -> a.getName().toLowerCase(Locale.ROOT).contains(lo))
				.findFirst();
	}

	/**
	 * Returns all attributes whose name (case-insensitive) <em>contains</em>
	 * {@code candidate}.
	 */
	public @NonNull List<Attribute> findAttrs(@NonNull String candidate) {
		var lo = candidate.toLowerCase(Locale.ROOT);
		return attributes.stream()
				.filter(a -> a.getName().toLowerCase(Locale.ROOT).contains(lo))
				.collect(Collectors.toList());
	}

	/**
	 * Returns all attributes whose names appear in {@link #LANG_ATTR_HINTS}
	 * and whose values are {@link Attribute.StringAttribute} instances.
	 */
	public @NonNull List<Attribute.StringAttribute> getLangAttributes() {
		final var result = new ArrayList<Attribute.StringAttribute>();
		for (final var attr : attributes) {
			if (attr instanceof Attribute.StringAttribute sa
					&& LANG_ATTR_HINTS.contains(attr.getName())) {
				result.add(sa);
			}
		}
		return result;
	}

	// ── Human-readable details ────────────────────────────────────────────────

	/**
	 * Returns a plain-text summary of this item (id, type, path, attributes)
	 * suitable for display or copying.
	 */
	public @NonNull String details() {
		final var sb = new StringBuilder();
		sb.append("ID:    ").append(id).append('\n');
		sb.append("Class: ").append(getClass().getSimpleName()).append('\n');
		sb.append("Path:  ").append(path).append('\n');

		if (!attributes.isEmpty()) {
			sb.append("\nAttributes:\n");
			for (final var attr : attributes) {
				sb.append("  • ").append(attr.getName())
						.append(": ").append(attr.getValue()).append('\n');
			}
		}

		return sb.toString();
	}

	// ── Object overrides ──────────────────────────────────────────────────────

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) return false;
		final ModItem that = (ModItem) o;
		return Objects.equals(id, that.id) && Objects.equals(path, that.path);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, path);
	}

	@Override
	public String toString() {
		return getClass().getName()
				+ "{id='" + id + '\''
				+ ", path='" + path + '\''
				+ ", attributes=" + attributes
				+ '}';
	}
}