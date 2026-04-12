package com.nukuhack.modforge.frontend.pages;

import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.backend.model.Attribute;
import com.nukuhack.modforge.backend.model.Attribute.BuffParam;
import com.nukuhack.modforge.backend.model.BuffParamMap;
import com.nukuhack.modforge.backend.model.E.MathOperation;
import com.nukuhack.modforge.backend.model.ModItem;
import com.nukuhack.modforge.backend.service.ModItemBuilder;
import com.nukuhack.modforge.frontend.BarManager;
import com.nukuhack.modforge.frontend.MainWindow;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicComboBoxEditor;
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
 *
 * Enhanced editors:
 * - EnumAttribute      → styled combo with ordinal badge + full name
 * - BuffParamListAttr  → table editor (stat | op | value) with add/remove
 * - XmlNodeAttribute   → collapsible with inline attr-preview in header
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
	
	// ── BuffParamMap cache ────────────────────────────────────────────────────
	// Built once at first use; never rebuilt. All rows share the same master list.
	private static List<BuffParamMap> BUFF_PARAM_ALL = null;
	
	private static List<BuffParamMap> getBuffParamAll() {
		if (BUFF_PARAM_ALL == null)
			BUFF_PARAM_ALL = BuffParamMap.BY_KEY.values().stream()
									 .sorted(java.util.Comparator.comparing(BuffParamMap::getName))
									 .toList();
		return BUFF_PARAM_ALL;
	}
	
	/**
	 * A {@link ComboBoxModel} backed by the master list that keeps a filtered
	 * sub-view. Filtering replaces only the internal index list — no
	 * removeAllItems / addItem loops, so it is O(n) with zero Swing events
	 * during the filter pass.
	 */
	private static class FilterableBuffParamModel extends AbstractListModel<BuffParamMap>
			implements MutableComboBoxModel<BuffParamMap> {
		
		private final List<BuffParamMap> master;
		private List<BuffParamMap> view;
		private BuffParamMap selected;
		
		FilterableBuffParamModel(List<BuffParamMap> master, BuffParamMap initial) {
			this.master   = master;
			this.view     = new ArrayList<>(master);
			this.selected = initial;
		}
		
		/** Narrow the visible list to entries matching {@code text} (case-insensitive). */
		void applyFilter(String text) {
			String lo = text == null ? "" : text.strip().toLowerCase();
			List<BuffParamMap> next = lo.isEmpty() ? new ArrayList<>(master)
											  : master.stream()
														.filter(b -> b.getName().toLowerCase().contains(lo)
																			 || b.getKey().toLowerCase().contains(lo)
																			 || (b.getDescription() != null && b.getDescription().toLowerCase().contains(lo)))
														.toList();
			int oldSize = view.size();
			this.view = new ArrayList<>(next);
			int newSize = view.size();
			// Fire a single coarse interval so the JList redraws
			fireContentsChanged(this, 0, Math.max(oldSize, newSize) - 1);
		}
		
		@Override public int getSize()                        { return view.size(); }
		@Override public BuffParamMap getElementAt(int i)    { return view.get(i); }
		@Override public Object getSelectedItem()             { return selected; }
		@Override public void setSelectedItem(Object o)       {
			selected = (o instanceof BuffParamMap b) ? b : null;
			fireContentsChanged(this, -1, -1);
		}
		@Override public void addElement(BuffParamMap o)      { /* unused */ }
		@Override public void removeElement(Object o)         { /* unused */ }
		@Override public void insertElementAt(BuffParamMap o, int i) { /* unused */ }
		@Override public void removeElementAt(int i)          { /* unused */ }
	}
	
	public ItemEdit(MainWindow w) {
		super(w);
		attributesPanel = new JPanel(new GridBagLayout());
		attributesPanel.setBackground(MainWindow.SURFACE);
		attributesPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
		initUI();
	}
	
	// ─────────────────────────────────────────────────────────────────────────
	// BaseEditPage contract
	// ─────────────────────────────────────────────────────────────────────────
	
	@Override
	protected String getPageTitle() { return "ui_item_edit"; }
	
	@Override
	protected String getFormPanelTitle() { return getLocalText("ui_attributes"); }
	
	@Override
	protected String getPreviewTitle() { return getLocalText("ui_live_preview"); }
	
	@Override
	protected JPanel buildFormPanel() { return attributesPanel; }
	
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
	
	// ─────────────────────────────────────────────────────────────────────────
	// Form construction
	// ─────────────────────────────────────────────────────────────────────────
	
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
			if (attr instanceof Attribute.XmlNodeAttribute x) xmlAttrs.add(x);
			else flat.add(attr);
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
	
	// ─────────────────────────────────────────────────────────────────────────
	// XML node section (collapsible)
	// ─────────────────────────────────────────────────────────────────────────
	
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
		inner.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 2, 0, 0, new Color(Integer.parseInt(accent.substring(1), 16))),
				BorderFactory.createEmptyBorder(4, 8, 4, 4)
		));
		
		int innerRow = 0;
		
		List<Attribute> flatChildren = new ArrayList<>();
		List<Attribute.XmlNodeAttribute> xmlChildren = new ArrayList<>();
		for (var attr : node.attributes()) {
			if (attr instanceof Attribute.XmlNodeAttribute x) xmlChildren.add(x);
			else flatChildren.add(attr);
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
			// Leaf node — show serialized value read-only
			String leafText = xmlAttr.serialize();
			JLabel leaf = new JLabel(leafText.isEmpty() ? "<empty />" : leafText);
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
			inner.setVisible(!toggle.isSelected());
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
	
	/**
	 * Header bar for an XML node section.
	 *
	 * Layout:  [▼] <tag>  (#children attrs)   |   attr1=val1  attr2=val2 …
	 *
	 * The right side shows a short inline preview of the node's flat attributes
	 * so users can tell sections apart at a glance without expanding them.
	 */
	private JPanel buildXmlSectionHeader(String tag, Attribute.XmlNode node, String accent) {
		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setBackground(new Color(0x181825));
		header.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0x313244)),
				BorderFactory.createEmptyBorder(4, 6, 4, 6)
		));
		
		// ── Toggle button ──────────────────────────────────────────────────
		JToggleButton toggle = new JToggleButton("▼");
		toggle.setSelected(false);
		toggle.setBackground(new Color(0x181825));
		toggle.setForeground(new Color(Integer.parseInt(accent.substring(1), 16)));
		toggle.setFocusPainted(false);
		toggle.setBorderPainted(false);
		toggle.setFont(new Font("Roboto", Font.PLAIN, 10));
		toggle.setPreferredSize(new Dimension(22, 22));
		toggle.addActionListener(e -> toggle.setText(toggle.isSelected() ? "▶" : "▼"));
		
		// ── Tag label ──────────────────────────────────────────────────────
		JLabel tagLabel = new JLabel("<" + tag + ">");
		tagLabel.setForeground(new Color(Integer.parseInt(accent.substring(1), 16)));
		tagLabel.setFont(new Font("Roboto Mono", Font.BOLD, 11));
		
		// ── Badge: child count ─────────────────────────────────────────────
		int childCount = node.attributes().size() + node.children().size();
		boolean isLeaf = node.isLeaf() && node.attributes().isEmpty();
		String badgeText = isLeaf ? "leaf" : (childCount + " " + (childCount == 1 ? "attr" : "attrs"));
		JLabel badge = new JLabel(badgeText);
		badge.setForeground(new Color(isLeaf ? 0x89b4fa : 0x6c6f85));
		badge.setFont(new Font("Roboto", Font.PLAIN, 9));
		badge.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(isLeaf ? 0x89b4fa : 0x45475a)),
				BorderFactory.createEmptyBorder(1, 4, 1, 4)
		));
		
		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		left.setOpaque(false);
		left.add(toggle);
		left.add(tagLabel);
		left.add(badge);
		
		// ── Inline attr preview (right side) ──────────────────────────────
		// Show up to 3 flat attributes as "key=value" chips so the user can
		// distinguish sections without expanding them.
		List<Attribute> flatAttrs = node.attributes().stream()
											.filter(a -> !(a instanceof Attribute.XmlNodeAttribute))
											.toList();
		
		if (!flatAttrs.isEmpty()) {
			JPanel previewPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
			previewPanel.setOpaque(false);
			
			int shown = Math.min(flatAttrs.size(), 3);
			for (int i = 0; i < shown; i++) {
				var a = flatAttrs.get(i);
				String valText = a.serialize();
				if (valText.length() > 16) valText = valText.substring(0, 14) + "…";
				JLabel chip = new JLabel(a.getName() + "=" + valText);
				chip.setForeground(new Color(0x6c6f85));
				chip.setFont(new Font("Roboto Mono", Font.PLAIN, 9));
				chip.setBorder(BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(new Color(0x313244)),
						BorderFactory.createEmptyBorder(1, 4, 1, 4)
				));
				chip.setToolTipText(a.getName() + " = " + a.serialize());
				previewPanel.add(chip);
			}
			if (flatAttrs.size() > 3) {
				JLabel more = new JLabel("+" + (flatAttrs.size() - 3) + " more");
				more.setForeground(new Color(0x45475a));
				more.setFont(new Font("Roboto", Font.PLAIN, 9));
				previewPanel.add(more);
			}
			header.add(previewPanel, BorderLayout.EAST);
		}
		
		header.add(left, BorderLayout.WEST);
		header.putClientProperty("toggle", toggle);
		return header;
	}
	
	// ─────────────────────────────────────────────────────────────────────────
	// Editor factory
	// ─────────────────────────────────────────────────────────────────────────
	
	private JComponent createEditorForAttribute(Attribute attr, String keyPath) {
		JComponent comp;
		
		if (attr instanceof Attribute.BooleanAttribute boolAttr) {
			comp = buildBooleanEditor(boolAttr);
			
		} else if (attr instanceof Attribute.DoubleAttribute doubleAttr) {
			comp = buildDoubleEditor(doubleAttr);
			
		} else if (attr instanceof Attribute.BuffParamListAttribute buffAttr) {
			// Must be checked BEFORE ListAttribute since it extends it
			comp = buildBuffParamEditor(buffAttr, keyPath);
			
		} else if (attr instanceof Attribute.ListAttribute<?> listAttr) {
			comp = buildListEditor(listAttr);
			
		} else if (attr instanceof Attribute.EnumAttribute enumAttr) {
			comp = buildEnumEditor(enumAttr);
			
		} else {
			JTextField tf = new JTextField(attr.serialize());
			styleTextField(tf);
			addChangeListeners(tf.getDocument());
			comp = tf;
		}
		
		attributeComponents.put(keyPath, comp);
		return comp;
	}
	
	// ── Boolean ───────────────────────────────────────────────────────────────
	
	private JComponent buildBooleanEditor(Attribute.BooleanAttribute attr) {
		JCheckBox cb = new JCheckBox();
		cb.setSelected(attr.getValue());
		cb.setBackground(MainWindow.SURFACE);
		cb.setForeground(MainWindow.TEXT);
		cb.addActionListener(e -> markChanged());
		return cb;
	}
	
	// ── Double ────────────────────────────────────────────────────────────────
	
	private JComponent buildDoubleEditor(Attribute.DoubleAttribute attr) {
		double billion = 1_000_000_000;
		JSpinner sp = new JSpinner(new SpinnerNumberModel((Number) attr.getValue(), -billion, billion, 1));
		sp.setEditor(new JSpinner.NumberEditor(sp, "#.####"));
		styleSpinner(sp);
		sp.addChangeListener(e -> markChanged());
		return sp;
	}
	
	// ── Generic list (raw text fallback) ─────────────────────────────────────
	
	private JComponent buildListEditor(Attribute.ListAttribute<?> attr) {
		JTextArea ta = new JTextArea(attr.serialize());
		ta.setRows(3);
		ta.setBackground(new Color(0x313244));
		ta.setForeground(MainWindow.TEXT);
		ta.setFont(new Font("Roboto Mono", Font.PLAIN, 12));
		ta.setLineWrap(true);
		ta.setWrapStyleWord(true);
		ta.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(0x45475a)),
				BorderFactory.createEmptyBorder(6, 8, 6, 8)
		));
		addChangeListeners(ta.getDocument());
		JScrollPane sp = new JScrollPane(ta);
		sp.setPreferredSize(new Dimension(0, 80));
		sp.setBackground(MainWindow.SURFACE);
		sp.setBorder(BorderFactory.createEmptyBorder());
		return sp;
	}
	
	// ── Enum ──────────────────────────────────────────────────────────────────
	
	/**
	 * Renders an enum attribute as a styled combo box.
	 *
	 * Each entry shows: [ordinal]  CONSTANT_NAME
	 * Tooltip on each item shows the constant's ordinal and full name.
	 * The current value is pre-selected.
	 */
	private JComponent buildEnumEditor(Attribute.EnumAttribute attr) {
		var enumType  = attr.getEnumType();
		var constants = enumType.getEnumConstants();
		
		// Model stores the enum constants directly; the renderer formats them.
		JComboBox<Enum<?>> cb = new JComboBox<>();
		for (var c : constants)
			cb.addItem(c);
		
		cb.setSelectedItem(attr.getValue());
		styleCombo(cb);
		
		cb.setRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value,
														  int index, boolean isSelected, boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof Enum<?> e) {
					// Badge showing ordinal
					setText(String.format(
							"<html><font color='#6c6f85'>[%d]</font>&nbsp;&nbsp;%s</html>",
							e.ordinal(), e.name()
					));
					setToolTipText(enumType.getSimpleName() + "." + e.name() + "  (ordinal " + e.ordinal() + ")");
				}
				if (!isSelected) {
					setBackground(new Color(0x313244));
					setForeground(MainWindow.TEXT);
				}
				return this;
			}
		});
		
		cb.addActionListener(e -> markChanged());
		return cb;
	}
	
	// ── BuffParamList ─────────────────────────────────────────────────────────
	
	/**
	 * Dedicated table editor for {@link Attribute.BuffParamListAttribute}.
	 *
	 * Layout (inside a titled panel):
	 * ┌──────────────────────────────────────────────────────┐
	 * │  Stat (searchable combo)  │  Op  │  Value  │  [–]   │  ← each row
	 * │  …                                                   │
	 * ├──────────────────────────────────────────────────────┤
	 * │  [+ Add param]                                       │
	 * └──────────────────────────────────────────────────────┘
	 *
	 * The combo shows "Name  —  description" and is searchable via editable text.
	 * The component stored under keyPath is the wrapping JPanel itself;
	 * {@link #extractBuffParams(JPanel)} harvests it on save.
	 */
	private JComponent buildBuffParamEditor(Attribute.BuffParamListAttribute attr, String keyPath) {
		JPanel wrapper = new JPanel(new BorderLayout(0, 4));
		wrapper.setOpaque(false);
		
		JPanel rows = new JPanel();
		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setBackground(new Color(0x1e1e2e));
		
		for (var param : attr.getValue())
			rows.add(buildBuffParamRow(rows, param));
		
		JScrollPane scroll = new JScrollPane(rows);
		scroll.setBackground(new Color(0x1e1e2e));
		scroll.getViewport().setBackground(new Color(0x1e1e2e));
		scroll.setBorder(BorderFactory.createLineBorder(new Color(0x313244)));
		scroll.setPreferredSize(new Dimension(0, Math.min(36 + attr.getValue().size() * 34, 200)));
		
		JButton addBtn = new JButton("＋  Add param");
		addBtn.setBackground(new Color(0x313244));
		addBtn.setForeground(new Color(0xa6e3a1));
		addBtn.setFocusPainted(false);
		addBtn.setBorderPainted(false);
		addBtn.setFont(new Font("Roboto", Font.PLAIN, 11));
		addBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addBtn.addActionListener(e -> {
			rows.add(buildBuffParamRow(rows, null));
			rows.revalidate();
			rows.repaint();
			// grow scroll pane slightly
			Dimension d = scroll.getPreferredSize();
			scroll.setPreferredSize(new Dimension(d.width, Math.min(d.height + 34, 300)));
			scroll.getParent().revalidate();
			markChanged();
		});
		
		JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		bottom.setOpaque(false);
		bottom.add(addBtn);
		
		wrapper.add(scroll, BorderLayout.CENTER);
		wrapper.add(bottom, BorderLayout.SOUTH);
		
		// Store the rows panel under the key so extractValue can find it
		attributeComponents.put(keyPath, wrapper);
		return wrapper;
	}
	
	/**
	 * Builds one editable row for a single {@link BuffParam}.
	 * Pass {@code null} to create a blank row (for the "Add" button).
	 *
	 * Row layout:  [stat combo ──────────] [op ▾] [value ±] [–]
	 *
	 * The stat combo uses {@link FilterableBuffParamModel} — the full master list
	 * is built once and reused across all rows. Filtering narrows the view in
	 * O(n) without any removeAllItems / addItem overhead, so there is no freeze.
	 *
	 * The editor text field always shows only {@code bpm.getName()} (short);
	 * the dropdown popup renderer shows the full "Name — description" form.
	 */
	private JPanel buildBuffParamRow(JPanel parent, BuffParam param) {
		JPanel row = new JPanel(new GridBagLayout());
		row.setBackground(new Color(0x1e1e2e));
		row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0x313244)));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		
		// ── Stat combo (searchable, freeze-free) ──────────────────────────
		BuffParamMap initial = (param != null) ? param.name() : null;
		var model = new FilterableBuffParamModel(getBuffParamAll(), initial);
		JComboBox<BuffParamMap> statCombo = new JComboBox<>(model);
		statCombo.setEditable(true);
		
		// ── Popup renderer: "Name  —  description" ────────────────────────
		statCombo.setRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value,
														  int index, boolean isSelected, boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof BuffParamMap bpm) {
					setText(String.format(
							"<html><b>%s</b>&nbsp;<font color='#6c6f85'>— %s</font></html>",
							bpm.getName(),
							bpm.getDescription() != null ? bpm.getDescription() : bpm.getKey()
					));
					setToolTipText("[" + bpm.getKey() + "]  " + bpm.getDescription());
				}
				if (!isSelected) {
					setBackground(new Color(0x313244));
					setForeground(MainWindow.TEXT);
				}
				return this;
			}
		});
		
		// ── Editor: always shows only the short name in the text field ─────
		// BasicComboBoxEditor with a custom setItem that writes getName() not toString()
		statCombo.setEditor(new BasicComboBoxEditor() {
			@Override
			public void setItem(Object o) {
				if (o instanceof BuffParamMap bpm)
					editor.setText(bpm.getName());
				else if (o instanceof String s)
					editor.setText(s);
				else
					editor.setText("");
			}
			@Override
			public Object getItem() {
				// Try to resolve typed text → model, so selection is always a BuffParamMap
				String text = editor.getText().strip();
				// Prefer exact name match, fall back to key
				BuffParamMap found = BuffParamMap.BY_NAME.get(text);
				if (found == null) found = BuffParamMap.BY_KEY.get(text);
				return found != null ? found : text;
			}
		});
		
		JTextField statEditor = (JTextField) statCombo.getEditor().getEditorComponent();
		statEditor.setBackground(new Color(0x313244));
		statEditor.setForeground(MainWindow.TEXT);
		statEditor.setCaretColor(MainWindow.TEXT);
		statEditor.setFont(new Font("Roboto", Font.PLAIN, 12));
		
		// Seed the text field with the current param name (no selection event yet)
		if (initial != null)
			statEditor.setText(initial.getName());
		
		// ── Keystroke filter: update model view, no item list rebuild ─────
		statEditor.getDocument().addDocumentListener(new DocumentListener() {
			private boolean filtering = false;
			private void filter() {
				if (filtering) return;
				filtering = true;
				// invokeLater so the document update finishes before we read it
				SwingUtilities.invokeLater(() -> {
					try {
						model.applyFilter(statEditor.getText());
						if (!statCombo.isPopupVisible())
							statCombo.showPopup();
					} finally {
						filtering = false;
					}
				});
			}
			public void insertUpdate(DocumentEvent e)  { filter(); markChanged(); }
			public void removeUpdate(DocumentEvent e)  { filter(); markChanged(); }
			public void changedUpdate(DocumentEvent e) {}
		});
		
		// When the user picks an item from the popup, write only the name back
		statCombo.addActionListener(e -> {
			Object sel = statCombo.getSelectedItem();
			if (sel instanceof BuffParamMap bpm) {
				// Reset filter so full list is visible next time popup opens
				model.applyFilter("");
				// Update text field without re-triggering the document listener
				String cur = statEditor.getText();
				if (!cur.equals(bpm.getName())) {
					statEditor.getDocument().removeDocumentListener(
							(DocumentListener) statEditor.getDocument()
													   .getProperty("buffRowDocListener"));
					statEditor.setText(bpm.getName());
				}
			}
			markChanged();
		});
		
		// ── Operation combo ────────────────────────────────────────────────
		JComboBox<MathOperation> opCombo = new JComboBox<>();
		for (var op : MathOperation.values())
			opCombo.addItem(op);
		opCombo.setRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value,
														  int index, boolean isSelected, boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof MathOperation op)
					setText(op.getSymbol() + "  (" + op.name() + ")");
				if (!isSelected) {
					setBackground(new Color(0x313244));
					setForeground(new Color(0xcba6f7));
				}
				return this;
			}
		});
		styleCombo(opCombo);
		if (param != null)
			opCombo.setSelectedItem(param.operation());
		opCombo.addActionListener(e -> markChanged());
		
		// ── Value spinner ──────────────────────────────────────────────────
		double initVal = (param != null) ? param.value() : 0.0;
		JSpinner valueSpinner = new JSpinner(new SpinnerNumberModel(initVal, -1_000_000.0, 1_000_000.0, 1.0));
		valueSpinner.setEditor(new JSpinner.NumberEditor(valueSpinner, "#.####"));
		styleSpinner(valueSpinner);
		valueSpinner.setPreferredSize(new Dimension(90, 28));
		valueSpinner.addChangeListener(e -> markChanged());
		
		// ── Remove button ──────────────────────────────────────────────────
		JButton removeBtn = new JButton("✕");
		removeBtn.setBackground(new Color(0x313244));
		removeBtn.setForeground(new Color(0xf38ba8));
		removeBtn.setFocusPainted(false);
		removeBtn.setBorderPainted(false);
		removeBtn.setFont(new Font("Roboto", Font.PLAIN, 11));
		removeBtn.setPreferredSize(new Dimension(26, 26));
		removeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		removeBtn.addActionListener(e -> {
			parent.remove(row);
			parent.revalidate();
			parent.repaint();
			markChanged();
		});
		
		// ── Layout ────────────────────────────────────────────────────────
		GridBagConstraints gc = new GridBagConstraints();
		gc.insets = new Insets(2, 2, 2, 2);
		gc.fill = GridBagConstraints.HORIZONTAL;
		gc.gridy = 0;
		
		gc.gridx = 0; gc.weightx = 1.0;
		row.add(statCombo, gc);
		
		gc.gridx = 1; gc.weightx = 0.0;
		row.add(opCombo, gc);
		
		gc.gridx = 2; gc.weightx = 0.0;
		row.add(valueSpinner, gc);
		
		gc.gridx = 3; gc.weightx = 0.0; gc.fill = GridBagConstraints.NONE;
		row.add(removeBtn, gc);
		
		// Tag the row panel so we can harvest it later
		row.putClientProperty("statCombo", statCombo);
		row.putClientProperty("statEditor", statEditor);
		row.putClientProperty("opCombo", opCombo);
		row.putClientProperty("valueSpinner", valueSpinner);
		
		return row;
	}
	
	// ─────────────────────────────────────────────────────────────────────────
	// Save / extract
	// ─────────────────────────────────────────────────────────────────────────
	
	private void saveChanges() {
		if (currentItem == null) return;
		
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
		if (comp == null) return;
		T val = extractValue(comp, attr);
		if (val != null) {
			currentItem.removeAttribute(attr);
			currentItem.addAttribute(attr.withValue(val));
		}
	}
	
	@SuppressWarnings("unchecked")
	private <T> T extractValue(JComponent comp, Attribute<T> attr) {
		log.debug("extracting attr data: {} from {}", attr, comp);
		
		if (attr instanceof Attribute.BooleanAttribute && comp instanceof JCheckBox cb)
			return (T) Boolean.valueOf(cb.isSelected());
		
		if (attr instanceof Attribute.DoubleAttribute && comp instanceof JSpinner sp)
			return (T) sp.getValue();
		
		if (attr instanceof Attribute.EnumAttribute ea && comp instanceof JComboBox<?> cb) {
			Object sel = cb.getSelectedItem();
			if (sel instanceof Enum<?> e) return (T) e;
			// fallback: match by name string
			if (sel instanceof String name) {
				for (var c : ea.getEnumType().getEnumConstants())
					if (c.name().equals(name)) return (T) c;
			}
			return null;
		}
		
		if (attr instanceof Attribute.BuffParamListAttribute && comp instanceof JPanel wrapper)
			return (T) extractBuffParams(wrapper);
		
		if (attr instanceof Attribute.ListAttribute<?> && comp instanceof JScrollPane sp && sp.getViewport().getView() instanceof JTextArea ta)
			return (T) List.of(ta.getText().split("\\s+"));
		
		if (comp instanceof JTextField tf)
			return (T) tf.getText();
		
		return null;
	}
	
	/**
	 * Harvest all {@link BuffParam} rows from the buff-param editor wrapper panel.
	 * The wrapper contains a JScrollPane whose viewport holds the rows JPanel.
	 */
	@SuppressWarnings("unchecked")
	private List<BuffParam> extractBuffParams(JPanel wrapper) {
		List<BuffParam> result = new ArrayList<>();
		for (var c : wrapper.getComponents()) {
			if (!(c instanceof JScrollPane sp)) continue;
			if (!(sp.getViewport().getView() instanceof JPanel rowsPanel)) continue;
			for (var rowComp : rowsPanel.getComponents()) {
				if (!(rowComp instanceof JPanel row)) continue;
				var statCombo    = (JComboBox<BuffParamMap>) row.getClientProperty("statCombo");
				var opCombo      = (JComboBox<MathOperation>) row.getClientProperty("opCombo");
				var valueSpinner = (JSpinner)                 row.getClientProperty("valueSpinner");
				var statEditor   = (JTextField)               row.getClientProperty("statEditor");
				if (statCombo == null || opCombo == null || valueSpinner == null) continue;
				
				// Resolve: prefer the model's selected item (already a BuffParamMap),
				// fall back to resolving whatever the editor text field contains.
				BuffParamMap bpm = null;
				Object sel = statCombo.getSelectedItem();
				if (sel instanceof BuffParamMap b) {
					bpm = b;
				} else if (statEditor != null) {
					String txt = statEditor.getText().strip();
					bpm = BuffParamMap.BY_NAME.get(txt);
					if (bpm == null) bpm = BuffParamMap.BY_KEY.get(txt);
				}
				if (bpm == null) continue; // skip rows with unresolvable stat
				
				MathOperation op = (MathOperation) opCombo.getSelectedItem();
				if (op == null) continue;
				
				double val = ((Number) valueSpinner.getValue()).doubleValue();
				result.add(new BuffParam(bpm, op, val));
			}
		}
		return result;
	}
	
	// ─────────────────────────────────────────────────────────────────────────
	// Navigation / mod helpers
	// ─────────────────────────────────────────────────────────────────────────
	
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
	
	// ─────────────────────────────────────────────────────────────────────────
	// Styling helpers
	// ─────────────────────────────────────────────────────────────────────────
	
	private void styleTextField(JTextField f) {
		f.setBackground(new Color(0x313244));
		f.setForeground(MainWindow.TEXT);
		f.setCaretColor(MainWindow.TEXT);
		f.setFont(new Font("Roboto", Font.PLAIN, 12));
		f.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(0x45475a)),
				BorderFactory.createEmptyBorder(6, 8, 6, 8)
		));
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
			public void insertUpdate(DocumentEvent e) { markChanged(); }
			public void removeUpdate(DocumentEvent e) { markChanged(); }
			public void changedUpdate(DocumentEvent e) {}
		});
	}
	
	// ─────────────────────────────────────────────────────────────────────────
	// GridBagConstraints factories
	// ─────────────────────────────────────────────────────────────────────────
	
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