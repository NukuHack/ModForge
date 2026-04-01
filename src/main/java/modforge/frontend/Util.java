package modforge.frontend;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class Util {
	// History implementation
	private static ArrayList<String> history = null;
	private static int currentIndex = -1;
	private static final int MAX_HISTORY_STEPS = 50;
	private static Consumer<String> navigationCallback = null;

	public static void copyText(String text) {
		Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new StringSelection(text), null);
	}

	// Initialize history if not already defined
	private static void initHistoryIfNeeded() {
		if (history == null) {
			history = new ArrayList<>();
		}
	}

	/**
	 * Set a callback to be notified when navigation occurs
	 * @param callback Called with the new path when navigating
	 */
	public static void setNavigationCallback(Consumer<String> callback) {
		navigationCallback = callback;
	}

	/**
	 * Add a new path to history
	 * @param path The path to add
	 */
	public static void addToHistory(String path) {
		if (path == null) return;

		initHistoryIfNeeded();

		// If we're not at the end of history, remove all forward entries
		if (currentIndex < history.size() - 1) {
			history.subList(currentIndex + 1, history.size()).clear();
		}

		// Add the new path
		history.add(path);
		currentIndex = history.size() - 1;

		// Trim history if exceeds max steps
		while (history.size() > MAX_HISTORY_STEPS) {
			history.remove(0);
			currentIndex--;
		}
	}

	/**
	 * Navigate backwards in history (button 6)
	 * @return The previous path, or null if no previous exists
	 */
	public static String goBack() {
		if (history == null || currentIndex <= 0) {
			return null;
		}
		currentIndex--;
		String path = history.get(currentIndex);
		if (navigationCallback != null) {
			navigationCallback.accept(path);
		}
		return path;
	}

	/**
	 * Navigate forwards in history (button 5)
	 * @return The next path, or null if no next exists
	 */
	public static String goForward() {
		if (history == null || currentIndex >= history.size() - 1) {
			return null;
		}
		currentIndex++;
		String path = history.get(currentIndex);
		if (navigationCallback != null) {
			navigationCallback.accept(path);
		}
		return path;
	}

	/**
	 * Check if backward navigation is available
	 * @return true if can go back
	 */
	public static boolean canGoBack() {
		return history != null && currentIndex > 0;
	}

	/**
	 * Check if forward navigation is available
	 * @return true if can go forward
	 */
	public static boolean canGoForward() {
		return history != null && currentIndex < history.size() - 1;
	}

	/**
	 * Get current position in history
	 * @return current index, or -1 if no history
	 */
	public static int getCurrentHistoryIndex() {
		return currentIndex;
	}

	/**
	 * Get total history size
	 * @return number of items in history
	 */
	public static int getHistorySize() {
		return history == null ? 0 : history.size();
	}

	/**
	 * Clear all history
	 */
	public static void clearHistory() {
		if (history != null) {
			history.clear();
		}
		currentIndex = -1;
	}

	/**
	 * Set the current path (for initializing from existing value)
	 * @param path The current path
	 */
	public static void setCurrentPath(String path) {
		if (path != null) {
			addToHistory(path);
		}
	}

	/**
	 * @return selected path, or null if cancelled
	 */
	public static CompletableFuture<String> pickFolderAsync() {
		CompletableFuture<String> future = new CompletableFuture<>();
		SwingUtilities.invokeLater(() -> {
			JFileChooser chooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			chooser.setDialogTitle("Select target folder");
			chooser.setAcceptAllFileFilterUsed(false);
			int result = chooser.showOpenDialog(null);
			String selectedPath = result == JFileChooser.APPROVE_OPTION
					? chooser.getSelectedFile().getAbsolutePath() : null;

			// Add selected path to history if it's not null
			if (selectedPath != null) {
				addToHistory(selectedPath);
			}

			future.complete(selectedPath);
		});
		return future;
	}
}