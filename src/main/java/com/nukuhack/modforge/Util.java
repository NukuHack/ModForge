package com.nukuhack.modforge;

import com.nukuhack.modforge.backend.model.E.Language;
import com.nukuhack.util.IOUtil;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Utility class providing common file operations, path handling, XML/JSON escaping,
 * PAK file packing, and cross-platform directory utilities for the ModForge application.
 */
@Slf4j
@UtilityClass
public final class Util {

    /**
     * Application name used for config directories and UI display
     */
    public final String APP_NAME = "ModForge";

    /**
     * Operating system name (lowercase) for cross-platform detection
     */
    public final String os = System.getProperty("os.name").toLowerCase();

    public final String XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    public final String STORM_HEADER = "<!DOCTYPE storm SYSTEM \"storm.dtd\">";

    /**
     * Username cross-platform
     */
    public final String username;
    /**
     * Main game tables PAK file containing game data tables
     */
    public final String TABLES = "Tables.pak";

    /**
     * Storm engine PAK file containing core engine resources
     */
    public final String STORM = "Storm.pak";
    /**
     * Standard compression/archive format extension for game files
     */
    public final String COMP_FORMAT = ".pak";
    /**
     * Standard data file format extension for XML configuration files
     */
    public final String DATA_FORMAT = ".xml";
    /**
     * Icons and UI graphics PAK file
     */
    public final String ICONS = "IPL_GameData.pak";
    /**
     * Directory name for localization/language files
     */
    public final String LOCALIZATION_DIR = "Localization";

    /**
     * Suffix added to language names for localization PAK files (e.g., "en_xml")
     */
    public final String LOCALIZATION_EXTRA = "_xml";
    /**
     * Main game data directory containing core game assets
     */
    public final String DATA_DIR = "Data";
    /**
     * Directory containing game libraries/dependencies
     */
    public final String LIBS_DIR = "Libs";
    /**
     * Directory containing game table definitions
     */
    public final String TABLES_DIR = "Tables";
    /**
     * Root directory for all mod installations
     */
    public final String MODS_DIR = "Mods";

    static {
        String user = System.getenv("USERNAME");

        if (user == null) {
            user = System.getenv("USER");

        }
        if (user == null) {
            user = System.getProperty("user.name");
        }
        username = user;
    }

    /**
     * Reads all text from an input stream using UTF-8 encoding.
     *
     * @param is Input stream to read from
     * @return String containing all text from the stream
     * @throws IOException If an I/O error occurs
     */
    public String readAllText(InputStream is) throws IOException {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Capitalizes the first character of a string and lowercases the rest.
     *
     * @param s Input string (maybe null or empty)
     * @return String with first letter capitalized, or original if null/empty
     */
    public String capitalStart(String s) {
        if (s == null || s.isEmpty())
            return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public String capitalStartOnly(String s) {
        if (s == null || s.isEmpty())
            return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the game data directory path.
     * Structure: {root}/{DATA_DIR}
     *
     * @param root Base game installation directory
     * @return Path to the Data directory
     */
    public Path dataDir(String root) {
        return joinP(root, DATA_DIR);
    }

    public Path dataDir(Path root) {
        return joinP(root, DATA_DIR);
    }

    /**
     * Returns the localization directory for a specific mod.
     * Structure: {root}/{MODS_DIR}/{modId}/{LOCALIZATION_DIR}
     *
     * @param root  Base game installation directory
     * @param modId Unique identifier for the mod
     * @return Path to the mod's localization directory
     */
    public Path modLocalDir(String root, String modId) {
        return joinP(modFolder(root, modId), LOCALIZATION_DIR);
    }

    /**
     * Returns the staging directory for mod file operations.
     * Structure: {modData}/{_stage}
     *
     * @param gameDir Game installation directory
     * @param modId   Unique identifier for the mod
     * @return Path to staging directory
     */
    public Path modStaging(String gameDir, String modId) {
        return Path.of(Util.modData(gameDir, modId), "_stage");
    }

    /**
     * Returns the Storm staging directory for mod data.
     * Structure: {modStaging}/{DATA_DIR}
     *
     * @param gameDir Game installation directory
     * @param modId   Unique identifier for the mod
     * @return Path to Storm staging directory
     */
    public Path modStormStaging(String gameDir, String modId) {
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
    public String locImport(String root, Language lang) {
        return join(root, LOCALIZATION_DIR, lang.getName() + LOCALIZATION_EXTRA);
    }

    /**
     * Returns the export path for a mod's localization XML file.
     * Structure: {modLocalDir}/{lang}{LOCALIZATION_EXTRA}/{text__{modId}.xml}
     *
     * @param root  Base game installation directory
     * @param lang  Target language
     * @param modId Unique identifier for the mod
     * @return Path to the localization XML export file
     */
    public Path locExport(String root, Language lang, String modId) {
        return joinP(modLocalDir(root, modId), lang.getName() + LOCALIZATION_EXTRA, modXmlForce("text", modId));
    }

    /**
     * Generates a mod-specific XML filename from a base filename.
     * Extracts the stem before "__" or removes .xml extension, then applies mod ID.
     *
     * @param fileName Original filename (may contain "__" delimiter or .xml extension)
     * @param modId    Unique identifier for the mod
     * @return Formatted filename: {stem}__{modId}.xml
     */
    public String modXmlFile(String fileName, String modId, boolean base) {
        final int delimit = fileName.indexOf("__");
        final String nameFinal;
        if (delimit != -1)
            nameFinal = fileName.substring(0, delimit);
        else if (fileName.endsWith(DATA_FORMAT))
            nameFinal = fileName.substring(0, fileName.length() - 4);
        else
            nameFinal = fileName;
        if (!base)
            return modXmlForce(nameFinal, modId);
        return nameFinal + DATA_FORMAT;
    }

    /**
     * Forces a filename into the mod XML naming convention.
     * Format: {fileName}__{modId}{DATA_FORMAT}
     *
     * @param fileName Base filename without extension
     * @param modId    Unique identifier for the mod
     * @return Formatted XML filename
     */
    private String modXmlForce(String fileName, String modId) {
        return fileName + "__" + modId + DATA_FORMAT;
    }

    /**
     * Returns the root folder path for a specific mod.
     * Structure: {root}/{MODS_DIR}/{modId}
     *
     * @param root  Base game installation directory
     * @param modId Unique identifier for the mod
     * @return Path string to mod's root folder
     */
    public String modFolder(String root, String modId) {
        return join(root, MODS_DIR, modId);
    }

    /**
     * Returns the mods folder path as a Path object.
     * Structure: {root}/{MODS_DIR}
     *
     * @param root Base game installation directory
     * @return Path to the Mods directory
     */
    public Path modFolder(String root) {
        return joinP(root, MODS_DIR);
    }

    /**
     * Returns the data directory for a specific mod.
     * Structure: {root}/{MODS_DIR}/{modId}/{DATA_DIR}
     *
     * @param root  Base game installation directory
     * @param modId Unique identifier for the mod
     * @return Path string to mod's data directory
     */
    public String modData(String root, String modId) {
        return join(root, MODS_DIR, modId, DATA_DIR);
    }

    /**
     * Returns all localization import paths for every supported language.
     * Uses locImport() for each Language enum value.
     *
     * @param root Base game installation directory
     * @return Set of PAK file paths for all languages
     */
    public Set<String> allLocPaths(String root) {
        return Arrays.stream(Language.values()).map(l -> locImport(root, l)).collect(Collectors.toSet());
    }

    /**
     * Returns the icons PAK file path.
     * Structure: {gameDataDir}/{ICONS}
     *
     * @param gameDir Game installation directory
     * @return Path to the icons PAK file
     */
    public Path icons(String gameDir) {
        return Util.joinP(dataDir(gameDir), ICONS);
    }

    /**
     * Join path segments using OS-based separators.
     *
     * @param parts Variable number of path segments
     * @return Combined path string with OS-appropriate separators
     * @example Windows: "C:\Users\name\file.txt"
     * @example Linux: "/home/name/file.txt"
     */
    public String join(String... parts) {
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
    public Path joinP(String... parts) {
        if (parts.length < 1)
            return Path.of("");
        else if (parts.length == 1)
            return Path.of(parts[0]);
        return Path.of(join(parts));
    }

    /**
     * Join a base Path with additional segments.
     *
     * @param base  Base Path object
     * @param parts Additional path segments to append
     * @return Combined Path object
     */
    public Path joinP(Path base, String... parts) {
        return Path.of(base.toString(), join(parts));
    }

    /**
     * Writes normalized and indented XML to a file.
     * Creates parent directories if they don't exist.
     *
     * @param inp     Raw XML content
     * @param outFile Destination file path
     * @throws IOException If file writing fails
     */
    public void writeXml(String inp, Path outFile) throws IOException {
        Files.createDirectories(outFile.getParent());
        inp = removeBlankLines(inp);
        if (!inp.startsWith(XML_HEADER))
            inp = XML_HEADER + "\n" + inp;

        try (var fileWriter = new FileWriter(outFile.toFile(), StandardCharsets.UTF_8)) {
            fileWriter.write(inp);
        }
    }

    public String removeBlankLines(String inp) {
        return inp.lines().filter(Predicate.not(String::isBlank)).collect(Collectors.joining("\n"));
    }

    /**
     * Converts uppercase string to snake case
     * example {@code "ClearSky"} to {@code "clear_sky"}
     */
    public static String convertCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < input.length(); i++) {
            char currentChar = input.charAt(i);

            if (Character.isUpperCase(currentChar)) {
                // Add underscore only if it's not the first character
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(currentChar));
            } else {
                result.append(currentChar);
            }
        }

        return result.toString();
    }


    /**
     * Escapes special characters for JSON string compatibility.
     * Handles quotes, backslashes, control characters, and Unicode.
     *
     * @param s Input string
     * @return JSON-escaped string, or empty string if input is null
     */
    public @NonNull String escapeJson(String s) {
        if (s == null)
            return "";
        var sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '/' -> sb.append("\\/");
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
    public String escHtml(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    /**
     * Escapes special characters for XML.
     *
     * @param s Input string
     * @return XML-escaped string, or empty string if input is null
     */
    public String escapeXml(String s) {
        if (s == null)
            return "";

        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    /**
     * Unescapes common XML entities back to their original characters.
     *
     * @param s XML-escaped string (maybe null)
     * @return Unescaped string, or empty string if input is null
     */
    public String unescapeXml(String s) {
        if (s == null)
            return "";
        return s.replace("&nbsp;", " ").replace("&apos;", "'").replace("&quot;", "\"").replace("&gt;", ">").replace("&lt;", "<").replace("&amp;", "&");
    }

    /**
     * Copies text to the system clipboard.
     *
     * @param text Text to copy to clipboard
     */
    public void copyText(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    /**
     * Extracts the filename stem from a ZIP entry path (no directory, no extension).
     *
     * @param entryName ZIP entry path (e.g., "folder/file.txt")
     * @return Filename without path or extension (e.g., "file")
     */
    public String stemOf(String entryName) {
        var slash = entryName.lastIndexOf('/');
        var filename = slash >= 0 ? entryName.substring(slash + 1) : entryName;
        var dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    public CompletableFuture<String> pickFileAsync() {
        return pickAsync("Pick a file", JFileChooser.FILES_ONLY, null, "yes");
    }

    public CompletableFuture<String> pickFileAsync(String title, String extension, String description) {
        return pickAsync(title, JFileChooser.FILES_ONLY, extension, description);
    }

    public CompletableFuture<String> pickFolderAsync() {
        return pickAsync("Select target folder", JFileChooser.DIRECTORIES_ONLY, null, "yes");
    }

    public CompletableFuture<String> pickFolderAsync(String title, String extension, String description) {
        return pickAsync(title, JFileChooser.DIRECTORIES_ONLY, extension, description);
    }

    /**
     * Opens a folder selection dialog asynchronously.
     *
     * @return CompletableFuture that completes with selected folder path, or null if canceled
     */
    public CompletableFuture<String> pickAsync(String title, int mode, String extension, String description) {
        var future = new CompletableFuture<String>();
        SwingUtilities.invokeLater(() -> {
            var chooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
            chooser.setFileSelectionMode(mode);
            chooser.setDialogTitle(title);
            if (extension != null) {
                chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(description, extension.substring(1)));
            }
            int result = chooser.showSaveDialog(null);
            var selectedPath = result == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile().getAbsolutePath() : null;

            future.complete(selectedPath);
        });
        return future;
    }

    /**
     * Opens a directory in the operating system's file explorer.
     * Cross-platform support for Windows, macOS, and Linux.
     *
     * @param w       Parent JPanel for error dialogs
     * @param dirPath Directory path to open
     */
    public void openDirectory(JPanel w, String dirPath) {
        try {
            IOUtil.openDirectory(dirPath);
            return;
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(w, "Game directory not correct. Please configure it in Settings.", "Directory Incorrect", JOptionPane.WARNING_MESSAGE);
        } catch (FileNotFoundException e) {
            JOptionPane.showMessageDialog(w, "Game directory does not exist or is invalid:\n" + dirPath, "Invalid Directory", JOptionPane.ERROR_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(w, "Failed to open game directory:\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            log.warn("IOException while choosing folder", Util.limitStackTrace(e, 10));
        }
        JOptionPane.showMessageDialog(w, "Cannot open directory on this operating system.", "Unsupported OS", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Returns the application configuration directory following platform conventions.
     * Uses APP_NAME constant for directory naming.
     *
     * @return Platform-specific config directory path
     * @example Windows: %APPDATA%\ModForge
     * @example macOS: ~/Library/Application Support/ModForge
     * @example Linux: ~/.config/modforge
     */
    public Path getConfigDir() {
        var home = System.getProperty("user.home");
        if (os.contains("win")) {

            String appData = System.getenv("APPDATA");
            if (appData == null || appData.isBlank()) {
                appData = home + "\\AppData\\Roaming";
            }
            return Paths.get(appData, APP_NAME);
        } else if (os.contains("mac")) {

            return Paths.get(home, "Library", "Application Support", APP_NAME);
        } else {

            var xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
            if (xdgConfigHome != null && !xdgConfigHome.isBlank()) {
                return Paths.get(xdgConfigHome, APP_NAME.toLowerCase());
            }

            return Paths.get(home, ".config", APP_NAME.toLowerCase());
        }
    }

    /**
     * Creates a new exception with limited stack trace depth.
     * Preserves the message, cause chain, and suppressed exceptions.
     */
    public static <T extends Throwable> T limitStackTrace(T exception, int maxLayers) {
        // Create a new Exception Object of the same type with the same message
        @SuppressWarnings("unchecked")
        T limited = (T) createNewInstance(exception, exception.getMessage());

        // Limit the main stack trace
        var fullTrace = exception.getStackTrace();
        var limitedTrace = new StackTraceElement[Math.min(maxLayers, fullTrace.length)];
        System.arraycopy(fullTrace, 0, limitedTrace, 0, limitedTrace.length);
        limited.setStackTrace(limitedTrace);

        // Preserve the cause chain
        if (exception.getCause() != null) {
            limited.initCause(limitStackTrace(exception.getCause(), maxLayers));
        }

        // Preserve suppressed exceptions
        for (var suppressed : exception.getSuppressed()) {
            limited.addSuppressed(limitStackTrace(suppressed, maxLayers));
        }

        return limited;
    }

    private static Throwable createNewInstance(Throwable original, String message) {
        try {
            return original.getClass()
                    .getConstructor(String.class)
                    .newInstance(message);
        } catch (Exception e) {
            // Fallback: wrap in RuntimeException if constructor not available
            var fallback = new RuntimeException(message);
            fallback.setStackTrace(original.getStackTrace());
            return fallback;
        }
    }

    /**
     * Returns a random integer between min (inclusive) and max (inclusive)
     */
    public static int getRandomNumber(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /**
     * Returns a random Color with random RGB values
     */
    public static Color getRandomColor() {
        int red = getRandomNumber(0, 255);
        int green = getRandomNumber(0, 255);
        int blue = getRandomNumber(0, 255);
        return new Color(red, green, blue);
    }/**
     * Returns a random Color from a random hex string
     */
    public static Color getRandomColorFromHex() {
        String hex = randomString(6); // 6 hex chars = 24 bits = RGB
        return Color.decode("#" + hex);
    }

    /**
     * Returns a random hex string of specified length using getRandomNumber()
     * @param chars number of hex characters to generate
     * @return random hex string
     */
    public String randomString(int chars) {
        if (chars <= 0 || chars > 999_999) {
            throw new IllegalArgumentException("chars must be between 1 and 999_999");
        }
        var sb = new StringBuilder(chars);
        for (int i = 0; i < chars; i++) {
            int hexDigit = getRandomNumber(0, 15); // 0-15 for hex digits
            sb.append(Integer.toHexString(hexDigit));
        }
        return sb.toString();
    }

    public UUID randomUUID() {
        return UUID.randomUUID();
    }
}