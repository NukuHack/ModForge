package modforge.backend.service;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class PakReader implements Closeable {
	private static final Logger log = Logger.getLogger(PakReader.class.getName());

	private final ZipFile zip;
	/**
	 * Normalised-key (lowercase forward-slash) -> entry, for O(1) lookup.
	 */
	private final Map<String, ZipEntry> entries = new HashMap<>();

	public PakReader(String pakPath) throws IOException {
		this.zip = new ZipFile(new File(pakPath));
		var it = zip.entries();
		while (it.hasMoreElements()) {
			var e = it.nextElement();
			entries.put(normalise(e.getName()), e);
		}
	}

	private static String normalise(String s) {
		return s.replace('\\', '/').toLowerCase(Locale.ROOT);
	}

	/**
	 * Read a text file from the archive.
	 * Tries exact match first, then a suffix match (relative-path fallback).
	 */
	public String readFile(String nameOrPath) throws IOException {
		String norm = normalise(nameOrPath);
		ZipEntry entry = entries.get(norm);

		if (entry == null) {
			entry = entries.values().stream()
					.filter(e -> normalise(e.getName()).endsWith(norm))
					.findFirst().orElse(null);
		}

		if (entry == null) {
			log.warning("[PakReader] Not found in pak: " + nameOrPath);
			return null;
		}

		try (var is = zip.getInputStream(entry)) {
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/**
	 * Returns the raw XML string for a storm virtual path, or null.
	 */
	public String readStormXml(String virtualPath) throws IOException {
		return readFile(virtualPath);
	}

	public Set<String> entryNames() {
		return Collections.unmodifiableSet(entries.keySet());
	}

	@Override
	public void close() throws IOException {
		zip.close();
	}
}
