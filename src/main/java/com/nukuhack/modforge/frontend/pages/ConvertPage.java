package com.nukuhack.modforge.frontend.pages;

import com.nukuhack.modforge.Util;
import com.nukuhack.modforge.backend.service.IconService;
import com.nukuhack.modforge.frontend.BarManager;
import com.nukuhack.modforge.frontend.MainWindow;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.concurrent.CompletableFuture;

import static com.nukuhack.modforge.frontend.MainWindow.getLocalText;

@Slf4j
public class ConvertPage extends BasePage {

    private final JLabel pathLabel = new JLabel(getLocalText("ui_no_path_selected"));
    private final JButton folderBtn = new JButton(getLocalText("ui_open_folder"));
    private final JButton toggleBtn;
    private final JButton goBtn = primaryBtn("ui_go", e -> runConversion());
    private String selectedPath = null;
    private boolean ddsToPng = true;
    public ConvertPage(MainWindow w) {
        super(w);
        setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        setLayout(new BorderLayout(0, 16));

        add(header("ui_convert_images"), BorderLayout.NORTH);

        JPanel card = card("DDS  ↔  PNG");

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);

        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(10, 4, 10, 4);

        gc.gridx = 0;
        gc.gridy = 0;
        gc.gridwidth = 4;
        gc.weightx = 1.0;
        JLabel desc = muted("ui_image_instruction");
        content.add(desc, gc);

        gc.gridy = 1;
        gc.gridwidth = 1;

        gc.gridx = 0;
        gc.weightx = 0;
        JButton browseBtn = primaryBtn("ui_browse", e -> Util.pickAsync(getLocalText("ui_select_convert_target"), JFileChooser.FILES_AND_DIRECTORIES, null, getLocalText("ui_select_what_to_convert")).thenAccept(selected -> {
            selectedPath = selected;
            pathLabel.setText(selected);
            pathLabel.setForeground(MainWindow.TEXT);
            setBtnEnabled(true);
        }));
        browseBtn.setPreferredSize(new Dimension(110, 32));
        content.add(browseBtn, gc);

        gc.gridx = 1;
        gc.weightx = 1.0;
        gc.gridwidth = 3;
        pathLabel.setForeground(MainWindow.MUTED);
        pathLabel.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
        pathLabel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x2a2a3a), 1), BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        pathLabel.setOpaque(true);
        pathLabel.setBackground(new Color(0x1e1e2e));
        content.add(pathLabel, gc);

        gc.gridy = 2;
        gc.gridwidth = 1;
        gc.weightx = 0;

        gc.gridx = 0;
        toggleBtn = new JButton(ddsToPng ? "DDS → PNG" : "PNG → DDS");
        styleButton(toggleBtn);
        toggleBtn.addActionListener(e -> {
            ddsToPng = !ddsToPng;
            toggleBtn.setText(ddsToPng ? "DDS → PNG" : "PNG → DDS");
        });
        content.add(toggleBtn, gc);

        gc.gridx = 1;
        gc.weightx = 0;
        styleButton(folderBtn);
        folderBtn.addActionListener(e -> openSelectedFolder());
        folderBtn.setBackground(new Color(0x313244));
        folderBtn.setForeground(MainWindow.ACCENT);
        folderBtn.setFont(new Font("Roboto", Font.PLAIN, 12));
        content.add(folderBtn, gc);

        gc.gridx = 2;
        gc.weightx = 1.0;
        content.add(Box.createHorizontalGlue(), gc);

        gc.gridx = 3;
        gc.weightx = 0;
        goBtn.setPreferredSize(new Dimension(90, 36));
        goBtn.setFont(new Font("Roboto", Font.BOLD, 15));
        setBtnEnabled(false);

        content.add(goBtn, gc);

        gc.gridx = 0;
        gc.gridy = 3;
        gc.gridwidth = 4;
        gc.weightx = 1.0;
        gc.weighty = 1.0;
        gc.fill = GridBagConstraints.BOTH;
        content.add(Box.createVerticalGlue(), gc);

        card.add(content, BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bottom.setOpaque(false);
        bottom.add(primaryBtn("ui_back", e -> window.navigate(MainWindow.Page.HOME)));
        add(bottom, BorderLayout.SOUTH);
    }

    @Override
    public void refresh(Object... input) {

    }

    private void styleButton(JButton b) {
        b.setBackground(new Color(0x313244));
        b.setForeground(MainWindow.ACCENT);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setFont(new Font("Roboto", Font.BOLD, 12));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(160, 32));
    }

    private void setBtnEnabled(boolean enabled) {
        goBtn.setEnabled(enabled);
        goBtn.setBackground(enabled ? MainWindow.ACCENT : new Color(0x313244));
        Color foreground = enabled ? new Color(0x1e1e2e) : new Color(0x6c6f85);
        goBtn.setForeground(foreground);

        folderBtn.setEnabled(enabled);
        folderBtn.setBackground(enabled ? MainWindow.ACCENT : new Color(0x313244));
        folderBtn.setForeground(foreground);
    }

    private void openSelectedFolder() {
        if (selectedPath == null || selectedPath.isBlank())
            return;
        var f = new File(selectedPath);
        String dirToOpen = f.isDirectory() ? f.getAbsolutePath() : f.getParent();
        Util.openDirectory(this, dirToOpen);
    }

    private void runConversion() {
        if (selectedPath == null || selectedPath.isBlank())
            return;

        goBtn.setEnabled(false);
        goBtn.setText(getLocalText("ui_loading"));

        var toPng = this.ddsToPng;
        var path = this.selectedPath;

        CompletableFuture.runAsync(() -> {
            try {
                IconService.convertImages(path, toPng);
                SwingUtilities.invokeLater(() -> showResult(true, toPng, null));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> showResult(false, toPng, ex.getMessage()));
            }
        });
    }

    private void showResult(boolean success, boolean wasToPng, String errorMsg) {

        goBtn.setText(getLocalText("ui_go"));
        setBtnEnabled(selectedPath != null);

        String directionSimple = wasToPng ? "DDS → PNG" : "PNG → DDS";

        if (success) {
            window.snackbar.show("ui_conversion_complete", BarManager.Type.SUCCESS, directionSimple);

            String message = String.format("<html><b>%s</b><br/><br/>" + "%s: <tt>%s</tt><br/>" + "%s: <tt>%s</tt><br/><br/>" + "%s</html>", getLocalText("ui_conversion_complete"), getLocalText("ui_conversion_direction"), directionSimple, getLocalText("ui_conversion_source"), selectedPath, getLocalText("ui_conversion_output_info"));

            JOptionPane.showMessageDialog(this, message, getLocalText("ui_conversion_done_title"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        window.snackbar.show("ui_conversion_failed", BarManager.Type.ERROR);

        String reason = (errorMsg != null && !errorMsg.isBlank()) ? errorMsg : getLocalText("ui_conversion_unknown_error");

        String message = String.format("<html><b>%s</b><br/><br/>" + "%s: <tt>%s</tt><br/>" + "%s: <tt>%s</tt><br/><br/>" + "<b>%s:</b><br/><tt>%s</tt></html>", getLocalText("ui_conversion_failed"), getLocalText("ui_conversion_direction"), directionSimple, getLocalText("ui_conversion_source"), selectedPath, getLocalText("ui_conversion_reason"), reason);

        JOptionPane.showMessageDialog(this, message, getLocalText("ui_conversion_error_title"), JOptionPane.ERROR_MESSAGE);
    }




}