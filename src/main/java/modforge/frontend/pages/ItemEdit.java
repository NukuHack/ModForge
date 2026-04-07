package modforge.frontend.pages;

import modforge.Util;
import modforge.backend.model.ModItem;
import modforge.backend.model.Attribute;
import modforge.backend.service.ModItemBuilder;
import modforge.backend.service.ModService;
import modforge.frontend.BarManager;
import modforge.frontend.MainWindow;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// =============================================================================
//  ITEM EDIT PAGE
// =============================================================================
@lombok.extern.slf4j.Slf4j
public class ItemEdit extends BaseEditPage {
	
	// ── Attribute form state ──────────────────────────────────────────────────
	private final Map<String, JComponent> attributeComponents = new LinkedHashMap<>();
	private final JPanel attributesPanel;
	
	// ── Constructor ──────────────────────────────────────────────────────────
	
	public ItemEdit(MainWindow w) {
		super(w);
		// Initialize our panel BEFORE calling initUI(), because initUI() will
		// invoke buildFormPanel() which returns attributesPanel.
		attributesPanel = new JPanel(new GridBagLayout());
		attributesPanel.setBackground(MainWindow.SURFACE);
		attributesPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
		initUI();  // safe to call now — attributesPanel is ready
	}
	
	// ── BaseItemEditPage overrides ────────────────────────────────────────────
	
	@Override
	protected String getPageTitle() {
		return "Item Edit";
	}
	
	@Override
	protected String getFormPanelTitle() {
		return "Attributes";
	}
	
	@Override
	protected String getPreviewTitle() {
		return "Live Preview";
	}
	
	@Override
	protected JPanel buildRightActions() {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		panel.setOpaque(false);
		
		DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
		modSelector = new JComboBox<>(model);
		modSelector.setFont(new Font("Roboto", Font.PLAIN, 12));
		modSelector.setBackground(MainWindow.SURFACE);
		modSelector.setForeground(MainWindow.TEXT);
		modSelector.setPreferredSize(new Dimension(200, 32));
		
		JLabel targetLabel = new JLabel("Target mod:");
		targetLabel.setForeground(MainWindow.MUTED);
		targetLabel.setFont(new Font("Roboto", Font.PLAIN, 12));
		
		panel.add(targetLabel);
		panel.add(modSelector);
		panel.add(primaryBtn("+ Add to Mod", e -> addCurrentItemToSelectedMod()));
		return panel;
	}
	
	@Override
	protected JPanel buildFormPanel() {
		return attributesPanel;
	}
	
	@Override
	protected JPanel buildActionButtons() {
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttons.setOpaque(false);
		buttons.add(primaryBtn("Save Changes", e -> saveChanges()));
		buttons.add(getDangerButton("← Back to Items", e -> navigateBack()));
		return buttons;
	}
	
	@Override
	protected void onItemSet(ModItem item) {
		refreshModSelector();
		buildAttributeEditor();
		
		// Wire up double-click and right-click menu on the shared previewPane.
		// NOTE: do NOT remove existing mouse listeners — Swing's BasicTextUI installs
		// its own listeners for text selection, and stripping them kills selectability.
		if (previewPane != null) {
			// Use a client-property flag so we only add our listener once,
			// even when onItemSet() is called repeatedly.
			if (previewPane.getClientProperty("itemEditListenerInstalled") == null) {
				previewPane.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent e) {
						if (e.getClickCount() == 2 && currentItem != null) {
							Util.copyText(currentItem.getId());
							window.snackbar.show("ID copied: " + currentItem.getId(), BarManager.Type.INFO);
						}
					}
				});
				previewPane.putClientProperty("itemEditListenerInstalled", Boolean.TRUE);
			}
			previewPane.setComponentPopupMenu(getPopupMenu());
		}
	}
	
	private JPopupMenu getPopupMenu() {
		JPopupMenu menu = new JPopupMenu();
		JMenuItem copyId = new JMenuItem("Copy ID");
		copyId.addActionListener(e -> {
			if (currentItem != null) {
				Util.copyText(currentItem.getId());
				window.snackbar.show("ID copied: " + currentItem.getId(), BarManager.Type.INFO);
			}
		});
		JMenuItem copyAll = new JMenuItem("Copy All Details");
		copyAll.addActionListener(e -> {
			if (currentItem != null) {
				Util.copyText(currentItem.details());
				window.snackbar.show("All details copied", BarManager.Type.INFO);
			}
		});
		menu.add(copyId);
		menu.add(copyAll);
		return menu;
	}
	
	@Override
	protected void updatePreview() {
		if (previewPane == null)
			return;
		final var html = htmlForItem(currentItem);
		previewPane.setText(html);
		previewPane.setCaretPosition(0);
	}
	
	@Override
	protected void navigateBack() {
		if (confirmDiscard())
			window.navigate(MainWindow.Page.ITEMS);
	}
	
	// ── Mod selector ──────────────────────────────────────────────────────────
	
	private void refreshModSelector() {
		String previous = (String) modSelector.getSelectedItem();
		DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) modSelector.getModel();
		model.removeAllElements();
		
		var mods = ModService.modCollection;
		if (mods.isEmpty()) {
			model.addElement("— no mods found —");
		} else {
			for (var mod : mods)
				model.addElement(mod.id + " | " + mod.name);
			if (previous != null)
				model.setSelectedItem(previous);
		}
	}
	
	private void addCurrentItemToSelectedMod() {
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
		ModItem copy = ModItemBuilder.deepCopy(currentItem, mod);
		mod.addItem(copy);
		window.snackbar.show("Added item to mod: " + mod.name, BarManager.Type.SUCCESS);
	}
	
	// ── Attribute editor ──────────────────────────────────────────────────────
	
	private void buildAttributeEditor() {
		attributesPanel.removeAll();
		attributeComponents.clear();
		
		if (currentItem == null) {
			JLabel empty = new JLabel("No item selected — go to Items and right-click an entry.");
			empty.setForeground(MainWindow.MUTED);
			attributesPanel.add(empty, defaultGbc(0));
			attributesPanel.revalidate();
			attributesPanel.repaint();
			return;
		}
		
		int row = 0;
		row = addIdRow(row);
		
		JSeparator sep = new JSeparator();
		sep.setForeground(new Color(0x313244));
		sep.setBackground(new Color(0x313244));
		attributesPanel.add(sep, separatorGbc(row++));
		
		for (var attr : currentItem.getAttributes())
			row = addAttributeRow(attr, row);
		
		GridBagConstraints filler = new GridBagConstraints();
		filler.gridy = row;
		filler.weighty = 1.0;
		filler.fill = GridBagConstraints.VERTICAL;
		attributesPanel.add(Box.createVerticalGlue(), filler);
		
		attributesPanel.revalidate();
		attributesPanel.repaint();
	}
	
	private int addIdRow(int row) {
		JLabel l = new JLabel("ID:");
		l.setForeground(MainWindow.ACCENT);
		l.setFont(new Font("Roboto", Font.BOLD, 12));
		
		JTextField field = new JTextField(currentItem.getId());
		styleTextField(field);
		
		JButton copyBtn = smallBtn("⎘", e -> {
			Util.copyText(field.getText());
			window.snackbar.show("ID copied", BarManager.Type.INFO);
		});
		
		JPanel fieldRow = new JPanel(new BorderLayout(4, 0));
		fieldRow.setOpaque(false);
		fieldRow.add(field, BorderLayout.CENTER);
		fieldRow.add(copyBtn, BorderLayout.EAST);
		
		attributesPanel.add(l, labelGbc(row));
		attributesPanel.add(fieldRow, editorGbc(row));
		attributeComponents.put("__id__", field);
		addChangeListeners(field.getDocument());
		return row + 1;
	}
	
	private int addAttributeRow(Attribute attr, int row) {
		JLabel lbl = new JLabel(attr.getName() + ":");
		lbl.setForeground(MainWindow.TEXT);
		lbl.setFont(new Font("Roboto", Font.PLAIN, 12));
		
		JComponent editor = createEditorForAttribute(attr);
		
		attributesPanel.add(lbl, labelGbc(row));
		attributesPanel.add(editor, editorGbc(row));
		attributeComponents.put(attr.getName(), editor);
		return row + 1;
	}
	
	// ── Save ──────────────────────────────────────────────────────────────────
	
	private void saveChanges() {
		if (currentItem == null)
			return;
		
		JComponent idComp = attributeComponents.get("__id__");
		if (idComp instanceof JPanel idPanel) {
			for (var c : idPanel.getComponents())
				if (c instanceof JTextField f)
					currentItem.setId(f.getText());
		} else if (idComp instanceof JTextField f) {
			currentItem.setId(f.getText());
		}
		
		for (Attribute<?> attr : currentItem.getAttributes())
			handleAttribute(attr);
		
		hasChanges = false;
		updatePreview();
		updateStatus();
		setCurrentItem(currentItem);
		window.snackbar.show("Item changes saved", BarManager.Type.SUCCESS);
	}
	
	private <T> void handleAttribute(Attribute<T> attr) {
		var comp = attributeComponents.get(attr.getName());
		if (comp == null)
			return;
		T val = extractValue(comp, attr);
		if (val != null) {
			currentItem.removeAttribute(attr);
			currentItem.addAttribute(attr.deepClone(val));
		}
	}
	
	@SuppressWarnings("unchecked")
	private <T> T extractValue(JComponent comp, Attribute<T> attr) {
		if (attr instanceof Attribute.BooleanAttribute && comp instanceof JCheckBox cb)
			return (T) Boolean.valueOf(cb.isSelected());
		if (attr instanceof Attribute.DoubleAttribute && comp instanceof JSpinner sp)
			return (T) sp.getValue();  // SpinnerNumberModel already stores Double
		if (attr instanceof Attribute.StringAttribute && comp instanceof JTextField tf)
			return (T) tf.getText();
		if (attr instanceof Attribute.ListAttribute<?> && comp instanceof JScrollPane sp && sp.getViewport().getView() instanceof JTextArea ta)
			return (T) List.of(ta.getText().split("\\s+"));
		
		// fallback for unknown string-backed attributes
		if (comp instanceof JTextField tf)
			return (T) tf.getText();
		
		return null;
	}
	
	// ── Component factories ───────────────────────────────────────────────────
	
	private JComponent createEditorForAttribute(Attribute attr) {
		if (attr instanceof Attribute.BooleanAttribute boolAttr) {
			JCheckBox cb = new JCheckBox();
			cb.setSelected(boolAttr.getValue());
			cb.setBackground(MainWindow.SURFACE);
			cb.setForeground(MainWindow.TEXT);
			cb.addActionListener(e -> markChanged());
			return cb;
		}
		if (attr instanceof Attribute.DoubleAttribute doubleAttr) {
			JSpinner sp = new JSpinner(new SpinnerNumberModel((Number) doubleAttr.getValue(), - Double.MAX_VALUE, Double.MAX_VALUE, 1.0));
			sp.setEditor(new JSpinner.NumberEditor(sp, "#.####"));
			styleSpinner(sp);
			sp.addChangeListener(e -> markChanged());
			return sp;
		}
		if (attr instanceof Attribute.StringAttribute) {
			JTextField tf = new JTextField(String.valueOf(attr.getValue()));
			styleTextField(tf);
			addChangeListeners(tf.getDocument());
			return tf;
		}
		if (attr instanceof Attribute.ListAttribute<?> listAttr) {
			@SuppressWarnings("unchecked") Attribute.ListAttribute<Object> la = (Attribute.ListAttribute<Object>) listAttr;
			JTextArea ta = new JTextArea(la.getValue().toString());
			ta.setRows(3);
			ta.setBackground(new Color(0x313244));
			ta.setForeground(MainWindow.TEXT);
			ta.setFont(new Font("Roboto", Font.PLAIN, 12));
			ta.setLineWrap(true);
			ta.setWrapStyleWord(true);
			ta.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x45475a)), BorderFactory.createEmptyBorder(6, 8, 6, 8)));
			addChangeListeners(ta.getDocument());
			JScrollPane sp = new JScrollPane(ta);
			sp.setPreferredSize(new Dimension(0, 80));
			sp.setBackground(MainWindow.SURFACE);
			sp.setBorder(BorderFactory.createEmptyBorder());
			return sp;
		}
		// fallback
		JTextField tf = new JTextField(String.valueOf(attr.getValue()));
		styleTextField(tf);
		addChangeListeners(tf.getDocument());
		return tf;
	}
	
	// ── Style helpers ─────────────────────────────────────────────────────────
	
	private void styleTextField(JTextField f) {
		f.setBackground(new Color(0x313244));
		f.setForeground(MainWindow.TEXT);
		f.setCaretColor(MainWindow.TEXT);
		f.setFont(new Font("Roboto", Font.PLAIN, 12));
		f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x45475a)), BorderFactory.createEmptyBorder(6, 8, 6, 8)));
	}
	
	private void styleSpinner(JSpinner sp) {
		sp.setBackground(new Color(0x313244));
		sp.setForeground(MainWindow.TEXT);
		JTextField tf = ((JSpinner.DefaultEditor) sp.getEditor()).getTextField();
		tf.setBackground(new Color(0x313244));
		tf.setForeground(MainWindow.TEXT);
		tf.setCaretColor(MainWindow.TEXT);
	}
	
	private JButton smallBtn(String text, java.awt.event.ActionListener action) {
		JButton b = new JButton(text);
		b.setBackground(new Color(0x45475a));
		b.setForeground(MainWindow.TEXT);
		b.setFocusPainted(false);
		b.setBorderPainted(false);
		b.setFont(new Font("Roboto", Font.PLAIN, 11));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.setPreferredSize(new Dimension(28, 28));
		b.addActionListener(action);
		return b;
	}
	
	// ── Change tracking ───────────────────────────────────────────────────────
	
	private void addChangeListeners(Document doc) {
		doc.addDocumentListener(new DocumentListener() {
			public void insertUpdate(javax.swing.event.DocumentEvent e) {
				markChanged();
			}
			
			public void removeUpdate(javax.swing.event.DocumentEvent e) {
				markChanged();
			}
			
			public void changedUpdate(javax.swing.event.DocumentEvent e) {
				markChanged();
			}
		});
	}
	
	// ── GridBag helpers ───────────────────────────────────────────────────────
	
	private GridBagConstraints labelGbc(int row) {
		GridBagConstraints gc = new GridBagConstraints();
		gc.gridx = 0;
		gc.gridy = row;
		gc.weightx = 0.0;
		gc.fill = GridBagConstraints.NONE;
		gc.anchor = GridBagConstraints.NORTHEAST;
		gc.insets = new Insets(5, 4, 5, 8);
		return gc;
	}
	
	private GridBagConstraints editorGbc(int row) {
		GridBagConstraints gc = new GridBagConstraints();
		gc.gridx = 1;
		gc.gridy = row;
		gc.weightx = 1.0;
		gc.fill = GridBagConstraints.HORIZONTAL;
		gc.anchor = GridBagConstraints.NORTHWEST;
		gc.insets = new Insets(5, 0, 5, 4);
		return gc;
	}
	
	private GridBagConstraints separatorGbc(int row) {
		GridBagConstraints gc = new GridBagConstraints();
		gc.gridx = 0;
		gc.gridy = row;
		gc.gridwidth = 2;
		gc.weightx = 1.0;
		gc.fill = GridBagConstraints.HORIZONTAL;
		gc.insets = new Insets(6, 0, 6, 0);
		return gc;
	}
	
	private GridBagConstraints defaultGbc(int row) {
		GridBagConstraints gc = new GridBagConstraints();
		gc.gridx = 0;
		gc.gridy = row;
		gc.gridwidth = 2;
		gc.anchor = GridBagConstraints.CENTER;
		gc.insets = new Insets(24, 0, 0, 0);
		return gc;
	}
}