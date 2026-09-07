package com.utsusynth.utsu.view.config;

import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.ResourceBundle;

/**
 * Preferences tab for this fork's Windows shortcut integration. The installer's own
 * "--win-shortcut-prompt" screen only offers this choice once, at install time -- this tab lets
 * it be changed afterward too, without reinstalling, by creating or deleting the desktop .lnk
 * file directly for whichever build is currently running.
 */
public class WindowsShortcutPreferencesEditor extends PreferencesEditor {
    private String displayName = "Windows Shortcut";
    private BorderPane view;
    private Label description;
    private CheckBox desktopShortcutCheckbox;
    private Label statusLabel;

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    protected void setDisplayNameInternal(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public BorderPane getView() {
        return view;
    }

    @Override
    protected void setViewInternal(BorderPane view) {
        this.view = view;
    }

    @Override
    protected Node initializeInternal() {
        description = new Label(
                "Controls the desktop shortcut for this specific build ("
                        + getShortcutBaseName() + "). Installing a different build does not "
                        + "affect this setting for other builds.");
        description.setWrapText(true);
        description.setMaxWidth(280);
        GridPane.setValignment(description, VPos.TOP);

        desktopShortcutCheckbox = new CheckBox("Create a desktop shortcut for this build");
        desktopShortcutCheckbox.setSelected(getShortcutFile().exists());

        statusLabel = new Label("");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(280);

        GridPane viewInternal = new GridPane();
        viewInternal.setHgap(10);
        viewInternal.setVgap(10);
        viewInternal.add(description, 0, 0);
        viewInternal.add(desktopShortcutCheckbox, 0, 1);
        viewInternal.add(statusLabel, 0, 2);
        return viewInternal;
    }

    @Override
    public boolean onCloseEditor(Stage stage) {
        return true;
    }

    @Override
    public void savePreferences() {
        boolean shouldExist = desktopShortcutCheckbox.isSelected();
        boolean currentlyExists = getShortcutFile().exists();
        if (shouldExist == currentlyExists) {
            return; // Nothing to do.
        }
        try {
            if (shouldExist) {
                createDesktopShortcut();
            } else {
                if (!getShortcutFile().delete()) {
                    statusLabel.setText("Could not remove the desktop shortcut.");
                }
            }
        } catch (IOException | InterruptedException e) {
            statusLabel.setText("Could not update the desktop shortcut: " + e.getMessage());
        }
    }

    @Override
    public void revertToPreferences() {
        desktopShortcutCheckbox.setSelected(getShortcutFile().exists());
    }

    /**
     * Full path to this specific build's own native launcher executable, as jpackage created it.
     * Derived from the currently running process, so this always matches whichever build the
     * user is actually running -- never hardcoded, never another build's shortcut.
     */
    private String getRunningExecutablePath() {
        return ProcessHandle.current().info().command().orElse("");
    }

    private String getShortcutBaseName() {
        String exePath = getRunningExecutablePath();
        String fileName = new File(exePath).getName();
        if (fileName.toLowerCase().endsWith(".exe")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }
        return fileName.isEmpty() ? "Utsu2" : fileName;
    }

    private File getShortcutFile() {
        String desktop = System.getProperty("user.home") + File.separator + "Desktop";
        return new File(desktop, getShortcutBaseName() + ".lnk");
    }

    private void createDesktopShortcut() throws IOException, InterruptedException {
        String exePath = getRunningExecutablePath();
        if (exePath.isEmpty()) {
            statusLabel.setText("Could not determine this build's own program location.");
            return;
        }
        File shortcutFile = getShortcutFile();
        File workingDir = new File(exePath).getParentFile();
        String script = "$WshShell = New-Object -ComObject WScript.Shell\r\n"
                + "$Shortcut = $WshShell.CreateShortcut('" + shortcutFile.getAbsolutePath().replace("'", "''") + "')\r\n"
                + "$Shortcut.TargetPath = '" + exePath.replace("'", "''") + "'\r\n"
                + "$Shortcut.WorkingDirectory = '"
                + (workingDir == null ? "" : workingDir.getAbsolutePath().replace("'", "''")) + "'\r\n"
                + "$Shortcut.Save()\r\n";
        File scriptFile = File.createTempFile("utsu2-desktop-shortcut", ".ps1");
        scriptFile.deleteOnExit();
        java.nio.file.Files.writeString(scriptFile.toPath(), script,
                java.nio.charset.StandardCharsets.UTF_8);
        Process process = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-File", scriptFile.getAbsolutePath())
                .start();
        int exitCode = process.waitFor();
        if (exitCode != 0 || !shortcutFile.exists()) {
            statusLabel.setText("Could not create the desktop shortcut.");
        }
    }
}
