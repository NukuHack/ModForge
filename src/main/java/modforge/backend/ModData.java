package modforge.backend;

import modforge.backend.model.IModItem;
import modforge.backend.model.Language;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ModData {
	public String id = "";
	public String name = "";
	public String description = "";
	public String author = "";
	public String modVersion = "";
	public String createdOn = "";
	public boolean modifiesLevel = false;
	public List<String> supportsGameVersions = new ArrayList<>();
	public List<IModItem> items = new ArrayList<>();

	/**
	 * lang-code -> (string-key -> localised-value)
	 */
	public Map<Language, Map<String, String>> localizations = new EnumMap<Language, Map<String, String>>(Language.class);

	@Override
	public String toString() {
		return "Mod[" + id + "]";
	}


	public static final ModData BASE_GAME = new ModData();
}
