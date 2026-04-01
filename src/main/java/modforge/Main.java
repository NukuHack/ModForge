package modforge;

import frontend.*;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.*;


public class Main {
	private static final Logger LOGGER = Logger.getLogger(Main.class.getName());



	static void main(String[] args) {
		// Configure console logging
		applyTheme();

		Logger root = Logger.getLogger("");
		root.setLevel(Level.INFO);
		for (var h : root.getHandlers()) h.setLevel(Level.INFO);

		System.out.println("ModForge Java - starting...");



		SwingUtilities.invokeLater(() -> {
			// Show loading splash while services boot
			LoadingScreen splash = new LoadingScreen();
			splash.setVisible(true);

			// Boot services off the EDT
			CompletableFuture.runAsync(() -> {

				ServiceRegistry registry = new ServiceRegistry();

				if (args.length > 0) {
					String dir = args[0];
					System.out.println("Using game directory: " + dir);
					registry.setGameDirectory(dir);
				} else {
					final String dir = System.getProperty("user.home");
					System.out.println("No game directory specified, using: " + dir );
					System.out.println("SpecifiedUsage:  java -cp . modforge.Main <path-to-KCD2-installation>");
					registry.setGameDirectory(dir);
				}

				try {
					Thread.sleep(1200);
				} catch (InterruptedException ignored) {
				}

			}).whenComplete((v, ex) -> SwingUtilities.invokeLater(() -> {
				splash.dispose();
				if (ex != null) {
					JOptionPane.showMessageDialog(null,
							"Startup error:\n" + ex.getMessage(),
							"ModForge – Error", JOptionPane.ERROR_MESSAGE);
					return;
				}
				MainWindow window = new MainWindow(/* registry */ null);
				window.setVisible(true);
			}));
		});
	}

	static void applyTheme() {
		try {
			// Requires FlatLaf on classpath
			Class<?> flat = Class.forName("com.formdev.flatlaf.FlatDarkLaf");
			flat.getMethod("setup").invoke(null);
		} catch (Exception e) {
			// Fallback: force metal dark-ish defaults
			UIManager.put("Panel.background", new Color(0x1e1e2e));
			UIManager.put("control", new Color(0x1e1e2e));
			UIManager.put("text", new Color(0xc1c1c4));
			UIManager.put("Button.background", new Color(0x313244));
			UIManager.put("Button.foreground", new Color(0xcdd6f4));
			UIManager.put("TextField.background", new Color(0x313244));
			UIManager.put("TextField.foreground", new Color(0xcdd6f4));
			UIManager.put("ScrollPane.background", new Color(0x1e1e2e));
			UIManager.put("List.background", new Color(0x181825));
			UIManager.put("List.foreground", new Color(0xcdd6f4));
			UIManager.put("List.selectionBackground", new Color(0x89b4fa));
			UIManager.put("List.selectionForeground", new Color(0x1e1e2e));
		}
	}
}
