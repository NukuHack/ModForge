package com.nukuhack.modforge.frontend.pages;

import com.nukuhack.modforge.backend.model.ModItem;
import com.nukuhack.modforge.frontend.MainWindow;
import com.nukuhack.modforge.frontend.Page;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 *
 * =============================================================================
 * BASE ITEM-EDIT PAGE
 * <p>
 * Shared structural skeleton for ItemEdit and LangEdit:
 * <p>
 * ┌─────────────────────────────────────────────────────────┐
 * │  [breadcrumb]               [right toolbar actions]     │  ← buildTopBar()
 * ├──────────────────────┬──────────────────────────────────┤
 * │  [left form panel]   │  [live preview]                  │  ← buildCenter()
 * ├─────────────────────────────────────────────────────────┤
 * │  [status label]                  [action buttons]       │  ← buildBottomBar()
 * └─────────────────────────────────────────────────────────┘
 * =============================================================================
 */
@Slf4j
public abstract class BaseEditPage extends BasePage {

    protected ModItem currentItem = null;
    protected boolean hasChanges = false;

    protected final JEditorPane previewPane = new JEditorPane();
    protected final JLabel statusLabel = new JLabel(" ");

    protected BaseEditPage(MainWindow w) {
        super(w);
        setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        setLayout(new BorderLayout(0, 16));

    }

    /**
     * Wire up the three-zone layout.  Must be called by each subclass
     * constructor <em>after</em> its own fields are ready, e.g.:
     * <pre>
     *   public ItemEdit(MainWindow w) {
     *       super(w);
     *       attributesPanel = new JPanel(...);
     *       initUI();
     *   }
     * </pre>
     */
    protected final void initUI() {
        buildUI();
    }

    /**
     * Text shown as the second crumb, e.g. "Item Edit" or "Lang Edit".
     */
    protected abstract String getPageTitle();

    /**
     * Widgets placed on the right side of the top bar (selectors, buttons).
     */
    protected abstract JPanel buildRightActions();

    /**
     * The scrollable panel that forms the left half of the split pane.
     * Subclasses create and return their own form/attribute panel here.
     */
    protected abstract JPanel buildFormPanel();

    /**
     * Title of the left-panel's titled border.
     */
    protected abstract String getFormPanelTitle();

    /**
     * Title of the preview panel's titled border.
     */
    protected abstract String getPreviewTitle();

    /**
     * Buttons placed in the bottom-right cluster (Save, Back, …).
     * Build and return a fully configured panel.
     */
    protected abstract JPanel buildActionButtons();

    /**
     * Called after {@link #setCurrentItem(ModItem)} has stored the new item.
     * Subclasses should refresh their form content and re-populate selectors.
     */
    protected abstract void onItemSet(ModItem item);

    /**
     * Regenerate the HTML shown in {@link #previewPane}.
     */
    protected abstract void updatePreview();

    public final void setCurrentItem(ModItem item) {
        this.currentItem = item;
        this.hasChanges = false;
        onItemSet(item);
        updatePreview();
        updateStatus();
    }

    @Override
    public void refresh(Page source, Object... input) {
        super.refresh(source, input);
        refreshModSelector();
        if (input.length > 0 && input[0] instanceof ModItem item)
            setCurrentItem(item);
        else
            navigateBack();
    }

    protected void markChanged() {
        hasChanges = true;
        updateStatus();
    }

    protected void updateStatus() {
        if (hasChanges) {
            statusLabel.setText(MainWindow.getLocalText("ui_unsaved_changes"));
            statusLabel.setForeground(new Color(0xf9e2af));
        } else if (currentItem != null) {
            statusLabel.setText(MainWindow.getLocalText("ui_no_pending_changes"));
            statusLabel.setForeground(new Color(0xa6e3a1));
        } else {
            statusLabel.setText(" ");
        }
    }

    /**
     * Show a discard-changes dialog when {@code hasChanges} is true.
     *
     * @return {@code true} if navigation may proceed, {@code false} if the user
     * chose to stay on the page.
     */
    protected boolean confirmDiscard() {
        if (!hasChanges)
            return true;
        int choice = JOptionPane.showConfirmDialog(this, MainWindow.getLocalText("ui_discard_changes_confirm"), MainWindow.getLocalText("ui_unsaved_changes_title"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            hasChanges = false;
            return true;
        }
        return false;
    }

    private void buildUI() {
        add(buildTopBar(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildBottomBar(), BorderLayout.SOUTH);
    }

    /**
     * Top bar: clickable breadcrumb on the left, subclass toolbar on the right.
     *
     * <pre>
     *  Items  ›  {pageTitle}  {itemId}         [right actions …]
     * </pre>
     */
    private JPanel buildTopBar() {
        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setOpaque(false);

        JPanel breadcrumbPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        breadcrumbPanel.setOpaque(false);

        JLabel pageTitleLabel = new JLabel(MainWindow.getLocalText(getPageTitle()));
        pageTitleLabel.setForeground(MainWindow.TEXT);
        pageTitleLabel.setFont(new Font("Roboto", Font.BOLD, 22));

        breadcrumbPanel.add(pageTitleLabel);

        top.add(breadcrumbPanel, BorderLayout.WEST);
        top.add(buildRightActions(), BorderLayout.EAST);
        return top;
    }

    /**
     * Center: horizontal split — scrollable form (left) | live preview (right).
     */
    private JSplitPane buildCenter() {

        var formScroll = new JScrollPane(buildFormPanel());
        formScroll.setBackground(MainWindow.SURFACE);
        formScroll.getViewport().setBackground(MainWindow.SURFACE);
        formScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(0x313244)), getFormPanelTitle(), TitledBorder.LEFT, TitledBorder.TOP, new Font("Roboto", Font.BOLD, 12), MainWindow.ACCENT));
        formScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        formScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        previewPane.setContentType("text/html");
        previewPane.setEditable(false);
        previewPane.setBackground(new Color(0x181825));
        previewPane.setForeground(MainWindow.TEXT);
        previewPane.setFont(new Font("Roboto", Font.PLAIN, 12));
        previewPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        var previewScroll = new JScrollPane(previewPane);
        previewScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(0x313244)), getPreviewTitle(), TitledBorder.LEFT, TitledBorder.TOP, new Font("Roboto", Font.BOLD, 12), MainWindow.ACCENT));
        previewScroll.setBackground(MainWindow.SURFACE);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, formScroll, previewScroll);
        split.setResizeWeight(0.6);
        split.setDividerSize(4);
        split.setBackground(MainWindow.BG);
        split.setBorder(BorderFactory.createEmptyBorder());
        return split;
    }

    /**
     * Bottom bar: status label (left) | subclass action buttons (right).
     */
    private JPanel buildBottomBar() {
        var bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        statusLabel.setFont(new Font("Roboto", Font.ITALIC, 11));
        statusLabel.setForeground(new Color(0xa6e3a1));

        bar.add(statusLabel, BorderLayout.WEST);
        bar.add(buildActionButtons(), BorderLayout.EAST);
        return bar;
    }
}