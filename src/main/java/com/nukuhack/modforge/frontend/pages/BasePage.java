package com.nukuhack.modforge.frontend.pages;

import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.backend.ModData;
import com.nukuhack.modforge.backend.model.ModItem;
import com.nukuhack.modforge.backend.model.I.Storm;
import com.nukuhack.modforge.backend.service.ModItemBuilder;
import com.nukuhack.modforge.backend.service.ModService;
import com.nukuhack.modforge.frontend.BarManager;
import com.nukuhack.modforge.frontend.MainWindow;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Optional;
import java.util.function.Supplier;

import static com.nukuhack.modforge.Util.copyText;
import static com.nukuhack.modforge.Util.escHtml;
import static com.nukuhack.modforge.frontend.MainWindow.getLocalText;

@Slf4j
public abstract class BasePage extends JPanel {
	protected final MainWindow window;
	
	protected final JComboBox<String> modSelector = new JComboBox<>(new DefaultComboBoxModel<>());
	protected final JTextField search = styledField("ui_search_all");
	
	BasePage(MainWindow window) {
		this.window = window;
		setBackground(MainWindow.BG);
		setLayout(new BorderLayout());
	}
	
	/**
	 * Returns the selected ModData, or empty if "Base Game" (index 0) or nothing is selected.
	 * Index 0 is always the "Base Game" sentinel — callers that want the base game
	 * should use Singleton.INSTANCE.getGame() as a fallback.
	 */
	protected Optional<ModData> getSelectedMod() {
		var sel = (String) modSelector.getSelectedItem();
		if (sel == null || modSelector.getSelectedIndex() < 1)
			return Optional.empty();
		var modName = sel.trim();
		var mod = ModService.modCollection.stream().filter(m -> m.name.equals(modName)).findFirst();
		log.debug("selected: {}", mod);
		return mod;
	}
	
	/**
	 * Rebuilds the mod selector without firing ActionListeners during the rebuild.
	 * Restores the previously selected mod if it still exists.
	 */
	protected void refreshModSelector() {
		// Capture previous selection before clearing
		var previous = getSelectedMod();
		var model = (DefaultComboBoxModel<String>) modSelector.getModel();
		
		// Temporarily remove all ActionListeners to prevent spurious events
		// while we clear and repopulate the model
		ActionListener[] listeners = modSelector.getActionListeners();
		for (ActionListener l : listeners)
			modSelector.removeActionListener(l);
		
		model.removeAllElements();
		
		var mods = ModService.modCollection;
		if (mods.isEmpty()) {
			model.addElement(getLocalText("ui_mods_not_Found"));
		} else {
			model.addElement("    " + getLocalText("ui_base_game"));
			for (var mod : mods)
				model.addElement("    " + mod.name);
			
			previous.ifPresent(m -> modSelector.setSelectedItem("    " + m.name));
		}
		
		// Re-attach listeners after model is fully built
		for (ActionListener l : listeners)
			modSelector.addActionListener(l);
	}
	
	protected JPopupMenu buildItemPopupMenu(
			Supplier<ModItem> itemSupplier,
			boolean showEditItem,
			boolean showEditLang,
			boolean showAddToMod) {
		
		var menu = new JPopupMenu();
		
		JMenuItem copyId = new JMenuItem(getLocalText("ui_copy_id"));
		copyId.addActionListener(e -> {
			var item = itemSupplier.get();
			if (item != null) {
				copyText(item.getId());
				window.snackbar.show(getLocalText("ui_copied_id"), BarManager.Type.INFO, item.getId());
			}
		});
		menu.add(copyId);
		
		JMenuItem copyAll = new JMenuItem(getLocalText("ui_copy_all_details"));
		copyAll.addActionListener(e -> {
			var item = itemSupplier.get();
			if (item != null) {
				copyText(item.details());
				window.snackbar.show(getLocalText("ui_copied_all_details"), BarManager.Type.INFO);
			}
		});
		menu.add(copyAll);
		
		if (showEditItem || showEditLang || showAddToMod)
			menu.addSeparator();
		
		if (showEditItem) {
			JMenuItem editItem = new JMenuItem(getLocalText("ui_edit_item"));
			editItem.addActionListener(e -> {
				var item = itemSupplier.get();
				if (item == null) return;
				if (item instanceof Storm stormItem)
					window.navigate(MainWindow.Page.STORM, stormItem);
				else
					window.navigate(MainWindow.Page.ITEM_EDIT, item);
			});
			menu.add(editItem);
		}
		
		if (showEditLang) {
			JMenuItem editLang = new JMenuItem(getLocalText("ui_edit_lang"));
			editLang.addActionListener(e -> {
				var item = itemSupplier.get();
				if (item != null)
					window.navigate(MainWindow.Page.LANG_EDIT, item);
			});
			menu.add(editLang);
		}
		
		if (showAddToMod) {
			JMenuItem addToMod = new JMenuItem(getLocalText("ui_add_to_mod"));
			addToMod.addActionListener(e -> {
				var item = itemSupplier.get();
				if (item != null)
					showAddToModDialog(item);
			});
			menu.add(addToMod);
		}
		
		return menu;
	}
	
	protected MouseAdapter mouseClicked(ModItem item) {
		return new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && item != null) {
					Util.copyText(item.getId());
					window.snackbar.show(getLocalText("ui_copied_id"), BarManager.Type.INFO, item.getId());
				}
			}
		};
	}
	
	protected void showAddToModDialog(ModItem item) {
		var dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
				getLocalText("ui_add_to_mod_title"), true);
		dialog.setSize(400, 180);
		dialog.setLocationRelativeTo(this);
		dialog.setLayout(new BorderLayout());
		
		var mainPanel = new JPanel(new BorderLayout(8, 12));
		mainPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
		mainPanel.setBackground(MainWindow.BG);
		
		var titleLabel = new JLabel(getLocalText("ui_add_to_mod_prompt"));
		titleLabel.setForeground(MainWindow.TEXT);
		titleLabel.setFont(new Font("Roboto", Font.BOLD, 14));
		mainPanel.add(titleLabel, BorderLayout.NORTH);
		
		var modCombo = new JComboBox<String>(new DefaultComboBoxModel<>());
		styleCombo(modCombo);
		var mods = ModService.modCollection;
		for (var mod : mods)
			modCombo.addItem(mod.name);
		mainPanel.add(modCombo, BorderLayout.CENTER);
		
		var buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttonPanel.setOpaque(false);
		
		var addBtn = new JButton(getLocalText("ui_add"));
		addBtn.setBackground(MainWindow.ACCENT);
		addBtn.setForeground(new Color(0x1e1e2e));
		addBtn.setFocusPainted(false);
		addBtn.setBorderPainted(false);
		addBtn.setFont(new Font("Roboto", Font.BOLD, 13));
		addBtn.addActionListener(e -> {
			var sel = (String) modCombo.getSelectedItem();
			if (sel == null) {
				window.snackbar.show(getLocalText("ui_select_mod_first"), BarManager.Type.WARNING);
				return;
			}
			var mod = ModService.modCollection.stream()
							  .filter(m -> m.name.equals(sel)).findFirst();
			mod.ifPresentOrElse(m -> {
				m.addItem(ModItemBuilder.deepCopy(item, m));
				window.snackbar.show(getLocalText("ui_item_added_to_mod"), BarManager.Type.SUCCESS, m.name);
				dialog.dispose();
			}, () -> window.snackbar.show(getLocalText("ui_select_mod_first"), BarManager.Type.WARNING));
		});
		
		var cancelBtn = new JButton(getLocalText("ui_cancel"));
		cancelBtn.setFocusPainted(false);
		cancelBtn.setBorderPainted(false);
		cancelBtn.setBackground(MainWindow.SURFACE);
		cancelBtn.setForeground(MainWindow.TEXT);
		cancelBtn.setFont(new Font("Roboto", Font.PLAIN, 13));
		cancelBtn.addActionListener(e -> dialog.dispose());
		
		buttonPanel.add(addBtn);
		buttonPanel.add(cancelBtn);
		mainPanel.add(buttonPanel, BorderLayout.SOUTH);
		
		dialog.add(mainPanel);
		dialog.setVisible(true);
	}
	
	protected static JPanel card(String title) {
		var card = new JPanel() {
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
			JLabel h = new JLabel(getLocalText(title));
			h.setForeground(MainWindow.ACCENT);
			h.setFont(new Font("Roboto", Font.BOLD, 16));
			card.add(h, BorderLayout.NORTH);
		}
		return card;
	}
	
	protected static JLabel header(String text) {
		var l = new JLabel(getLocalText(text));
		l.setForeground(MainWindow.TEXT);
		l.setFont(new Font("Roboto", Font.BOLD, 22));
		l.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));
		return l;
	}
	
	protected static JLabel muted(String text) {
		var l = new JLabel(getLocalText(text));
		l.setForeground(MainWindow.MUTED);
		l.setFont(new Font("Roboto", Font.PLAIN, 13));
		return l;
	}
	
	protected static JButton primaryBtn(String text, ActionListener action) {
		var b = new JButton(getLocalText(text));
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
		var f = new JTextField();
		f.setBackground(new Color(0x313244));
		f.setForeground(MainWindow.TEXT);
		f.setCaretColor(MainWindow.TEXT);
		f.setBorder(BorderFactory.createCompoundBorder(
				new LineBorder(new Color(0x45475a), 1),
				BorderFactory.createEmptyBorder(6, 10, 6, 10)));
		f.setFont(new Font("Roboto", Font.PLAIN, 13));
		return getJTextField(getLocalText(placeholder), f);
	}
	
	protected static void styleCombo(JComboBox<?> cb) {
		cb.setFont(new Font("Roboto", Font.PLAIN, 12));
		cb.setBackground(MainWindow.SURFACE);
		cb.setForeground(MainWindow.TEXT);
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
		var b = new JButton(getLocalText(text));
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
			return "<html><body style='background:#181825;color:#6c6f85;"
						   + "font-family:sans-serif;padding:12px;'>"
						   + "<i>" + getLocalText("ui_no_item") + "</i></body></html>";
		}
		
		var html = new StringBuilder();
		html.append("<html><body style='background:#181825;color:#cdd6f4;font-family:sans-serif;padding:12px;'>");
		
		html.append("<b style='color:#89b4fa;font-size:13px;'>").append(escHtml(item.getId())).append("</b>");
		html.append("<br/>");
		html.append("<span style='color:#6c6f85;font-size:10px;'>").append(item.getClass().getSimpleName()).append("</span>");
		html.append("<hr style='border-color:#313244;margin:8px 0;'/>");
		
		html.append("<div style='margin-bottom:10px;'>");
		html.append("<span style='color:#6c6f85;font-size:10px;'>").append(getLocalText("ui_path")).append("</span><br/>");
		html.append("<span style='color:#89b4fa;font-size:11px;font-family:monospace;'>").append(escHtml(item.getPath())).append("</span>");
		html.append("</div>");
		
		if (!item.getAttributes().isEmpty()) {
			html.append("<hr style='border-color:#313244;margin:8px 0;'/>");
			html.append("<span style='color:#6c6f85;font-size:10px;'>").append(getLocalText("ui_attributes")).append("</span><br/><br/>");
			for (var attr : item.getAttributes()) {
				html.append("<div style='margin-bottom:8px;'>");
				html.append("<span style='color:#6c6f85;font-size:10px;'>").append(escHtml(attr.getName())).append("</span><br/>");
				html.append("<span style='color:#cdd6f4;'>").append(escHtml(attr.serialize())).append("</span>");
				html.append("</div>");
			}
		}
		
		if (!item.getLinkedItems().isEmpty()) {
			html.append("<hr style='border-color:#313244;margin:8px 0;'/>");
			html.append("<span style='color:#6c6f85;font-size:10px;'>").append(getLocalText("ui_linked_items")).append("</span><br/><br/>");
			for (var linkedId : item.getLinkedItems()) {
				html.append("<div style='margin-bottom:6px;'>");
				html.append("<span style='color:#89b4fa;font-size:11px;font-family:monospace;'>").append(escHtml(linkedId.toString())).append("</span>");
				html.append("</div>");
			}
		}
		
		html.append("</body></html>");
		return html.toString();
	}
	
	public abstract void refresh(Object... input);
}