package modforge.backend.model.storm;

import java.util.ArrayList;
import java.util.List;

/**
 * A custom selector declared at the top of a STORM file
 * (i.e. a named selector template, not a usage instance).
 */
public final class CustomStormSelector {

    private String name    = "";
    private String comment = "";

    /** Attribute names defined by this custom selector. */
    private final List<String> attributeNames = new ArrayList<>();

    public String getName()    { return name; }
    public void   setName(String name)    { this.name    = name == null ? "" : name; }

    public String getComment() { return comment; }
    public void   setComment(String comment) { this.comment = comment == null ? "" : comment; }

    public List<String> getAttributeNames() { return attributeNames; }

    @Override
    public String toString() {
        return "CustomStormSelector{name='" + name + "'}";
    }
}
