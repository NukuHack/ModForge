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
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

// =============================================================================
//  LANG EDIT PAGE
//  Mirrors the ItemEdit pattern but operates on localization key→value pairs.
//  The item's lang-key attributes (buff_ui_name, buff_ui_desc, UIName,
//  UIInfo, ui_lore_desc, …) are resolved to their current translated strings,
//  displayed in an editable form, and can be saved back to any loaded mod.
// =============================================================================
public class LangEdit extends BasePage {
	
	// ── Attribute names that hold localization keys (checked case-insensitively)
	private static final List<String> LANG_ATTR_HINTS = List.of(
			"ui_name", "uiname", "name",
			"ui_desc", "uidesc", "uiinfo",
			"ui_lore_desc", "uiloredesc"
	);
	
	// ── State ─────────────────────────────────────────────────────────────────
	private ModItem currentItem;
	private boolean hasChanges = false;
	
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
	 * langKey → the JTextField the user edits.
	 */
	private final Map<String, JTextArea> keyToEditor = new LinkedHashMap<>();
	
	// ── UI components ─────────────────────────────────────────────────────────
	private JComboBox<String> modSelector;
	private JComboBox<String> langSelector;
	private JPanel fieldsPanel;
	private JEditorPane previewPane;
	private JLabel statusLabel;
	private JLabel breadcrumbItem;
	
	// ── Constructor ───────────────────────────────────────────────────────────
	
	public LangEdit(MainWindow w) {
		super(w);
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
		setLayout(new BorderLayout(0, 16));
		
		fieldsPanel = new JPanel(new GridBagLayout());
		fieldsPanel.setBackground(MainWindow.SURFACE);
		fieldsPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
		
		buildUI();
	}
	
	// ── BasePage contract ─────────────────────────────────────────────────────
	
	@Override
	public void refresh(Object... input) {
		if (input.length > 0 && input[0] instanceof ModItem item) {
			setItem(item);
		} else {
			window.navigate(MainWindow.Page.ITEMS);
		}
	}
	
	// ── Public API ────────────────────────────────────────────────────────────
	
	public void setItem(ModItem item) {
		this.currentItem = item;
		this.hasChanges = false;
		
		refreshModSelector();
		refreshLangSelector();
		rebuildFields();
		updatePreview();
		updateStatus();
	}
	
	// ── UI construction ───────────────────────────────────────────────────────
	
	private void buildUI() {
		add(buildTopBar(), BorderLayout.NORTH);
		add(buildCenter(), BorderLayout.CENTER);
		add(buildBottomBar(), BorderLayout.SOUTH);
	}
	
	/** Breadcrumb  |  language selector  |  mod selector  |  Add-to-Mod button */
	private JPanel buildTopBar() {
		JPanel top = new JPanel(new BorderLayout(12, 0));
		top.setOpaque(false);
		
		// Left: breadcrumb
		JPanel breadcrumbPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		breadcrumbPanel.setOpaque(false);
		
		JLabel breadcrumbBase = new JLabel("Items  ›  ");
		breadcrumbBase.setForeground(MainWindow.ACCENT);
		breadcrumbBase.setFont(new Font("Roboto", Font.BOLD, 22));
		breadcrumbBase.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		breadcrumbBase.setToolTipText("Back to Items");
		breadcrumbBase.addMouseListener(new MouseAdapter() {
			@Override public void mouseClicked(MouseEvent e) { navigateBack(); }
		});
		
		JLabel separator = new JLabel("Lang Edit");
		separator.setForeground(MainWindow.TEXT);
		separator.setFont(new Font("Roboto", Font.BOLD, 22));
		
		breadcrumbItem = new JLabel("—");
		breadcrumbItem.setForeground(MainWindow.MUTED);
		breadcrumbItem.setFont(new Font("Roboto", Font.PLAIN, 18));
		
		breadcrumbPanel.add(breadcrumbBase);
		breadcrumbPanel.add(separator);
		breadcrumbPanel.add(breadcrumbItem);
		
		// Right: language + mod selectors
		JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		rightActions.setOpaque(false);
		
		// Language selector
		langSelector = new JComboBox<>();
		for (String name : Language.getAllDisplayNames()) {
			langSelector.addItem(name);
		}
		// Default to configured language
		String cfgLang = window.getRegistry().userConfig.language;
		Language defLang = Language.fromIsoCode(cfgLang);
		if (defLang != null) langSelector.setSelectedItem(defLang.getDisplayName());
		styleCombo(langSelector);
		langSelector.setPreferredSize(new Dimension(130, 32));
		langSelector.addActionListener(e -> rebuildFields());
		
		// Mod selector
		modSelector = new JComboBox<>();
		styleCombo(modSelector);
		modSelector.setPreferredSize(new Dimension(200, 32));
		
		JButton addToModBtn = primaryBtn("+ Add to Mod", e -> addEntriesToSelectedMod());
		
		rightActions.add(labelMuted("Language:"));
		rightActions.add(langSelector);
		rightActions.add(Box.createHorizontalStrut(8));
		rightActions.add(labelMuted("Target mod:"));
		rightActions.add(modSelector);
		rightActions.add(addToModBtn);
		
		top.add(breadcrumbPanel, BorderLayout.WEST);
		top.add(rightActions, BorderLayout.EAST);
		return top;
	}
	
	/** Left: scrollable key→value fields  |  Right: live preview */
	private JSplitPane buildCenter() {
		JScrollPane fieldScroll = new JScrollPane(fieldsPanel);
		fieldScroll.setBackground(MainWindow.SURFACE);
		fieldScroll.getViewport().setBackground(MainWindow.SURFACE);
		fieldScroll.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(new Color(0x313244)),
				"Localization Entries",
				TitledBorder.LEFT, TitledBorder.TOP,
				new Font("Roboto", Font.BOLD, 12), MainWindow.ACCENT));
		fieldScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		
		previewPane = new JEditorPane();
		previewPane.setContentType("text/html");
		previewPane.setEditable(false);
		previewPane.setBackground(new Color(0x181825));
		previewPane.setForeground(MainWindow.TEXT);
		previewPane.setFont(new Font("Roboto", Font.PLAIN, 12));
		previewPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
		
		JScrollPane previewScroll = new JScrollPane(previewPane);
		previewScroll.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(new Color(0x313244)),
				"Live Preview",
				TitledBorder.LEFT, TitledBorder.TOP,
				new Font("Roboto", Font.BOLD, 12), MainWindow.ACCENT));
		previewScroll.setBackground(MainWindow.SURFACE);
		
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, fieldScroll, previewScroll);
		split.setResizeWeight(0.6);
		split.setDividerSize(4);
		split.setBackground(MainWindow.BG);
		split.setBorder(BorderFactory.createEmptyBorder());
		return split;
	}
	
	/** Status label  +  Save / Back buttons */
	private JPanel buildBottomBar() {
		JPanel bar = new JPanel(new BorderLayout());
		bar.setOpaque(false);
		bar.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		
		statusLabel = new JLabel(" ");
		statusLabel.setFont(new Font("Roboto", Font.ITALIC, 11));
		statusLabel.setForeground(new Color(0xa6e3a1));
		
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttons.setOpaque(false);
		buttons.add(primaryBtn("Save Changes", e -> saveChanges()));
		buttons.add(getDangerButton("← Back to Items", e -> navigateBack()));
		
		bar.add(statusLabel, BorderLayout.WEST);
		bar.add(buttons, BorderLayout.EAST);
		return bar;
	}
	
	// ── Field building ────────────────────────────────────────────────────────
	
	/**
	 * Walk the current item's attributes to find localization-key attributes,
	 * then resolve each key to its translation in the selected language.
	 * Build one labelled JTextArea per entry.
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
		for (var attr : currentItem.getAttributes()) {
			final String attrName = attr.getName().toLowerCase(Locale.ROOT);
			boolean isLangAttr = LANG_ATTR_HINTS.stream().anyMatch(attrName::contains);
			if (!isLangAttr) continue;
			
			final String langKey = String.valueOf(attr.getValue()).trim();
			if (langKey.isBlank()) continue;
			
			// Resolve current translation (mod-then-base fallback)
			String translated = local.resolve(langKey, baseGame, lang);
			if (translated == null) translated = "";
			
			workingEntries.put(langKey, translated);
			keyToAttrName.put(langKey, attr.getName());
		}
		
		// If nothing found, show a friendly message
		if (workingEntries.isEmpty()) {
			GridBagConstraints gc = new GridBagConstraints();
			gc.gridx = 0; gc.gridy = 0; gc.insets = new Insets(24, 0, 0, 0);
			fieldsPanel.add(labelMuted("No localization keys found on this item."), gc);
			fieldsPanel.revalidate();
			fieldsPanel.repaint();
			updatePreview();
			return;
		}
		
		// Build one row per entry:  [attr-name label] [lang-key label] [text area]
		int row = 0;
		for (var entry : workingEntries.entrySet()) {
			final String langKey = entry.getKey();
			final String attrName = keyToAttrName.get(langKey);
			
			// ── Attribute name (small muted) ──────────────────────────────
			JLabel attrLabel = new JLabel(attrName + ":");
			attrLabel.setForeground(MainWindow.MUTED);
			attrLabel.setFont(new Font("Roboto", Font.PLAIN, 10));
			
			GridBagConstraints attrGc = new GridBagConstraints();
			attrGc.gridx = 0; attrGc.gridy = row;
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
				@Override public void mouseClicked(MouseEvent e) {
					Util.copyText(langKey);
					window.snackbar.show("Key copied: " + langKey, BarManager.Type.INFO);
				}
			});
			
			GridBagConstraints keyGc = new GridBagConstraints();
			keyGc.gridx = 0; keyGc.gridy = row;
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
			ta.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(new Color(0x45475a)),
					BorderFactory.createEmptyBorder(6, 8, 6, 8)));
			ta.getDocument().addDocumentListener(new DocumentListener() {
				public void insertUpdate(DocumentEvent e) { markChanged(); updatePreview(); }
				public void removeUpdate(DocumentEvent e) { markChanged(); updatePreview(); }
				public void changedUpdate(DocumentEvent e) { markChanged(); updatePreview(); }
			});
			
			JScrollPane taSp = new JScrollPane(ta);
			taSp.setPreferredSize(new Dimension(0, 80));
			taSp.setBackground(MainWindow.SURFACE);
			taSp.setBorder(BorderFactory.createEmptyBorder());
			
			GridBagConstraints editorGc = new GridBagConstraints();
			editorGc.gridx = 1; editorGc.gridy = row;
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
			sepGc.gridx = 0; sepGc.gridy = row;
			sepGc.gridwidth = 2;
			sepGc.fill = GridBagConstraints.HORIZONTAL;
			sepGc.insets = new Insets(2, 0, 2, 0);
			fieldsPanel.add(sep, sepGc);
			row++;
		}
		
		// Spacer at the bottom so everything anchors to top
		GridBagConstraints spacerGc = new GridBagConstraints();
		spacerGc.gridx = 0; spacerGc.gridy = row;
		spacerGc.weighty = 1.0;
		spacerGc.fill = GridBagConstraints.VERTICAL;
		fieldsPanel.add(Box.createVerticalGlue(), spacerGc);
		
		fieldsPanel.revalidate();
		fieldsPanel.repaint();
		updatePreview();
	}
	
	// ── Save / Preview ────────────────────────────────────────────────────────
	
	private void saveChanges() {
		if (currentItem == null || keyToEditor.isEmpty()) return;
		
		// Flush editor content into workingEntries
		for (var e : keyToEditor.entrySet()) {
			workingEntries.put(e.getKey(), e.getValue().getText());
		}
		
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
		if (currentItem == null) return;
		
		final ModData targetMod = selectedMod();
		if (targetMod == null) {
			window.snackbar.show("No mod selected", BarManager.Type.INFO);
			return;
		}
		
		final Language lang = selectedLanguage();
		if (lang == null) {
			window.snackbar.show("Unknown language", BarManager.Type.INFO);
			return;
		}
		
		// Flush editors first
		for (var e : keyToEditor.entrySet()) {
			workingEntries.put(e.getKey(), e.getValue().getText());
		}
		
		if (workingEntries.isEmpty()) {
			window.snackbar.show("No entries to add", BarManager.Type.INFO);
			return;
		}
		
		// Merge into mod's localization map (LocalService stores per-Language)
		// ModData.getLocal() is unmodifiable, so we build a mutable copy and set it back
		final Map<Language, Map<String, String>> existing = new EnumMap<>(targetMod.getLocal());
		existing.computeIfAbsent(lang, k -> new LinkedHashMap<>()).putAll(workingEntries);
		targetMod.setLocal(existing);
		
		hasChanges = false;
		updateStatus();
		window.snackbar.show(
				"Added " + workingEntries.size() + " entries → " + targetMod.name + " [" + lang.getDisplayName() + "]",
				BarManager.Type.SUCCESS);
	}
	
	// ── Preview ───────────────────────────────────────────────────────────────
	
	private void updatePreview() {
		if (currentItem == null || workingEntries.isEmpty()) {
			previewPane.setText(emptyPreviewHtml());
			return;
		}
		
		// Sync working map from editors
		for (var e : keyToEditor.entrySet()) {
			workingEntries.put(e.getKey(), e.getValue().getText());
		}
		
		final Language lang = selectedLanguage();
		final StringBuilder html = new StringBuilder();
		html.append("<html><body style='background:#181825;color:#cdd6f4;font-family:sans-serif;padding:12px;'>");
		html.append("<b style='color:#89b4fa;font-size:13px;'>")
				.append(escHtml(currentItem.getId()))
				.append("</b><br/>");
		html.append("<span style='color:#6c6f85;font-size:10px;'>")
				.append(currentItem.getClass().getSimpleName())
				.append("</span>");
		html.append("<hr style='border-color:#313244;margin:8px 0;'/>");
		
		if (lang != null) {
			html.append("<span style='color:#6c6f85;font-size:10px;'>Language: ")
					.append(escHtml(lang.getDisplayName()))
					.append("</span><br/><br/>");
		}
		
		for (var entry : workingEntries.entrySet()) {
			final String attrName = keyToAttrName.getOrDefault(entry.getKey(), "");
			html.append("<div style='margin-bottom:10px;'>");
			html.append("<span style='color:#6c6f85;font-size:10px;'>")
					.append(escHtml(attrName)).append("</span><br/>");
			html.append("<span style='color:#89b4fa;font-size:11px;font-family:monospace;'>")
					.append(escHtml(entry.getKey())).append("</span><br/>");
			html.append("<span style='color:#cdd6f4;'>")
					.append(escHtml(entry.getValue())).append("</span>");
			html.append("</div>");
		}
		
		html.append("</body></html>");
		previewPane.setText(html.toString());
		previewPane.setCaretPosition(0);
	}
	
	private String emptyPreviewHtml() {
		return "<html><body style='background:#181825;color:#6c6f85;font-family:sans-serif;padding:12px;'>" +
					   "<i>No item selected or no lang keys found.</i></body></html>";
	}
	
	// ── Selectors ─────────────────────────────────────────────────────────────
	
	private void refreshModSelector() {
		modSelector.removeAllItems();
		for (ModData mod : window.getRegistry().modService.modCollection) {
			modSelector.addItem(mod.id + " | " + mod.name);
		}
	}
	
	private void refreshLangSelector() {
		// Re-select configured language
		String cfgLang = window.getRegistry().userConfig.language;
		Language defLang = Language.fromIsoCode(cfgLang);
		if (defLang != null) langSelector.setSelectedItem(defLang.getDisplayName());
	}
	
	private Language selectedLanguage() {
		Object sel = langSelector.getSelectedItem();
		if (sel == null) return null;
		return Language.fromDisplayName(sel.toString());
	}
	
	private ModData selectedMod() {
		Object sel = modSelector.getSelectedItem();
		if (sel == null) return null;
		String modId = sel.toString().split(" \\| ")[0];
		return window.getRegistry().modService.modCollection.stream()
					   .filter(m -> m.id.equals(modId))
					   .findFirst()
					   .orElse(null);
	}
	
	// ── Navigation ────────────────────────────────────────────────────────────
	
	private void navigateBack() {
		if (hasChanges) {
			int choice = JOptionPane.showConfirmDialog(this,
					"You have unsaved changes. Discard them?",
					"Unsaved Changes",
					JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE);
			if (choice != JOptionPane.YES_OPTION) return;
			hasChanges = false;
		}
		window.navigate(MainWindow.Page.ITEMS);
	}
	
	// ── Status ────────────────────────────────────────────────────────────────
	
	private void markChanged() {
		hasChanges = true;
		updateStatus();
	}
	
	private void updateStatus() {
		if (hasChanges) {
			statusLabel.setText("⚠  Unsaved changes");
			statusLabel.setForeground(new Color(0xf9e2af));
		} else if (currentItem != null) {
			statusLabel.setText("✓  No pending changes");
			statusLabel.setForeground(new Color(0xa6e3a1));
		} else {
			statusLabel.setText(" ");
		}
	}
	
	// ── Helpers ───────────────────────────────────────────────────────────────
	
	private void styleCombo(JComboBox<?> cb) {
		cb.setFont(new Font("Roboto", Font.PLAIN, 12));
		cb.setBackground(MainWindow.SURFACE);
		cb.setForeground(MainWindow.TEXT);
		cb.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(0x2a2a3a)),
				BorderFactory.createEmptyBorder(4, 8, 4, 8)));
	}
	
	private static JLabel labelMuted(String text) {
		JLabel l = new JLabel(text);
		l.setForeground(MainWindow.MUTED);
		l.setFont(new Font("Roboto", Font.PLAIN, 12));
		return l;
	}
	
	private static String escHtml(String s) {
		if (s == null) return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}