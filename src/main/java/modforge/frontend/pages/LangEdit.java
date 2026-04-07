package modforge.frontend.pages;

import modforge.Singleton;
import modforge.Util;
import modforge.backend.ModData;
import modforge.backend.model.Language;
import modforge.backend.model.ModItem;
import modforge.backend.service.LocalService;
import modforge.frontend.BarManager;
import modforge.frontend.MainWindow;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static modforge.Util.escHtml;

// =============================================================================
//  LANG EDIT PAGE
// =============================================================================
@lombok.extern.slf4j.Slf4j
public class LangEdit extends BaseEditPage {
	
	/**
	 * Working copy: langKey → current translated value (editable).
	 * Keys come from the item's localization-key attributes.
	 */
	private final Map<String, String> workingEntries = new LinkedHashMap<>();
	
	/**
	 * langKey → attribute name on the item (so we can show a nice label).
	 * e.g. "buff_realistic_shoe_durability_name" → "buff_ui_name"
	 */
	private final Map<String, String> keyToAttrName = new LinkedHashMap<>();
	
	/**
	 * langKey → the JTextArea the user edits.
	 */
	private final Map<String, JTextArea> keyToEditor = new LinkedHashMap<>();
	
	// ── Form panel ────────────────────────────────────────────────────────────
	private final JPanel fieldsPanel;
	
	// ── Top-bar widgets owned by this page ───────────────────────────────────
	private JComboBox<String> langSelector;
	
	// ── Constructor ──────────────────────────────────────────────────────────
	
	public LangEdit(MainWindow w) {
		super(w);
		// Initialize our panel BEFORE calling initUI(), because initUI() will
		// invoke buildFormPanel() which returns fieldsPanel.
		fieldsPanel = new JPanel(new GridBagLayout());
		fieldsPanel.setBackground(MainWindow.SURFACE);
		fieldsPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
		initUI();  // safe to call now — fieldsPanel is ready
	}
	
	// ── BaseItemEditPage overrides ────────────────────────────────────────────
	
	private static String emptyPreviewHtml() {
		return "<html><body style='background:#181825;color:#6c6f85;" + "font-family:sans-serif;padding:12px;'>" + "<i>No item selected or no lang keys found.</i></body></html>";
	}
	
	protected static JLabel muted(String text) {
		JLabel l = new JLabel(text);
		l.setForeground(MainWindow.MUTED);
		l.setFont(new Font("Roboto", Font.PLAIN, 12));
		return l;
	}
	
	@Override
	protected String getPageTitle() {
		return "Lang Edit";
	}
	
	@Override
	protected String getFormPanelTitle() {
		return "Localization Entries";
	}
	
	@Override
	protected String getPreviewTitle() {
		return "Live Preview";
	}
	
	@Override
	protected JPanel buildRightActions() {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		panel.setOpaque(false);
		
		// Language selector
		langSelector = new JComboBox<>();
		for (String name : Language.getAllDisplayNames())
			langSelector.addItem(name);
		final var defLang = window.getRegistry().userConfig.language;
		if (defLang != null)
			langSelector.setSelectedItem(defLang.getDisplayName());
		styleCombo(langSelector);
		langSelector.setPreferredSize(new Dimension(130, 32));
		langSelector.addActionListener(e -> rebuildFields());
		
		// Mod selector
		modSelector = new JComboBox<>();
		styleCombo(modSelector);
		modSelector.setPreferredSize(new Dimension(200, 32));
		
		panel.add(muted("Language:"));
		panel.add(langSelector);
		panel.add(Box.createHorizontalStrut(8));
		panel.add(muted("Target mod:"));
		panel.add(modSelector);
		panel.add(primaryBtn("+ Add to Mod", e -> addEntriesToSelectedMod()));
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
		buttons.add(primaryBtn("Save Changes", e -> saveChanges()));
		buttons.add(getDangerButton("← Back to Items", e -> navigateBack()));
		return buttons;
	}
	
	// ── Field building ────────────────────────────────────────────────────────
	
	@Override
	protected void onItemSet(ModItem item) {
		refreshLangSelector();
		rebuildFields();
	}
	
	// ── Save / persist ────────────────────────────────────────────────────────
	
	@Override
	protected void updatePreview() {
		if (previewPane == null)
			return;
		if (currentItem == null || workingEntries.isEmpty()) {
			previewPane.setText(emptyPreviewHtml());
			return;
		}
		
		// Sync working map from current editor state
		keyToEditor.forEach((key, ta) -> workingEntries.put(key, ta.getText()));
		
		final Language lang = selectedLanguage();
		final StringBuilder html = new StringBuilder();
		html.append("<html><body style='background:#181825;color:#cdd6f4;" + "font-family:sans-serif;padding:12px;'>");
		html.append("<b style='color:#89b4fa;font-size:13px;'>").append(escHtml(currentItem.getId())).append("</b><br/>");
		html.append("<span style='color:#6c6f85;font-size:10px;'>").append(currentItem.getClass().getSimpleName()).append("</span>");
		html.append("<hr style='border-color:#313244;margin:8px 0;'/>");
		
		if (lang != null) {
			html.append("<span style='color:#6c6f85;font-size:10px;'>Language: ").append(escHtml(lang.getDisplayName())).append("</span><br/><br/>");
		}
		
		for (var entry : workingEntries.entrySet()) {
			final String attrName = keyToAttrName.getOrDefault(entry.getKey(), "");
			html.append("<div style='margin-bottom:10px;'>");
			html.append("<span style='color:#6c6f85;font-size:10px;'>").append(escHtml(attrName)).append("</span><br/>");
			html.append("<span style='color:#89b4fa;font-size:11px;font-family:monospace;'>").append(escHtml(entry.getKey())).append("</span><br/>");
			html.append("<span style='color:#cdd6f4;'>").append(escHtml(entry.getValue())).append("</span>");
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
	
	// ── Helpers ───────────────────────────────────────────────────────────────
	
	/**
	 * Walk the current item's attributes to find localization-key attributes,
	 * resolve each key to its translation in the selected language,
	 * and build one labeled JTextArea per entry.
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
		
		final Language lang = selectedLanguage();
		final LocalService local = window.getRegistry().localService;
		final ModData baseGame = Singleton.INSTANCE.game();
		
		// Collect lang-key attributes from the item
		for (var attr : currentItem.getLangAttributes()) {
			final String langKey = attr.getValue().trim();
			if (langKey.isBlank())
				continue;
			
			String translated = local.resolve(langKey, baseGame, lang);
			if (translated == null)
				translated = "--translation-not-found--";
			
			workingEntries.put(langKey, translated);
			keyToAttrName.put(langKey, attr.getName());
		}
		
		// If nothing found, show a friendly message
		if (workingEntries.isEmpty()) {
			GridBagConstraints gc = new GridBagConstraints();
			gc.gridx = 0;
			gc.gridy = 0;
			gc.insets = new Insets(24, 0, 0, 0);
			fieldsPanel.add(muted("No localization keys found on this item."), gc);
			fieldsPanel.revalidate();
			fieldsPanel.repaint();
			updatePreview();
			return;
		}
		
		// Build one row per entry: [attr-name label] [lang-key label] [text area]
		int row = 0;
		for (var entry : workingEntries.entrySet()) {
			final String langKey = entry.getKey();
			final String attrName = keyToAttrName.get(langKey);
			
			// ── Attribute name (small, muted) ─────────────────────────────
			JLabel attrLabel = new JLabel(attrName + ":");
			attrLabel.setForeground(MainWindow.MUTED);
			attrLabel.setFont(new Font("Roboto", Font.PLAIN, 10));
			GridBagConstraints attrGc = new GridBagConstraints();
			attrGc.gridx = 0;
			attrGc.gridy = row;
			attrGc.anchor = GridBagConstraints.NORTHEAST;
			attrGc.insets = new Insets(2, 4, 0, 8);
			fieldsPanel.add(attrLabel, attrGc);
			row++;
			
			// ── Lang key (accent, copyable) ───────────────────────────────
			JLabel keyLabel = new JLabel(langKey);
			keyLabel.setForeground(MainWindow.ACCENT);
			keyLabel.setFont(new Font("Roboto Mono", Font.PLAIN, 11));
			keyLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			keyLabel.setToolTipText("Click to copy key");
			keyLabel.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					Util.copyText(langKey);
					window.snackbar.show("Key copied: " + langKey, BarManager.Type.INFO);
				}
			});
			GridBagConstraints keyGc = new GridBagConstraints();
			keyGc.gridx = 0;
			keyGc.gridy = row;
			keyGc.anchor = GridBagConstraints.NORTHEAST;
			keyGc.insets = new Insets(4, 4, 4, 8);
			fieldsPanel.add(keyLabel, keyGc);
			
			// ── Text area (editable translation) ──────────────────────────
			JTextArea ta = new JTextArea(entry.getValue());
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
			
			JScrollPane taSp = new JScrollPane(ta);
			taSp.setPreferredSize(new Dimension(0, 80));
			taSp.setBackground(MainWindow.SURFACE);
			taSp.setBorder(BorderFactory.createEmptyBorder());
			
			GridBagConstraints editorGc = new GridBagConstraints();
			editorGc.gridx = 1;
			editorGc.gridy = row;
			editorGc.weightx = 1.0;
			editorGc.fill = GridBagConstraints.HORIZONTAL;
			editorGc.anchor = GridBagConstraints.NORTHWEST;
			editorGc.insets = new Insets(4, 0, 4, 4);
			fieldsPanel.add(taSp, editorGc);
			keyToEditor.put(langKey, ta);
			row++;
			
			// ── Separator ─────────────────────────────────────────────────
			JSeparator sep = new JSeparator();
			sep.setForeground(new Color(0x313244));
			GridBagConstraints sepGc = new GridBagConstraints();
			sepGc.gridx = 0;
			sepGc.gridy = row;
			sepGc.gridwidth = 2;
			sepGc.fill = GridBagConstraints.HORIZONTAL;
			sepGc.insets = new Insets(2, 0, 2, 0);
			fieldsPanel.add(sep, sepGc);
			row++;
		}
		
		// Spacer so content anchors to top
		GridBagConstraints spacerGc = new GridBagConstraints();
		spacerGc.gridx = 0;
		spacerGc.gridy = row;
		spacerGc.weighty = 1.0;
		spacerGc.fill = GridBagConstraints.VERTICAL;
		fieldsPanel.add(Box.createVerticalGlue(), spacerGc);
		
		fieldsPanel.revalidate();
		fieldsPanel.repaint();
		updatePreview();
	}
	
	private void saveChanges() {
		if (currentItem == null || keyToEditor.isEmpty())
			return;
		keyToEditor.forEach((key, ta) -> workingEntries.put(key, ta.getText()));
		hasChanges = false;
		updateStatus();
		updatePreview();
		window.snackbar.show("Lang changes staged (use 'Add to Mod' to persist)", BarManager.Type.SUCCESS);
	}
	
	/**
	 * Copy the working entries into the selected mod's localization map
	 * for the selected language, then write the mod to disk.
	 */
	private void addEntriesToSelectedMod() {
		if (currentItem == null) {
			window.snackbar.show("No item loaded", BarManager.Type.WARNING);
			return;
		}
		final var targetMod = getSelectedMod();
		if (targetMod.isEmpty()) {
			window.snackbar.show("Please select a mod first", BarManager.Type.WARNING);
			return;
		}
		var mod = targetMod.get();
		final Language lang = selectedLanguage();
		if (lang == null) {
			window.snackbar.show("Unknown language", BarManager.Type.INFO);
			return;
		}
		
		// Flush editors first
		keyToEditor.forEach((key, ta) -> workingEntries.put(key, ta.getText()));
		
		if (workingEntries.isEmpty()) {
			window.snackbar.show("No entries to add", BarManager.Type.INFO);
			return;
		}
		mod.addLocal(lang, new HashMap<>(workingEntries));
		hasChanges = false;
		updateStatus();
		window.snackbar.show("Added " + workingEntries.size() + " entries → " + mod.name + " [" + lang.getDisplayName() + "]", BarManager.Type.SUCCESS);
	}
	
	private void refreshLangSelector() {
		final var defLang = window.getRegistry().userConfig.language;
		if (defLang != null)
			langSelector.setSelectedItem(defLang.getDisplayName());
	}
	
	private Language selectedLanguage() {
		Object sel = langSelector.getSelectedItem();
		return sel == null ? null : Language.fromDisplayName(sel.toString());
	}
}