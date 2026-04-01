package modforge;

import modforge.backend.service.ServiceRegistry;
import modforge.frontend.LoadingScreen;
import modforge.frontend.MainWindow;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.*;


public class Main {
	private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

	static void main(String[] args) {
		// Configure console logging
		applyTheme();

		LOGGER.setLevel(Level.INFO);
		for (var h : LOGGER.getHandlers()) h.setLevel(Level.INFO);

		System.out.println("ModForge Java - starting...");

		SwingUtilities.invokeLater(() -> {
			// Show loading splash while services boot
			final LoadingScreen splash = new LoadingScreen();
			splash.setVisible(true);

			// Boot services off the EDT
			CompletableFuture.runAsync(() -> {
				final ServiceRegistry registry = new ServiceRegistry();

				final String dir = args.length > 0 ? args[0] : System.getProperty("user.home");
				if (args.length == 0) {
					System.out.println("No game directory specified, using config or : " + dir);
					System.out.println("SpecifiedUsage:  java -cp . modforge.Main <path-to-KCD2-installation>");
				} else {
					System.out.println("Using game directory: " + dir);
				}
				registry.init(dir);
				Singleton.INSTANCE.setRegistry(registry);

			}).whenComplete((v, ex) -> SwingUtilities.invokeLater(() -> {
				splash.dispose();
				if (ex != null) {
					JOptionPane.showMessageDialog(null,
							"Startup error:\n" + ex.getMessage(),
							"ModForge – Error", JOptionPane.ERROR_MESSAGE);
					return;
				}
				var registry = Singleton.INSTANCE.getRegistry();
				MainWindow window = new MainWindow(registry);
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
