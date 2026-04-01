package frontend;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.concurrent.CompletableFuture;

// =============================================================================
//  CLIPBOARD HELPER
// =============================================================================
public class Util {
	static void install() { /* no-op; copyText is a static call */ }

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
			future.complete(result == JFileChooser.APPROVE_OPTION
					? chooser.getSelectedFile().getAbsolutePath() : null);
		});
		return future;
	}
}
