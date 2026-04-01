package modforge.backend.service;

import modforge.backend.DataPointFactory;
import modforge.backend.model.IDataPoint;
import modforge.backend.model.IModItem;
import modforge.backend.model.IModItemAdapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class XmlService {
	private static final Logger log = Logger.getLogger(XmlService.class.getName());

	private final IModItemAdapter adapter;
	private final UserConfigurationService configService;

	public List<IModItem> perks = new ArrayList<>();
	public List<IModItem> buffs = new ArrayList<>();
	public List<IModItem> weapons = new ArrayList<>();
	public List<IModItem> armors = new ArrayList<>();
	public List<IModItem> consumables = new ArrayList<>();
	public List<IModItem> craftingMaterials = new ArrayList<>();
	public List<IModItem> miscItems = new ArrayList<>();
	public List<IModItem> weaponClasses = new ArrayList<>();

	public XmlService(IModItemAdapter adapter,
					  LocalizationService localizationService,
					  UserConfigurationService configService) {
		this.adapter = adapter;
		this.configService = configService;
	}

	/**
	 * Try to read all XML pak files.
	 * Returns false if the game directory is not configured.
	 */
	public boolean tryReadXmlFiles() {
		String dir = configService.getCurrent().gameDirectory;
		if (dir == null || dir.isBlank()) {
			log.warning("Game directory is not configured.");
			return false;
		}
		readAll(dir);
		return true;
	}

	/**
	 * Look up a mod item by ID across all loaded collections.
	 */
	public Optional<IModItem> getModItem(String id) {
		return Stream.of(perks, buffs, weapons, armors,
						consumables, craftingMaterials, miscItems)
				.flatMap(Collection::stream)
				.filter(x -> id.equals(x.getId()))
				.findFirst();
	}

	// ------------------------------------------------------------------

	private void readAll(String gameDir) {
		long start = System.currentTimeMillis();

		var allPoints = new ArrayList<IDataPoint>();
		ItemType.endpoints().forEach((type, eps) ->
				eps.forEach((key, pak) ->
						allPoints.add(DataPointFactory.create(
								gameDir + "/" + pak, key, type))));

		ItemType.PERK.get().forEach(t -> perks.addAll(readOf(allPoints, t)));
		ItemType.BUFF.get().forEach(t -> buffs.addAll(readOf(allPoints, t)));
		ItemType.WEAPON_CLASS.get().forEach(t -> weaponClasses.addAll(readOf(allPoints, t)));
		ItemType.WEAPON_TYPE.get().forEach(t -> weapons.addAll(readOf(allPoints, t)));
		ItemType.ARMOR_TYPE.get().forEach(t -> armors.addAll(readOf(allPoints, t)));
		ItemType.CONSUMABLE_TYPE.get().forEach(t -> consumables.addAll(readOf(allPoints, t)));
		ItemType.CRAFTING_TYPE.get().forEach(t -> craftingMaterials.addAll(readOf(allPoints, t)));
		ItemType.MISC_TYPE.get().forEach(t -> miscItems.addAll(readOf(allPoints, t)));

		log.info(String.format(
				"XML read done in %d ms | perks=%d buffs=%d weapons=%d armors=%d",
				System.currentTimeMillis() - start,
				perks.size(), buffs.size(), weapons.size(), armors.size()));
	}

	private List<IModItem> readOf(List<IDataPoint> all, Class<?> type) {
		return all.stream()
				.filter(dp -> dp.type().equals(type))
				.flatMap(dp -> adapter.readModItems(dp).stream())
				.collect(Collectors.toCollection(ArrayList::new));
	}
}
