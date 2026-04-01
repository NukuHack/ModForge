package modforge.frontend;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class Util {

	public static void copyText(String text) {
		Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new StringSelection(text), null);
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
			String selectedPath = result == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile().getAbsolutePath() : null;

			future.complete(selectedPath);
		});
		return future;
	}
}