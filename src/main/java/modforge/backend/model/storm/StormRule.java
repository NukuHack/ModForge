package modforge.backend.model.storm;

import java.util.ArrayList;
import java.util.List;

/**
 * A single rule inside a STORM file.
 *
 * <p>A rule bundles a list of {@link GenericSelector selectors} (the "when" side)
 * with a list of {@link GenericOperation operations} (the "then" side).</p>
 */
public final class StormRule {

    private String name    = "";
    private String comment = "";

    private final List<GenericSelector>  selectors  = new ArrayList<>();
    private final List<GenericOperation> operations = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String getName()    { return name; }
    public void   setName(String name)    { this.name    = name == null ? "" : name; }

    public String getComment() { return comment; }
    public void   setComment(String comment) { this.comment = comment == null ? "" : comment; }

    public List<GenericSelector>  getSelectors()  { return selectors; }
    public List<GenericOperation> getOperations() { return operations; }

    @Override
    public String toString() {
        return "StormRule{name='" + name + "', selectors=" + selectors.size()
                + ", operations=" + operations.size() + "}";
    }
}
