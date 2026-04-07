package modforge.backend.model.storm;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * A single rule inside a STORM file.
 *
 * <p>A rule bundles a list of {@link GenericSelector selectors} (the "when" side)
 * with a list of {@link GenericOperation operations} (the "then" side).</p>
 *
 * <p>Example XML:</p>
 * <pre>{@code
 * <rule name="underwear_tarasMura_taras" mode="..." comment="...">
 *   <selectors>
 *     <hasName name="ksta_taras"/>
 *   </selectors>
 *   <operations>
 *     <setUnderwear name="tarasMura_underwear"/>
 *   </operations>
 * </rule>
 * }</pre>
 */
@Getter
@lombok.extern.slf4j.Slf4j
public final class StormRule {
	
	private final List<GenericSelector> selectors = new ArrayList<>();
	private final List<GenericOperation> operations = new ArrayList<>();
	
	private String name = "";
	private String comment = "";
	
	/**
	 * Optional mode string from the {@code mode} XML attribute.
	 * Empty string when absent.
	 */
	private String mode = "";
	
	// -------------------------------------------------------------------------
	// Accessors
	// -------------------------------------------------------------------------
	
	public void setName(String name) {
		this.name = name == null ? "" : name;
	}
	
	public void setComment(String comment) {
		this.comment = comment == null ? "" : comment;
	}
	
	public void setMode(String mode) {
		this.mode = mode == null ? "" : mode;
	}
	
	@Override
	public String toString() {
		return "StormRule{name='" + name + "', mode='" + mode
					   + "', selectors=" + selectors.size()
					   + ", operations=" + operations.size() + "}";
	}
}