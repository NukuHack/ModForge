package modforge.backend.model.storm;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * A single rule inside a STORM file.
 *
 * <p>A rule bundles a list of {@link GenericSelector selectors} (the "when" side)
 * with a list of {@link GenericOperation operations} (the "then" side).</p>
 */
@Getter
@lombok.extern.slf4j.Slf4j
public final class StormRule {
	
	private final List<GenericSelector> selectors = new ArrayList<>();
	private final List<GenericOperation> operations = new ArrayList<>();
	private String name = "";
	private String comment = "";
	
	// -------------------------------------------------------------------------
	// Accessors
	// -------------------------------------------------------------------------
	
	public void setName(String name) {
		this.name = name == null ? "" : name;
	}
	
	public void setComment(String comment) {
		this.comment = comment == null ? "" : comment;
	}
	
	@Override
	public String toString() {
		return "StormRule{name='" + name + "', selectors=" + selectors.size() + ", operations=" + operations.size() + "}";
	}
}
