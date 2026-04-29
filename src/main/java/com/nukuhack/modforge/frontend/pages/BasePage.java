package com.nukuhack.modforge.frontend.pages;

import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.backend.ModData;
import com.nukuhack.modforge.backend.model.Attribute;
import com.nukuhack.modforge.backend.model.E;
import com.nukuhack.modforge.backend.model.ModItem;
import com.nukuhack.modforge.backend.model.Storm;
import com.nukuhack.modforge.backend.service.ModItemBuilder;
import com.nukuhack.modforge.backend.service.ModService;
import com.nukuhack.modforge.frontend.BarManager;
import com.nukuhack.modforge.frontend.MainWindow;
import com.nukuhack.modforge.frontend.Page;
import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.nukuhack.modforge.frontend.MainWindow.getLocalText;

@Slf4j
@ExtensionMethod({Util.class})
public abstract class BasePage extends JPanel {

    protected Page sourcePage = Page.HOME;
    private static final String[] DEPTH_ACCENTS = {"#89b4fa", "#cba6f7", "#89dceb", "#a6e3a1", "#f9e2af",};
    protected final MainWindow window;
    protected final ExecutorService executor = Executors.newSingleThreadExecutor();
    protected final JComboBox<String> modSelector = new JComboBox<>(new DefaultComboBoxModel<>());
    protected final JComboBox<String> langSelector = new JComboBox<>();
    protected final JTextField search = styledField("ui_search_all");

    BasePage(MainWindow window) {
        this.window = window;
        setBackground(MainWindow.BG);
        setLayout(new BorderLayout());
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
        f.setBorder(BorderFactory.createCompoundBorder(new LineBorder(new Color(0x45475a), 1), BorderFactory.createEmptyBorder(6, 10, 6, 10)));
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
            return "<html><body style='background:#181825;color:#6c6f85;font-family:sans-serif;padding:12px;'><i>" + getLocalText("ui_no_item") + "</i></body></html>";
        }

        var html = new StringBuilder();
        html.append("<html><body style='background:#181825;color:#cdd6f4;font-family:sans-serif;padding:12px;margin:0;'>");
        appendItemContents(html, item, 0);
        html.append("</body></html>");
        return html.toString();
    }

    /**
     * Renders the inner content of a ModItem into {@code html}.
     * {@code depth} controls visual nesting — 0 = top level, 1+ = inside an XmlNodeAttribute.
     */
    private static void appendItemContents(StringBuilder html, ModItem item, int depth) {
        boolean nested = depth > 0;
        String accentColor = DEPTH_ACCENTS[Math.min(depth, DEPTH_ACCENTS.length - 1)];

        html.append("<div style='display:flex;align-items:center;margin-bottom:4px;'>");
        html.append("<b style='color:").append(accentColor).append(";font-size:").append(nested ? "11" : "14").append("px;'>").append(Util.escHtml(item.getId())).append("</b>");
        if (!nested)
            html.append("<br/><span style='background:#313244;color:#a6e3a1;font-size:9px;padding:2px 6px;border-radius:3px;margin-left:8px;'>").append(Util.escHtml(item.getClass().getSimpleName())).append("</span>");
        html.append("</div>");

        if (!nested && !item.getPath().isBlank()) {
            html.append("<div style='background:#1e1e2e;padding:8px;border-radius:4px;margin:8px 0;border-left:3px solid ").append(accentColor).append(";'>");
            html.append("<span style='color:#6c6f85;font-size:9px;text-transform:uppercase;letter-spacing:0.5px;'>📁 ").append(getLocalText("ui_path")).append("</span><br/>");
            html.append("<span style='color:#cdd6f4;font-size:11px;font-family:monospace;'>").append(Util.escHtml(item.getPath())).append("</span>");
            html.append("</div>");
        }

        if (!item.getAttributes().isEmpty()) {
            html.append("<div style='margin-top:").append(nested ? "6" : "12").append("px;'>");

            var groupedAttrs = item.getAttributes().stream().collect(Collectors.groupingBy(BasePage::getAttributeCategory));

            for (var entry : groupedAttrs.entrySet()) {
                if (entry.getValue().isEmpty())
                    continue;

                html.append("<div style='margin-top:8px;margin-left:4px;'>");
                html.append("<span style='color:#6c6f85;font-size:9px;text-transform:uppercase;'>").append(entry.getKey()).append("</span>");

                for (var attr : entry.getValue()) {
                    html.append("<div style='margin:6px 0 6px 8px;padding:6px 8px;background:#1e1e2e;border-radius:3px;border-left:2px solid ").append(getTypeColor(attr)).append(";'>");

                    formatAttributeValue(html, attr, depth);

                    html.append("</div>");
                }
                html.append("</div>");
            }
            html.append("</div>");
        }
    }

    private static String getAttributeCategory(Attribute attr) {
        if (attr instanceof Attribute.StringAttribute) {
            String name = attr.getName().toLowerCase();
            if (ModItem.LANG_ATTR_HINTS.stream().anyMatch(name::contains)) {
                return "🌐 Localization";
            }
            return "📝 Strings";
        } else if (attr instanceof Attribute.BuffParamListAttribute) {
            return "⚔️ Combat";
        } else if (attr instanceof Attribute.BooleanAttribute) {
            return "🔘 Flags";
        } else if (attr instanceof Attribute.DoubleAttribute) {
            return "📊 Numbers";
        } else if (attr instanceof Attribute.UUIDAttribute) {
            return "🔑 Identifiers";
        } else if (attr instanceof Attribute.ListAttribute) {
            return "📋 Lists";
        } else if (attr instanceof Attribute.EnumAttribute) {
            return "🎯 Enums";
        } else if (attr instanceof Attribute.XmlNodeAttribute) {
            return "🗂️ XML";
        }
        return "📄 Other";
    }

    private static String getTypeColor(Attribute attr) {
        if (attr instanceof Attribute.StringAttribute)
            return "#a6e3a1";
        if (attr instanceof Attribute.BuffParamListAttribute)
            return "#f38ba8";
        if (attr instanceof Attribute.BooleanAttribute)
            return "#fab387";
        if (attr instanceof Attribute.DoubleAttribute)
            return "#89b4fa";
        if (attr instanceof Attribute.UUIDAttribute)
            return "#cba6f7";
        if (attr instanceof Attribute.ListAttribute)
            return "#94e2d5";
        if (attr instanceof Attribute.EnumAttribute)
            return "#f9e2af";
        if (attr instanceof Attribute.XmlNodeAttribute)
            return "#89dceb";
        return "#6c6f85";
    }

    private static void formatAttributeValue(StringBuilder html, Attribute attr, int depth) {
        if (attr instanceof Attribute.BuffParamListAttribute buff) {

            html.append("<div style='display:flex;align-items:center;margin-bottom:3px;'>");
            html.append("<span style='color:#6c6f85;font-size:9px;font-family:monospace;'>").append(Util.escHtml(buff.getName())).append("</span>");
            html.append("</div>");

            html.append("<div style='margin-left:2px;'>");
            html.append("<span style='color:#f38ba8;font-size:10px;font-family:monospace;'>⚡ ");
            html.append(Util.escHtml(buff.getValue().stream().map(Attribute.BuffParam::beautify).collect(Collectors.joining(" \n")))).append("</span>");

        } else if (attr instanceof Attribute.BooleanAttribute) {

            html.append("<div style='display:flex;align-items:center;margin-bottom:3px;'>");
            html.append("<span style='color:#6c6f85;font-size:9px;font-family:monospace;'>").append(Util.escHtml(attr.getName())).append("</span>");
            html.append("</div>");

            html.append("<div style='margin-left:2px;'>");
            boolean val = Boolean.parseBoolean(attr.serialize());
            html.append("<span style='color:").append(val ? "#a6e3a1" : "#f38ba8").append(";font-size:10px;font-weight:bold;'>").append(val).append("</span>");

        } else if (attr instanceof Attribute.DoubleAttribute) {

            html.append("<div style='display:flex;align-items:center;margin-bottom:3px;'>");
            html.append("<span style='color:#6c6f85;font-size:9px;font-family:monospace;'>").append(Util.escHtml(attr.getName())).append("</span>");
            html.append("</div>");

            html.append("<div style='margin-left:2px;'>");
            html.append("<span style='color:#89b4fa;font-size:10px;font-family:monospace;'>").append(Util.escHtml(attr.serialize())).append("</span>");

        } else if (attr instanceof Attribute.UUIDAttribute) {

            html.append("<div style='display:flex;align-items:center;margin-bottom:3px;'>");
            html.append("<span style='color:#6c6f85;font-size:9px;font-family:monospace;'>").append(Util.escHtml(attr.getName())).append("</span>");
            html.append("</div>");

            html.append("<div style='margin-left:2px;'>");
            html.append("<span style='color:#cba6f7;font-size:10px;font-family:monospace;'>").append(Util.escHtml(attr.serialize())).append("</span>");

        } else if (attr instanceof Attribute.ListAttribute) {

            html.append("<div style='display:flex;align-items:center;margin-bottom:3px;'>");
            html.append("<span style='color:#6c6f85;font-size:9px;font-family:monospace;'>").append(Util.escHtml(attr.getName())).append("</span>");
            html.append("</div>");

            html.append("<div style='margin-left:2px;'>");
            html.append("<span style='color:#94e2d5;font-size:10px;'>[</span>");
            html.append("<span style='color:#cdd6f4;font-size:10px;'>").append(Util.escHtml(attr.serialize())).append("</span>");
            html.append("<span style='color:#94e2d5;font-size:10px;'>]</span>");

        } else if (attr instanceof Attribute.EnumAttribute en) {

            html.append("<div style='display:flex;align-items:center;margin-bottom:3px;'>");
            html.append("<span style='color:#6c6f85;font-size:9px;font-family:monospace;'>").append(Util.escHtml(en.getName())).append("</span>");
            html.append("</div>");

            html.append("<div style='margin-left:2px;'>");
            html.append("<span style='color:#f9e2af;font-size:10px;font-family:monospace;'>").append(Util.escHtml(en.getValue().name())).append("</span>");

        } else if (attr instanceof Attribute.XmlNodeAttribute xmlAttr) {

            html.append("<div style='margin-left:2px;'>");

            appendItemContents(html, xmlAttr.getValue().asItem(), depth + 1);
        } else {

            html.append("<div style='display:flex;align-items:center;margin-bottom:3px;'>");
            html.append("<span style='color:#6c6f85;font-size:9px;font-family:monospace;'>").append(Util.escHtml(attr.getName())).append("</span>");
            html.append("</div>");

            html.append("<div style='margin-left:2px;'>");

            html.append("<span style='color:#a6e3a1;font-size:10px;'>").append(Util.escHtml(attr.serialize())).append("</span>");
        }
        html.append("</div>");
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
        return ModService.modCollection.stream().filter(m -> m.getName().equals(modName)).findFirst();
    }

    protected Optional<E.Language> getSelectedLang() {
        var sel = langSelector.getSelectedItem();
        if (sel == null)
            return Optional.empty();
        return Optional.ofNullable(E.Language.fromDisplayName((String) sel));
    }

    /**
     * Rebuilds the mod selector without firing ActionListeners during the rebuild.
     * Restores the previously selected mod if it still exists.
     */
    protected void refreshModSelector() {

        var previous = getSelectedMod();
        var model = (DefaultComboBoxModel<String>) modSelector.getModel();

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
                model.addElement("    " + mod.getName());

            previous.ifPresent(m -> modSelector.setSelectedItem("    " + m.getName()));
        }

        for (ActionListener l : listeners)
            modSelector.addActionListener(l);
    }

    protected void refreshLangSelector() {

        var listeners = langSelector.getActionListeners();
        for (var l : listeners)
            langSelector.removeActionListener(l);

        langSelector.removeAllItems();

        var defLang = window.getRegistry().userConfig.getLanguage();

        for (var lang : E.Language.getAllLang())
            langSelector.addItem(lang);

        langSelector.setSelectedItem(defLang.getDisplayName());

        for (var l : listeners)
            langSelector.addActionListener(l);
    }

    protected JPopupMenu buildItemPopupMenu(Supplier<ModItem> itemSupplier, boolean showEditItem, boolean showEditLang) {

        var menu = new JPopupMenu();

        JMenuItem copyId = new JMenuItem(getLocalText("ui_copy_id"));
        copyId.addActionListener(e -> {
            var item = itemSupplier.get();
            if (item != null) {
                Util.copyText(item.getId());
                window.snackbar.show("ui_copied_id", BarManager.Type.INFO, item.getId());
            }
        });
        menu.add(copyId);

        JMenuItem copyAll = new JMenuItem(getLocalText("ui_copy_all_details"));
        copyAll.addActionListener(e -> {
            var item = itemSupplier.get();
            if (item != null) {
                Util.copyText(item.details());
                window.snackbar.show("ui_copied_all_details", BarManager.Type.INFO);
            }
        });
        menu.add(copyAll);

        menu.addSeparator();

        if (showEditItem) {
            var editItem = new JMenuItem(getLocalText("ui_edit_item"));
            editItem.addActionListener(e -> {
                var item = itemSupplier.get();
                if (item == null)
                    return;
                if (item instanceof Storm stormItem)
                    window.navigate(Page.STORM, stormItem);
                else
                    window.navigate(Page.ITEM_EDIT, item);
            });
            menu.add(editItem);

            var addToMod = new JMenuItem(getLocalText("ui_add_to_mod"));
            addToMod.addActionListener(e -> {
                var item = itemSupplier.get();
                if (item != null)
                    showAddToModDialog(item);
            });
            menu.add(addToMod);
        }

        if (showEditLang) {
            var editLang = new JMenuItem(getLocalText("ui_edit_lang"));
            editLang.addActionListener(e -> {
                var item = itemSupplier.get();
                if (item != null)
                    window.navigate(Page.LANG_EDIT, item);
            });
            menu.add(editLang);
        }

        return menu;
    }

    protected MouseAdapter mouseClicked(ModItem item) {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && item != null) {
                    Util.copyText(item.getId());
                    window.snackbar.show("ui_copied_id", BarManager.Type.INFO, item.getId());
                }
            }
        };
    }

    protected void showAddToModDialog(ModItem item) {
        var dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), getLocalText("ui_add_to_mod_title"), true);
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
            modCombo.addItem(mod.getName());
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
                window.snackbar.show("ui_select_mod_first", BarManager.Type.WARNING);
                return;
            }
            var mod = ModService.modCollection.stream().filter(m -> m.getName().equals(sel)).findFirst();
            mod.ifPresentOrElse(m -> {
                var copy = ModItemBuilder.deepCopy(item, m);
                m.addItem(copy);
                window.snackbar.show("ui_item_added_to_mod", BarManager.Type.SUCCESS, m.getName());
                dialog.dispose();
            }, () -> window.snackbar.show("ui_select_mod_first", BarManager.Type.WARNING));
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

    public void refresh(Page source, Object... input) {
        sourcePage = source;
    }
    protected void refresh() {
        this.refresh(sourcePage, (Object) null);
    }
    /**
     * Navigate away from this page.  Implementations should guard with an
     * unsaved-changes dialog when {@code hasChanges} is true.
     */
    protected void navigateBack() {
        if (confirmDiscard())
            window.navigate(sourcePage);
    }
    protected boolean confirmDiscard() {
        return true;
    }
}