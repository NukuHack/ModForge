package com.nukuhack.modforge.frontend.pages;

import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.backend.model.Attribute;
import com.nukuhack.modforge.backend.model.ModItem;
import com.nukuhack.modforge.backend.service.ModItemBuilder;
import com.nukuhack.modforge.frontend.BarManager;
import com.nukuhack.modforge.frontend.MainWindow;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.nukuhack.modforge.frontend.MainWindow.getLocalText;

/**
 * Item attribute editor.
 *
 * Supports recursive {@link Attribute.XmlNodeAttribute} trees:
 * each XML node is rendered as a collapsible sub-panel with the same
 * label+editor layout as the parent, indented to show hierarchy.
 *
 * Flat attributes:  label | editor  (GridBagLayout, 2-column)
 * XML attributes:   ▶ Tag name  [collapsible section with child attrs]
 */
@lombok.extern.slf4j.Slf4j
public class ItemEdit extends BaseEditPage {
	
	/**
	 * Flat map of "path → component" where path is either
	 *   "__id__"               for the ID field
	 *   "AttrName"             for a top-level attribute
	 *   "XmlTag/ChildAttr"    for nested XML attributes
	 */
	private final Map<String, JComponent> attributeComponents = new LinkedHashMap<>();
	
	private final JPanel attributesPanel;
	
	public ItemEdit(MainWindow w) {
		super(w);
		attributesPanel = new JPanel(new GridBagLayout());
		attributesPanel.setBackground(MainWindow.SURFACE);
		attributesPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
		initUI();
	}
	
	@Override
	protected String getPageTitle() {
		return "ui_item_edit";
	}
	
	@Override
	protected String getFormPanelTitle() {
		return getLocalText("ui_attributes");
	}
	
	@Override
	protected String getPreviewTitle() {
		return getLocalText("ui_live_preview");
	}
	
	@Override
	protected JPanel buildFormPanel() {
		return attributesPanel;
	}
	
	@Override
	protected JPanel buildRightActions() {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		panel.setOpaque(false);
		styleCombo(modSelector);
		modSelector.setPreferredSize(new Dimension(200, 28));
		modSelector.setToolTipText(getLocalText("ui_mod_source_tip"));
		panel.add(muted("ui_target_mod"));
		panel.add(modSelector);
		panel.add(primaryBtn("ui_add_to_mod", e -> addCurrentItemToSelectedMod()));
		return panel;
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
		refreshModSelector();
		buildAttributeEditor();
		
		if (previewPane.getClientProperty("itemEditListenerInstalled") == null) {
			previewPane.addMouseListener(mouseClicked(currentItem));
			previewPane.putClientProperty("itemEditListenerInstalled", Boolean.TRUE);
		}
		previewPane.setComponentPopupMenu(buildItemPopupMenu(() -> currentItem, false, true));
	}
	
	@Override
	protected void updatePreview() {
		previewPane.setText(htmlForItem(currentItem));
		previewPane.setCaretPosition(0);
	}
	
	@Override
	protected void navigateBack() {
		if (confirmDiscard())
			window.navigate(MainWindow.Page.ITEMS);
	}
	
	private void buildAttributeEditor() {
		attributesPanel.removeAll();
		attributeComponents.clear();
		
		if (currentItem == null) {
			JLabel empty = new JLabel(getLocalText("ui_no_item_selected_hint"));
			empty.setForeground(MainWindow.MUTED);
			attributesPanel.add(empty, defaultGbc(0));
			attributesPanel.revalidate();
			attributesPanel.repaint();
			return;
		}
		
		int row = 0;
		row = addIdRow(attributesPanel, row);
		
		attributesPanel.add(makeSeparator(), separatorGbc(row++));
		
		List<Attribute> flat = new ArrayList<>();
		List<Attribute.XmlNodeAttribute> xmlAttrs = new ArrayList<>();
		
		for (var attr : currentItem.getAttributes()) {
			if (attr instanceof Attribute.XmlNodeAttribute x)
				xmlAttrs.add(x);
			else
				flat.add(attr);
		}
		
		for (var attr : flat)
			row = addAttributeRow(attributesPanel, attr, attr.getName(), row, 0);
		
		for (var xmlAttr : xmlAttrs) {
			row = addXmlNodeSection(attributesPanel, xmlAttr, row, 0);
			attributesPanel.add(makeSeparator(), separatorGbc(row++));
		}
		
		GridBagConstraints filler = new GridBagConstraints();
		filler.gridy = row;
		filler.weighty = 1.0;
		filler.fill = GridBagConstraints.VERTICAL;
		attributesPanel.add(Box.createVerticalGlue(), filler);
		
		attributesPanel.revalidate();
		attributesPanel.repaint();
	}
	
	private int addIdRow(JPanel panel, int row) {
		JLabel l = new JLabel(getLocalText("ui_id") + ":");
		l.setForeground(MainWindow.ACCENT);
		l.setFont(new Font("Roboto", Font.BOLD, 12));
		
		JTextField field = new JTextField(currentItem.getId());
		styleTextField(field);
		
		JButton copyBtn = smallBtn("⎘", e -> {
			Util.copyText(field.getText());
			window.snackbar.show("ui_copied_id", BarManager.Type.INFO, field.getText());
		});
		copyBtn.setToolTipText(getLocalText("ui_copy_id"));
		
		JPanel fieldRow = new JPanel(new BorderLayout(4, 0));
		fieldRow.setOpaque(false);
		fieldRow.add(field, BorderLayout.CENTER);
		fieldRow.add(copyBtn, BorderLayout.EAST);
		
		panel.add(l, labelGbc(row, 0));
		panel.add(fieldRow, editorGbc(row, 0));
		attributeComponents.put("__id__", field);
		addChangeListeners(field.getDocument());
		return row + 1;
	}
	
	private int addAttributeRow(JPanel panel, Attribute attr, String keyPath, int row, int indentLevel) {
		JLabel lbl = new JLabel(attr.getName() + ":");
		lbl.setForeground(indentLevel == 0 ? MainWindow.TEXT : MainWindow.MUTED);
		lbl.setFont(new Font("Roboto", Font.PLAIN, 12));
		
		var editor = createEditorForAttribute(attr, keyPath);
		panel.add(lbl, labelGbc(row, indentLevel));
		panel.add(editor, editorGbc(row, indentLevel));
		return row + 1;
	}
	
	/**
	 * Renders an {@link Attribute.XmlNodeAttribute} as a collapsible titled section
	 * that recursively renders child attributes (which may themselves be XML nodes).
	 *
	 * @param panel       the panel to add into
	 * @param xmlAttr     the XML node attribute
	 * @param row         current GridBag row in {@code panel}
	 * @param depth       nesting depth (0 = top-level XML section)
	 * @return next available row index
	 */
	private int addXmlNodeSection(JPanel panel, Attribute.XmlNodeAttribute xmlAttr, int row, int depth) {
		final String nodeTag = xmlAttr.getName();
		final Attribute.XmlNode node = xmlAttr.getValue();
		
		String accent = depth == 0 ? "#89dceb" : "#6c6f85";
		JPanel header = buildXmlSectionHeader(nodeTag, node, accent);
		
		GridBagConstraints headerGbc = new GridBagConstraints();
		headerGbc.gridx = 0;
		headerGbc.gridy = row;
		headerGbc.gridwidth = 2;
		headerGbc.weightx = 1.0;
		headerGbc.fill = GridBagConstraints.HORIZONTAL;
		headerGbc.insets = new Insets(depth == 0 ? 10 : 4, depth * 16, 2, 4);
		panel.add(header, headerGbc);
		row++;
		
		JPanel inner = new JPanel(new GridBagLayout());
		inner.setBackground(new Color(0x1e1e2e));
		inner.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, new Color(Integer.parseInt(accent.substring(1), 16))), BorderFactory.createEmptyBorder(4, 8, 4, 4)));
		
		int innerRow = 0;
		
		List<Attribute> flatChildren = new ArrayList<>();
		List<Attribute.XmlNodeAttribute> xmlChildren = new ArrayList<>();
		for (var attr : node.attributes()) {
			if (attr instanceof Attribute.XmlNodeAttribute x)
				xmlChildren.add(x);
			else
				flatChildren.add(attr);
		}
		
		for (var child : node.children()) {
			xmlChildren.add(new Attribute.XmlNodeAttribute(child.tag(), child));
		}
		
		for (var attr : flatChildren) {
			String keyPath = nodeTag + "/" + attr.getName();
			innerRow = addAttributeRow(inner, attr, keyPath, innerRow, 0);
		}
		for (var child : xmlChildren) {
			innerRow = addXmlNodeSection(inner, child, innerRow, depth + 1);
		}
		
		if (innerRow == 0) {
			
			JLabel leaf = new JLabel(xmlAttr.serialize().isEmpty() ? "<empty>" : xmlAttr.serialize());
			leaf.setForeground(new Color(0x6c6f85));
			leaf.setFont(new Font("Roboto Mono", Font.PLAIN, 10));
			GridBagConstraints leafGbc = new GridBagConstraints();
			leafGbc.gridx = 0;
			leafGbc.gridy = 0;
			leafGbc.weightx = 1.0;
			leafGbc.fill = GridBagConstraints.HORIZONTAL;
			leafGbc.insets = new Insets(2, 0, 2, 0);
			inner.add(leaf, leafGbc);
		}
		
		GridBagConstraints spacer = new GridBagConstraints();
		spacer.gridx = 0;
		spacer.gridy = innerRow;
		spacer.weighty = 1.0;
		spacer.fill = GridBagConstraints.VERTICAL;
		inner.add(Box.createVerticalGlue(), spacer);
		
		JToggleButton toggle = (JToggleButton) header.getClientProperty("toggle");
		toggle.addActionListener(e -> {
			inner.setVisible(! toggle.isSelected());
			panel.revalidate();
			panel.repaint();
		});
		
		GridBagConstraints innerGbc = new GridBagConstraints();
		innerGbc.gridx = 0;
		innerGbc.gridy = row;
		innerGbc.gridwidth = 2;
		innerGbc.weightx = 1.0;
		innerGbc.fill = GridBagConstraints.HORIZONTAL;
		innerGbc.insets = new Insets(0, depth * 16 + 8, 4, 4);
		panel.add(inner, innerGbc);
		row++;
		
		return row;
	}
	
	private JPanel buildXmlSectionHeader(String tag, Attribute.XmlNode node, String accent) {
		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setBackground(new Color(0x181825));
		header.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0x313244)), BorderFactory.createEmptyBorder(4, 6, 4, 6)));
		
		JToggleButton toggle = new JToggleButton("▼");
		toggle.setSelected(false);
		
		toggle.setBackground(new Color(0x181825));
		toggle.setForeground(new Color(Integer.parseInt(accent.substring(1), 16)));
		toggle.setFocusPainted(false);
		toggle.setBorderPainted(false);
		toggle.setFont(new Font("Roboto", Font.PLAIN, 10));
		toggle.setPreferredSize(new Dimension(22, 22));
		toggle.addActionListener(e -> toggle.setText(toggle.isSelected() ? "▶" : "▼"));
		
		JLabel tagLabel = new JLabel("<" + tag + ">");
		tagLabel.setForeground(new Color(Integer.parseInt(accent.substring(1), 16)));
		tagLabel.setFont(new Font("Roboto Mono", Font.BOLD, 11));
		
		int childCount = node.attributes().size() + node.children().size();
		JLabel badge = new JLabel(childCount + " " + (childCount == 1 ? "attr" : "attrs"));
		badge.setForeground(new Color(0x6c6f85));
		badge.setFont(new Font("Roboto", Font.PLAIN, 9));
		
		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		left.setOpaque(false);
		left.add(toggle);
		left.add(tagLabel);
		left.add(badge);
		
		header.add(left, BorderLayout.WEST);
		header.putClientProperty("toggle", toggle);
		return header;
	}
	
	private JComponent createEditorForAttribute(Attribute attr, String keyPath) {
		JComponent comp;
		
		if (attr instanceof Attribute.BooleanAttribute boolAttr) {
			JCheckBox cb = new JCheckBox();
			cb.setSelected(boolAttr.getValue());
			cb.setBackground(MainWindow.SURFACE);
			cb.setForeground(MainWindow.TEXT);
			cb.addActionListener(e -> markChanged());
			comp = cb;
			
		} else if (attr instanceof Attribute.DoubleAttribute doubleAttr) {
			double billion = 1_000_000_000;
			JSpinner sp = new JSpinner(new SpinnerNumberModel((Number) doubleAttr.getValue(), - billion, billion, 1));
			sp.setEditor(new JSpinner.NumberEditor(sp, "#.####"));
			styleSpinner(sp);
			sp.addChangeListener(e -> markChanged());
			comp = sp;
			
		} else if (attr instanceof Attribute.ListAttribute<?>) {
			JTextArea ta = new JTextArea(attr.serialize());
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
			comp = sp;
			
		} else if (attr instanceof Attribute.EnumAttribute enumAttr) {
			var enumType = enumAttr.getEnumType();
			var constants = enumType.getEnumConstants();
			JComboBox<String> cb = new JComboBox<>();
			for (var c : constants)
				cb.addItem(c.name());
			cb.setSelectedItem(enumAttr.getValue().name());
			styleCombo(cb);
			cb.addActionListener(e -> markChanged());
			comp = cb;
			
		} else {
			
			JTextField tf = new JTextField(attr.serialize());
			styleTextField(tf);
			addChangeListeners(tf.getDocument());
			comp = tf;
		}
		
		attributeComponents.put(keyPath, comp);
		return comp;
	}
	
	private void saveChanges() {
		if (currentItem == null)
			return;
		
		JComponent idComp = attributeComponents.get("__id__");
		if (idComp instanceof JTextField f)
			currentItem.setId(f.getText());
		
		for (Attribute<?> attr : currentItem.getAttributes())
			handleAttribute(attr, attr.getName());
		
		hasChanges = false;
		updatePreview();
		updateStatus();
		setCurrentItem(currentItem);
		window.snackbar.show("ui_item_changes_saved", BarManager.Type.SUCCESS);
	}
	
	private <T> void handleAttribute(Attribute<T> attr, String keyPath) {
		var comp = attributeComponents.get(keyPath);
		if (comp == null)
			return;
		T val = extractValue(comp, attr);
		if (val != null) {
			currentItem.removeAttribute(attr);
			currentItem.addAttribute(attr.withValue(val));
		}
	}
	
	@SuppressWarnings("unchecked")
	private <T> T extractValue(JComponent comp, Attribute<T> attr) {
		if (attr instanceof Attribute.BooleanAttribute && comp instanceof JCheckBox cb)
			return (T) Boolean.valueOf(cb.isSelected());
		if (attr instanceof Attribute.DoubleAttribute && comp instanceof JSpinner sp)
			return (T) sp.getValue();
		if (attr instanceof Attribute.EnumAttribute ea && comp instanceof JComboBox<?> cb) {
			
			String name = (String) cb.getSelectedItem();
			if (name != null) {
				for (var c : ea.getEnumType().getEnumConstants())
					if (c.name().equals(name))
						return (T) c;
			}
			return null;
		}
		if (attr instanceof Attribute.ListAttribute<?> && comp instanceof JScrollPane sp && sp.getViewport().getView() instanceof JTextArea ta)
			return (T) List.of(ta.getText().split("\\s+"));
		if (comp instanceof JTextField tf)
			return (T) tf.getText();
		return null;
	}
	
	private void addCurrentItemToSelectedMod() {
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
		mod.addItem(ModItemBuilder.deepCopy(currentItem, mod));
		window.snackbar.show("ui_item_added_to_mod", BarManager.Type.SUCCESS, mod.name);
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
	
	private JSeparator makeSeparator() {
		JSeparator sep = new JSeparator();
		sep.setForeground(new Color(0x313244));
		sep.setBackground(new Color(0x313244));
		return sep;
	}
	
	private void addChangeListeners(Document doc) {
		doc.addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e) {
				markChanged();
			}
			
			public void removeUpdate(DocumentEvent e) {
				markChanged();
			}
			
			public void changedUpdate(DocumentEvent e) {
				markChanged();
			}
		});
	}
	
	private GridBagConstraints labelGbc(int row, int indentLevel) {
		GridBagConstraints gc = new GridBagConstraints();
		gc.gridx = 0;
		gc.gridy = row;
		gc.weightx = 0.0;
		gc.fill = GridBagConstraints.NONE;
		gc.anchor = GridBagConstraints.NORTHEAST;
		gc.insets = new Insets(5, 4 + indentLevel * 16, 5, 8);
		return gc;
	}
	
	private GridBagConstraints editorGbc(int row, int indentLevel) {
		GridBagConstraints gc = new GridBagConstraints();
		gc.gridx = 1;
		gc.gridy = row;
		gc.weightx = 1.0;
		gc.fill = GridBagConstraints.HORIZONTAL;
		gc.anchor = GridBagConstraints.NORTHWEST;
		gc.insets = new Insets(5, 0, 5, 4 + indentLevel * 16);
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