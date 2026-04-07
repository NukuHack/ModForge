package modforge.frontend.pages;

import modforge.backend.model.ModItem;
import modforge.frontend.MainWindow;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.logging.Logger;

import static modforge.Util.escHtml;


@lombok.extern.slf4j.Slf4j
public abstract class BasePage extends JPanel {
	protected final MainWindow window;
	
	BasePage(MainWindow window) {
		this.window = window;
		setBackground(MainWindow.BG);
		setLayout(new BorderLayout());
	}
	
	/**
	 * Rounded card panel.
	 */
	protected static JPanel card(String title) {
		JPanel card = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(new Color(0x181825));
				g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
				g2.dispose();
			}
		};
		card.setOpaque(false);
		card.setLayout(new BorderLayout(0, 12));
		card.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
		
		if (title != null) {
			JLabel h = new JLabel(title);
			h.setForeground(MainWindow.ACCENT);
			h.setFont(new Font("Roboto", Font.BOLD, 16));
			card.add(h, BorderLayout.NORTH);
		}
		return card;
	}
	
	protected static JLabel header(String text) {
		JLabel l = new JLabel(text);
		l.setForeground(MainWindow.TEXT);
		l.setFont(new Font("Roboto", Font.BOLD, 22));
		l.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));
		return l;
	}
	
	protected static JLabel muted(String text) {
		JLabel l = new JLabel(text);
		l.setForeground(MainWindow.MUTED);
		l.setFont(new Font("Roboto", Font.PLAIN, 13));
		return l;
	}
	
	protected static JButton primaryBtn(String text, ActionListener action) {
		JButton b = new JButton(text);
		b.setBackground(MainWindow.ACCENT);
		b.setForeground(new Color(0x1e1e2e));
		b.setFocusPainted(false);
		b.setBorderPainted(false);
		b.setFont(new Font("Roboto", Font.BOLD, 13));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.addActionListener(action);
		return b;
	}
	
	protected static JTextField styledField(String placeholder) {
		JTextField f = new JTextField();
		f.setBackground(new Color(0x313244));
		f.setForeground(MainWindow.TEXT);
		f.setCaretColor(MainWindow.TEXT);
		f.setBorder(BorderFactory.createCompoundBorder(new LineBorder(new Color(0x45475a), 1), BorderFactory.createEmptyBorder(6, 10, 6, 10)));
		f.setFont(new Font("Roboto", Font.PLAIN, 13));
		// Placeholder via focus listener
		return getJTextField(placeholder, f);
	}
	
	static JTextField getJTextField(String placeholder, JTextField f) {
		f.setText(placeholder);
		f.setForeground(MainWindow.MUTED);
		f.addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent e) {
				if (f.getText().equals(placeholder)) {
					f.setText("");
					f.setForeground(MainWindow.TEXT);
				}
			}
			
			@Override
			public void focusLost(FocusEvent e) {
				if (f.getText().isBlank()) {
					f.setText(placeholder);
					f.setForeground(MainWindow.MUTED);
				}
			}
		});
		return f;
	}
	
	static JButton getDangerButton(String text, ActionListener action) {
		JButton b = new JButton(text);
		b.setBackground(MainWindow.DANGER);
		b.setForeground(new Color(0x1e1e2e));
		b.setFocusPainted(false);
		b.setBorderPainted(false);
		b.setFont(new Font("Roboto", Font.BOLD, 13));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.addActionListener(action);
		return b;
	}
	
	protected static String htmlForItem(ModItem item) {
		if (item == null) {
			return ("<html><body style='background:#181825;color:#6c6f85;" + "font-family:sans-serif;padding:12px;'>" + "<i>No item selected.</i></body></html>");
		}
		
		final StringBuilder html = new StringBuilder();
		html.append("<html><body style='background:#181825;color:#cdd6f4;font-family:sans-serif;padding:12px;'>");
		
		// ── Header: ID + class ────────────────────────────────────────────
		html.append("<b style='color:#89b4fa;font-size:13px;'>").append(escHtml(item.getId())).append("</b>");
		html.append("<br/>");
		html.append("<span style='color:#6c6f85;font-size:10px;'>").append(item.getClass().getSimpleName()).append("</span>");
		html.append("<hr style='border-color:#313244;margin:8px 0;'/>");
		
		// ── Path ─────────────────────────────────────────────────────────
		html.append("<div style='margin-bottom:10px;'>");
		html.append("<span style='color:#6c6f85;font-size:10px;'>path</span><br/>");
		html.append("<span style='color:#89b4fa;font-size:11px;font-family:monospace;'>").append(escHtml(item.getPath())).append("</span>");
		html.append("</div>");
		
		// ── Attributes ────────────────────────────────────────────────────
		if (! item.getAttributes().isEmpty()) {
			html.append("<hr style='border-color:#313244;margin:8px 0;'/>");
			html.append("<span style='color:#6c6f85;font-size:10px;'>ATTRIBUTES</span><br/><br/>");
			for (var attr : item.getAttributes()) {
				html.append("<div style='margin-bottom:8px;'>");
				html.append("<span style='color:#6c6f85;font-size:10px;'>").append(escHtml(attr.getName())).append("</span><br/>");
				html.append("<span style='color:#cdd6f4;'>").append(escHtml(String.valueOf(attr.getValue()))).append("</span>");
				html.append("</div>");
			}
		}
		
		// ── Linked IDs ────────────────────────────────────────────────────
		if (! item.getLinkedIds().isEmpty()) {
			html.append("<hr style='border-color:#313244;margin:8px 0;'/>");
			html.append("<span style='color:#6c6f85;font-size:10px;'>LINKED ITEMS</span><br/><br/>");
			for (String linkedId : item.getLinkedIds()) {
				html.append("<div style='margin-bottom:6px;'>");
				html.append("<span style='color:#89b4fa;font-size:11px;font-family:monospace;'>").append(escHtml(linkedId)).append("</span>");
				html.append("</div>");
			}
		}
		
		html.append("</body></html>");
		return (html.toString());
	}
	
	protected JButton secondaryBtn(String text, ActionListener action) {
		JButton b = new JButton(text);
		b.setBackground(MainWindow.ACCENT);
		b.setForeground(new Color(0x1e1e2e));
		b.setFocusPainted(false);
		b.setBorderPainted(false);
		b.setFont(new Font("Roboto", Font.BOLD, 13));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.addActionListener(action);
		return b;
	}
	
	public abstract void refresh(Object... input);
	
}
