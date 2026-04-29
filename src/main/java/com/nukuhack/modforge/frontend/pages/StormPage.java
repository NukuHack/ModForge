package com.nukuhack.modforge.frontend.pages;

import com.nukuhack.modforge.backend.ModData;
import com.nukuhack.modforge.backend.model.Attribute;
import com.nukuhack.modforge.backend.model.Attribute.XmlNode;
import com.nukuhack.modforge.backend.model.Storm;
import com.nukuhack.modforge.backend.model.Storm.StormRule;
import com.nukuhack.modforge.backend.service.ModService;
import com.nukuhack.modforge.backend.service.StormService;
import com.nukuhack.modforge.frontend.BarManager;
import com.nukuhack.modforge.frontend.MainWindow;
import com.nukuhack.modforge.frontend.Page;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@lombok.extern.slf4j.Slf4j
public class StormPage extends BasePage {

    private static final Color BG = new Color(0x0d0d14);
    private static final Color SURFACE = new Color(0x13131f);
    private static final Color SURFACE2 = new Color(0x1a1a2b);
    private static final Color SURFACE3 = new Color(0x22223a);
    private static final Color BORDER = new Color(0x2e2e4a);
    private static final Color ACCENT = new Color(0x7c6af7);

    private static final Color ACCENT2 = new Color(0x56cfe1);

    private static final Color TEXT = new Color(0xe0e0f5);
    private static final Color MUTED = new Color(0x6b6b8f);
    private static final Color DANGER = new Color(0xf05c6e);
    private static final Color SUCCESS = new Color(0x56d994);

    private static final Color C_AND = new Color(0x7c6af7);
    private static final Color C_OR = new Color(0x56cfe1);
    private static final Color C_NOT = new Color(0xf05c6e);
    private static final Color C_SELECTOR = new Color(0xc9b8ff);
    private static final Color C_OPERATION = new Color(0xffd580);
    private static final Color C_ATTR_KEY = new Color(0x56cfe1);
    private static final Color C_ATTR_VAL = new Color(0x9effd6);

    private static final Set<String> COMBINATORS = Set.of("and", "or", "not");

    private static final Font FONT_TITLE = new Font("SansSerif", Font.BOLD, 20);
    private static final Font FONT_HEADER = new Font("SansSerif", Font.BOLD, 13);
    private static final Font FONT_BODY = new Font("SansSerif", Font.PLAIN, 12);
    private static final Font FONT_MONO = new Font("Monospaced", Font.PLAIN, 11);
    private static final Font FONT_SMALL = new Font("SansSerif", Font.PLAIN, 11);
    private static final Font FONT_BADGE = new Font("SansSerif", Font.BOLD, 10);
    private final DefaultListModel<StormRule> ruleListModel = new DefaultListModel<>();
    private final JList<StormRule> ruleList = new JList<>(ruleListModel);
    /**
     * The Storm ModItem currently being viewed/edited (might be null).
     */
    private Storm currentStorm;
    private ModData currentMod;
    private JLabel ruleNameLabel;
    private JLabel ruleMetaLabel;
    private DefaultTreeModel selectorTreeModel;
    private JTree selectorTree;
    private DefaultTreeModel operationTreeModel;
    private JTree operationTree;
    private JEditorPane xmlPreviewPane;

    private JLabel breadcrumbLabel;
    private JLabel categoryLabel;
    private JLabel statusLabel;

    private JLabel rulesCountLabel;
    private JLabel tasksCountLabel;
    private JLabel selectorsCountLabel;
    private JLabel operationsCountLabel;

    public StormPage(MainWindow w) {
        super(w);
        setBackground(BG);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        setLayout(new BorderLayout(0, 14));

        add(buildTopBar(), BorderLayout.NORTH);
        add(buildMainArea(), BorderLayout.CENTER);
        add(buildBottomBar(), BorderLayout.SOUTH);
    }

    private static String xmlToHtml(String xml) {

        String e = xml.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");

        e = e.replaceAll("(&lt;/?)([a-zA-Z_][a-zA-Z0-9_:.-]*)", "$1<font color='#7c6af7'>$2</font>");

        e = e.replaceAll(" ([a-zA-Z_][a-zA-Z0-9_]*)=&quot;", " <font color='#56cfe1'>$1</font>=<font color='#9effd6'>&quot;");

        e = e.replaceAll("&quot;(?=[^=])", "&quot;</font>");
        return "<html><body style='background:#080810;color:#c0c0e0;"
                + "font-family:monospace;font-size:11px;padding:10px;"
                + "white-space:pre;line-height:1.5;'>" + e + "</body></html>";
    }

    private static String xmlPlaceholderHtml() {
        return "<html><body style='background:#080810;color:#3a3a5c;"
                + "font-family:monospace;padding:14px;font-size:11px;'>"
                + "<i>Open a Storm item from the Items page to see XML here.</i>"
                + "</body></html>";
    }

    private static boolean isCombinator(String tag) {
        return COMBINATORS.contains(tag.toLowerCase(Locale.ROOT));
    }

    private static int countNodes(List<XmlNode> nodes) {
        int count = nodes.size();
        for (XmlNode n : nodes) count += countNodes(n.children());
        return count;
    }

    protected static JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(MUTED);
        l.setFont(FONT_SMALL);
        return l;
    }

    private static JLabel statBadge(String text, Color color) {
        JLabel l = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color.darker().darker());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        l.setForeground(color);
        l.setFont(FONT_BADGE);
        l.setBorder(BorderFactory.createEmptyBorder(3, 9, 3, 9));
        l.setOpaque(false);
        return l;
    }

    private static JButton accentBtn(String text, ActionListener a) {
        JButton b = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isPressed() ? ACCENT.darker() : getModel().isRollover() ? ACCENT.brighter() : ACCENT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setForeground(new Color(0x0d0d14));
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
        b.addActionListener(a);
        return b;
    }

    private static JButton ghostBtn(String text, ActionListener a) {
        JButton b = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? SURFACE3 : SURFACE2);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setForeground(TEXT);
        b.setFont(FONT_BODY);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
        b.addActionListener(a);
        return b;
    }

    private static JButton dangerBtn(String text, ActionListener a) {
        JButton b = ghostBtn(text, a);
        b.setForeground(DANGER);
        return b;
    }

    private static JButton miniBtn(String text, Color fg, ActionListener a) {
        JButton b = new JButton(text);
        b.setFont(FONT_BADGE);
        b.setForeground(fg);
        b.setBackground(SURFACE3);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(2, 7, 2, 7));
        b.addActionListener(a);
        return b;
    }

    private static JMenuItem styledMenuItem(String text) {
        JMenuItem item = new JMenuItem(text);
        item.setBackground(SURFACE2);
        item.setForeground(TEXT);
        item.setFont(FONT_BODY);
        return item;
    }

    private static JPanel treeCard(String title, JTree tree, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        JLabel lbl = new JLabel(title);
        lbl.setForeground(accentColor);
        lbl.setFont(FONT_HEADER);
        lbl.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JScrollPane sp = new JScrollPane(tree);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(SURFACE);

        card.add(lbl, BorderLayout.NORTH);
        card.add(sp, BorderLayout.CENTER);
        return card;
    }

    private static JTree buildStyledTree(DefaultTreeModel model) {
        JTree tree = new JTree(model);
        tree.setBackground(SURFACE);
        tree.setForeground(TEXT);
        tree.setFont(FONT_MONO);
        tree.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(24);
        tree.putClientProperty("JTree.lineStyle", "Angled");
        tree.setCellRenderer(new StormTreeRenderer());
        return tree;
    }

    @Override
    public void refresh(Page source, Object... input) {
        super.refresh(source, input);
        if (input.length > 0 && input[0] instanceof Storm stormItem) {
            if (!stormItem.isStormLoaded()) {
                window.snackbar.show("Storm data not parsed yet", BarManager.Type.WARNING);
                return;
            }
            currentStorm = stormItem;
        } else {
            window.navigate(Page.HOME);
            return;
        }
        populateFromStorm();
    }

    private JPanel buildTopBar() {
        JPanel top = new JPanel(new BorderLayout(12, 6));
        top.setOpaque(false);

        breadcrumbLabel = new JLabel("Storm  ›  (no file)");
        breadcrumbLabel.setForeground(TEXT);
        breadcrumbLabel.setFont(FONT_TITLE);
        breadcrumbLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        breadcrumbLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                currentStorm = null;
                populateFromStorm();
            }
        });

        JPanel metaRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        metaRow.setOpaque(false);
        categoryLabel = muted("Category: —");
        metaRow.add(categoryLabel);

        JPanel leftSide = new JPanel(new BorderLayout(0, 3));
        leftSide.setOpaque(false);
        leftSide.add(breadcrumbLabel, BorderLayout.NORTH);
        leftSide.add(metaRow, BorderLayout.SOUTH);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(accentBtn("＋ New Rule", e -> openRuleDialog(null)));
        actions.add(ghostBtn("💾 Save to Mod", e -> saveToMod()));
        actions.add(ghostBtn("← Back", e -> window.navigate(Page.ITEMS)));

        top.add(leftSide, BorderLayout.WEST);
        top.add(actions, BorderLayout.EAST);

        JPanel summaryRow = buildSummaryRow();

        JPanel wrapper = new JPanel(new BorderLayout(0, 8));
        wrapper.setOpaque(false);
        wrapper.add(top, BorderLayout.NORTH);
        wrapper.add(summaryRow, BorderLayout.SOUTH);
        return wrapper;
    }

    private JPanel buildSummaryRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row.setOpaque(false);

        rulesCountLabel = statBadge("0 rules", ACCENT);
        tasksCountLabel = statBadge("0 tasks", ACCENT2);
        selectorsCountLabel = statBadge("0 selectors", C_SELECTOR);
        operationsCountLabel = statBadge("0 operations", C_OPERATION);

        row.add(rulesCountLabel);
        row.add(tasksCountLabel);
        row.add(selectorsCountLabel);
        row.add(operationsCountLabel);
        return row;
    }

    private JSplitPane buildMainArea() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildRuleListPanel(),
                buildRightSplit());
        split.setDividerLocation(270);
        split.setDividerSize(3);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setBackground(BG);
        split.setOpaque(false);
        return split;
    }

    private JPanel buildRuleListPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(12, 10, 10, 10)));

        JLabel title = new JLabel("Rules");
        title.setForeground(ACCENT);
        title.setFont(FONT_HEADER);

        ruleList.setBackground(SURFACE);
        ruleList.setForeground(TEXT);
        ruleList.setSelectionBackground(SURFACE3);
        ruleList.setFont(FONT_BODY);
        ruleList.setFixedCellHeight(44);
        ruleList.setCellRenderer(new RuleListRenderer());
        ruleList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showRule(ruleList.getSelectedValue());
        });
        ruleList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    StormRule sel = ruleList.getSelectedValue();
                    if (sel != null) openRuleDialog(sel);
                }
            }
        });

        JPopupMenu popup = new JPopupMenu();
        popup.setBackground(SURFACE2);
        JMenuItem editItem = styledMenuItem("✏  Edit Rule");
        JMenuItem deleteItem = styledMenuItem("🗑  Delete Rule");
        editItem.addActionListener(e -> {
            StormRule s = ruleList.getSelectedValue();
            if (s != null) openRuleDialog(s);
        });
        deleteItem.addActionListener(e -> deleteSelectedRule());
        popup.add(editItem);
        popup.add(deleteItem);
        ruleList.setComponentPopupMenu(popup);

        JScrollPane scroll = new JScrollPane(ruleList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(SURFACE);

        JButton addBtn = accentBtn("＋ Add Rule", e -> openRuleDialog(null));
        addBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        panel.add(title, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(addBtn, BorderLayout.SOUTH);
        return panel;
    }

    private JSplitPane buildRightSplit() {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                buildRuleDetail(),
                buildXmlPreview());
        split.setResizeWeight(0.65);
        split.setDividerSize(3);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setBackground(BG);
        return split;
    }

    private JPanel buildRuleDetail() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(BG);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));

        ruleNameLabel = new JLabel("(select a rule)");
        ruleNameLabel.setForeground(TEXT);
        ruleNameLabel.setFont(new Font("SansSerif", Font.BOLD, 16));

        ruleMetaLabel = new JLabel(" ");
        ruleMetaLabel.setForeground(MUTED);
        ruleMetaLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));

        JPanel header = new JPanel(new BorderLayout(0, 2));
        header.setOpaque(false);
        header.add(ruleNameLabel, BorderLayout.NORTH);
        header.add(ruleMetaLabel, BorderLayout.SOUTH);

        selectorTreeModel = new DefaultTreeModel(new DefaultMutableTreeNode("Conditions"));
        operationTreeModel = new DefaultTreeModel(new DefaultMutableTreeNode("Operations"));
        selectorTree = buildStyledTree(selectorTreeModel);
        operationTree = buildStyledTree(operationTreeModel);

        JPanel treeArea = new JPanel(new GridLayout(1, 2, 10, 0));
        treeArea.setOpaque(false);
        treeArea.add(treeCard("⬡  Conditions", selectorTree, C_SELECTOR));
        treeArea.add(treeCard("⚡  Operations", operationTree, C_OPERATION));

        panel.add(header, BorderLayout.NORTH);
        panel.add(treeArea, BorderLayout.CENTER);
        return panel;
    }

    private JScrollPane buildXmlPreview() {
        xmlPreviewPane = new JEditorPane();
        xmlPreviewPane.setContentType("text/html");
        xmlPreviewPane.setEditable(false);
        xmlPreviewPane.setBackground(new Color(0x080810));
        xmlPreviewPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        xmlPreviewPane.setText(xmlPlaceholderHtml());

        JScrollPane sp = new JScrollPane(xmlPreviewPane);
        sp.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER),
                "XML Preview",
                TitledBorder.LEFT, TitledBorder.TOP,
                FONT_BADGE, MUTED));
        sp.setBackground(SURFACE);
        return sp;
    }

    private JPanel buildBottomBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);

        statusLabel = new JLabel(" ");
        statusLabel.setForeground(MUTED);
        statusLabel.setFont(FONT_SMALL);

        bar.add(statusLabel, BorderLayout.WEST);
        return bar;
    }

    private void populateFromStorm() {
        ruleListModel.clear();
        clearRuleDetail();

        if (currentStorm == null) {
            breadcrumbLabel.setText("Storm  ›");
            categoryLabel.setText("Category: —");
            updateCounters(0, 0, 0, 0);
            setStatus("Select a Storm file from the Items page.", MUTED);
            xmlPreviewPane.setText(xmlPlaceholderHtml());
            return;
        }

        String cat = currentStorm.getCategory();
        breadcrumbLabel.setText("Storm  ›  " + currentStorm.getId());
        categoryLabel.setText("Category: " + (cat != null ? cat : "Miscellaneous"));

        List<StormRule> rules = currentStorm.getRules();
        for (StormRule r : rules) ruleListModel.addElement(r);

        int totalSel = rules.stream().mapToInt(r -> countNodes(r.selectors())).sum();
        int totalOps = rules.stream().mapToInt(r -> countNodes(r.operations())).sum();
        updateCounters(rules.size(), currentStorm.getTasks().size(), totalSel, totalOps);

        setStatus("Loaded " + rules.size() + " rule(s)  ·  " + currentStorm.getTasks().size() + " task(s)  ·  " + currentStorm.getCustomSelectors().size() + " custom selector(s)  ·  " + currentStorm.getCustomOperations().size() + " custom operation(s)", SUCCESS);

        if (!ruleListModel.isEmpty()) ruleList.setSelectedIndex(0);

        refreshXmlPreview();
    }

    private void showRule(StormRule rule) {
        if (rule == null) {
            clearRuleDetail();
            return;
        }

        ruleNameLabel.setText(rule.name().isEmpty() ? "(unnamed)" : rule.name());

        String meta = "";
        if (!rule.mode().isEmpty()) meta += "mode: " + rule.mode() + "  ";
        if (!rule.comment().isEmpty()) meta += "// " + rule.comment();
        ruleMetaLabel.setText(meta.isEmpty() ? " " : meta.trim());

        DefaultMutableTreeNode selRoot = new DefaultMutableTreeNode("Conditions");
        for (XmlNode sel : rule.selectors()) selRoot.add(buildSelectorTreeNode(sel));
        selectorTreeModel.setRoot(selRoot);
        expandAll(selectorTree);

        DefaultMutableTreeNode opRoot = new DefaultMutableTreeNode("Operations");
        for (XmlNode op : rule.operations()) opRoot.add(buildOperationTreeNode(op));
        operationTreeModel.setRoot(opRoot);
        expandAll(operationTree);

        refreshXmlPreview();
    }

    /**
     * Build a tree node for a selector XmlNode.
     * Combinators show their tag. Leaf selectors show tag + all attributes richly.
     */
    private DefaultMutableTreeNode buildSelectorTreeNode(XmlNode sel) {
        SelectorNode node = new SelectorNode(sel, formatSelectorLabel(sel));
        for (XmlNode child : sel.children()) node.add(buildSelectorTreeNode(child));
        return node;
    }

    private String formatSelectorLabel(XmlNode sel) {
        String tag = sel.tag();
        if (tag.isEmpty()) return "(unnamed)";

        if (isCombinator(tag)) return tag.toUpperCase(Locale.ROOT);

        if (sel.attributes().isEmpty()) return "<" + tag + ">";

        String attrs = sel.attributes().stream()
                .filter(a -> !a.getName().startsWith("_"))
                .map(a -> a.getName() + "=" + a.getValue())
                .collect(Collectors.joining("  "));
        return "<" + tag + ">  " + attrs;
    }

    private DefaultMutableTreeNode buildOperationTreeNode(XmlNode op) {
        OperationNode node = new OperationNode(op, formatOperationLabel(op));
        for (XmlNode child : op.children()) node.add(buildOperationTreeNode(child));
        return node;
    }

    private String formatOperationLabel(XmlNode op) {
        String tag = op.tag();
        if (tag.isEmpty()) return "(unnamed)";

        if (op.attributes().isEmpty()) return tag;

        String attrs = op.attributes().stream()
                .filter(a -> !a.getName().startsWith("_"))
                .map(a -> a.getName() + "=" + a.getValue())
                .collect(Collectors.joining("  "));
        return tag + "  " + attrs;
    }

    private void clearRuleDetail() {
        ruleNameLabel.setText("(select a rule)");
        ruleMetaLabel.setText(" ");
        selectorTreeModel.setRoot(new DefaultMutableTreeNode("Conditions"));
        operationTreeModel.setRoot(new DefaultMutableTreeNode("Operations"));
    }

    private void refreshXmlPreview() {
        if (currentStorm == null) {
            xmlPreviewPane.setText(xmlPlaceholderHtml());
            return;
        }
        try {
            String xml = StormService.serialize(currentStorm);
            xmlPreviewPane.setText(xmlToHtml(xml));
            xmlPreviewPane.setCaretPosition(0);
        } catch (Exception ex) {
            xmlPreviewPane.setText("<html><body style='background:#080810;color:#f05c6e;"
                    + "font-family:monospace;padding:10px;'>Serialization error: "
                    + ex.getMessage() + "</body></html>");
        }
    }

    private void saveToMod() {
        if (currentStorm == null) {
            window.snackbar.show("No Storm file loaded", BarManager.Type.WARNING);
            return;
        }
        var mods = ModService.modCollection;
        if (mods.isEmpty()) {
            window.snackbar.show("No mods — create one first", BarManager.Type.WARNING);
            return;
        }

        String[] modNames = mods.stream()
                .map(m -> m.getId() + " | " + m.getName()).toArray(String[]::new);
        String choice = (String) JOptionPane.showInputDialog(this,
                "Select target mod:", "Save Storm File to Mod",
                JOptionPane.PLAIN_MESSAGE, null, modNames, modNames[0]);
        if (choice == null) return;

        String modId = choice.split(" \\| ")[0];
        mods.stream().filter(m -> m.getId().equals(modId)).findFirst().ifPresent(mod -> {

        });
    }

    private void deleteSelectedRule() {
        if (currentStorm == null) return;
        StormRule sel = ruleList.getSelectedValue();
        if (sel == null) return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete rule '" + sel.name() + "'?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        currentStorm.getRules().remove(sel);
        ruleListModel.removeElement(sel);
        clearRuleDetail();
        refreshXmlPreview();
        setStatus("Rule deleted.", DANGER);
    }

    private void openRuleDialog(StormRule existing) {
        if (currentStorm == null) {
            window.snackbar.show("Open a Storm file first", BarManager.Type.WARNING);
            return;
        }
        RuleWizardDialog dlg = new RuleWizardDialog(
                (Frame) SwingUtilities.getWindowAncestor(this), existing);
        dlg.setVisible(true);
        StormRule result = dlg.getResult();
        if (result == null) return;

        if (existing == null) {
            currentStorm.getRules().add(result);
            ruleListModel.addElement(result);
            ruleList.setSelectedValue(result, true);
            setStatus("Rule '" + result.name() + "' added.", SUCCESS);
        } else {
            int idx = currentStorm.getRules().indexOf(existing);
            if (idx >= 0) currentStorm.getRules().set(idx, result);

            int listIdx = -1;
            for (int i = 0; i < ruleListModel.size(); i++)
                if (ruleListModel.get(i) == existing) {
                    listIdx = i;
                    break;
                }
            if (listIdx >= 0) ruleListModel.set(listIdx, result);
            ruleList.setSelectedValue(result, true);
            showRule(result);
            setStatus("Rule '" + result.name() + "' updated.", SUCCESS);
        }
        refreshXmlPreview();
    }

    private void updateCounters(int rules, int tasks, int selectors, int operations) {
        rulesCountLabel.setText(rules + " rules");
        tasksCountLabel.setText(tasks + " tasks");
        selectorsCountLabel.setText(selectors + " selectors");
        operationsCountLabel.setText(operations + " operations");
    }

    private void expandAll(JTree tree) {
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
    }

    private void setStatus(String text, Color color) {
        statusLabel.setText(text);
        statusLabel.setForeground(color);
    }

    static final class SelectorNode extends DefaultMutableTreeNode {
        final XmlNode selector;

        SelectorNode(XmlNode sel, String display) {
            super(display);
            this.selector = sel;
        }
    }

    static final class OperationNode extends DefaultMutableTreeNode {
        final XmlNode operation;

        OperationNode(XmlNode op, String display) {
            super(display);
            this.operation = op;
        }
    }

    private static final class RuleListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object val, int idx, boolean sel, boolean focus) {
            super.getListCellRendererComponent(list, val, idx, sel, focus);
            setBackground(sel ? SURFACE3 : SURFACE);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                    BorderFactory.createEmptyBorder(6, 10, 6, 10)));
            setFont(FONT_BODY);

            if (val instanceof StormRule rule) {
                String name = rule.name().isEmpty() ? "(unnamed)" : rule.name();
                String comment = rule.comment().isEmpty() ? "" : "   // " + rule.comment();
                String selCount = rule.selectors().size() + "s";
                String opCount = rule.operations().size() + "o";

                setText("<html><b style='color:#e0e0f5;'>" + name + "</b>"
                        + "<font color='#6b6b8f'>" + comment + "</font>"
                        + "&nbsp;&nbsp;<font color='#7c6af7' style='font-size:9px;'>[" + selCount + " · " + opCount + "]</font>"
                        + "</html>");
            }
            setForeground(sel ? ACCENT : TEXT);
            return this;
        }
    }

    private static final class StormTreeRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(
                JTree tree, Object val, boolean sel, boolean exp, boolean leaf, int row, boolean focus) {
            super.getTreeCellRendererComponent(tree, val, sel, exp, leaf, row, focus);
            setBackground(sel ? SURFACE3 : SURFACE);
            setBackgroundNonSelectionColor(SURFACE);
            setBackgroundSelectionColor(SURFACE3);
            setBorderSelectionColor(null);
            setFont(FONT_MONO);

            if (val instanceof SelectorNode sn) {
                String tag = sn.selector.tag().toLowerCase(Locale.ROOT);
                setForeground(switch (tag) {
                    case "and" -> C_AND;
                    case "or" -> C_OR;
                    case "not" -> C_NOT;
                    default -> C_SELECTOR;
                });
            } else if (val instanceof OperationNode) {
                setForeground(C_OPERATION);
            } else {
                setForeground(MUTED);
            }
            return this;
        }
    }

    static final class RuleWizardDialog extends JDialog {

        private static final String[] STEP_TITLES = {
                "1 — Name & Comment",
                "2 — Conditions (Selectors)",
                "3 — Operations"
        };
        private final List<XmlNode> workingSelectors = new ArrayList<>();
        private final List<XmlNode> workingOperations = new ArrayList<>();
        private String workingName = "";
        private String workingComment = "";
        private String workingMode = "";
        private StormRule result = null;
        private int currentStep = 0;

        private JTextField nameField;
        private JTextField commentField;
        private JTextField modeField;
        private JLabel nameError;

        private JPanel selectorContainer;
        private JPanel operationContainer;

        private JLabel stepLabel;
        private JButton backBtn;
        private JButton nextBtn;
        private JPanel stepDotRow;

        RuleWizardDialog(Frame owner, StormRule existing) {
            super(owner, existing == null ? "New Rule" : "Edit — " + existing.name(), true);

            if (existing != null) {
                workingName = existing.name();
                workingComment = existing.comment();
                workingMode = existing.mode();

                for (XmlNode s : existing.selectors()) workingSelectors.add(deepCopy(s));
                for (XmlNode o : existing.operations()) workingOperations.add(deepCopy(o));
            }

            setSize(860, 660);
            setLocationRelativeTo(owner);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            getContentPane().setBackground(SURFACE2);
            setLayout(new BorderLayout());

            buildDialog();

            if (existing != null) {
                nameField.setText(existing.name());
                commentField.setText(existing.comment());
                modeField.setText(existing.mode());
            }
            updateStep();
        }

        /**
         * XmlNode.tag() is immutable (record field) — replace node in its parent list.
         */
        private static void replaceNodeTag(XmlNode node, String newTag, List<XmlNode> parentList) {
            int idx = parentList.indexOf(node);
            if (idx < 0) return;
            XmlNode replacement = new XmlNode(newTag, node.attributes(), node.children());
            parentList.set(idx, replacement);
        }

        private static XmlNode deepCopy(XmlNode n) {
            var attrs = n.attributes().stream().map(Attribute::deepClone).collect(Collectors.toCollection(ArrayList::new));
            var children = n.children().stream().map(RuleWizardDialog::deepCopy).collect(Collectors.toCollection(ArrayList::new));
            return new XmlNode(n.tag(), attrs, children);
        }

        private static Color selectorColor(String type) {
            return switch (type.toLowerCase(Locale.ROOT)) {
                case "and" -> C_AND;
                case "or" -> C_OR;
                case "not" -> C_NOT;
                default -> C_SELECTOR;
            };
        }

        private static JTextField wizardField(int cols) {
            JTextField f = new JTextField(cols);
            f.setBackground(SURFACE3);
            f.setForeground(TEXT);
            f.setCaretColor(TEXT);
            f.setFont(FONT_MONO);
            f.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER),
                    BorderFactory.createEmptyBorder(4, 7, 4, 7)));
            return f;
        }

        private static JScrollPane styledScroll(JComponent content) {
            JScrollPane sp = new JScrollPane(content);
            sp.setBorder(BorderFactory.createLineBorder(BORDER));
            sp.getViewport().setBackground(new Color(0x11111e));
            return sp;
        }

        private static void addFormRow(JPanel p, String label, JComponent field, int row) {
            GridBagConstraints lc = gbc(0, row);
            lc.weightx = 0.18;
            lc.anchor = GridBagConstraints.EAST;
            lc.insets = new Insets(9, 4, 9, 14);
            JLabel lbl = new JLabel(label);
            lbl.setForeground(MUTED);
            lbl.setFont(FONT_BODY);
            p.add(lbl, lc);

            GridBagConstraints fc = gbc(1, row);
            fc.weightx = 0.82;
            fc.fill = GridBagConstraints.HORIZONTAL;
            fc.insets = new Insets(9, 0, 9, 4);
            p.add(field, fc);
        }

        private static GridBagConstraints gbc(int x, int y) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = x;
            c.gridy = y;
            return c;
        }

        private static DocumentListener simpleListener(Runnable r) {
            return new DocumentListener() {
                public void insertUpdate(DocumentEvent e) {
                    r.run();
                }

                public void removeUpdate(DocumentEvent e) {
                    r.run();
                }

                public void changedUpdate(DocumentEvent e) {
                    r.run();
                }
            };
        }

        private void buildDialog() {

            JPanel header = new JPanel(new BorderLayout(0, 8));
            header.setBackground(new Color(0x0d0d14));
            header.setBorder(BorderFactory.createEmptyBorder(16, 22, 14, 22));

            stepLabel = new JLabel();
            stepLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
            stepLabel.setForeground(ACCENT);

            stepDotRow = buildStepDotRow();

            header.add(stepLabel, BorderLayout.NORTH);
            header.add(stepDotRow, BorderLayout.SOUTH);

            JPanel steps = new JPanel(new CardLayout());
            steps.setBackground(SURFACE2);
            steps.add(buildStep1(), "0");
            steps.add(buildStep2(), "1");
            steps.add(buildStep3(), "2");

            JPanel nav = new JPanel(new BorderLayout());
            nav.setBackground(new Color(0x0d0d14));
            nav.setBorder(BorderFactory.createEmptyBorder(10, 22, 12, 22));

            backBtn = ghostBtn("← Back", e -> goBack());
            nextBtn = accentBtn("Next →", e -> goNext());
            JButton cancelBtn = dangerBtn("Cancel", e -> dispose());

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            left.setOpaque(false);
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            right.setOpaque(false);
            left.add(cancelBtn);
            right.add(backBtn);
            right.add(nextBtn);
            nav.add(left, BorderLayout.WEST);
            nav.add(right, BorderLayout.EAST);

            add(header, BorderLayout.NORTH);
            add(steps, BorderLayout.CENTER);
            add(nav, BorderLayout.SOUTH);
        }

        private JPanel buildStepDotRow() {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            row.setOpaque(false);
            for (int i = 0; i < STEP_TITLES.length; i++) {
                JLabel dot = new JLabel("⬤");
                dot.setName("dot_" + i);
                dot.setForeground(i == 0 ? ACCENT : MUTED);
                dot.setFont(new Font("SansSerif", Font.PLAIN, 9));
                row.add(dot);

                JLabel lbl = new JLabel(STEP_TITLES[i].split("—")[1].trim());
                lbl.setName("lbl_" + i);
                lbl.setForeground(i == 0 ? TEXT : MUTED);
                lbl.setFont(FONT_SMALL);
                row.add(lbl);

                if (i < STEP_TITLES.length - 1) {
                    JLabel sep = new JLabel("›");
                    sep.setForeground(MUTED);
                    row.add(sep);
                }
            }
            return row;
        }

        private JPanel buildStep1() {
            JPanel p = new JPanel(new GridBagLayout());
            p.setBackground(SURFACE2);
            p.setBorder(BorderFactory.createEmptyBorder(28, 32, 28, 32));

            nameField = wizardField(24);
            commentField = wizardField(24);
            modeField = wizardField(12);
            nameError = new JLabel(" ");
            nameError.setForeground(DANGER);
            nameError.setFont(new Font("SansSerif", Font.ITALIC, 11));

            nameField.getDocument().addDocumentListener(simpleListener(() -> nameError.setText(" ")));

            addFormRow(p, "Name *", nameField, 0);
            addFormRow(p, "Comment", commentField, 1);
            addFormRow(p, "Mode", modeField, 2);

            GridBagConstraints ec = gbc(1, 3);
            ec.fill = GridBagConstraints.HORIZONTAL;
            p.add(nameError, ec);
            return p;
        }

        private JPanel buildStep2() {
            JPanel p = new JPanel(new BorderLayout(0, 10));
            p.setBackground(SURFACE2);
            p.setBorder(BorderFactory.createEmptyBorder(16, 22, 16, 22));

            JLabel hint = muted("Build the condition tree. Combinators (and / or / not) nest child selectors.");

            selectorContainer = new JPanel();
            selectorContainer.setLayout(new BoxLayout(selectorContainer, BoxLayout.Y_AXIS));
            selectorContainer.setBackground(new Color(0x11111e));
            selectorContainer.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

            JScrollPane scroll = styledScroll(selectorContainer);

            JPanel addBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            addBar.setOpaque(false);
            addBar.add(muted("Add:"));
            for (String type : new String[]{"selector", "and", "or", "not"}) {
                Color fg = selectorColor(type);
                addBar.add(miniBtn("+ " + type, fg, e -> addTopLevelSelector(type)));
            }

            p.add(hint, BorderLayout.NORTH);
            p.add(scroll, BorderLayout.CENTER);
            p.add(addBar, BorderLayout.SOUTH);
            return p;
        }

        private JPanel buildStep3() {
            JPanel p = new JPanel(new BorderLayout(0, 10));
            p.setBackground(SURFACE2);
            p.setBorder(BorderFactory.createEmptyBorder(16, 22, 16, 22));

            JLabel hint = muted("Add operations that run when this rule fires. Operations may have child operations.");

            operationContainer = new JPanel();
            operationContainer.setLayout(new BoxLayout(operationContainer, BoxLayout.Y_AXIS));
            operationContainer.setBackground(new Color(0x11111e));
            operationContainer.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

            JScrollPane scroll = styledScroll(operationContainer);

            JButton addBtn = accentBtn("+ Add Operation", e -> addTopLevelOperation());

            JPanel addBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            addBar.setOpaque(false);
            addBar.add(addBtn);

            p.add(hint, BorderLayout.NORTH);
            p.add(scroll, BorderLayout.CENTER);
            p.add(addBar, BorderLayout.SOUTH);
            return p;
        }

        private void updateStep() {

            JPanel steps = (JPanel) ((BorderLayout) getContentPane().getLayout())
                    .getLayoutComponent(BorderLayout.CENTER);
            ((CardLayout) steps.getLayout()).show(steps, String.valueOf(currentStep));

            stepLabel.setText(STEP_TITLES[currentStep]);
            backBtn.setEnabled(currentStep > 0);
            boolean isLast = currentStep == STEP_TITLES.length - 1;
            nextBtn.setText(isLast ? "✓ Save Rule" : "Next →");

            int dotIdx = 0;
            for (Component c : stepDotRow.getComponents()) {
                if (c instanceof JLabel lbl && lbl.getName() != null) {
                    if (lbl.getName().startsWith("dot_")) {
                        lbl.setForeground(dotIdx == currentStep ? ACCENT : MUTED);
                        dotIdx++;
                    } else if (lbl.getName().startsWith("lbl_")) {

                        int idx = Integer.parseInt(lbl.getName().substring(4));
                        lbl.setForeground(idx == currentStep ? TEXT : MUTED);
                    }
                }
            }

            if (currentStep == 1) repopulateSelectorContainer();
            if (currentStep == 2) repopulateOperationContainer();
        }

        private void goNext() {
            if (currentStep == 0 && !validateStep1()) return;
            if (currentStep == 1 && !validateStep2()) return;

            if (currentStep == STEP_TITLES.length - 1) {
                if (!validateStep3()) return;
                applyStep1();

                XmlNode ruleAttrs = Storm.node("rule", Map.of(
                        "name", workingName,
                        "comment", workingComment,
                        "mode", workingMode));
                result = new StormRule(ruleAttrs, new ArrayList<>(workingSelectors), new ArrayList<>(workingOperations));
                dispose();
            } else {
                if (currentStep == 0) applyStep1();
                currentStep++;
                updateStep();
            }
        }

        private void goBack() {
            if (currentStep > 0) {
                currentStep--;
                updateStep();
            }
        }

        private boolean validateStep1() {
            String n = nameField.getText().trim();
            if (n.isBlank()) {
                nameError.setText("⚠ Rule name cannot be empty");
                return false;
            }
            if (!n.matches("[a-zA-Z][a-zA-Z0-9_\\s]*")) {
                nameError.setText("⚠ Letters, digits, underscores, spaces only (must start with a letter)");
                return false;
            }
            nameError.setText(" ");
            return true;
        }

        private boolean validateStep2() {
            if (workingSelectors.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Add at least one selector.", "Missing Selector", JOptionPane.WARNING_MESSAGE);
                return false;
            }
            for (XmlNode s : workingSelectors) {
                if (s.tag().isBlank()) {
                    JOptionPane.showMessageDialog(this, "A selector has no tag name.", "Unnamed Selector", JOptionPane.WARNING_MESSAGE);
                    return false;
                }
            }
            return true;
        }

        private boolean validateStep3() {
            if (workingOperations.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Add at least one operation.", "Missing Operation", JOptionPane.WARNING_MESSAGE);
                return false;
            }
            for (XmlNode o : workingOperations) {
                if (o.tag().isBlank()) {
                    JOptionPane.showMessageDialog(this, "An operation has no tag name.", "Unnamed Operation", JOptionPane.WARNING_MESSAGE);
                    return false;
                }
            }
            return true;
        }

        private void applyStep1() {
            workingName = nameField.getText().trim().replace(" ", "_").toLowerCase(Locale.ROOT);
            workingComment = commentField.getText().trim();
            workingMode = modeField.getText().trim();
        }

        private void addTopLevelSelector(String type) {
            XmlNode sel = Storm.node(type);
            workingSelectors.add(sel);
            selectorContainer.add(buildSelectorRow(sel, workingSelectors, selectorContainer, 0));
            selectorContainer.revalidate();
            selectorContainer.repaint();
        }

        private void repopulateSelectorContainer() {
            selectorContainer.removeAll();
            for (XmlNode sel : workingSelectors)
                selectorContainer.add(buildSelectorRow(sel, workingSelectors, selectorContainer, 0));
            selectorContainer.revalidate();
            selectorContainer.repaint();
        }

        /**
         * Recursively build a UI row for one selector XmlNode.
         * Combinators show their tag label and their children indented below.
         * Leaf selectors show a tag field + an attributes editor button.
         */
        private JPanel buildSelectorRow(XmlNode sel, List<XmlNode> parentList, JPanel parentContainer, int depth) {
            JPanel outer = new JPanel();
            outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
            outer.setOpaque(false);

            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            row.setOpaque(false);
            row.setBorder(BorderFactory.createEmptyBorder(0, depth * 22, 0, 0));

            boolean isComb = isCombinator(sel.tag());

            if (isComb) {
                JLabel tag = new JLabel("<" + sel.tag() + ">");
                tag.setForeground(selectorColor(sel.tag()));
                tag.setFont(new Font("Monospaced", Font.BOLD, 12));
                row.add(tag);
            } else {

                JTextField tagF = wizardField(16);
                tagF.setText(sel.tag());
                tagF.setToolTipText("Selector tag name");

                tagF.getDocument().addDocumentListener(simpleListener(() -> replaceNodeTag(sel, tagF.getText().trim(), parentList)));
                row.add(tagF);

                int attrCount = (int) sel.attributes().stream()
                        .filter(a -> !a.getName().startsWith("_")).count();
                JButton attrBtn = miniBtn("attrs [" + attrCount + "]", C_ATTR_KEY, e ->
                        openAttributeEditor(sel, "Selector Attributes — <" + sel.tag() + ">", () -> {}));
                row.add(attrBtn);
            }

            JButton delBtn = miniBtn("✕", DANGER, e -> {
                parentList.remove(sel);
                parentContainer.remove(outer);
                parentContainer.revalidate();
                parentContainer.repaint();
            });
            row.add(delBtn);
            outer.add(row);

            if (isComb) {
                JPanel childrenPanel = new JPanel();
                childrenPanel.setLayout(new BoxLayout(childrenPanel, BoxLayout.Y_AXIS));
                childrenPanel.setOpaque(false);
                for (XmlNode child : sel.children())
                    childrenPanel.add(buildSelectorRow(child, sel.children(), childrenPanel, depth + 1));
                outer.add(childrenPanel);

                JPanel childBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
                childBar.setOpaque(false);
                childBar.setBorder(BorderFactory.createEmptyBorder(0, (depth + 1) * 22, 0, 0));
                childBar.add(muted("add:"));
                for (String type : new String[]{"selector", "and", "or", "not"}) {
                    childBar.add(miniBtn("+" + type, selectorColor(type), e -> {
                        XmlNode child = Storm.node(type);
                        sel.children().add(child);
                        childrenPanel.add(buildSelectorRow(child, sel.children(), childrenPanel, depth + 1));
                        childrenPanel.revalidate();
                        childrenPanel.repaint();
                    }));
                }
                outer.add(childBar);
            }

            return outer;
        }

        private void addTopLevelOperation() {
            XmlNode op = Storm.node("");
            workingOperations.add(op);
            operationContainer.add(buildOperationRow(op, workingOperations, operationContainer));
            operationContainer.revalidate();
            operationContainer.repaint();
        }

        private void repopulateOperationContainer() {
            operationContainer.removeAll();
            for (XmlNode op : workingOperations)
                operationContainer.add(buildOperationRow(op, workingOperations, operationContainer));
            operationContainer.revalidate();
            operationContainer.repaint();
        }

        private JPanel buildOperationRow(XmlNode op, List<XmlNode> parentList, JPanel parentContainer) {
            JPanel outer = new JPanel();
            outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
            outer.setOpaque(false);
            outer.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 3, 0, 0, C_OPERATION),
                    BorderFactory.createEmptyBorder(4, 10, 4, 4)));

            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
            row.setOpaque(false);

            JTextField tagF = wizardField(22);
            tagF.setText(op.tag());
            tagF.setForeground(C_OPERATION);
            tagF.setToolTipText("Operation tag name");
            tagF.getDocument().addDocumentListener(simpleListener(() ->
                    replaceNodeTag(op, tagF.getText().trim(), parentList)));
            row.add(tagF);

            int attrCount = (int) op.attributes().stream()
                    .filter(a -> !a.getName().startsWith("_")).count();
            JButton attrBtn = miniBtn("attrs [" + attrCount + "]", C_ATTR_KEY, e ->
                    openAttributeEditor(op, "Operation Attributes — " + op.tag(), () -> {
                    }));
            row.add(attrBtn);

            JButton addChildBtn = miniBtn("+ child op", C_OPERATION, e -> {
                XmlNode child = Storm.node("");
                op.children().add(child);
                outer.add(buildOperationRow(child, op.children(), outer));
                outer.revalidate();
                outer.repaint();
            });
            row.add(addChildBtn);

            JButton delBtn = miniBtn("✕", DANGER, e -> {
                parentList.remove(op);
                parentContainer.remove(outer);
                parentContainer.revalidate();
                parentContainer.repaint();
            });
            row.add(delBtn);

            outer.add(row);

            for (XmlNode child : op.children())
                outer.add(buildOperationRow(child, op.children(), outer));

            return outer;
        }

        /**
         * Opens a dialog to edit the XML attributes of an XmlNode in-place.
         * Changes are written directly back into node.attributes().
         */
        private void openAttributeEditor(XmlNode node, String title, Runnable onClose) {
            JDialog dlg = new JDialog(this, title, true);
            dlg.setSize(500, 420);
            dlg.setLocationRelativeTo(this);
            dlg.setLayout(new BorderLayout(0, 8));
            dlg.getContentPane().setBackground(SURFACE2);

            JPanel attrList = new JPanel();
            attrList.setLayout(new BoxLayout(attrList, BoxLayout.Y_AXIS));
            attrList.setBackground(new Color(0x11111e));
            attrList.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

            List<JTextField> keyFields = new ArrayList<>();
            List<JTextField> valFields = new ArrayList<>();

            Consumer<Void> buildRows = ignored -> {
                attrList.removeAll();
                keyFields.clear();
                valFields.clear();
                for (var attr : node.attributes()) {
                    if (attr.getName().startsWith("_")) continue;

                    addAttrEditorRow(attrList, keyFields, valFields,
                            attr.getName(), attr.getValue().toString());
                }
                attrList.revalidate();
                attrList.repaint();
            };
            buildRows.accept(null);

            JScrollPane scroll = styledScroll(attrList);

            JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
            btnBar.setOpaque(false);

            JButton addRowBtn = ghostBtn("+ Add Attribute", e -> {
                addAttrEditorRow(attrList, keyFields, valFields, "", "");
                attrList.revalidate();
                attrList.repaint();
            });

            JButton saveBtn = accentBtn("Save & Close", e -> {

                node.attributes().clear();
                for (int i = 0; i < keyFields.size(); i++) {
                    String k = keyFields.get(i).getText().trim();
                    String v = valFields.get(i).getText().trim();
                    if (!k.isEmpty())
                        node.attributes().add(new Attribute.StringAttribute(k, v));
                }
                onClose.run();
                dlg.dispose();
            });

            btnBar.add(addRowBtn);
            btnBar.add(saveBtn);

            dlg.add(scroll, BorderLayout.CENTER);
            dlg.add(btnBar, BorderLayout.SOUTH);
            dlg.setVisible(true);
        }

        private void addAttrEditorRow(JPanel container,
                                      List<JTextField> keys, List<JTextField> vals,
                                      String key, String value) {
            JPanel row = new JPanel(new BorderLayout(6, 0));
            row.setOpaque(false);
            row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

            JTextField kf = wizardField(14);
            kf.setText(key);
            kf.setForeground(C_ATTR_KEY);
            JTextField vf = wizardField(20);
            vf.setText(value);
            vf.setForeground(C_ATTR_VAL);

            JButton del = miniBtn("✕", DANGER, e -> {
                container.remove(row);
                keys.remove(kf);
                vals.remove(vf);
                container.revalidate();
                container.repaint();
            });

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