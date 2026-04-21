package com.nukuhack.modforge.frontend.pages;

import com.nukuhack.modforge.Singleton;
import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.backend.model.ModItem;
import com.nukuhack.modforge.frontend.BarManager;
import com.nukuhack.modforge.frontend.MainWindow;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.nukuhack.modforge.frontend.MainWindow.getLocalText;

/**
 * Localization edit page.
 * <p>
 * Layout per entry (vertical, generous space):
 *   ┌──────────────────────────────────────┐
 *   │ ATTRIBUTE NAME (muted label)         │
 *   │ lang.key.monospace  [copy]           │
 *   │ ┌────────────────────────────────┐   │
 *   │ │  editable text area            │   │
 *   │ └────────────────────────────────┘   │
 *   │ ──────────────────────────────────── │
 *   └──────────────────────────────────────┘
 */
@Slf4j
@ExtensionMethod({ Util.class })
public class LangEdit extends BaseEditPage {
	
	/** Working copy: langKey → current translated value (editable). */
	private final Map<String, String> workingEntries = new LinkedHashMap<>();
	
	/** langKey → attribute name on the item (for labeling). */
	private final Map<String, String> keyToAttrName = new LinkedHashMap<>();
	
	/** langKey → the JTextArea the user edits. */
	private final Map<String, JTextArea> keyToEditor = new LinkedHashMap<>();
	
	private final JPanel fieldsPanel;
	
	public LangEdit(MainWindow w) {
		super(w);
		fieldsPanel = new JPanel();
		fieldsPanel.setLayout(new BoxLayout(fieldsPanel, BoxLayout.Y_AXIS));
		fieldsPanel.setBackground(MainWindow.SURFACE);
		fieldsPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
		initUI();
	}
	
	private static String emptyPreviewHtml() {
		return "<html><body style='background:#181825;color:#6c6f85;" + "font-family:sans-serif;padding:12px;'>" + "<i>" + getLocalText("ui_lang_edit_empty_preview") + "</i></body></html>";
	}
	
	@Override
	protected String getPageTitle() {
		return "ui_localization_edit";
	}
	
	@Override
	protected String getFormPanelTitle() {
		return getLocalText("ui_localization_entries");
	}
	
	@Override
	protected String getPreviewTitle() {
		return getLocalText("ui_live_preview");
	}
	
	@Override
	protected JPanel buildRightActions() {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		panel.setOpaque(false);
		
		refreshLangSelector();
		styleCombo(langSelector);
		langSelector.setPreferredSize(new Dimension(130, 28));
		langSelector.addActionListener(e -> rebuildFields());
		
		styleCombo(modSelector);
		modSelector.setPreferredSize(new Dimension(200, 28));
		modSelector.setToolTipText(getLocalText("ui_mod_source_tip"));
		
		panel.add(muted("ui_lang"));
		panel.add(langSelector);
		panel.add(Box.createHorizontalStrut(8));
		panel.add(muted("ui_mod"));
		panel.add(modSelector);
		panel.add(primaryBtn("ui_add_to_mod", e -> addEntriesToSelectedMod()));
		return panel;
	}
	
	@Override
	protected JPanel buildFormPanel() {
		return fieldsPanel;
	}
	
	@Override
	protected JPanel buildActionButtons() {
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttons.setOpaque(false);
		buttons.add(primaryBtn("ui_save_changes", e -> saveChanges()));
		buttons.add(getDangerButton("ui_back", e -> navigateBack()));
		return buttons;
	}
	
	@Override
	protected void onItemSet(ModItem item) {
		refreshLangSelector();
		rebuildFields();
		
		if (previewPane.getClientProperty("langEditListenerInstalled") == null) {
			previewPane.addMouseListener(mouseClicked(currentItem));
			previewPane.putClientProperty("langEditListenerInstalled", Boolean.TRUE);
		}
		previewPane.setComponentPopupMenu(buildItemPopupMenu(() -> currentItem, true, false));
	}
	
	@Override
	protected void updatePreview() {
		if (currentItem == null || workingEntries.isEmpty()) {
			previewPane.setText(emptyPreviewHtml());
			return;
		}
		
		keyToEditor.forEach((key, ta) -> workingEntries.put(key, ta.getText()));
		
		var lang = getSelectedLang().orElseGet(Singleton.getRegistry().userConfig::getLanguage);
		
		var html = new StringBuilder();
		html.append("<html><body style='background:#181825;color:#cdd6f4;" + "font-family:sans-serif;padding:12px;margin:0;'>");
		
		// ── Item header (reuse shared renderer) ──────────────────────────────
		
		html.append("<b style='color:#89b4fa;font-size:13px;'>").append(currentItem.getId().escHtml()).append("</b><br/>");
		
		html.append("<hr style='border-color:#313244;margin:10px 0;'/>");
		html.append("<span style='color:#6c6f85;font-size:9px;text-transform:uppercase;" + "letter-spacing:0.5px;'>").append(getLocalText("ui_language")).append(": ").append(lang.getDisplayName().escHtml()).append("</span><br/><br/>");
		
		for (var entry : workingEntries.entrySet()) {
			final String attrName = keyToAttrName.getOrDefault(entry.getKey(), "");
			html.append("<div style='margin-bottom:10px;background:#1e1e2e;" + "border-left:3px solid #cba6f7;padding:8px 10px;border-radius:3px;'>");
			if (! attrName.isBlank())
				html.append("<span style='color:#cba6f7;font-size:9px;" + "text-transform:uppercase;letter-spacing:0.5px;'>").append(attrName.escHtml()).append("</span><br/>");
			html.append("<span style='color:#6c6f85;font-size:10px;font-family:monospace;'>").append(entry.getKey().escHtml()).append("</span><br/>");
			html.append("<span style='color:#cdd6f4;font-size:12px;'>").append(entry.getValue().escHtml()).append("</span>");
			html.append("</div>");
		}
		html.append("</body></html>");
		previewPane.setText(html.toString());
		previewPane.setCaretPosition(0);
	}
	
	@Override
	protected void navigateBack() {
		if (confirmDiscard())
			window.navigate(MainWindow.Page.ITEMS);
	}
	
	/**
	 * Walk the current item's lang attributes, resolve each key, and build
	 * one card per entry with a VERTICAL layout:
	 *   attribute-name label
	 *   lang key (monospace, clickable copy)
	 *   text area for the translation
	 */
	private void rebuildFields() {
		fieldsPanel.removeAll();
		keyToEditor.clear();
		workingEntries.clear();
		keyToAttrName.clear();
		
		if (currentItem == null) {
			fieldsPanel.revalidate();
			fieldsPanel.repaint();
			return;
		}
		
		var lang = getSelectedLang().orElseGet(Singleton.getRegistry().userConfig::getLanguage);
		var local = window.getRegistry().localService;
		var baseGame = Singleton.getGame();
		
		for (var attr : currentItem.getLangAttributes()) {
			final String langKey = attr.getValue().trim();
			if (langKey.isBlank())
				continue;
			
			String translated = local.resolve(langKey, baseGame, lang);
			if (translated == null)
				translated = getLocalText("ui_translation_not_found");
			
			workingEntries.put(langKey, translated);
			keyToAttrName.put(langKey, attr.getName());
		}
		
		if (workingEntries.isEmpty()) {
			var empty = muted("ui_local_not_found");
			empty.setBorder(BorderFactory.createEmptyBorder(24, 0, 0, 0));
			empty.setAlignmentX(Component.LEFT_ALIGNMENT);
			fieldsPanel.add(empty);
			fieldsPanel.revalidate();
			fieldsPanel.repaint();
			updatePreview();
			return;
		}
		
		for (var entry : workingEntries.entrySet()) {
			final String langKey = entry.getKey();
			final String attrName = keyToAttrName.get(langKey);
			fieldsPanel.add(buildEntryCard(langKey, attrName, entry.getValue()));
			fieldsPanel.add(Box.createVerticalStrut(4));
		}
		
		fieldsPanel.add(Box.createVerticalGlue());
		
		fieldsPanel.revalidate();
		fieldsPanel.repaint();
		updatePreview();
	}
	
	/**
	 * Builds a single card for one localization entry.
	 *
	 * <pre>
	 *  ┌─ card ──────────────────────────────────────┐
	 *  │  ATTRIBUTE_NAME                             │  ← muted, 9px
	 *  │  lang.key.here                    [C Copy]  │  ← monospace accent, clickable
	 *  │  ┌─────────────────────────────────────┐    │
	 *  │  │  textarea (editable translation)    │    │
	 *  │  └─────────────────────────────────────┘    │
	 *  └─────────────────────────────────────────────┘
	 * </pre>
	 */
	private JPanel buildEntryCard(String langKey, String attrName, String value) {
		
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(new Color(0x1e1e2e));
		card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x313244), 1), BorderFactory.createEmptyBorder(10, 12, 10, 12)));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		
		if (attrName != null && ! attrName.isBlank()) {
			JLabel attrLabel = new JLabel(attrName);
			attrLabel.setForeground(new Color(0xcba6f7));
			attrLabel.setFont(new Font("Roboto", Font.BOLD, 9));
			attrLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			card.add(attrLabel);
			card.add(Box.createVerticalStrut(4));
		}
		
		JPanel keyRow = new JPanel(new BorderLayout(6, 0));
		keyRow.setOpaque(false);
		keyRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		keyRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		
		JLabel keyLabel = new JLabel(langKey);
		keyLabel.setForeground(MainWindow.ACCENT);
		keyLabel.setFont(new Font("Roboto Mono", Font.PLAIN, 11));
		keyLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		keyLabel.setToolTipText(getLocalText("ui_click_to_copy_key"));
		keyLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				Util.copyText(langKey);
				window.snackbar.show("ui_copied_key", BarManager.Type.INFO, langKey);
			}
		});
		
		JButton copyBtn = makeCopyButton(langKey);
		keyRow.add(keyLabel, BorderLayout.CENTER);
		keyRow.add(copyBtn, BorderLayout.EAST);
		card.add(keyRow);
		card.add(Box.createVerticalStrut(6));
		
		JTextArea ta = new JTextArea(value);
		ta.setRows(3);
		ta.setBackground(new Color(0x313244));
		ta.setForeground(MainWindow.TEXT);
		ta.setCaretColor(MainWindow.TEXT);
		ta.setFont(new Font("Roboto", Font.PLAIN, 12));
		ta.setLineWrap(true);
		ta.setWrapStyleWord(true);
		ta.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x45475a)), BorderFactory.createEmptyBorder(6, 8, 6, 8)));
		ta.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e) {
				markChanged();
				updatePreview();
			}
			
			public void removeUpdate(DocumentEvent e) {
				markChanged();
				updatePreview();
			}
			
			public void changedUpdate(DocumentEvent e) {
				markChanged();
				updatePreview();
			}
		});
		keyToEditor.put(langKey, ta);
		
		JScrollPane taSp = new JScrollPane(ta);
		taSp.setAlignmentX(Component.LEFT_ALIGNMENT);
		taSp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
		taSp.setPreferredSize(new Dimension(0, 80));
		taSp.setBackground(MainWindow.SURFACE);
		taSp.setBorder(BorderFactory.createEmptyBorder());
		card.add(taSp);
		
		return card;
	}
	
	private JButton makeCopyButton(String langKey) {
		JButton b = new JButton("⎘");
		b.setPreferredSize(new Dimension(28, 22));
		b.setBackground(new Color(0x45475a));
		b.setForeground(MainWindow.TEXT);
		b.setFocusPainted(false);
		b.setBorderPainted(false);
		b.setFont(new Font("Roboto", Font.PLAIN, 11));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.setToolTipText(getLocalText("ui_copy_key"));
		b.addActionListener(e -> {
			Util.copyText(langKey);
			window.snackbar.show("ui_copied_key", BarManager.Type.INFO, langKey);
		});
		return b;
	}
	
	/**
	 * Flush all in-editor translations to the selected mod's lang map and mark clean.
	 *
	 * <p>The item's {@link com.nukuhack.modforge.backend.model.Attribute.StringAttribute}
	 * values hold <em>lang keys</em> (e.g. {@code "ui.sword.name"}), not the translated
	 * text — so there is nothing to write back onto the item itself.  The editable
	 * content (the resolved translations) lives in {@code workingEntries} and must be
	 * committed to the mod's localization map via {@code mod.addLocal(...)}.
	 */
	private void saveChanges() {
		if (currentItem == null) {
			window.snackbar.show("ui_no_item_selected", BarManager.Type.WARNING);
			return;
		}
		final var targetMod = getSelectedMod();
		if (targetMod.isEmpty()) {
			window.snackbar.show("ui_select_mod_first", BarManager.Type.WARNING);
			return;
		}
		final var lang = getSelectedLang();
		if (lang.isEmpty()) {
			window.snackbar.show("ui_unknown_language", BarManager.Type.INFO);
			return;
		}
		
		// Flush editor widgets → workingEntries
		keyToEditor.forEach((key, ta) -> workingEntries.put(key, ta.getText()));
		
		if (workingEntries.isEmpty()) {
			window.snackbar.show("ui_no_entries_to_add", BarManager.Type.INFO);
			return;
		}
		
		targetMod.get().addLocal(lang.get(), new HashMap<>(workingEntries));
		hasChanges = false;
		updatePreview();
		updateStatus();
		window.snackbar.show("ui_item_changes_saved", BarManager.Type.SUCCESS);
	}
	
	private void addEntriesToSelectedMod() {
		if (currentItem == null) {
			window.snackbar.show("ui_no_item_selected", BarManager.Type.WARNING);
			return;
		}
		final var targetMod = getSelectedMod();
		if (targetMod.isEmpty()) {
			window.snackbar.show("ui_select_mod_first", BarManager.Type.WARNING);
			return;
		}
		var mod = targetMod.get();
		var lang = getSelectedLang();
		if (lang.isEmpty()) {
			window.snackbar.show("ui_unknown_language", BarManager.Type.INFO);
			return;
		}
		keyToEditor.forEach((key, ta) -> workingEntries.put(key, ta.getText()));
		if (workingEntries.isEmpty()) {
			window.snackbar.show("ui_no_entries_to_add", BarManager.Type.INFO);
			return;
		}
		mod.addLocal(lang.get(), new HashMap<>(workingEntries));
		hasChanges = false;
		updateStatus();
		window.snackbar.show("ui_entries_added", BarManager.Type.SUCCESS, workingEntries.size());
	}
}