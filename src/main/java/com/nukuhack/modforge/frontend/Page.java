package com.nukuhack.modforge.frontend;

import com.nukuhack.modforge.frontend.pages.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Enum of all navigable pages in the application.
 *
 * <p>Extracted from {@link MainWindow} so that any class can import just this
 * enum without pulling in the entire window.  {@link MainWindow} re-exports it
 * as a type alias for backwards-compat:
 * <pre>
 *   public static final class Page extends AppPage {} // not needed — just use AppPage
 * </pre>
 *
 * Navigation is always performed through {@link MainWindow#navigate(Page, Object...)}.
 */
@Getter
@AllArgsConstructor
public enum Page {

    HOME        ("ui_home",               HomePage.class),
    MODS        ("ui_mods",               ModsPage.class),
    MOD_EDIT    ("ui_mod_edit",           ModEditPage.class),
    ITEMS       ("ui_items",              ItemsPage.class),
    STORM       ("ui_storm",             StormPage.class),
    ITEM_EDIT   ("ui_item_edit",          ItemEdit.class),
    LANG_EDIT   ("ui_localization_edit",  LangEdit.class),
    SETTINGS    ("ui_settings",           SettingsPage.class),
    LANG        ("ui_localization",       LangPage.class),
    ARCHIVE     ("ui_archive_title",      ArchivePage.class),
    CONVERT     ("ui_image_convert",      ConvertPage.class),
    KCD_CONVERTER("ui_kcd_title",         KCDConverterPage.class),
    ;

    private final String displayName;
    private final Class<? extends BasePage> pageClass;

    /** Set once during {@link MainWindow#initializePages()}. */
    @Setter
    static BasePage instance;

    /** Convenience: does this page require a {@link com.nukuhack.modforge.backend.model.ModItem} argument? */
    public boolean isEditPage() {
        return this == ITEM_EDIT || this == LANG_EDIT || this == STORM;
    }

    /** Pages shown in the sidebar navigation. */
    public static Page[] sidebarPages() {
        return new Page[]{ HOME, MODS, ITEMS, LANG, ARCHIVE, CONVERT, KCD_CONVERTER };
    }
}