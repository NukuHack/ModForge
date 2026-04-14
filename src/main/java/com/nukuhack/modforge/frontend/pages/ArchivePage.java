package com.nukuhack.modforge.frontend.pages;

import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.frontend.BarManager;
import com.nukuhack.modforge.frontend.MainWindow;
import com.nukuhack.util.IOUtil;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static com.nukuhack.modforge.frontend.MainWindow.getLocalText;

@Slf4j
public class ArchivePage extends BasePage {

    // ── Mode ─────────────────────────────────────────────────────────────────
    /** true = decompress (file → folder), false = compress (folder → file) */
    private boolean decompressMode = true;

    // ── State ────────────────────────────────────────────────────────────────
    private String inputPath  = null;
    private String outputPath = null;

    // ── UI elements that need to be referenced after construction ────────────
    private final JLabel inputLabel;
    private final JLabel outputLabel;
    private final JLabel inputHint;
    private final JLabel outputHint;
    private final JButton inputBrowseBtn;
    private final JButton outputBrowseBtn;
    private final JButton toggleModeBtn;
    private final JButton goBtn;
    private final JButton openOutputBtn;

    // ── Colours (reuse from MainWindow palette) ───────────────────────────────
    private static final Color SURFACE2  = new Color(0x313244);
    private static final Color BG_FIELD  = new Color(0x1e1e2e);
    private static final Color BORDER    = new Color(0x2a2a3a);

    public ArchivePage(MainWindow w) {
        super(w);
        setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        setLayout(new BorderLayout(0, 16));

        // ── Page header ───────────────────────────────────────────────────────
        add(header("ui_archive_title"), BorderLayout.NORTH);

        // ── Card ──────────────────────────────────────────────────────────────
        JPanel card = card(null); // no card-level title; we put our own content
        card.setLayout(new BorderLayout(0, 0));

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);

        GridBagConstraints gc = new GridBagConstraints();
        gc.fill    = GridBagConstraints.HORIZONTAL;
        gc.insets  = new Insets(8, 4, 8, 4);
        gc.weightx = 1.0;

        // ── Row 0 – description ───────────────────────────────────────────────
        gc.gridx = 0; gc.gridy = 0; gc.gridwidth = 4;
        content.add(muted("ui_archive_description"), gc);

        // ── Row 1 – mode toggle ───────────────────────────────────────────────
        gc.gridy = 1; gc.gridwidth = 1; gc.weightx = 0;
        gc.gridx = 0;
        toggleModeBtn = new JButton(modeLabel());
        styleSecondaryBtn(toggleModeBtn);
        toggleModeBtn.addActionListener(e -> toggleMode());
        content.add(toggleModeBtn, gc);

        gc.gridx = 1; gc.gridwidth = 3; gc.weightx = 1.0;
        inputHint = new JLabel(inputHintText());
        inputHint.setForeground(MainWindow.MUTED);
        inputHint.setFont(new Font("Roboto", Font.ITALIC, 12));
        content.add(inputHint, gc);

        // ── Row 2 – separator label ───────────────────────────────────────────
        gc.gridy = 2; gc.gridx = 0; gc.gridwidth = 4; gc.weightx = 1.0;
        JLabel inputSectionLabel = new JLabel(getLocalText("ui_archive_input"));
        inputSectionLabel.setForeground(MainWindow.ACCENT);
        inputSectionLabel.setFont(new Font("Roboto", Font.BOLD, 13));
        content.add(inputSectionLabel, gc);

        // ── Row 3 – input selector row ────────────────────────────────────────
        gc.gridy = 3; gc.gridwidth = 1; gc.weightx = 0;
        gc.gridx = 0;
        inputBrowseBtn = primaryBtn("ui_browse", e -> browseInput());
        inputBrowseBtn.setPreferredSize(new Dimension(110, 32));
        content.add(inputBrowseBtn, gc);

        gc.gridx = 1; gc.gridwidth = 3; gc.weightx = 1.0;
        inputLabel = pathLabel(getLocalText("ui_archive_no_input"));
        content.add(inputLabel, gc);

        // ── Row 4 – output section label ─────────────────────────────────────
        gc.gridy = 4; gc.gridx = 0; gc.gridwidth = 4; gc.weightx = 1.0;
        JLabel outputSectionLabel = new JLabel(getLocalText("ui_archive_output"));
        outputSectionLabel.setForeground(MainWindow.ACCENT);
        outputSectionLabel.setFont(new Font("Roboto", Font.BOLD, 13));
        content.add(outputSectionLabel, gc);

        // ── Row 5 – output selector row ───────────────────────────────────────
        gc.gridy = 5; gc.gridwidth = 1; gc.weightx = 0;
        gc.gridx = 0;
        outputBrowseBtn = primaryBtn("ui_browse", e -> browseOutput());
        outputBrowseBtn.setPreferredSize(new Dimension(110, 32));
        content.add(outputBrowseBtn, gc);

        gc.gridx = 1; gc.gridwidth = 3; gc.weightx = 1.0;
        outputLabel = pathLabel(getLocalText("ui_archive_no_output"));
        content.add(outputLabel, gc);

        // ── Row 6 – output hint ───────────────────────────────────────────────
        gc.gridy = 6; gc.gridx = 0; gc.gridwidth = 4; gc.weightx = 1.0;
        outputHint = new JLabel(outputHintText());
        outputHint.setForeground(MainWindow.MUTED);
        outputHint.setFont(new Font("Roboto", Font.ITALIC, 12));
        content.add(outputHint, gc);

        // ── Row 7 – action buttons ────────────────────────────────────────────
        gc.gridy = 7; gc.gridwidth = 1; gc.weightx = 0;

        gc.gridx = 0;
        openOutputBtn = new JButton(getLocalText("ui_open_folder"));
        styleSecondaryBtn(openOutputBtn);
        openOutputBtn.setEnabled(false);
        openOutputBtn.addActionListener(e -> openOutput());
        content.add(openOutputBtn, gc);

        gc.gridx = 1; gc.weightx = 1.0; gc.gridwidth = 2;
        content.add(Box.createHorizontalGlue(), gc);

        gc.gridx = 3; gc.weightx = 0; gc.gridwidth = 1;
        goBtn = primaryBtn("ui_go", e -> runOperation());
        goBtn.setPreferredSize(new Dimension(110, 36));
        goBtn.setFont(new Font("Roboto", Font.BOLD, 15));
        goBtn.setEnabled(false);
        goBtn.setBackground(SURFACE2);
        goBtn.setForeground(new Color(0x6c6f85));
        content.add(goBtn, gc);

        // ── Row 8 – vertical filler ───────────────────────────────────────────
        gc.gridy = 8; gc.gridx = 0; gc.gridwidth = 4;
        gc.weightx = 1.0; gc.weighty = 1.0;
        gc.fill = GridBagConstraints.BOTH;
        content.add(Box.createVerticalGlue(), gc);

        card.add(content, BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);

        // ── South – back button ───────────────────────────────────────────────
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bottom.setOpaque(false);
        bottom.add(primaryBtn("ui_back", e -> window.navigate(MainWindow.Page.HOME)));
        add(bottom, BorderLayout.SOUTH);
    }

    // ── BasePage contract ─────────────────────────────────────────────────────

    @Override
    public void refresh(Object... input) {
        // Nothing to reload from external state on each navigation
    }

    // ── Mode helpers ──────────────────────────────────────────────────────────

    private void toggleMode() {
        decompressMode = !decompressMode;

        // Clear paths because the required types changed
        inputPath  = null;
        outputPath = null;
        inputLabel.setText(getLocalText("ui_archive_no_input"));
        inputLabel.setForeground(MainWindow.MUTED);
        outputLabel.setText(getLocalText("ui_archive_no_output"));
        outputLabel.setForeground(MainWindow.MUTED);

        toggleModeBtn.setText(modeLabel());
        inputHint.setText(inputHintText());
        outputHint.setText(outputHintText());

        refreshGoButton();
    }

    private String modeLabel() {
        return decompressMode
                ? getLocalText("ui_archive_mode_decompress")
                : getLocalText("ui_archive_mode_compress");
    }

    private String inputHintText() {
        return decompressMode
                ? getLocalText("ui_archive_input_hint_decompress")
                : getLocalText("ui_archive_input_hint_compress");
    }

    private String outputHintText() {
        return decompressMode
                ? getLocalText("ui_archive_output_hint_decompress")
                : getLocalText("ui_archive_output_hint_compress");
    }

    // ── Browse callbacks ──────────────────────────────────────────────────────

    private void browseInput() {
        if (decompressMode) {
            // Decompress mode: input must be a file (archive)
            Util.pickAsync(
                    getLocalText("ui_archive_pick_archive"),
                    JFileChooser.FILES_ONLY,
                    null,
                    getLocalText("ui_archive_pick_archive_desc")
            ).thenAccept(selected -> {
                if (selected == null) return;

                // Validate: must be a ZIP-like archive
                Path p = Path.of(selected);
                if (!IOUtil.isZipLike(p)) {
                    SwingUtilities.invokeLater(() ->
                            window.snackbar.show("ui_archive_not_archive", BarManager.Type.WARNING, selected));
                    return;
                }

                SwingUtilities.invokeLater(() -> {
                    inputPath = selected;
                    inputLabel.setText(selected);
                    inputLabel.setForeground(MainWindow.TEXT);
                    refreshGoButton();
                });
            });
        } else {
            // Compress mode: input must be a folder
            Util.pickAsync(
                    getLocalText("ui_archive_pick_folder"),
                    JFileChooser.DIRECTORIES_ONLY,
                    null,
                    getLocalText("ui_archive_pick_folder_desc")
            ).thenAccept(selected -> {
                if (selected == null) return;
                SwingUtilities.invokeLater(() -> {
                    inputPath = selected;
                    inputLabel.setText(selected);
                    inputLabel.setForeground(MainWindow.TEXT);
                    refreshGoButton();
                });
            });
        }
    }

    private void browseOutput() {
        if (decompressMode) {
            // Decompress mode: output must be a folder
            Util.pickAsync(
                    getLocalText("ui_archive_pick_output_folder"),
                    JFileChooser.DIRECTORIES_ONLY,
                    null,
                    getLocalText("ui_archive_pick_output_folder_desc")
            ).thenAccept(selected -> {
                if (selected == null) return;
                SwingUtilities.invokeLater(() -> {
                    outputPath = selected;
                    outputLabel.setText(selected);
                    outputLabel.setForeground(MainWindow.TEXT);
                    refreshGoButton();
                });
            });
        } else {
            // Compress mode: output is a file path (user types/selects save location)
            Util.pickAsync(
                    getLocalText("ui_archive_pick_output_file"),
                    JFileChooser.FILES_ONLY,
                    null,
                    getLocalText("ui_archive_pick_output_file_desc")
            ).thenAccept(selected -> {
                if (selected == null) return;
                // Ensure .pak extension for compress output
                String finalPath = selected.endsWith(".pak") || selected.endsWith(".zip")
                        ? selected
                        : selected + ".pak";
                SwingUtilities.invokeLater(() -> {
                    outputPath = finalPath;
                    outputLabel.setText(finalPath);
                    outputLabel.setForeground(MainWindow.TEXT);
                    refreshGoButton();
                });
            });
        }
    }

    // ── Go button state ───────────────────────────────────────────────────────

    private void refreshGoButton() {
        boolean ready = inputPath != null && outputPath != null;
        goBtn.setEnabled(ready);
        goBtn.setBackground(ready ? MainWindow.ACCENT : SURFACE2);
        goBtn.setForeground(ready ? new Color(0x1e1e2e) : new Color(0x6c6f85));
    }

    // ── Operation ─────────────────────────────────────────────────────────────

    private void runOperation() {
        if (inputPath == null || outputPath == null) return;

        goBtn.setEnabled(false);
        goBtn.setText(getLocalText("ui_loading"));

        boolean mode = decompressMode;
        String  in   = inputPath;
        String  out  = outputPath;

        CompletableFuture.runAsync(() -> {
            boolean success;
            try {
                if (mode) {
                    // Decompress: archive file → destination folder
                    success = IOUtil.unpackArchive(
                            Path.of(in),
                            Path.of(out),
                            null,   // no filter – extract everything
                            true    // overwrite existing files
                    );
                } else {
                    // Compress: source folder → archive file
                    success = IOUtil.packFolder(
                            Path.of(in),
                            Path.of(out),
                            null,   // no filter – pack everything
                            false   // keep metadata
                    );
                }
            } catch (Exception ex) {
                log.error("Archive operation failed", ex);
                success = false;
            }

            final boolean result = success;
            SwingUtilities.invokeLater(() -> showResult(result, mode, in, out));
        });
    }

    private void showResult(boolean success, boolean wasDecompress, String in, String out) {
        goBtn.setText(getLocalText("ui_go"));
        refreshGoButton();

        if (success) {
            openOutputBtn.setEnabled(true);
            window.snackbar.show("ui_archive_success", BarManager.Type.SUCCESS,
                    getLocalText(wasDecompress ? "ui_archive_mode_decompress" : "ui_archive_mode_compress"));

            String msg = String.format(
                    "<html><b>%s</b><br/><br/>%s: <tt>%s</tt><br/>%s: <tt>%s</tt></html>",
                    getLocalText("ui_archive_done_title"),
                    getLocalText("ui_archive_input"),  in,
                    getLocalText("ui_archive_output"), out
            );
            JOptionPane.showMessageDialog(this, msg,
                    getLocalText("ui_archive_done_title"), JOptionPane.INFORMATION_MESSAGE);
        } else {
            window.snackbar.show("ui_archive_failed", BarManager.Type.ERROR);

            String msg = String.format(
                    "<html><b>%s</b><br/><br/>%s: <tt>%s</tt><br/>%s: <tt>%s</tt><br/><br/>%s</html>",
                    getLocalText("ui_archive_failed"),
                    getLocalText("ui_archive_input"),  in,
                    getLocalText("ui_archive_output"), out,
                    getLocalText("ui_archive_failed_reason")
            );
            JOptionPane.showMessageDialog(this, msg,
                    getLocalText("ui_archive_error_title"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openOutput() {
        if (outputPath == null || outputPath.isBlank()) return;
        java.io.File f = new java.io.File(outputPath);
        String dir = f.isDirectory() ? f.getAbsolutePath() : f.getParent();
        Util.openDirectory(this, dir);
    }

    // ── Widget helpers ────────────────────────────────────────────────────────

    /** Creates a read-only styled path display label, matching the ConvertPage style. */
    private static JLabel pathLabel(String placeholder) {
        JLabel l = new JLabel(placeholder);
        l.setForeground(MainWindow.MUTED);
        l.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
        l.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        l.setOpaque(true);
        l.setBackground(BG_FIELD);
        return l;
    }

    private static void styleSecondaryBtn(JButton b) {
        b.setBackground(SURFACE2);
        b.setForeground(MainWindow.ACCENT);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setFont(new Font("Roboto", Font.BOLD, 12));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(160, 32));
    }
}