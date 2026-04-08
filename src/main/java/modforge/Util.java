package modforge;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import modforge.backend.model.E.Language;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Utility class providing common file operations, path handling, XML/JSON escaping,
 * PAK file packing, and cross-platform directory utilities for the ModForge application.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@lombok.extern.slf4j.Slf4j
public final class Util {
	// ================================================================================
	// APPLICATION CONSTANTS - Modify these to change application behavior
	// ================================================================================
	
	/** Application name used for config directories and UI display */
	public static final String APP_NAME = "ModForge";
	
	/** Operating system name (lowercase) for cross-platform detection */
	public static final String os = System.getProperty("os.name").toLowerCase();
	
	/** Username cross-platform */
	public static final String username;
	static {
		String user = System.getenv("USERNAME"); // Windows
		if (user == null) {
			user = System.getenv("USER"); // Linux/Mac
		}
		if (user == null) {
			user = System.getProperty("user.name");
		}
		username = user;
	}
	
	// ================================================================================
	// GAME FILE CONSTANTS - Modify if game file names change
	// ================================================================================
	
	/** Main game tables PAK file containing game data tables */
	public static final String TABLES = "Tables.pak";
	
	/** Storm engine PAK file containing core engine resources */
	public static final String STORM = "Storm.pak";
	
	/** Standard compression/archive format extension for game files */
	public static final String COMP_FORMAT = ".pak";
	
	/** Standard data file format extension for XML configuration files */
	public static final String DATA_FORMAT = ".xml";
	
	/** Icons and UI graphics PAK file */
	public static final String ICONS = "IPL_GameData.pak";
	
	// ================================================================================
	// DIRECTORY STRUCTURE CONSTANTS - Modify to change folder organization
	// ================================================================================
	
	/** Directory name for localization/language files */
	public static final String LOCALIZATION_DIR = "Localization";
	
	/** Suffix added to language names for localization PAK files (e.g., "en_xml") */
	public static final String LOCALIZATION_EXTRA = "_xml";
	
	/** Main game data directory containing core game assets */
	public static final String DATA_DIR = "Data";
	
	/** Directory containing game libraries/dependencies */
	public static final String LIBS_DIR = "Libs";
	
	/** Directory containing game table definitions */
	public static final String TABLES_DIR = "Tables";
	
	/** Root directory for all mod installations */
	public static final String MODS_DIR = "Mods";
	
	// ================================================================================
	// STRING UTILITIES
	// ================================================================================
	
	/**
	 * Reads all text from an input stream using UTF-8 encoding.
	 *
	 * @param is Input stream to read from
	 * @return String containing all text from the stream
	 * @throws IOException If an I/O error occurs
	 */
	public static String readAllText(InputStream is) throws IOException {
		return new String(is.readAllBytes(), StandardCharsets.UTF_8);
	}
	
	/**
	 * Capitalizes the first character of a string and lowercases the rest.
	 *
	 * @param s Input string (maybe null or empty)
	 * @return String with first letter capitalized, or original if null/empty
	 */
	public static String capitalStart(String s) {
		if (s == null || s.isEmpty())
			return s;
		return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
	}
	
	// ================================================================================
	// PATH CONSTRUCTION METHODS - All paths are built using constants above
	// ================================================================================
	
	/**
	 * Returns the game data directory path.
	 * Structure: {root}/{DATA_DIR}
	 *
	 * @param root Base game installation directory
	 * @return Path to the Data directory
	 */
	public static Path gameDataDir(String root) {
		return joinP(root, DATA_DIR);
	}
	
	/**
	 * Returns the localization directory for a specific mod.
	 * Structure: {root}/{MODS_DIR}/{modId}/{LOCALIZATION_DIR}
	 *
	 * @param root Base game installation directory
	 * @param modId Unique identifier for the mod
	 * @return Path to the mod's localization directory
	 */
	public static Path modLocalDir(String root, String modId) {
		return joinP(modFolder(root, modId), LOCALIZATION_DIR);
	}
	
	/**
	 * Returns the staging directory for mod file operations.
	 * Structure: {modData}/{_stage}
	 *
	 * @param gameDir Game installation directory
	 * @param modId Unique identifier for the mod
	 * @return Path to staging directory
	 */
	public static Path modStaging(String gameDir, String modId) {
		return Path.of(Util.modData(gameDir, modId), "_stage");
	}
	
	/**
	 * Returns the Storm staging directory for mod data.
	 * Structure: {modStaging}/{DATA_DIR}
	 *
	 * @param gameDir Game installation directory
	 * @param modId Unique identifier for the mod
	 * @return Path to Storm staging directory
	 */
	public static Path modStormStaging(String gameDir, String modId) {
		return Path.of(Util.modStaging(gameDir, modId).toString(), DATA_DIR, "_Storm");
	}
	
	/**
	 * Returns the localization import PAK file path for a language.
	 * Structure: {root}/{LOCALIZATION_DIR}/{lang}{LOCALIZATION_EXTRA}.pak
	 *
	 * @param root Base game installation directory
	 * @param lang Target language
	 * @return PAK file path string for the language localization
	 */
	public static String locImport(String root, Language lang) {
		return join(root, LOCALIZATION_DIR, lang.getName() + LOCALIZATION_EXTRA);
	}
	
	/**
	 * Returns the export path for a mod's localization XML file.
	 * Structure: {modLocalDir}/{lang}{LOCALIZATION_EXTRA}/{text__{modId}.xml}
	 *
	 * @param root Base game installation directory
	 * @param lang Target language
	 * @param modId Unique identifier for the mod
	 * @return Path to the localization XML export file
	 */
	public static Path locExport(String root, Language lang, String modId) {
		return joinP(modLocalDir(root, modId), lang.getName() + LOCALIZATION_EXTRA, modXmlForce("text", modId));
	}
	
	/**
	 * Generates a mod-specific XML filename from a base filename.
	 * Extracts the stem before "__" or removes .xml extension, then applies mod ID.
	 *
	 * @param fileName Original filename (may contain "__" delimiter or .xml extension)
	 * @param modId Unique identifier for the mod
	 * @return Formatted filename: {stem}__{modId}.xml
	 */
	public static String modXmlFile(String fileName, String modId) {
		final int delimit = fileName.indexOf("__");
		final String nameFinal;
		if (delimit != - 1)
			nameFinal = fileName.substring(0, delimit);
		else if (fileName.endsWith(DATA_FORMAT))
			nameFinal = fileName.substring(0, fileName.length() - 4);
		else
			nameFinal = fileName;
		return modXmlForce(nameFinal, modId);
	}
	
	/**
	 * Forces a filename into the mod XML naming convention.
	 * Format: {fileName}__{modId}{DATA_FORMAT}
	 *
	 * @param fileName Base filename without extension
	 * @param modId Unique identifier for the mod
	 * @return Formatted XML filename
	 */
	private static String modXmlForce(String fileName, String modId) {
		return fileName + "__" + modId + DATA_FORMAT;
	}
	
	/**
	 * Returns the root folder path for a specific mod.
	 * Structure: {root}/{MODS_DIR}/{modId}
	 *
	 * @param root Base game installation directory
	 * @param modId Unique identifier for the mod
	 * @return Path string to mod's root folder
	 */
	public static String modFolder(String root, String modId) {
		return join(root, MODS_DIR, modId);
	}
	
	/**
	 * Returns the mods folder path as a Path object.
	 * Structure: {root}/{MODS_DIR}
	 *
	 * @param root Base game installation directory
	 * @return Path to the Mods directory
	 */
	public static Path modFolder(String root) {
		return joinP(root, MODS_DIR);
	}
	
	/**
	 * Returns the data directory for a specific mod.
	 * Structure: {root}/{MODS_DIR}/{modId}/{DATA_DIR}
	 *
	 * @param root Base game installation directory
	 * @param modId Unique identifier for the mod
	 * @return Path string to mod's data directory
	 */
	public static String modData(String root, String modId) {
		return join(root, MODS_DIR, modId, DATA_DIR);
	}
	
	/**
	 * Returns all localization import paths for every supported language.
	 * Uses locImport() for each Language enum value.
	 *
	 * @param root Base game installation directory
	 * @return Set of PAK file paths for all languages
	 */
	public static Set<String> allLocPaths(String root) {
		return Arrays.stream(Language.values()).map(l -> locImport(root, l)).collect(Collectors.toSet());
	}
	
	/**
	 * Returns the icons PAK file path.
	 * Structure: {gameDataDir}/{ICONS}
	 *
	 * @param gameDir Game installation directory
	 * @return Path to the icons PAK file
	 */
	public static Path icons(String gameDir) {
		return Util.joinP(gameDataDir(gameDir), ICONS);
	}
	
	// ================================================================================
	// PATH JOINING UTILITIES
	// ================================================================================
	
	/**
	 * Join path segments using OS-based separators.
	 *
	 * @param parts Variable number of path segments
	 * @return Combined path string with OS-appropriate separators
	 *
	 * @example Windows: "C:\Users\name\file.txt"
	 * @example Linux: "/home/name/file.txt"
	 */
	public static String join(String... parts) {
		if (parts.length < 1)
			return "";
		return String.join(File.separator, parts);
	}
	
	/**
	 * Join path segments and return as a Path object.
	 *
	 * @param parts Variable number of path segments
	 * @return Path object representing the joined path
	 */
	public static Path joinP(String... parts) {
		if (parts.length < 1)
			return Path.of("");
		else if (parts.length == 1)
			return Path.of(parts[0]);
		return Path.of(join(parts));
	}
	
	/**
	 * Join a base Path with additional segments.
	 *
	 * @param base Base Path object
	 * @param parts Additional path segments to append
	 * @return Combined Path object
	 */
	public static Path joinP(Path base, String... parts) {
		return Path.of(base.toString(), join(parts));
	}
	
	// ================================================================================
	// XML PROCESSING UTILITIES
	// ================================================================================
	
	/**
	 * Normalizes XML by removing whitespace between tags and trimming lines.
	 * Useful for comparing XML files regardless of formatting.
	 *
	 * @param xml Raw XML string
	 * @return Normalized XML string with consistent line breaks and no empty lines
	 */
	public static String normalizeXml(String xml) {
		// Remove whitespace between tags and normalize line endings
		final var nicer = xml.replaceAll(">\\s+<", ">\n<");
		final var sb = new StringBuilder();
		for (String line : nicer.split("\n")) {
			line = line.trim(); // this also removes any bad line breaks like \r ...
			if (line.isEmpty())
				continue;
			sb.append(line).append("\n");
		}
		return sb.toString();
	}
	
	/**
	 * Writes normalized and indented XML to a file.
	 * Creates parent directories if they don't exist.
	 *
	 * @param inp Raw XML content
	 * @param outFile Destination file path
	 * @throws IOException If file writing fails
	 */
	public static void writeXml(String inp, Path outFile) throws IOException {
		final var result = Util.normalizeXml(inp);
		Files.createDirectories(outFile.getParent());
		
		// Write the cleaned output
		try (var fileWriter = new FileWriter(outFile.toFile(), StandardCharsets.UTF_8)) {
			fileWriter.write(result);
		}
	}
	
	// ================================================================================
	// STRING ESCAPING UTILITIES
	// ================================================================================
	
	/**
	 * Escapes special characters for JSON string compatibility.
	 * Handles quotes, backslashes, control characters, and Unicode.
	 *
	 * @param s Input string
	 * @return JSON-escaped string
	 */
	public static String escapeJson(String s) {
		final var sb = new StringBuilder();
		for (char c : s.toCharArray()) {
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\b' -> sb.append("\\b");
				case '\f' -> sb.append("\\f");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
				}
			}
		}
		return sb.toString();
	}
	
	/**
	 * Escapes special characters for HTML.
	 *
	 * @param s Input string (may be null)
	 * @return HTML-escaped string, or empty string if input is null
	 */
	public static String escHtml(String s) {
		if (s == null)
			return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
	}
	
	/**
	 * Escapes special characters for XML.
	 *
	 * @param s Input string
	 * @return XML-escaped string
	 */
	public static String escapeXml(String s) {
		// theoretically escapeHtml could work, but I made this just in case
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
	}
	
	/**
	 * Unescapes common XML entities back to their original characters.
	 *
	 * @param s XML-escaped string (maybe null)
	 * @return Unescaped string, or null if input is null
	 */
	public static String unescapeXml(String s) {
		if (s == null) {
			return null;
		}
		return s.replace("&nbsp;", " ").replace("&apos;", "'").replace("&quot;", "\"").replace("&gt;", ">").replace("&lt;", "<").replace("&amp;", "&");
	}
	
	// ================================================================================
	// CLIPBOARD AND UI UTILITIES
	// ================================================================================
	
	/**
	 * Copies text to the system clipboard.
	 *
	 * @param text Text to copy to clipboard
	 */
	public static void copyText(String text) {
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
	}
	
	/**
	 * Extracts the filename stem from a ZIP entry path (no directory, no extension).
	 *
	 * @param entryName ZIP entry path (e.g., "folder/file.txt")
	 * @return Filename without path or extension (e.g., "file")
	 */
	public static String stemOf(String entryName) {
		var slash = entryName.lastIndexOf('/');
		var filename = slash >= 0 ? entryName.substring(slash + 1) : entryName;
		var dot = filename.lastIndexOf('.');
		return (dot > 0 ? filename.substring(0, dot) : filename).toLowerCase(Locale.ROOT);
	}
	
	/**
	 * Opens a folder selection dialog asynchronously.
	 *
	 * @return CompletableFuture that completes with selected folder path, or null if canceled
	 */
	public static CompletableFuture<String> pickFolderAsync() {
		CompletableFuture<String> future = new CompletableFuture<>();
		SwingUtilities.invokeLater(() -> {
			JFileChooser chooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			chooser.setDialogTitle("Select target folder");
			chooser.setAcceptAllFileFilterUsed(false);
			int result = chooser.showOpenDialog(null);
			String selectedPath = result == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile().getAbsolutePath() : null;
			
			future.complete(selectedPath);
		});
		return future;
	}
	
	/**
	 * Opens a directory in the operating system's file explorer.
	 * Cross-platform support for Windows, macOS, and Linux.
	 *
	 * @param w Parent JPanel for error dialogs
	 * @param dirPath Directory path to open
	 */
	public static void openDirectory(JPanel w, String dirPath) {
		if (dirPath == null || dirPath.isBlank()) {
			JOptionPane.showMessageDialog(w, "Game directory not set. Please configure it in Settings.", "Directory Not Set", JOptionPane.WARNING_MESSAGE);
			return;
		}
		
		final File gameDir = new File(dirPath);
		if (! gameDir.exists() || ! gameDir.isDirectory()) {
			JOptionPane.showMessageDialog(w, "Game directory does not exist or is invalid:\n" + dirPath, "Invalid Directory", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		try {
			// Cross-platform directory opening
			if (Desktop.isDesktopSupported()) {
				Desktop.getDesktop().open(gameDir);
				return;
			}
			// Fallback for systems without Desktop support
			final Runtime runtime = Runtime.getRuntime();
			
			if (os.contains("win")) {
				runtime.exec(new String[] { "explorer", dirPath });
			} else if (os.contains("mac")) {
				runtime.exec(new String[] { "open", dirPath });
			} else if (os.contains("nix") || os.contains("nux")) {
				runtime.exec(new String[] { "xdg-open", dirPath });
			} else {
				JOptionPane.showMessageDialog(w, "Cannot open directory on this operating system.", "Unsupported OS", JOptionPane.ERROR_MESSAGE);
			}
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(w, "Failed to open game directory:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
			log.warn("IOException while chosing folder", ex);
		}
	}
	
	// ================================================================================
	// FILE TYPE DETECTION
	// ================================================================================
	
	/**
	 * Determines if a file is ZIP-like (ZIP or PAK) by checking extension or magic bytes.
	 *
	 * @param path Path to the file
	 * @return true if file has .zip/.pak extension or starts with ZIP magic bytes (PK\x03\x04)
	 */
	public static boolean isZipLike(Path path) {
		final String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
		if (lower.endsWith(".zip") || lower.endsWith(".pak"))
			return true;
		try (var in = Files.newInputStream(path)) {
			final byte[] magic = in.readNBytes(4);
			// ZIP local-file header signature: 0x50 0x4B 0x03 0x04
			return magic.length >= 4 && magic[0] == 0x50 && magic[1] == 0x4B && magic[2] == 0x03 && magic[3] == 0x04;
		} catch (IOException ex) {
			return false;
		}
	}
	
	// ================================================================================
	// CONFIGURATION DIRECTORY - Platform-specific paths using APP_NAME constant
	// ================================================================================
	
	/**
	 * Returns the application configuration directory following platform conventions.
	 * Uses APP_NAME constant for directory naming.
	 *
	 * @return Platform-specific config directory path
	 *
	 * @example Windows: %APPDATA%\ModForge
	 * @example macOS: ~/Library/Application Support/ModForge
	 * @example Linux: ~/.config/modforge
	 */
	public static Path getConfigDir() {
		var home = System.getProperty("user.home");
		if (os.contains("win")) {
			// Windows: %APPDATA%\ {APP_NAME}
			String appData = System.getenv("APPDATA");
			if (appData == null || appData.isBlank()) {
				appData = home + "\\AppData\\Roaming";
			}
			return Paths.get(appData, APP_NAME);
		} else if (os.contains("mac")) {
			// macOS: ~/Library/Application Support/ {APP_NAME}
			return Paths.get(home, "Library", "Application Support", APP_NAME);
		} else {
			// Linux/Unix: ~/.config/ {APP_NAME} (XDG Base Directory Specification)
			String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
			if (xdgConfigHome != null && ! xdgConfigHome.isBlank()) {
				return Paths.get(xdgConfigHome, APP_NAME.toLowerCase());
			}
			// Fallback to ~/.config/ {APP_NAME}
			return Paths.get(home, ".config", APP_NAME.toLowerCase());
		}
	}
	
	// ================================================================================
	// PAK FILE PACKING UTILITIES
	// ================================================================================
	
	/**
	 * Pack a source folder into a destination PAK file.
	 * The PAK will contain the contents of the source folder as the root,
	 * maintaining the relative directory structure.
	 *
	 * @param sourceFolder   The folder to pack (its contents become the PAK root)
	 * @param destPakFile    The output PAK file path
	 * @param fileFilter     Optional filter to exclude certain files (can be null to include all)
	 * @param stripMetadata  If true, removes timestamps and other metadata from ZIP entries
	 * @return true if packing succeeded, false otherwise
	 */
	public static boolean packFolder(final Path sourceFolder, final Path destPakFile, final Predicate<Path> fileFilter, final boolean stripMetadata) {
		if (! Files.exists(sourceFolder) || ! Files.isDirectory(sourceFolder)) {
			log.warn("Source folder does not exist: {}", sourceFolder);
			return false;
		}
		final FileTime ft = FileTime.fromMillis(0);
		try {
			Files.createDirectories(destPakFile.getParent());
			Files.deleteIfExists(destPakFile);
		} catch (final IOException e) {
			log.error("PAK creation failed", e);
			return false;
		}
		try (var fos = new FileOutputStream(destPakFile.toFile()); var out = new ZipOutputStream(fos); var walk = Files.walk(sourceFolder)) {
			
			// Optionally set the ZipOutputStream comment to empty or fixed
			if (stripMetadata) {
				out.setComment(""); // Remove any comments
			}
			
			walk.filter(Files::isRegularFile).filter(path -> fileFilter == null || fileFilter.test(path)).forEach(file -> {
				try {
					// Get the path relative to the source folder
					final String relPath = sourceFolder.relativize(file).toString().replace('\\', '/');
					
					// Create ZipEntry with a fixed timestamp to strip metadata
					final var entry = getZipEntry(relPath, ft, stripMetadata);
					
					out.putNextEntry(entry);
					Files.copy(file, out);
					out.closeEntry();
					
					log.info("Added to PAK: {}", relPath);
				} catch (IOException e) {
					log.warn("Cannot add to pak: {} - {}", file, e.getMessage());
				}
			});
		} catch (IOException e) {
			log.error("PAK creation failed", e);
			return false;
		}
		
		log.info("PAK created: {}", destPakFile);
		return true;
	}
	
	/**
	 * Creates a ZipEntry with optional metadata stripping.
	 *
	 * @param relPath Relative path inside the ZIP
	 * @param ft Fixed FileTime to use when stripping metadata
	 * @param stripMetadata Whether to strip timestamps and metadata
	 * @return Configured ZipEntry
	 */
	private static ZipEntry getZipEntry(final String relPath, final FileTime ft, final boolean stripMetadata) {
		final var entry = new ZipEntry(relPath);
		
		if (stripMetadata) {
			// Set a fixed timestamp (Unix epoch, or any constant value)
			// This prevents storing the actual file modification time
			entry.setTime(0L); // Jan 1 1970 00:00:00 UTC
			
			// Set other metadata to fixed/default values
			entry.setCreationTime(ft);
			entry.setLastAccessTime(ft);
			entry.setLastModifiedTime(ft);
			
			// Optionally set compression method and level
			entry.setMethod(ZipEntry.DEFLATED);
		}
		return entry;
	}
	
	/**
	 * Pack a source folder into a destination PAK file.
	 * Convenience method with no file filter and metadata stripping enabled.
	 */
	public static boolean packFolder(Path sourceFolder, Path destPakFile) {
		return packFolder(sourceFolder, destPakFile, null, true);
	}
	
	/**
	 * Pack a source folder into a destination PAK file, excluding the PAK file itself.
	 * Useful when packing from a folder that might contain the target PAK.
	 */
	public static boolean packFolderExcludingSelf(Path sourceFolder, Path destPakFile) {
		return packFolder(sourceFolder, destPakFile, path -> ! path.equals(destPakFile), true);
	}
	
	// ================================================================================
	// FILE SYSTEM OPERATIONS
	// ================================================================================
	
	/**
	 * Recursively deletes a file or directory and all its contents.
	 *
	 * @param path Path to delete (file or directory)
	 */
	public static void deleteRecursively(Path path) {
		if (! Files.exists(path))
			return;
		try (var walk = Files.walk(path)) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		} catch (IOException ex) {
			log.info("Could not delete file/folder {}", path, ex);
		}
	}
	
	/**
	 * Low n bits of nanoTime → 8 hex chars, e.g. "a3f9c012"
	 * @param n number of bits to use (must be multiple of 4, max 32)
	 */
	public static String getRandomString(int n) {
		if (n <= 0 || n > 32) {
			throw new IllegalArgumentException("n must be between 1 and 32");
		}
		
		int chars = n / 4; // each hex char = 4 bits
		if (n % 4 != 0) {
			chars++; // round up for non-multiple of 4
		}
		
		long nanoTime = System.nanoTime();
		long mask = (1L << n) - 1;
		long value = nanoTime & mask;
		
		return String.format("%0" + chars + "x", value);
	}
}