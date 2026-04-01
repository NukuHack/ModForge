package modforge.backend.service;

import modforge.backend.DataPointFactory;
import modforge.backend.model.IDataPoint;
import modforge.backend.model.IModItem;
import modforge.backend.model.IModItemAdapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class ItemService {
	private static final Logger log = Logger.getLogger(ItemService.class.getName());

	private final ItemAdapter adapter;
	private final UserService configService;

	public ItemService(ItemAdapter adapter, UserService configService) {
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
		return Stream.of(items)
				.flatMap(Collection::stream)
				.filter(x -> id.equals(x.getId()))
				.findFirst();
	}

	// ------------------------------------------------------------------

	private void readAll(String gameDir) {
		long start = System.currentTimeMillis();

		final var allPoints = new ArrayList<IDataPoint>();
		ItemType.endpoints().forEach((type, eps) ->
				eps.forEach((key, pak) ->
						allPoints.add(DataPointFactory.create(gameDir + "/" + pak, key, type))));

		items.addAll(adapter.readModItems(allPoints));

		log.info(String.format("XML read done in %d ms | items=%d",
				System.currentTimeMillis() - start, items.size()));
	}
}
