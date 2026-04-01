package modforge.backend;

import java.util.ArrayList;

public final class ModCollection extends ArrayList<ModData> {
	public void addMod(ModData m) {
		add(m);
	}

	public void removeMod(ModData m) {
		remove(m);
	}

	public ModData getMod(String id) {
		return stream().filter(m -> id.equals(m.id)).findFirst().orElse(null);
	}
}
