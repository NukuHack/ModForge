package modforge.frontend.pages;

import modforge.backend.ModData;
import modforge.backend.model.item.Storm;
import modforge.backend.model.storm.GenericOperation;
import modforge.backend.model.storm.GenericSelector;
import modforge.backend.model.storm.StormData;
import modforge.backend.model.storm.StormRule;
import modforge.backend.service.StormService;
import modforge.frontend.BarManager;
import modforge.frontend.MainWindow;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// =============================================================================
//  STORM PAGE  –  view & edit Storm rules
// =============================================================================
public class StormPage extends BasePage {

	// ── palette helpers ───────────────────────────────────────────────────────
	private static final Color COMBINATOR_AND = new Color(0x89b4fa); // blue
	private static final Color COMBINATOR_OR = new Color(0xa6e3a1); // green
	private static final Color COMBINATOR_NOT = new Color(0xf38ba8); // red
	private static final Color SELECTOR_FG = new Color(0xcba6f7); // mauve
	private static final Color OPERATION_FG = new Color(0xf9e2af); // yellow
	private static final Color ATTR_KEY = new Color(0x89dceb); // sky
	private static final Color SURFACE2 = new Color(0x1e1e2e);
	private static final Color INPUT_BG = new Color(0x313244);
	private static final Color BORDER_COL = new Color(0x45475a);

	// ── state ─────────────────────────────────────────────────────────────────
	// ── left panel – rule list ────────────────────────────────────────────────
	private final DefaultListModel<StormRule> ruleListModel = new DefaultListModel<>();
	private final JList<StormRule> ruleList = new JList<>(ruleListModel);
	/**
	 * The StormData we are currently viewing / editing (may be null).
	 */
	private StormData currentStorm;
	/**
	 * The mod context when opened from a mod item (may be null = view-only).
	 */
	private ModData currentMod;
	// ── right panel – rule editor ─────────────────────────────────────────────
	private JLabel ruleNameLabel;
	private JLabel ruleCommentLabel;
	private DefaultTreeModel selectorTreeModel;
	private JTree selectorTree;
	private DefaultTreeModel operationTreeModel;
	private JTree operationTree;
	private JEditorPane xmlPreviewPane;

	// ── header ────────────────────────────────────────────────────────────────
	private JLabel breadcrumbLabel;
	private JLabel categoryLabel;
	private JLabel fileIdLabel;

	// ── status ────────────────────────────────────────────────────────────────
	private JLabel statusLabel;

	public StormPage(MainWindow w) {
		super(w);
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
		setLayout(new BorderLayout(0, 12));

		add(buildTopBar(), BorderLayout.NORTH);
		add(buildMainArea(), BorderLayout.CENTER);
		add(buildBottomBar(), BorderLayout.SOUTH);
	}

	// =========================================================================
	//  BasePage contract
	// =========================================================================

	private static String xmlToHtml(String xml) {
		String escaped = xml.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		// Basic syntax colouring
		escaped = escaped.replaceAll("(&lt;[/]?)([a-zA-Z_][a-zA-Z0-9_]*)", "$1<font color='#89b4fa'>$2</font>").replaceAll("([a-zA-Z_][a-zA-Z0-9_]*)=&quot;", "<font color='#89dceb'>$1</font>=<font color='#a6e3a1'>&quot;").replaceAll("&quot;(?=[^=])", "&quot;</font>");
		return "<html><body style='background:#11111b;color:#cdd6f4;font-family:monospace;font-size:11px;padding:8px;white-space:pre;'>" + escaped + "</body></html>";
	}

	// =========================================================================
	//  Top bar
	// =========================================================================

	private static String xmlPlaceholderHtml() {
		return "<html><body style='background:#11111b;color:#6c6f85;font-family:monospace;padding:12px;'>" + "<i>Open a Storm item from the Items page to see its XML here.</i></body></html>";
	}

	@Override
	public void refresh(Object... input) {
		if (input.length > 0 && input[0] instanceof Storm stormItem) {
			// Opened via "Edit Item" on a Storm ModItem
			final StormData sd = stormItem.getStormData();
			if (sd == null) {
				window.snackbar.show("Storm data not parsed yet", BarManager.Type.WARNING);
				return;
			}
			currentStorm = sd;
		} else if (input.length > 0 && input[0] instanceof StormData sd) {
			currentStorm = sd;
		} else {
			window.navigate(MainWindow.Page.HOME);
		}
		populateFromStorm();
	}

	// =========================================================================
	//  Main area – 3-column layout
	// =========================================================================

	private JPanel buildTopBar() {
		JPanel top = new JPanel(new BorderLayout(12, 0));
		top.setOpaque(false);

		// Breadcrumb
		breadcrumbLabel = new JLabel("Storm  ›  (no file selected)");
		breadcrumbLabel.setForeground(MainWindow.TEXT);
		breadcrumbLabel.setFont(new Font("Roboto", Font.BOLD, 22));
		breadcrumbLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		breadcrumbLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				currentStorm = null;
				populateFromStorm();
			}
		});
		breadcrumbLabel.setToolTipText("Click to deselect current file");

		// Meta labels
		JPanel metaPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
		metaPanel.setOpaque(false);

		categoryLabel = styledMeta("Category: —");
		fileIdLabel = styledMeta("ID: —");
		metaPanel.add(categoryLabel);
		metaPanel.add(fileIdLabel);

		JPanel leftSide = new JPanel(new BorderLayout(0, 4));
		leftSide.setOpaque(false);
		leftSide.add(breadcrumbLabel, BorderLayout.NORTH);
		leftSide.add(metaPanel, BorderLayout.SOUTH);

		// Right: action buttons
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		actions.setOpaque(false);
		actions.add(primaryBtn("+ Add Rule", e -> openRuleDialog(null)));
		actions.add(primaryBtn("Save to Mod", e -> saveToMod()));
		actions.add(primaryBtn("← Back", e -> window.navigate(MainWindow.Page.ITEMS)));

		top.add(leftSide, BorderLayout.WEST);
		top.add(actions, BorderLayout.EAST);
		return top;
	}

	// ── Left: rule list ───────────────────────────────────────────────────────

	private JLabel styledMeta(String text) {
		JLabel l = new JLabel(text);
		l.setForeground(MainWindow.MUTED);
		l.setFont(new Font("Roboto", Font.PLAIN, 12));
		return l;
	}

	// ── Right: detail area ────────────────────────────────────────────────────

	private JSplitPane buildMainArea() {
		// Left column – rule list
		JPanel leftPanel = buildRuleListPanel();
		// Right area  – rule detail (selector tree + operation tree + XML preview)
		JSplitPane rightSplit = buildRuleDetailArea();

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightSplit);
		split.setDividerLocation(260);
		split.setDividerSize(4);
		split.setBorder(BorderFactory.createEmptyBorder());
		split.setBackground(MainWindow.BG);
		return split;
	}

	private JPanel buildRuleListPanel() {
		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.setBackground(MainWindow.SURFACE);
		panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER_COL, 1), BorderFactory.createEmptyBorder(12, 8, 8, 8)));

		JLabel title = new JLabel("Rules");
		title.setForeground(MainWindow.ACCENT);
		title.setFont(new Font("Roboto", Font.BOLD, 14));

		ruleList.setBackground(new Color(0x181825));
		ruleList.setForeground(MainWindow.TEXT);
		ruleList.setSelectionBackground(new Color(0x313244));
		ruleList.setFont(new Font("Roboto", Font.PLAIN, 12));
		ruleList.setFixedCellHeight(36);
		ruleList.setCellRenderer(new RuleListRenderer());

		ruleList.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) showRule(ruleList.getSelectedValue());
		});

		// Double-click → edit rule
		ruleList.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					StormRule sel = ruleList.getSelectedValue();
					if (sel != null) openRuleDialog(sel);
				}
			}
		});

		// Right-click context menu
		JPopupMenu ruleMenu = new JPopupMenu();
		JMenuItem editItem = new JMenuItem("✏  Edit Rule");
		JMenuItem deleteItem = new JMenuItem("🗑  Delete Rule");
		editItem.addActionListener(e -> {
			StormRule sel = ruleList.getSelectedValue();
			if (sel != null) openRuleDialog(sel);
		});
		deleteItem.addActionListener(e -> deleteSelectedRule());
		ruleMenu.add(editItem);
		ruleMenu.add(deleteItem);
		ruleList.setComponentPopupMenu(ruleMenu);

		JScrollPane scroll = new JScrollPane(ruleList);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getViewport().setBackground(new Color(0x181825));

		JButton addBtn = primaryBtn("+ New Rule", e -> openRuleDialog(null));

		panel.add(title, BorderLayout.NORTH);
		panel.add(scroll, BorderLayout.CENTER);
		panel.add(addBtn, BorderLayout.SOUTH);
		return panel;
	}

	private JSplitPane buildRuleDetailArea() {
		JPanel topDetail = buildRuleHeaderAndTrees();
		JScrollPane xmlPane = buildXmlPreview();

		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topDetail, xmlPane);
		split.setResizeWeight(0.65);
		split.setDividerSize(4);
		split.setBorder(BorderFactory.createEmptyBorder());
		split.setBackground(MainWindow.BG);
		return split;
	}

	// ── Tree helpers ──────────────────────────────────────────────────────────

	private JPanel buildRuleHeaderAndTrees() {
		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.setBackground(MainWindow.BG);
		panel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));

		// Rule name + comment header
		ruleNameLabel = new JLabel("(select a rule)");
		ruleNameLabel.setForeground(MainWindow.TEXT);
		ruleNameLabel.setFont(new Font("Roboto", Font.BOLD, 16));

		ruleCommentLabel = new JLabel(" ");
		ruleCommentLabel.setForeground(MainWindow.MUTED);
		ruleCommentLabel.setFont(new Font("Roboto", Font.ITALIC, 12));

		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setOpaque(false);
		header.add(ruleNameLabel);
		header.add(ruleCommentLabel);
		header.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

		// Trees side by side
		selectorTreeModel = buildPlaceholderTreeModel("Selectors");
		operationTreeModel = buildPlaceholderTreeModel("Operations");

		selectorTree = buildTree(selectorTreeModel);
		operationTree = buildTree(operationTreeModel);

		JPanel treeArea = new JPanel(new GridLayout(1, 2, 8, 0));
		treeArea.setOpaque(false);
		treeArea.add(treeCard("Conditions  (selector tree)", selectorTree));
		treeArea.add(treeCard("Operations", operationTree));

		panel.add(header, BorderLayout.NORTH);
		panel.add(treeArea, BorderLayout.CENTER);
		return panel;
	}

	private JScrollPane buildXmlPreview() {
		xmlPreviewPane = new JEditorPane();
		xmlPreviewPane.setContentType("text/html");
		xmlPreviewPane.setEditable(false);
		xmlPreviewPane.setBackground(new Color(0x11111b));
		xmlPreviewPane.setForeground(new Color(0xa6e3a1));
		xmlPreviewPane.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
		xmlPreviewPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
		xmlPreviewPane.setText(xmlPlaceholderHtml());

		JScrollPane sp = new JScrollPane(xmlPreviewPane);
		sp.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(BORDER_COL), "XML Preview", TitledBorder.LEFT, TitledBorder.TOP, new Font("Roboto", Font.BOLD, 11), MainWindow.ACCENT));
		sp.setBackground(MainWindow.SURFACE);
		return sp;
	}

	private JPanel treeCard(String title, JTree tree) {
		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(new Color(0x181825));
		card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER_COL, 1), BorderFactory.createEmptyBorder(8, 8, 8, 8)));

		JLabel lbl = new JLabel(title);
		lbl.setForeground(MainWindow.ACCENT);
		lbl.setFont(new Font("Roboto", Font.BOLD, 12));
		lbl.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

		JScrollPane sp = new JScrollPane(tree);
		sp.setBorder(BorderFactory.createEmptyBorder());
		sp.getViewport().setBackground(new Color(0x181825));

		card.add(lbl, BorderLayout.NORTH);
		card.add(sp, BorderLayout.CENTER);
		return card;
	}

	// =========================================================================
	//  Bottom bar
	// =========================================================================

	private JTree buildTree(DefaultTreeModel model) {
		JTree tree = new JTree(model);
		tree.setBackground(new Color(0x181825));
		tree.setForeground(MainWindow.TEXT);
		tree.setFont(new Font("JetBrains Mono", Font.PLAIN, 11));
		tree.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		tree.setRootVisible(true);
		tree.setShowsRootHandles(true);
		tree.setRowHeight(22);
		tree.putClientProperty("JTree.lineStyle", "Angled");
		tree.setCellRenderer(new StormTreeRenderer());
		// Expand all by default
		for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
		return tree;
	}

	// =========================================================================
	//  Data → UI
	// =========================================================================

	private DefaultTreeModel buildPlaceholderTreeModel(String rootLabel) {
		return new DefaultTreeModel(new DefaultMutableTreeNode(rootLabel));
	}

	private JPanel buildBottomBar() {
		JPanel bar = new JPanel(new BorderLayout());
		bar.setOpaque(false);
		bar.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

		statusLabel = new JLabel(" ");
		statusLabel.setForeground(new Color(0xa6e3a1));
		statusLabel.setFont(new Font("Roboto", Font.ITALIC, 11));

		bar.add(statusLabel, BorderLayout.WEST);
		return bar;
	}

	private void populateFromStorm() {
		ruleListModel.clear();
		clearRuleDetail();

		if (currentStorm == null) {
			breadcrumbLabel.setText("Storm  ›  (no file selected)");
			categoryLabel.setText("Category: —");
			fileIdLabel.setText("ID: —");
			setStatus("Select a Storm file from the Items page.", new Color(0x6c6f85));
			xmlPreviewPane.setText(xmlPlaceholderHtml());
			return;
		}

		breadcrumbLabel.setText("Storm  ›  " + currentStorm.getId());
		categoryLabel.setText("Category: " + (currentStorm.getCategory() != null ? currentStorm.getCategory() : "Miscellaneous"));
		fileIdLabel.setText("ID: " + currentStorm.getId());

		for (StormRule rule : currentStorm.getRules()) {
			ruleListModel.addElement(rule);
		}

		setStatus("Loaded " + currentStorm.getRules().size() + " rule(s)  |  " + currentStorm.getTasks().size() + " task(s)", new Color(0xa6e3a1));

		// Select first rule automatically
		if (!ruleListModel.isEmpty()) {
			ruleList.setSelectedIndex(0);
		}

		refreshXmlPreview();
	}

	private void showRule(StormRule rule) {
		if (rule == null) {
			clearRuleDetail();
			return;
		}

		ruleNameLabel.setText(rule.getName().isEmpty() ? "(unnamed)" : rule.getName());
		ruleCommentLabel.setText(rule.getComment().isEmpty() ? " " : "// " + rule.getComment());

		// Build selector tree
		DefaultMutableTreeNode selRoot = new DefaultMutableTreeNode("Conditions");
		for (GenericSelector sel : rule.getSelectors()) {
			selRoot.add(buildSelectorNode(sel));
		}
		selectorTreeModel.setRoot(selRoot);
		expandAll(selectorTree);

		// Build operation tree
		DefaultMutableTreeNode opRoot = new DefaultMutableTreeNode("Operations");
		for (GenericOperation op : rule.getOperations()) {
			opRoot.add(buildOperationNode(op));
		}
		operationTreeModel.setRoot(opRoot);
		expandAll(operationTree);

		refreshXmlPreview();
	}

	private DefaultMutableTreeNode buildSelectorNode(GenericSelector sel) {
		String display = formatSelectorLabel(sel);
		SelectorNode node = new SelectorNode(sel, display);
		for (GenericSelector child : sel.getChildren()) {
			node.add(buildSelectorNode(child));
		}
		return node;
	}

	private DefaultMutableTreeNode buildOperationNode(GenericOperation op) {
		String display = formatOperationLabel(op);
		OperationNode node = new OperationNode(op, display);
		for (GenericOperation child : op.getChildren()) {
			node.add(buildOperationNode(child));
		}
		return node;
	}

	private String formatSelectorLabel(GenericSelector sel) {
		if (sel.getName().isEmpty()) return "(unnamed selector)";
		StringBuilder sb = new StringBuilder("<").append(sel.getName()).append(">");
		if (!sel.getAttributes().isEmpty()) {
			sb.append("  ");
			sel.getAttributes().forEach((k, v) -> sb.append(k).append("=").append(v).append("  "));
		}
		return sb.toString().trim();
	}

	private String formatOperationLabel(GenericOperation op) {
		if (op.getName().isEmpty()) return "(unnamed operation)";
		StringBuilder sb = new StringBuilder(op.getName()).append("  ");
		op.getAttributes().forEach((k, v) -> sb.append(k).append("=").append(v).append("  "));
		return sb.toString().trim();
	}

	// =========================================================================
	//  XML preview
	// =========================================================================

	private void clearRuleDetail() {
		ruleNameLabel.setText("(select a rule)");
		ruleCommentLabel.setText(" ");
		selectorTreeModel.setRoot(new DefaultMutableTreeNode("Conditions"));
		operationTreeModel.setRoot(new DefaultMutableTreeNode("Operations"));
	}

	private void expandAll(JTree tree) {
		for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
	}

	private void refreshXmlPreview() {
		if (currentStorm == null) {
			xmlPreviewPane.setText(xmlPlaceholderHtml());
			return;
		}
		try {
			String xml = StormService.StormParser.serialize(currentStorm);
			xmlPreviewPane.setText(xmlToHtml(xml));
			xmlPreviewPane.setCaretPosition(0);
		} catch (Exception ex) {
			xmlPreviewPane.setText("<html><body style='background:#11111b;color:#f38ba8;font-family:monospace;padding:8px;'>" + "Serialization error: " + ex.getMessage() + "</body></html>");
		}
	}

	// =========================================================================
	//  Save
	// =========================================================================

	private void saveToMod() {
		if (currentStorm == null) {
			window.snackbar.show("No Storm file loaded", BarManager.Type.WARNING);
			return;
		}
		// Pick a mod
		List<ModData> mods = window.getRegistry().modService.modCollection;
		if (mods.isEmpty()) {
			window.snackbar.show("No mods available — create a mod first", BarManager.Type.WARNING);
			return;
		}

		String[] modNames = mods.stream().map(m -> m.id + " | " + m.name).toArray(String[]::new);
		String choice = (String) JOptionPane.showInputDialog(this, "Select target mod:", "Save Storm File to Mod", JOptionPane.PLAIN_MESSAGE, null, modNames, modNames[0]);
		if (choice == null) return;

		String modId = choice.split(" \\| ")[0];
		mods.stream().filter(m -> m.id.equals(modId)).findFirst().ifPresent(mod -> {
			String gameDir = window.getRegistry().userConfig.gameDirectory;
			boolean ok = StormService.writeStormFile(gameDir, modId, currentStorm);
			if (ok) window.snackbar.show("Storm file saved to mod: " + mod.name, BarManager.Type.SUCCESS);
			else window.snackbar.show("Failed to save Storm file", BarManager.Type.ERROR);
		});
	}

	// =========================================================================
	//  Delete rule
	// =========================================================================

	private void deleteSelectedRule() {
		if (currentStorm == null) return;
		StormRule sel = ruleList.getSelectedValue();
		if (sel == null) return;

		int confirm = JOptionPane.showConfirmDialog(this, "Delete rule '" + sel.getName() + "'?", "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (confirm != JOptionPane.YES_OPTION) return;

		currentStorm.getRules().remove(sel);
		ruleListModel.removeElement(sel);
		clearRuleDetail();
		refreshXmlPreview();
		setStatus("Rule deleted.", MainWindow.ACCENT);
	}

	// =========================================================================
	//  Rule dialog (3-step wizard: Name → Selectors → Operations)
	// =========================================================================

	/**
	 * Opens a wizard to create a new rule or edit an existing one.
	 */
	private void openRuleDialog(StormRule existingRule) {
		if (currentStorm == null) {
			window.snackbar.show("Open a Storm file first", BarManager.Type.WARNING);
			return;
		}
		RuleWizardDialog dlg = new RuleWizardDialog((Frame) SwingUtilities.getWindowAncestor(this), existingRule, window.getRegistry().stormService);

		dlg.setVisible(true);
		StormRule result = dlg.getResult();
		if (result == null) return;

		if (existingRule == null) {
			// New rule
			currentStorm.getRules().add(result);
			ruleListModel.addElement(result);
			ruleList.setSelectedValue(result, true);
			setStatus("Rule '" + result.getName() + "' added.", new Color(0xa6e3a1));
		} else {
			// In-place edit — the rule object was mutated by the wizard
			ruleList.repaint();
			showRule(result);
			setStatus("Rule '" + result.getName() + "' updated.", new Color(0xa6e3a1));
		}
		refreshXmlPreview();
	}

	// =========================================================================
	//  Status
	// =========================================================================

	private void setStatus(String text, Color color) {
		statusLabel.setText(text);
		statusLabel.setForeground(color);
	}

	// =========================================================================
	//  Custom tree node types (carry the model object for the renderer)
	// =========================================================================

	static final class SelectorNode extends DefaultMutableTreeNode {
		final GenericSelector selector;

		SelectorNode(GenericSelector sel, String display) {
			super(display);
			this.selector = sel;
		}
	}

	static final class OperationNode extends DefaultMutableTreeNode {
		final GenericOperation operation;

		OperationNode(GenericOperation op, String display) {
			super(op.getName().isEmpty() ? "(unnamed)" : op.getName());
			this.operation = op;
		}
	}

	// =========================================================================
	//  Custom renderers
	// =========================================================================

	/**
	 * Colours rule names in the left list
	 */
	private static final class RuleListRenderer extends DefaultListCellRenderer {
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object val, int idx, boolean sel, boolean focus) {
			super.getListCellRendererComponent(list, val, idx, sel, focus);
			setBackground(sel ? new Color(0x313244) : new Color(0x181825));
			setForeground(sel ? MainWindow.ACCENT : MainWindow.TEXT);
			setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
			setFont(new Font("Roboto", Font.PLAIN, 12));
			if (val instanceof StormRule rule) {
				String name = rule.getName().isEmpty() ? "(unnamed rule)" : rule.getName();
				setText("⚡  " + name + (rule.getComment().isEmpty() ? "" : "  //  " + rule.getComment()));
			}
			return this;
		}
	}

	/**
	 * Colours selector/operation nodes by type
	 */
	private static final class StormTreeRenderer extends DefaultTreeCellRenderer {
		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object val, boolean sel, boolean exp, boolean leaf, int row, boolean focus) {
			super.getTreeCellRendererComponent(tree, val, sel, exp, leaf, row, focus);
			setBackground(sel ? new Color(0x313244) : new Color(0x181825));
			setBackgroundNonSelectionColor(new Color(0x181825));
			setBackgroundSelectionColor(new Color(0x313244));
			setBorderSelectionColor(null);
			setFont(new Font("JetBrains Mono", Font.PLAIN, 11));

			if (val instanceof SelectorNode sn) {
				String name = sn.selector.getName().toLowerCase(Locale.ROOT);
				setForeground(switch (name) {
					case "and" -> COMBINATOR_AND;
					case "or" -> COMBINATOR_OR;
					case "not" -> COMBINATOR_NOT;
					default -> SELECTOR_FG;
				});
			} else if (val instanceof OperationNode) {
				setForeground(OPERATION_FG);
			} else {
				setForeground(MainWindow.MUTED);
			}
			return this;
		}
	}

	// =========================================================================
	//  INNER CLASS: Rule Wizard Dialog  (3 steps)
	// =========================================================================

	static final class RuleWizardDialog extends JDialog {

		private static final String[] STEP_TITLES = {"Step 1 — Name & Comment", "Step 2 — Selectors  (conditions)", "Step 3 — Operations"};

		private final StormService stormService;
		private final JLabel stepLabel = new JLabel();
		// Step panels
		private final JPanel stepContainer = new JPanel(new CardLayout());
		private final StormRule workingRule; // the rule being built / edited
		private StormRule result = null;
		// Step indicator
		private int currentStep = 0;
		// Step 1 components
		private JTextField nameField;
		private JTextField commentField;
		private JLabel nameError;

		// Step 2 – selector editor
		private JPanel selectorContainer;

		// Step 3 – operation editor
		private JPanel operationContainer;

		// Nav buttons
		private JButton backBtn;
		private JButton nextBtn;

		RuleWizardDialog(Frame owner, StormRule existing, StormService stormService) {
			super(owner, existing == null ? "New Rule" : "Edit Rule — " + existing.getName(), true);
			this.stormService = stormService;

			// Clone or create working rule
			workingRule = new StormRule();
			if (existing != null) {
				workingRule.setName(existing.getName());
				workingRule.setComment(existing.getComment());
				for (var s : existing.getSelectors()) workingRule.getSelectors().add(s.deepCopy());
				for (var o : existing.getOperations()) workingRule.getOperations().add(o.deepCopy());
			}

			setSize(820, 640);
			setLocationRelativeTo(owner);
			setDefaultCloseOperation(DISPOSE_ON_CLOSE);
			setBackground(SURFACE2);
			setLayout(new BorderLayout());

			buildDialog();

			// If editing an existing rule, skip straight to step 1 populated
			if (existing != null) {
				nameField.setText(existing.getName());
				commentField.setText(existing.getComment());
			}
			updateStep();
		}

		// ── Dialog construction ───────────────────────────────────────────

		private static JPanel stepPanel() {
			JPanel p = new JPanel(new GridBagLayout());
			p.setBackground(SURFACE2);
			p.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
			return p;
		}

		private static void addRow(JPanel p, String label, JComponent field, int row) {
			GridBagConstraints lc = new GridBagConstraints();
			lc.gridx = 0;
			lc.gridy = row;
			lc.weightx = 0.2;
			lc.anchor = GridBagConstraints.EAST;
			lc.insets = new Insets(8, 4, 8, 12);
			JLabel lbl = new JLabel(label);
			lbl.setForeground(MainWindow.TEXT);
			lbl.setFont(new Font("Roboto", Font.PLAIN, 13));
			p.add(lbl, lc);

			GridBagConstraints fc = new GridBagConstraints();
			fc.gridx = 1;
			fc.gridy = row;
			fc.weightx = 0.8;
			fc.fill = GridBagConstraints.HORIZONTAL;
			fc.insets = new Insets(8, 0, 8, 4);
			p.add(field, fc);
		}

		// ── Step 1: Name & Comment ────────────────────────────────────────

		private static JTextField dialogField(String placeholder) {
			JTextField f = new JTextField();
			styleField(f);
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

		// ── Step 2: Selectors ─────────────────────────────────────────────

		private static JTextField dialogField(int cols) {
			JTextField f = new JTextField(cols);
			styleField(f);
			return f;
		}

		// ── Step 3: Operations ────────────────────────────────────────────

		private static void styleField(JTextField f) {
			f.setBackground(INPUT_BG);
			f.setForeground(MainWindow.TEXT);
			f.setCaretColor(MainWindow.TEXT);
			f.setFont(new Font("Roboto", Font.PLAIN, 12));
			f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER_COL), BorderFactory.createEmptyBorder(5, 8, 5, 8)));
		}

		// ── Step navigation ───────────────────────────────────────────────

		private static JButton btn(String text, ActionListener a) {
			JButton b = new JButton(text);
			b.setFocusPainted(false);
			b.setBorderPainted(false);
			b.setFont(new Font("Roboto", Font.BOLD, 12));
			b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			b.setBackground(new Color(0x313244));
			b.setForeground(MainWindow.TEXT);
			b.addActionListener(a);
			return b;
		}

		private static JButton miniBtn(String text, ActionListener a) {
			JButton b = btn(text, a);
			b.setFont(new Font("Roboto", Font.PLAIN, 10));
			b.setBackground(new Color(0x45475a));
			b.setPreferredSize(new Dimension(0, 22));
			return b;
		}

		/**
		 * A DocumentListener that runs a single Runnable on any change.
		 */
		private static javax.swing.event.DocumentListener simpleListener(Runnable r) {
			return new javax.swing.event.DocumentListener() {
				public void insertUpdate(javax.swing.event.DocumentEvent e) {
					r.run();
				}

				public void removeUpdate(javax.swing.event.DocumentEvent e) {
					r.run();
				}

				public void changedUpdate(javax.swing.event.DocumentEvent e) {
					r.run();
				}
			};
		}

		// ── Step validators ───────────────────────────────────────────────

		private void buildDialog() {
			// ── Header ────────────────────────────────────────────────────
			JPanel header = new JPanel(new BorderLayout());
			header.setBackground(new Color(0x11111b));
			header.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 20));

			stepLabel.setFont(new Font("Roboto", Font.BOLD, 15));
			stepLabel.setForeground(MainWindow.ACCENT);

			JPanel stepsRow = buildStepIndicatorBar();

			header.add(stepLabel, BorderLayout.NORTH);
			header.add(stepsRow, BorderLayout.SOUTH);

			// ── Step panels ───────────────────────────────────────────────
			stepContainer.setBackground(SURFACE2);
			stepContainer.add(buildStep1(), "0");
			stepContainer.add(buildStep2(), "1");
			stepContainer.add(buildStep3(), "2");

			// ── Bottom nav ────────────────────────────────────────────────
			JPanel nav = new JPanel(new BorderLayout());
			nav.setBackground(new Color(0x11111b));
			nav.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

			backBtn = btn("← Back", e -> goBack());
			nextBtn = btn("Next →", e -> goNext());
			backBtn.setBackground(new Color(0x45475a));
			nextBtn.setBackground(MainWindow.ACCENT);
			nextBtn.setForeground(new Color(0x1e1e2e));

			JButton cancelBtn = btn("Cancel", e -> dispose());
			cancelBtn.setBackground(MainWindow.DANGER);
			cancelBtn.setForeground(new Color(0x1e1e2e));

			JPanel leftNav = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
			leftNav.setOpaque(false);
			leftNav.add(cancelBtn);

			JPanel rightNav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
			rightNav.setOpaque(false);
			rightNav.add(backBtn);
			rightNav.add(nextBtn);

			nav.add(leftNav, BorderLayout.WEST);
			nav.add(rightNav, BorderLayout.EAST);

			add(header, BorderLayout.NORTH);
			add(stepContainer, BorderLayout.CENTER);
			add(nav, BorderLayout.SOUTH);
		}

		private JPanel buildStepIndicatorBar() {
			JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
			row.setOpaque(false);
			for (int i = 0; i < STEP_TITLES.length; i++) {
				final int idx = i;
				JLabel dot = new JLabel("●");
				dot.setName("step_dot_" + i);
				dot.setForeground(i == 0 ? MainWindow.ACCENT : MainWindow.MUTED);
				dot.setFont(new Font("Roboto", Font.PLAIN, 10));
				row.add(dot);
				JLabel lbl = new JLabel(STEP_TITLES[i].split("—")[1].trim());
				lbl.setName("step_lbl_" + i);
				lbl.setForeground(i == 0 ? MainWindow.TEXT : MainWindow.MUTED);
				lbl.setFont(new Font("Roboto", Font.PLAIN, 11));
				row.add(lbl);
				if (i < STEP_TITLES.length - 1) {
					JLabel arrow = new JLabel("›");
					arrow.setForeground(MainWindow.MUTED);
					row.add(arrow);
				}
			}
			return row;
		}

		private JPanel buildStep1() {
			JPanel p = stepPanel();

			nameField = dialogField("e.g. attack_enemy_on_sight");
			commentField = dialogField("Optional description");
			nameError = new JLabel(" ");
			nameError.setForeground(MainWindow.DANGER);
			nameError.setFont(new Font("Roboto", Font.ITALIC, 11));

			addRow(p, "Rule Name *", nameField, 0);
			addRow(p, "Comment", commentField, 1);

			GridBagConstraints gc = new GridBagConstraints();
			gc.gridx = 1;
			gc.gridy = 2;
			gc.weightx = 1;
			gc.fill = GridBagConstraints.HORIZONTAL;
			gc.insets = new Insets(0, 0, 0, 0);
			p.add(nameError, gc);

			return p;
		}

		private JPanel buildStep2() {
			JPanel p = new JPanel(new BorderLayout(0, 8));
			p.setBackground(SURFACE2);
			p.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

			JLabel hint = new JLabel("Build the condition tree. Combinators (and/or/not) can hold child selectors.");
			hint.setForeground(MainWindow.MUTED);
			hint.setFont(new Font("Roboto", Font.ITALIC, 12));

			selectorContainer = new JPanel();
			selectorContainer.setLayout(new BoxLayout(selectorContainer, BoxLayout.Y_AXIS));
			selectorContainer.setBackground(new Color(0x181825));
			selectorContainer.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

			JScrollPane scroll = new JScrollPane(selectorContainer);
			scroll.setBorder(BorderFactory.createLineBorder(BORDER_COL));
			scroll.getViewport().setBackground(new Color(0x181825));

			JPanel addBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
			addBar.setOpaque(false);
			addBar.add(new JLabel("Add:") {{
				setForeground(MainWindow.MUTED);
				setFont(new Font("Roboto", Font.PLAIN, 12));
			}});
			addBar.add(addSelectorBtn("selector", "⊞ selector", SELECTOR_FG));
			addBar.add(addSelectorBtn("and", "⊕ and", COMBINATOR_AND));
			addBar.add(addSelectorBtn("or", "⊕ or", COMBINATOR_OR));
			addBar.add(addSelectorBtn("not", "⊕ not", COMBINATOR_NOT));

			p.add(hint, BorderLayout.NORTH);
			p.add(scroll, BorderLayout.CENTER);
			p.add(addBar, BorderLayout.SOUTH);
			return p;
		}

		// ── Selector rows (step 2) ────────────────────────────────────────

		private JPanel buildStep3() {
			JPanel p = new JPanel(new BorderLayout(0, 8));
			p.setBackground(SURFACE2);
			p.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

			JLabel hint = new JLabel("Add operations that run when this rule fires. Operations may have child operations.");
			hint.setForeground(MainWindow.MUTED);
			hint.setFont(new Font("Roboto", Font.ITALIC, 12));

			operationContainer = new JPanel();
			operationContainer.setLayout(new BoxLayout(operationContainer, BoxLayout.Y_AXIS));
			operationContainer.setBackground(new Color(0x181825));
			operationContainer.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

			JScrollPane scroll = new JScrollPane(operationContainer);
			scroll.setBorder(BorderFactory.createLineBorder(BORDER_COL));
			scroll.getViewport().setBackground(new Color(0x181825));

			JButton addOpBtn = btn("+ Add Operation", e -> {
				GenericOperation op = new GenericOperation();
				workingRule.getOperations().add(op);
				operationContainer.add(buildOperationRow(op, workingRule.getOperations(), operationContainer));
				operationContainer.revalidate();
				operationContainer.repaint();
			});
			addOpBtn.setBackground(MainWindow.ACCENT);
			addOpBtn.setForeground(new Color(0x1e1e2e));

			JPanel addBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
			addBar.setOpaque(false);
			addBar.add(addOpBtn);

			p.add(hint, BorderLayout.NORTH);
			p.add(scroll, BorderLayout.CENTER);
			p.add(addBar, BorderLayout.SOUTH);
			return p;
		}

		private void updateStep() {
			CardLayout cl = (CardLayout) stepContainer.getLayout();
			cl.show(stepContainer, String.valueOf(currentStep));

			stepLabel.setText(STEP_TITLES[currentStep]);
			backBtn.setEnabled(currentStep > 0);

			boolean isLast = currentStep == STEP_TITLES.length - 1;
			nextBtn.setText(isLast ? "✓ Save Rule" : "Next →");

			// Populate step 2/3 when entering them
			if (currentStep == 1) repopulateSelectorContainer();
			if (currentStep == 2) repopulateOperationContainer();

			// Update step indicator dots
			Container header = (Container) getContentPane().getComponent(0);
			if (header.getComponentCount() > 1) {
				Container stepsRow = (Container) header.getComponent(1);
				int dotIdx = 0;
				for (Component c : stepsRow.getComponents()) {
					if (c instanceof JLabel lbl && lbl.getName() != null) {
						if (lbl.getName().startsWith("step_dot_")) {
							lbl.setForeground(dotIdx == currentStep ? MainWindow.ACCENT : MainWindow.MUTED);
							dotIdx++;
						}
					}
				}
			}
		}

		private void goNext() {
			if (currentStep == 0 && !validateStep1()) return;
			if (currentStep == 1 && !validateStep2()) return;

			if (currentStep == STEP_TITLES.length - 1) {
				// Final step — validate & commit
				if (!validateStep3()) return;
				applyStep1();
				result = workingRule;
				dispose();
			} else {
				if (currentStep == 0) applyStep1(); // commit name/comment immediately
				currentStep++;
				updateStep();
			}
		}

		// ── Operation rows (step 3) ───────────────────────────────────────

		private void goBack() {
			if (currentStep > 0) {
				currentStep--;
				updateStep();
			}
		}

		private boolean validateStep1() {
			String name = nameField.getText().trim();
			if (name.isBlank()) {
				nameError.setText("⚠  Rule name cannot be empty");
				return false;
			}
			if (!name.matches("[a-zA-Z][a-zA-Z0-9_\\s]*")) {
				nameError.setText("⚠  Only letters, digits, underscores and spaces (must start with a letter)");
				return false;
			}
			nameError.setText(" ");
			return true;
		}

		// ── Attribute editor popup ────────────────────────────────────────

		private boolean validateStep2() {
			if (workingRule.getSelectors().isEmpty()) {
				JOptionPane.showMessageDialog(this, "Thou art missing a selector, milord. Add at least one.", "Missing Selector", JOptionPane.WARNING_MESSAGE);
				return false;
			}
			for (GenericSelector sel : workingRule.getSelectors()) {
				if (sel.getName().isBlank()) {
					JOptionPane.showMessageDialog(this, "A nameless selector lurketh among thy ranks!", "Unnamed Selector", JOptionPane.WARNING_MESSAGE);
					return false;
				}
			}
			return true;
		}

		private boolean validateStep3() {
			if (workingRule.getOperations().isEmpty()) {
				JOptionPane.showMessageDialog(this, "Without an operation, this rule is but an empty scroll!", "Missing Operation", JOptionPane.WARNING_MESSAGE);
				return false;
			}
			for (GenericOperation op : workingRule.getOperations()) {
				if (op.getName().isBlank()) {
					JOptionPane.showMessageDialog(this, "An operation has no name. Fill it in before saving.", "Unnamed Operation", JOptionPane.WARNING_MESSAGE);
					return false;
				}
			}
			return true;
		}

		// ── Utilities ─────────────────────────────────────────────────────

		private void applyStep1() {
			workingRule.setName(nameField.getText().trim().replace(" ", "_").toLowerCase(Locale.ROOT));
			workingRule.setComment(commentField.getText().trim());
		}

		private void repopulateSelectorContainer() {
			selectorContainer.removeAll();
			for (GenericSelector sel : workingRule.getSelectors()) {
				selectorContainer.add(buildSelectorRow(sel, workingRule.getSelectors(), selectorContainer, 0));
			}
			selectorContainer.revalidate();
			selectorContainer.repaint();
		}

		private JButton addSelectorBtn(String type, String label, Color fg) {
			JButton b = btn(label, e -> {
				GenericSelector sel = new GenericSelector(type);
				if (type.equals("and") || type.equals("or") || type.equals("not")) {
					sel.getChildren().add(new GenericSelector());
				}
				workingRule.getSelectors().add(sel);
				selectorContainer.add(buildSelectorRow(sel, workingRule.getSelectors(), selectorContainer, 0));
				selectorContainer.revalidate();
				selectorContainer.repaint();
			});
			b.setForeground(fg);
			b.setBackground(new Color(0x313244));
			return b;
		}

		/**
		 * Build a UI row for one GenericSelector at a given indent depth.
		 * Combinators recursively render their children below, indented.
		 */
		private JPanel buildSelectorRow(GenericSelector sel, List<GenericSelector> parentList, JPanel parentContainer, int depth) {
			JPanel outer = new JPanel();
			outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
			outer.setOpaque(false);

			// Row itself
			JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
			row.setOpaque(false);
			row.setBorder(BorderFactory.createEmptyBorder(0, depth * 20, 0, 0));

			// Name field / combinator label
			boolean isCombinator = sel.isCombinator();
			if (isCombinator) {
				JLabel tag = new JLabel("<" + sel.getName() + ">");
				tag.setForeground(switch (sel.getName()) {
					case "and" -> COMBINATOR_AND;
					case "or" -> COMBINATOR_OR;
					default -> COMBINATOR_NOT;
				});
				tag.setFont(new Font("JetBrains Mono", Font.BOLD, 12));
				row.add(tag);
			} else {
				JTextField nameF = dialogField(16);
				nameF.setText(sel.getName());
				nameF.setToolTipText("Selector name");
				nameF.getDocument().addDocumentListener(simpleListener(() -> sel.setName(nameF.getText().trim())));
				row.add(nameF);

				// Attribute editor button
				JButton attrBtn = miniBtn("attrs (" + sel.getAttributes().size() + ")", e -> openAttributeEditor(sel.getAttributes(), "Selector Attributes", () -> {
					// refresh nothing — just in-place
				}));
				row.add(attrBtn);
			}

			// Delete button
			JButton delBtn = miniBtn("✕", e -> {
				parentList.remove(sel);
				parentContainer.remove(outer);
				parentContainer.revalidate();
				parentContainer.repaint();
			});
			delBtn.setForeground(MainWindow.DANGER);
			row.add(delBtn);

			outer.add(row);

			// Children panel (indented)
			if (isCombinator) {
				JPanel childrenPanel = new JPanel();
				childrenPanel.setLayout(new BoxLayout(childrenPanel, BoxLayout.Y_AXIS));
				childrenPanel.setOpaque(false);

				for (GenericSelector child : sel.getChildren()) {
					childrenPanel.add(buildSelectorRow(child, sel.getChildren(), childrenPanel, depth + 1));
				}
				outer.add(childrenPanel);

				// Add-child bar
				JPanel addChildBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
				addChildBar.setOpaque(false);
				addChildBar.setBorder(BorderFactory.createEmptyBorder(0, (depth + 1) * 20, 0, 0));

				JLabel addLbl = new JLabel("add child:");
				addLbl.setForeground(MainWindow.MUTED);
				addLbl.setFont(new Font("Roboto", Font.PLAIN, 10));
				addChildBar.add(addLbl);

				for (String type : new String[]{"selector", "and", "or", "not"}) {
					Color fg2 = switch (type) {
						case "and" -> COMBINATOR_AND;
						case "or" -> COMBINATOR_OR;
						case "not" -> COMBINATOR_NOT;
						default -> SELECTOR_FG;
					};
					JButton childAddBtn = miniBtn("+" + type, e -> {
						GenericSelector child = new GenericSelector(type);
						if (!type.equals("selector")) child.getChildren().add(new GenericSelector());
						sel.getChildren().add(child);
						childrenPanel.add(buildSelectorRow(child, sel.getChildren(), childrenPanel, depth + 1));
						childrenPanel.revalidate();
						childrenPanel.repaint();
					});
					childAddBtn.setForeground(fg2);
					addChildBar.add(childAddBtn);
				}
				outer.add(addChildBar);
			}

			return outer;
		}

		private void repopulateOperationContainer() {
			operationContainer.removeAll();
			for (GenericOperation op : workingRule.getOperations()) {
				operationContainer.add(buildOperationRow(op, workingRule.getOperations(), operationContainer));
			}
			operationContainer.revalidate();
			operationContainer.repaint();
		}

		private JPanel buildOperationRow(GenericOperation op, List<GenericOperation> parentList, JPanel parentContainer) {
			JPanel outer = new JPanel();
			outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
			outer.setOpaque(false);
			outer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, OPERATION_FG), BorderFactory.createEmptyBorder(4, 8, 4, 4)));

			JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
			row.setOpaque(false);

			JTextField nameF = dialogField(20);
			nameF.setText(op.getName());
			nameF.setForeground(OPERATION_FG);
			nameF.setToolTipText("Operation name");
			nameF.getDocument().addDocumentListener(simpleListener(() -> op.setName(nameF.getText().trim())));
			row.add(nameF);

			JButton attrBtn = miniBtn("attrs (" + op.getAttributes().size() + ")", e -> openAttributeEditor(op.getAttributes(), "Operation Attributes", () -> {
			}));
			row.add(attrBtn);

			JButton addChildBtn = miniBtn("+ child op", e -> {
				GenericOperation child = new GenericOperation();
				op.getChildren().add(child);
				// Add child after the row separator
				outer.add(buildOperationRow(child, op.getChildren(), outer));
				outer.revalidate();
				outer.repaint();
			});
			addChildBtn.setForeground(OPERATION_FG);
			row.add(addChildBtn);

			JButton delBtn = miniBtn("✕", e -> {
				parentList.remove(op);
				parentContainer.remove(outer);
				parentContainer.revalidate();
				parentContainer.repaint();
			});
			delBtn.setForeground(MainWindow.DANGER);
			row.add(delBtn);

			outer.add(row);
			return outer;
		}

		private void openAttributeEditor(java.util.Map<String, String> attrs, String title, Runnable onClose) {
			JDialog attrDialog = new JDialog(this, title, true);
			attrDialog.setSize(480, 400);
			attrDialog.setLocationRelativeTo(this);
			attrDialog.setLayout(new BorderLayout(0, 8));
			attrDialog.getContentPane().setBackground(SURFACE2);

			JPanel attrList = new JPanel();
			attrList.setLayout(new BoxLayout(attrList, BoxLayout.Y_AXIS));
			attrList.setBackground(new Color(0x181825));
			attrList.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

			// Build a row per existing attribute
			List<JTextField> keyFields = new ArrayList<>();
			List<JTextField> valFields = new ArrayList<>();

			Runnable buildRows = () -> {
				attrList.removeAll();
				keyFields.clear();
				valFields.clear();
				for (var entry : attrs.entrySet()) {
					addAttrRow(attrList, keyFields, valFields, entry.getKey(), entry.getValue());
				}
				attrList.revalidate();
				attrList.repaint();
			};
			buildRows.run();

			JScrollPane scroll = new JScrollPane(attrList);
			scroll.setBorder(BorderFactory.createEmptyBorder());
			scroll.getViewport().setBackground(new Color(0x181825));

			JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
			btnBar.setOpaque(false);

			JButton addRow = btn("+ Add Attribute", e -> {
				addAttrRow(attrList, keyFields, valFields, "", "");
				attrList.revalidate();
				attrList.repaint();
			});
			addRow.setBackground(new Color(0x313244));
			addRow.setForeground(MainWindow.TEXT);

			JButton saveClose = btn("Save & Close", e -> {
				// Commit key/value pairs back to the map
				attrs.clear();
				for (int i = 0; i < keyFields.size(); i++) {
					String k = keyFields.get(i).getText().trim();
					String v = valFields.get(i).getText().trim();
					if (!k.isEmpty()) attrs.put(k, v);
				}
				onClose.run();
				attrDialog.dispose();
			});
			saveClose.setBackground(MainWindow.ACCENT);
			saveClose.setForeground(new Color(0x1e1e2e));

			btnBar.add(addRow);
			btnBar.add(saveClose);

			attrDialog.add(scroll, BorderLayout.CENTER);
			attrDialog.add(btnBar, BorderLayout.SOUTH);
			attrDialog.setVisible(true);
		}

		private void addAttrRow(JPanel container, List<JTextField> keys, List<JTextField> vals, String key, String value) {
			JPanel row = new JPanel(new BorderLayout(6, 0));
			row.setOpaque(false);
			row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

			JTextField kf = dialogField(14);
			kf.setText(key);
			kf.setForeground(ATTR_KEY);
			JTextField vf = dialogField(20);
			vf.setText(value);

			JButton del = miniBtn("✕", e -> {
				container.remove(row);
				keys.remove(kf);
				vals.remove(vf);
				container.revalidate();
				container.repaint();
			});
			del.setForeground(MainWindow.DANGER);

			row.add(kf, BorderLayout.WEST);
			row.add(vf, BorderLayout.CENTER);
			row.add(del, BorderLayout.EAST);

			container.add(row);
			keys.add(kf);
			vals.add(vf);
		}

		StormRule getResult() {
			return result;
		}
	}
}