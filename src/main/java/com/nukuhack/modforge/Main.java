package com.nukuhack.modforge;

import com.nukuhack.modforge.backend.service.LocalService;
import com.nukuhack.modforge.backend.service.ServiceRegistry;
import com.nukuhack.modforge.frontend.LoadingScreen;
import com.nukuhack.modforge.frontend.MainWindow;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

/**
 * <p> ███╗   ███╗ ██████╗ ██████╗ ███████╗ ██████╗ ██████╗  ██████╗ ███████╗</p>
 * <p> ████╗ ████║██╔═══██╗██╔══██╗██╔════╝██╔═══██╗██╔══██╗██╔════╝ ██╔════╝</p>
 * <p> ██╔████╔██║██║   ██║██║  ██║█████╗  ██║   ██║██████╔╝██║  ███╗█████╗  </p>
 * <p> ██║╚██╔╝██║██║   ██║██║  ██║██╔══╝  ██║   ██║██╔══██╗██║   ██║██╔══╝  </p>
 * <p> ██║ ╚═╝ ██║╚██████╔╝██████╔╝██║     ╚██████╔╝██║  ██║╚██████╔╝███████╗</p>
 * <p> ╚═╝     ╚═╝ ╚═════╝ ╚═════╝ ╚═╝      ╚═════╝ ╚═╝  ╚═╝ ╚═════╝ ╚══════╝</p>
 * main class - start here
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class Main {
	private static final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	
	public static void main(String[] args) {
		// looks better in green but the logger logs it in red :(
		log.info("""
				
				███╗   ███╗ ██████╗ ██████╗ ███████╗ ██████╗ ██████╗  ██████╗ ███████╗
				████╗ ████║██╔═══██╗██╔══██╗██╔════╝██╔═══██╗██╔══██╗██╔════╝ ██╔════╝
				██╔████╔██║██║   ██║██║  ██║█████╗  ██║   ██║██████╔╝██║  ███╗█████╗
				██║╚██╔╝██║██║   ██║██║  ██║██╔══╝  ██║   ██║██╔══██╗██║   ██║██╔══╝
				██║ ╚═╝ ██║╚██████╔╝██████╔╝██║     ╚██████╔╝██║  ██║╚██████╔╝███████╗
				╚═╝     ╚═╝ ╚═════╝ ╚═════╝ ╚═╝      ╚═════╝ ╚═╝  ╚═╝ ╚═════╝ ╚══════╝
				
				🚀 ModForge Backend | {} | Profile: {}
				""", LocalDateTime.now().format(fmt), Util.username);
		
		applyTheme();
		LocalService.loadUILocalizations();
		SwingUtilities.invokeLater(() -> {
			// Show loading splash while services boot
			var splash = new LoadingScreen();
			splash.setVisible(true);
			
			// Boot services off the EDT
			CompletableFuture.runAsync(() -> Singleton.setRegistry(new ServiceRegistry())).whenComplete((v, ex) -> SwingUtilities.invokeLater(() -> {
				splash.dispose();
				if (ex != null) {
					JOptionPane.showMessageDialog(null, "Startup error:\n" + ex.getMessage(), "ModForge – Error", JOptionPane.ERROR_MESSAGE);
					return;
				}
				var registry = Singleton.getRegistry();
				var window = new MainWindow(registry);
				window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				Singleton.setMainWindow(window);
				window.setVisible(true);
			}));
		});
	}
	
	public static void applyTheme() {
		setNicerFont();
		try {
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (final Exception e) {
				log.warn("Failed to set system look and feel", e);
			}
			// Requires FlatLaf on classpath
			var flat = Class.forName("com.formdev.flatlaf.FlatDarkLaf");
			flat.getMethod("setup").invoke(null);
		} catch (final Exception e) {
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
	
	///  just to make Korean chars actually display
	private static void setNicerFont() {
		for (var fontName : new String[] { "Noto Sans CJK KR", "Nanum Gothic", "SansSerif" }) {
			Font testFont = new Font(fontName, Font.PLAIN, 12);
			if (testFont.canDisplay('한')) { // Test with a Korean character
				// Set as default font
				UIManager.put("defaultFont", testFont);
				log.info("Using font: {}", fontName);
				return;
			}
		}
		
		log.warn("Warning: No good font found");
	}
}