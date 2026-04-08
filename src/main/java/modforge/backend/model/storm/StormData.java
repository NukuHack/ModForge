package modforge.backend.model.storm;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

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
 * &lt;storm&gt;
 *   &lt;common&gt;
 *     &lt;source path="..."/&gt;
 *   &lt;/common&gt;
 *   &lt;customSelectors&gt; ... &lt;/customSelectors&gt;
 *   &lt;customOperations&gt; ... &lt;/customOperations&gt;
 *   &lt;tasks&gt; ... &lt;/tasks&gt;
 *   &lt;rules&gt;
 *     &lt;rule name="..." comment="..."&gt;
 *       &lt;selectors&gt;
 *         &lt;and&gt;
 *           &lt;selector .../&gt;
 *           &lt;or&gt;
 *             &lt;selector .../&gt;
 *             &lt;not&gt;&lt;selector .../&gt;&lt;/not&gt;
 *           &lt;/or&gt;
 *         &lt;/and&gt;
 *       &lt;/selectors&gt;
 *       &lt;operations&gt;
 *         &lt;setAttribute ...&gt;
 *           &lt;subOp .../&gt;
 *         &lt;/setAttribute&gt;
 *       &lt;/operations&gt;
 *     &lt;/rule&gt;
 *   &lt;/rules&gt;
 * &lt;/storm&gt;
 * </pre>
 */
@Getter
@Slf4j
public final class StormData {
	
	private final List<String> commonSources = new ArrayList<>();
	private final List<CustomStormSelector> customSelectors = new ArrayList<>();
	private final List<CustomStormOperation> customOperations = new ArrayList<>();
	private final List<StormTask> tasks = new ArrayList<>();
	private final List<StormRule> rules = new ArrayList<>();
	
	/** Stable identifier derived from the file path (used for navigation). */
	@Setter
	private String id = "";
	
	/**
	 * Optional category grouping (derived from the file path or a {@code category}
	 * attribute on the root element). {@code null} means "Miscellaneous".
	 */
	@Setter
	private String category = null;
	
	@Override
	public String toString() {
		return String.format("StormData{id='%s', category=%s, commonSources=%d, rules=%d, tasks=%d}", id, category, commonSources.size(), rules.size(), tasks.size());
	}
}