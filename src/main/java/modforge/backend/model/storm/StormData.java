package modforge.backend.model.storm;

import lombok.Getter;
import lombok.Setter;
import modforge.backend.DataPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * The fully-parsed content of a single {@code .storm} / Storm XML file.
 *
 * <p>Mirrors the C# {@code StormDto} class while dropping Blazor-specific
 * rendering concerns.</p>
 *
 * <p>Structure of a Storm file (simplified):</p>
 * <pre>
 * &lt;AISystem&gt;
 *   &lt;CustomSelectors&gt; ... &lt;/CustomSelectors&gt;
 *   &lt;CustomOperations&gt; ... &lt;/CustomOperations&gt;
 *   &lt;Tasks&gt; ... &lt;/Tasks&gt;
 *   &lt;Rules&gt;
 *     &lt;Rule name="..." comment="..."&gt;
 *       &lt;Conditions&gt;
 *         &lt;and&gt;           &lt;!-- combinator: recursive, ≥3 levels deep --&gt;
 *           &lt;selector .../&gt;
 *           &lt;or&gt;
 *             &lt;selector .../&gt;
 *             &lt;not&gt;&lt;selector .../&gt;&lt;/not&gt;
 *           &lt;/or&gt;
 *         &lt;/and&gt;
 *       &lt;/Conditions&gt;
 *       &lt;Operations&gt;
 *         &lt;setAttribute ...&gt;
 *           &lt;subOp .../&gt;     &lt;!-- operations can also nest --&gt;
 *         &lt;/setAttribute&gt;
 *       &lt;/Operations&gt;
 *     &lt;/Rule&gt;
 *   &lt;/Rules&gt;
 * &lt;/AISystem&gt;
 * </pre>
 */
@Getter
@lombok.extern.slf4j.Slf4j
public final class StormData {
	
	private final List<CustomStormSelector> customSelectors = new ArrayList<>();
	private final List<CustomStormOperation> customOperations = new ArrayList<>();
	private final List<StormTask> tasks = new ArrayList<>();
	private final List<StormRule> rules = new ArrayList<>();
	/** Stable identifier derived from the file path (used for navigation). */
	private String id = "";
	/**
	 * Optional category grouping (derived from the file path or a {@code category}
	 * attribute on the root element). {@code null} means "Miscellaneous".
	 */
	@Setter
	private String category = null;
	/** Source file information (path inside a PAK, endpoint tag, type). */
	@Setter
	private DataPoint dataPoint;
	
	// -------------------------------------------------------------------------
	// Accessors
	// -------------------------------------------------------------------------
	
	public void setId(String id) {
		this.id = id == null ? "" : id;
	}
	
	@Override
	public String toString() {
		return "StormData{id='" + id + "', category=" + category + ", rules=" + rules.size() + ", tasks=" + tasks.size() + "}";
	}
}
