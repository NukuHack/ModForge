package modforge.backend.model.storm;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An operation node in a STORM rule. Like {@link GenericSelector}, operations
 * can carry child operations, making the tree recursive.
 *
 * <p>Special-case flags ({@code isStat} / {@code isSpan}) are UI hints from the
 * C# {@code RuleOperation} component and control which attribute fields are
 * visible for {@code setAttribute} / {@code modAttribute} operations.</p>
 */
@Getter
@lombok.extern.slf4j.Slf4j
public final class GenericOperation {
	
	/**
	 * Key-value attributes for this operation.
	 * Ordered map to preserve serialization round-trips.
	 */
	private final Map<String, String> attributes = new LinkedHashMap<>();
	/**
	 * Nested child operations. Not all operations have children, but the model
	 * supports arbitrary depth.
	 */
	private final List<GenericOperation> children = new ArrayList<>();
	/** XML tag name of this operation, e.g. {@code "setAttribute"}, {@code "addExp"}. */
	private String name = "";
	/**
	 * UI-only flag: when {@code true} the operation targets a Stat, otherwise a Skill.
	 * Only meaningful for {@code setAttribute} / {@code modAttribute}.
	 */
	@Setter
	private boolean isStat = false;
	
	/**
	 * UI-only flag: when {@code true} the operation uses a span (min/max range),
	 * otherwise a fixed value.
	 */
	@Setter
	private boolean isSpan = false;
	
	// -------------------------------------------------------------------------
	// Construction helpers
	// -------------------------------------------------------------------------
	
	public GenericOperation() {
	}
	
	public GenericOperation(String name) {
		this.name = name;
	}
	
	// -------------------------------------------------------------------------
	// Accessors
	// -------------------------------------------------------------------------
	
	public void setName(String name) {
		this.name = name == null ? "" : name;
	}
	
	public void putAttribute(String key, String value) {
		attributes.put(key, value == null ? "" : value);
	}
	
	// -------------------------------------------------------------------------
	// Deep-copy
	// -------------------------------------------------------------------------
	
	public GenericOperation deepCopy() {
		var copy = new GenericOperation(this.name);
		copy.attributes.putAll(this.attributes);
		copy.isStat = this.isStat;
		copy.isSpan = this.isSpan;
		for (var child : this.children) {
			copy.children.add(child.deepCopy());
		}
		return copy;
	}
	
	@Override
	public String toString() {
		return "GenericOperation{name='" + name + "', attrs=" + attributes.size() + ", children=" + children.size() + "}";
	}
}
