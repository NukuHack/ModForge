package modforge.frontend;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

// =============================================================================
//  LOADING SCREEN
// =============================================================================
public class LoadingScreen extends JWindow {
	public LoadingScreen() {
		setSize(500, 340);
		setLocationRelativeTo(null);
		setBackground(new Color(0, 0, 0, 0));
		
		JPanel panel = getJPanel();
		
		JLabel logo = new JLabel("⚒  ModForge", SwingConstants.CENTER);
		logo.setFont(new Font("Roboto", Font.BOLD, 36));
		logo.setForeground(new Color(0x89b4fa));
		
		JLabel sub = new JLabel("Loading game data …", SwingConstants.CENTER);
		sub.setFont(new Font("Roboto", Font.PLAIN, 14));
		sub.setForeground(new Color(0x6c6f85));
		
		JProgressBar bar = new JProgressBar();
		bar.setIndeterminate(true);
		bar.setForeground(new Color(0x89b4fa));
		bar.setBackground(new Color(0x313244));
		bar.setBorderPainted(false);
		
		panel.add(logo, BorderLayout.CENTER);
		panel.add(sub, BorderLayout.SOUTH);
		
		JPanel outer = new JPanel(new BorderLayout(0, 16));
		outer.setOpaque(false);
		outer.add(panel, BorderLayout.CENTER);
		outer.add(bar, BorderLayout.SOUTH);
		
		setContentPane(outer);
		setShape(new RoundRectangle2D.Float(0, 0, 500, 340, 24, 24));
	}
	
	private static JPanel getJPanel() {
		JPanel panel = new JPanel(new BorderLayout()) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(new Color(0x1e1e2e));
				g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 24, 24));
				g2.dispose();
			}
		};
		panel.setOpaque(false);
		panel.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));
		return panel;
	}
}
