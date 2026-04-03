// ItemEdit.java
package modforge.frontend.pages;

import modforge.Util;
import modforge.backend.ModData;
import modforge.backend.ModItemFactory;
import modforge.backend.model.ModItem;
import modforge.backend.model.attributes.*;
import modforge.frontend.BarManager;
import modforge.frontend.MainWindow;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

// =============================================================================
//  ITEM EDIT PAGE
// =============================================================================
public class ItemEdit extends BasePage {
	
	private final Map<String, JComponent> attributeComponents = new LinkedHashMap<>();
	// Center
	private final JPanel attributesPanel;
	private ModItem currentItem;
	private JComboBox<String> modSelector;
	private JEditorPane previewPane;
	// Bottom
	private JLabel statusLabel;
	// Change tracking
	private boolean hasChanges = false;
	
	public ItemEdit(MainWindow w) {
		super(w);
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
		setLayout(new BorderLayout(0, 16));
		
		attributesPanel = new JPanel(new GridBagLayout());
		attributesPanel.setBackground(MainWindow.SURFACE);
		attributesPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
		
		buildUI();
	}
	
	private static JLabel plainLabel(String text) {
		JLabel l = new JLabel(text + ":");
		l.setForeground(MainWindow.TEXT);
		l.setFont(new Font("Roboto", Font.PLAIN, 12));
		return l;
	}
	
	// ── UI Construction ───────────────────────────────────────────────────────
	
	@Override
	public void refresh(Object... input) {
		if (input.length > 0 && input[0] instanceof ModItem item)
			this.setItem(item);
		else
			window.navigate(MainWindow.Page.HOME);
	}
	
	private void buildUI() {
		add(buildTopBar(), BorderLayout.NORTH);
		add(buildCenter(), BorderLayout.CENTER);
		add(buildBottomBar(), BorderLayout.SOUTH);
	}
	
	/** Breadcrumb + mod selector + action buttons */
	private JPanel buildTopBar() {
		JPanel top = new JPanel(new BorderLayout(12, 0));
		top.setOpaque(false);
		
		// Left: breadcrumb
		JLabel breadcrumb = new JLabel("Items  ›  Edit Item");
		breadcrumb.setForeground(MainWindow.TEXT);
		breadcrumb.setFont(new Font("Roboto", Font.BOLD, 22));
		breadcrumb.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		
		// Make "Items" portion feel clickable
		breadcrumb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		breadcrumb.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				navigateBack();
			}
			
			@Override
			public void mouseEntered(MouseEvent e) {
			}
		});
		breadcrumb.setToolTipText("Click to go back to Items");
		
		// Right: mod selector + "Add to Mod" button
		JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		rightActions.setOpaque(false);
		
		DefaultComboBoxModel<String> modComboModel = new DefaultComboBoxModel<>();
		modSelector = new JComboBox<>(modComboModel);
		modSelector.setFont(new Font("Roboto", Font.PLAIN, 12));
		modSelector.setBackground(MainWindow.SURFACE);
		modSelector.setForeground(MainWindow.TEXT);
		modSelector.setPreferredSize(new Dimension(200, 32));
		
		JButton addToModBtn = primaryBtn("+ Add to Mod", e -> addCurrentItemToSelectedMod());
		
		rightActions.add(new JLabel("Target mod:") {{
			setForeground(MainWindow.MUTED);
			setFont(new Font("Roboto", Font.PLAIN, 12));
		}});
		rightActions.add(modSelector);
		rightActions.add(addToModBtn);
		
		top.add(breadcrumb, BorderLayout.WEST);
		top.add(rightActions, BorderLayout.EAST);
		return top;
	}
	
	/** Split pane: attribute form (left) + live preview (right) */
	private JSplitPane buildCenter() {
		// ── Left: scrollable attributes ───────────────────────────────────
		JScrollPane attrScroll = new JScrollPane(attributesPanel);
		attrScroll.setBackground(MainWindow.SURFACE);
		attrScroll.getViewport().setBackground(MainWindow.SURFACE);
		attrScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(0x313244)), "Attributes", TitledBorder.LEFT, TitledBorder.TOP, new Font("Roboto", Font.BOLD, 12), MainWindow.ACCENT));
		// Only vertical scroll — the GridBag layout fills width correctly
		attrScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		attrScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		
		// ── Right: live HTML preview ──────────────────────────────────────
		previewPane = new JEditorPane();
		previewPane.setContentType("text/html");
		previewPane.setEditable(false);
		previewPane.setBackground(new Color(0x181825));
		previewPane.setForeground(MainWindow.TEXT);
		previewPane.setFont(new Font("Roboto", Font.PLAIN, 12));
		previewPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
		
		// Double-click preview → copy ID
		previewPane.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && currentItem != null) {
					Util.copyText(currentItem.getId());
					window.snackbar.show("ID copied: " + currentItem.getId(), BarManager.Type.INFO);
				}
			}
		});
		
		// Right-click menu on preview
		JPopupMenu previewMenu = new JPopupMenu();
		JMenuItem copyIdItem = new JMenuItem("Copy ID");
		copyIdItem.addActionListener(e -> {
			if (currentItem != null) {
				Util.copyText(currentItem.getId());
				window.snackbar.show("ID copied: " + currentItem.getId(), BarManager.Type.INFO);
			}
		});
		JMenuItem copyAllItem = new JMenuItem("Copy All Details");
		copyAllItem.addActionListener(e -> {
			if (currentItem != null) {
				Util.copyText(currentItem.details());
				window.snackbar.show("All details copied", BarManager.Type.INFO);
			}
		});
		previewMenu.add(copyIdItem);
		previewMenu.add(copyAllItem);
		previewPane.setComponentPopupMenu(previewMenu);
		
		JScrollPane previewScroll = new JScrollPane(previewPane);
		previewScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(0x313244)), "Live Preview  (double-click to copy ID)", TitledBorder.LEFT, TitledBorder.TOP, new Font("Roboto", Font.BOLD, 12), MainWindow.ACCENT));
		previewScroll.setBackground(MainWindow.SURFACE);
		
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, attrScroll, previewScroll);
		split.setResizeWeight(0.6);
		split.setDividerSize(4);
		split.setBackground(MainWindow.BG);
		split.setBorder(BorderFactory.createEmptyBorder());
		return split;
	}
	
	// ── Navigation ────────────────────────────────────────────────────────────
	
	/** Status label (left) + Save / Cancel buttons (right) */
	private JPanel buildBottomBar() {
		JPanel bar = new JPanel(new BorderLayout());
		bar.setOpaque(false);
		bar.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		
		statusLabel = new JLabel(" ");
		statusLabel.setFont(new Font("Roboto", Font.ITALIC, 11));
		statusLabel.setForeground(new Color(0xa6e3a1));
		
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttons.setOpaque(false);
		
		JButton saveBtn = primaryBtn("Save Changes", e -> saveChanges());
		JButton cancelBtn = getDangerButton("← Back to Items", e -> navigateBack());
		
		buttons.add(saveBtn);
		buttons.add(cancelBtn);
		
		bar.add(statusLabel, BorderLayout.WEST);
		bar.add(buttons, BorderLayout.EAST);
		return bar;
	}
	
	// ── Public API ────────────────────────────────────────────────────────────
	
	private void navigateBack() {
		if (hasChanges) {
			int choice = JOptionPane.showConfirmDialog(this, "You have unsaved changes. Discard them?", "Unsaved Changes", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (choice != JOptionPane.YES_OPTION)
				return;
			hasChanges = false;
		}
		updateStatus();
		window.navigate(MainWindow.Page.ITEMS);
	}
	
	/** Called by ItemsPage before navigating here */
	public void setItem(ModItem item) {
		this.currentItem = item;
		this.hasChanges = false;
		
		refreshModSelector();
		buildAttributeEditor();
		updatePreview();
		updateStatus();
	}
	
	// ── Mod Selector ──────────────────────────────────────────────────────────
	
	/**
	 * Repopulate the mod selector from the live modCollection.
	 * Keeps the previously selected mod when refreshing (e.g. after save).
	 */
	private void refreshModSelector() {
		String previousSelection = (String) modSelector.getSelectedItem();
		
		DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) modSelector.getModel();
		model.removeAllElements();
		
		var mods = window.getRegistry().modService.modCollection;
		if (mods.isEmpty()) {
			model.addElement("— no mods found —");
		} else {
			for (var mod : mods) {
				model.addElement(mod.id + " | " + mod.name);
			}
			// Restore previous selection if it still exists
			if (previousSelection != null) {
				model.setSelectedItem(previousSelection);
			}
		}
	}
	
	// ── Attribute Editor ──────────────────────────────────────────────────────
	
	private void addCurrentItemToSelectedMod() {
		String sel = (String) modSelector.getSelectedItem();
		if (sel == null || sel.startsWith("—")) {
			window.snackbar.show("Please select a mod first", BarManager.Type.WARNING);
			return;
		}
		if (currentItem == null) {
			window.snackbar.show("No item loaded", BarManager.Type.WARNING);
			return;
		}
		String modId = sel.split(" \\| ")[0];
		final Optional<ModData> selectedMod = window.getRegistry().modService.modCollection.stream().filter(m -> m.id.equals(modId)).findFirst();
		
		if (selectedMod.isEmpty()) {
			window.snackbar.show("Please select a Correct mod first", BarManager.Type.WARNING);
			return;
		}
		
		final var mod = selectedMod.get();
		final Path fullPath = Path.of(currentItem.getPath());
		final String path = fullPath.getParent().toString();
		String name = fullPath.getFileName().toString();
		if (name.contains("__")) {
			// Replace __anything.xml with __modId.ml
			name = name.replaceAll("__[^_]+\\.xml", "__" + mod.id + ".ml");
		} else {
			name = name.replace(".xml", "__" + mod.id + ".xml");
		}
		final ModItem copy = ModItemFactory.deepCopy(currentItem, path + name);
		mod.addItem(copy);
		window.snackbar.show("Added to mod: " + mod.name + " (" + mod.getItems().size() + " items)", BarManager.Type.SUCCESS);
	}
	
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
		
		// Separator
		JSeparator sep = new JSeparator();
		sep.setForeground(new Color(0x313244));
		sep.setBackground(new Color(0x313244));
		attributesPanel.add(sep, separatorGbc(row++));
		
		for (Attribute attr : currentItem.getAttributes()) {
			row = addAttributeRow(attr, row);
		}
		
		// Push everything to the top
		GridBagConstraints filler = new GridBagConstraints();
		filler.gridy = row;
		filler.weighty = 1.0;
		filler.fill = GridBagConstraints.VERTICAL;
		attributesPanel.add(Box.createVerticalGlue(), filler);
		
		attributesPanel.revalidate();
		attributesPanel.repaint();
	}
	
	private int addIdRow(int row) {
		JLabel l = new JLabel("ID" + ":");
		l.setForeground(MainWindow.ACCENT);
		l.setFont(new Font("Roboto", Font.BOLD, 12));
		JTextField field = new JTextField(currentItem.getId());
		styleTextField(field);
		
		// Copy button next to ID
		JButton copyBtn = smallBtn("⎘", e -> {
			Util.copyText(field.getText());
			window.snackbar.show("ID copied", BarManager.Type.INFO);
		});
		
		JPanel fieldRow = new JPanel(new BorderLayout(4, 0));
		fieldRow.setOpaque(false);
		fieldRow.add(field, BorderLayout.CENTER);
		fieldRow.add(copyBtn, BorderLayout.EAST);
		
		GridBagConstraints labelGbc = labelGbc(row);
		GridBagConstraints editorGbc = editorGbc(row);
		
		attributesPanel.add(l, labelGbc);
		attributesPanel.add(fieldRow, editorGbc);
		attributeComponents.put("__id__", field);
		
		addChangeListeners(field.getDocument());
		return row + 1;
	}
	
	// ── GridBag helpers ───────────────────────────────────────────────────────
	
	private int addAttributeRow(Attribute attr, int row) {
		JLabel lbl = plainLabel(attr.getName());
		JComponent editor = createEditorForAttribute(attr);
		
		attributesPanel.add(lbl, labelGbc(row));
		attributesPanel.add(editor, editorGbc(row));
		attributeComponents.put(attr.getName(), editor);
		
		return row + 1;
	}
	
	/** Label column: fixed-width, right-aligned */
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
	
	// ── Component factories ───────────────────────────────────────────────────
	
	private GridBagConstraints defaultGbc(int row) {
		GridBagConstraints gc = new GridBagConstraints();
		gc.gridx = 0;
		gc.gridy = row;
		gc.gridwidth = 2;
		gc.anchor = GridBagConstraints.CENTER;
		gc.insets = new Insets(24, 0, 0, 0);
		return gc;
	}
	
	private JComponent createEditorForAttribute(Attribute attr) {
		if (attr instanceof BooleanAttribute boolAttr) {
			JCheckBox cb = new JCheckBox();
			cb.setSelected((Boolean) boolAttr.getValue());
			cb.setBackground(MainWindow.SURFACE);
			cb.setForeground(MainWindow.TEXT);
			cb.addActionListener(e -> markChanged());
			return cb;
		}
		
		if (attr instanceof DoubleAttribute doubleAttr) {
			JSpinner sp = new JSpinner(new SpinnerNumberModel((Number) doubleAttr.getValue(), - Double.MAX_VALUE, Double.MAX_VALUE, 1.0));
			sp.setEditor(new JSpinner.NumberEditor(sp, "#.####"));
			styleSpinner(sp);
			sp.addChangeListener(e -> markChanged());
			return sp;
		}
		
		if (attr instanceof StringAttribute) {
			JTextField tf = new JTextField(String.valueOf(attr.getValue()));
			styleTextField(tf);
			addChangeListeners(tf.getDocument());
			return tf;
		}
		
		if (attr instanceof ListAttribute<?> listAttr) {
			@SuppressWarnings("unchecked") ListAttribute<Object> la = (ListAttribute<Object>) listAttr;
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
		((JSpinner.DefaultEditor) sp.getEditor()).getTextField().setBackground(new Color(0x313244));
		((JSpinner.DefaultEditor) sp.getEditor()).getTextField().setForeground(MainWindow.TEXT);
		((JSpinner.DefaultEditor) sp.getEditor()).getTextField().setCaretColor(MainWindow.TEXT);
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
	
	// ── Change Tracking ───────────────────────────────────────────────────────
	
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
	
	private void markChanged() {
		hasChanges = true;
		updateStatus();
		// Live-update the preview as the user types
		updatePreview();
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
	
	// ── Save / Preview ────────────────────────────────────────────────────────
	
	private void saveChanges() {
		if (currentItem == null)
			return;
		
		// Save ID
		JComponent idComp = attributeComponents.get("__id__");
		if (idComp instanceof JPanel idPanel) {
			// The field is inside the BorderLayout panel
			for (Component c : idPanel.getComponents()) {
				if (c instanceof JTextField idField) {
					currentItem.setId(idField.getText());
				}
			}
		} else if (idComp instanceof JTextField idField) {
			currentItem.setId(idField.getText());
		}
		
		// Save all attributes
		for (Attribute attr : currentItem.getAttributes()) {
			JComponent comp = attributeComponents.get(attr.getName());
			if (comp == null)
				continue;
			Object newVal = extractValue(comp, attr);
			if (newVal != null)
				attr.setValue(newVal);
		}
		
		hasChanges = false;
		updatePreview();
		updateStatus();
		
		setItem(currentItem);
		
		window.snackbar.show("Item changes saved", BarManager.Type.SUCCESS);
	}
	
	private Object extractValue(JComponent comp, Attribute attr) {
		if (comp instanceof JCheckBox cb && attr instanceof BooleanAttribute)
			return cb.isSelected();
		if (comp instanceof JSpinner sp && attr instanceof DoubleAttribute)
			return sp.getValue();
		if (comp instanceof JTextField tf) {
			String text = tf.getText();
			if (attr instanceof DoubleAttribute) {
				try {
					return Double.parseDouble(text);
				} catch (NumberFormatException e) {
					return 0.0;
				}
			}
			return text;
		}
		if (comp instanceof JScrollPane sp && sp.getViewport().getView() instanceof JTextArea ta) {
			return ta.getText();
		}
		return null;
	}
	
	private void updatePreview() {
		if (currentItem == null) {
			previewPane.setText("<html><body style='background:#181825; color:#6c6f85; font-family:monospace; padding:12px;'>" + "<i>No item selected</i></body></html>");
			return;
		}
		
		previewPane.setText(currentItem.detailPanel());
		previewPane.setCaretPosition(0); // scroll to top
	}
}