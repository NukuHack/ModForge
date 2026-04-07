package modforge;

import modforge.backend.service.ServiceRegistry;
import modforge.frontend.LoadingScreen;
import modforge.frontend.MainWindow;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CompletableFuture;

@lombok.extern.slf4j.Slf4j
public class Main {
	static void main(String[] args) {
		// Configure console logging
		applyTheme();
		
		System.out.println("ModForge Java - starting...");
		
		SwingUtilities.invokeLater(() -> {
			// Show loading splash while services boot
			final var splash = new LoadingScreen();
			splash.setVisible(true);
			
			// Boot services off the EDT
			CompletableFuture.runAsync(() -> Singleton.INSTANCE.setRegistry(new ServiceRegistry())).whenComplete((v, ex) -> SwingUtilities.invokeLater(() -> {
				splash.dispose();
				if (ex != null) {
					JOptionPane.showMessageDialog(null, "Startup error:\n" + ex.getMessage(), "ModForge – Error", JOptionPane.ERROR_MESSAGE);
					return;
				}
				//  should be made a bit nicer, for now most of the stuff is stored inside a String - should make data storage for more specific attributes
				final var registry = Singleton.INSTANCE.getRegistry();
				//System.out.println(AttributeFactory.getTypeMap());
				final MainWindow window = new MainWindow(registry);
				Singleton.INSTANCE.setMainWindow(window);
				window.setVisible(true);
			}));
		});
	}
	
	static void applyTheme() {
		setNicerFont();
		try {
			// Requires FlatLaf on classpath
			var flat = Class.forName("com.formdev.flatlaf.FlatDarkLaf");
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
	
	///  just to make korean chars actually display
	private static void setNicerFont() {
		for (var fontName : new String[] { "Noto Sans CJK KR", "Nanum Gothic", "SansSerif" }) {
			Font testFont = new Font(fontName, Font.PLAIN, 12);
			if (testFont.canDisplay('한')) { // Test with a Korean character
				// Set as default font
				UIManager.put("defaultFont", testFont);
				System.out.println("Using font: " + fontName);
				return;
			}
		}
		
		System.out.println("Warning: No good font found");
	}
}
