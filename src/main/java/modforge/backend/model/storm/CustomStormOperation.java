package modforge.backend.model.storm;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A custom operation declared at the top of a STORM file.
 *
 * <p>Custom operations can expose "mod attributes" that describe how
 * a stat can be modified (min/max mod values).</p>
 *
 * <p>Example XML:</p>
 * <pre>{@code
 * <customOperations>
 *   <operation name="myOp" mode="add">
 *     <attribute stat="Strength" minMod="0" maxMod="1"/>
 *   </operation>
 * </customOperations>
 * }</pre>
 */
@Getter
@lombok.extern.slf4j.Slf4j
public final class CustomStormOperation {
	
	/** Attribute ranges exposed by this operation. */
	private final List<ModAttribute> modAttributes = new ArrayList<>();
	
	private String name = "";
	
	/**
	 * Optional mode string from the {@code mode} XML attribute (e.g. {@code "add"},
	 * {@code "set"}). Empty string when absent.
	 */
	private String mode = "";
	
	// -------------------------------------------------------------------------
	
	public void setName(String name) {
		this.name = name == null ? "" : name;
	}
	
	public void setMode(String mode) {
		this.mode = mode == null ? "" : mode;
	}
	
	@Override
	public String toString() {
		return "CustomStormOperation{name='" + name + "', mode='" + mode + "', modAttrs=" + modAttributes.size() + "}";
	}
	
	// -------------------------------------------------------------------------
	// Nested type
	// -------------------------------------------------------------------------
	
	/**
	 * Describes the numeric range of a modifiable attribute within a custom operation.
	 */
	@Getter
	public static final class ModAttribute {
		private String stat = "";
		@Setter
		private double minMod = 0;
		@Setter
		private double maxMod = 0;
		
		public void setStat(String stat) {
			this.stat = stat == null ? "" : stat;
		}
		
		@Override
		public String toString() {
			return "ModAttribute{stat='" + stat + "', range=[" + minMod + "," + maxMod + "]}";
		}
	}
}