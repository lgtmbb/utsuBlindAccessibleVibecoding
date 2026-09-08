package com.utsusynth.utsu.view.config;

import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.prefs.Preferences;
import java.util.ResourceBundle;

/**
 * Preferences tab for this fork's own settings: Windows desktop/Start Menu shortcut integration,
 * and other small Utsu2-specific behaviors that don't belong in the upstream Theme/Editor/Engine
 * tabs. The installer's own "--win-shortcut-prompt" screen only offers these choices once, at
 * install time -- this tab lets them be changed afterward too, without reinstalling, by
 * creating or deleting the relevant .lnk file directly for whichever build is currently running.
 */
public class WindowsShortcutPreferencesEditor extends PreferencesEditor {
    private String displayName = "Utsu2 Settings";
    private BorderPane view;
    private Label description;
    private CheckBox desktopShortcutCheckbox;
    private CheckBox startMenuShortcutCheckbox;
    private Label statusLabel;
    private CheckBox confirmTabCloseCheckbox;
    private static final Preferences UTSU2_PREFS = Preferences.userRoot().node("utsu2");

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
                "Controls the desktop and Start Menu shortcuts for this specific build ("
                        + getShortcutBaseName() + "). Installing a different build does not "
                        + "affect this setting for other builds.");
        description.setWrapText(true);
        description.setMaxWidth(280);
        GridPane.setValignment(description, VPos.TOP);

        desktopShortcutCheckbox = new CheckBox("Create a desktop shortcut for this build");
        desktopShortcutCheckbox.setSelected(getDesktopShortcutFile().exists());

        startMenuShortcutCheckbox =
                new CheckBox("Create a Start Menu shortcut for this build");
        startMenuShortcutCheckbox.setSelected(getStartMenuShortcutFile().exists());

        statusLabel = new Label("");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(280);

        Separator separator = new Separator();

        confirmTabCloseCheckbox = new CheckBox(
                "Ask for confirmation when closing a tab with the Delete key");
        confirmTabCloseCheckbox.setSelected(
                UTSU2_PREFS.getBoolean("confirmTabCloseOnDelete", true));

        GridPane viewInternal = new GridPane();
        viewInternal.setHgap(10);
        viewInternal.setVgap(10);
        viewInternal.add(description, 0, 0);
        viewInternal.add(desktopShortcutCheckbox, 0, 1);
        viewInternal.add(startMenuShortcutCheckbox, 0, 2);
        viewInternal.add(statusLabel, 0, 3);
        viewInternal.add(separator, 0, 4);
        viewInternal.add(confirmTabCloseCheckbox, 0, 5);
        return viewInternal;
    }

    @Override
    public boolean onCloseEditor(Stage stage) {
        return true;
    }

    @Override
    public void savePreferences() {
        syncShortcut(desktopShortcutCheckbox.isSelected(), getDesktopShortcutFile());
        syncShortcut(startMenuShortcutCheckbox.isSelected(), getStartMenuShortcutFile());
        UTSU2_PREFS.putBoolean(
                "confirmTabCloseOnDelete", confirmTabCloseCheckbox.isSelected());
    }

    @Override
    public void revertToPreferences() {
        desktopShortcutCheckbox.setSelected(getDesktopShortcutFile().exists());
        startMenuShortcutCheckbox.setSelected(getStartMenuShortcutFile().exists());
        confirmTabCloseCheckbox.setSelected(
                UTSU2_PREFS.getBoolean("confirmTabCloseOnDelete", true));
    }

    private void syncShortcut(boolean shouldExist, File shortcutFile) {
        boolean currentlyExists = shortcutFile.exists();
        if (shouldExist == currentlyExists) {
            return;
        }
        try {
            if (shouldExist) {
                createShortcut(shortcutFile);
            } else if (!shortcutFile.delete()) {
                statusLabel.setText("Could not remove " + shortcutFile.getName() + ".");
            }
        } catch (IOException | InterruptedException e) {
            statusLabel.setText(
                    "Could not update " + shortcutFile.getName() + ": " + e.getMessage());
        }
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

    private File getDesktopShortcutFile() {
        String desktop = System.getProperty("user.home") + File.separator + "Desktop";
        return new File(desktop, getShortcutBaseName() + ".lnk");
    }

    private File getStartMenuShortcutFile() {
        String startMenu = System.getenv("APPDATA")
                + File.separator + "Microsoft" + File.separator + "Windows"
                + File.separator + "Start Menu" + File.separator + "Programs";
        return new File(startMenu, getShortcutBaseName() + ".lnk");
    }

    private void createShortcut(File shortcutFile) throws IOException, InterruptedException {
        String exePath = getRunningExecutablePath();
        if (exePath.isEmpty()) {
            statusLabel.setText("Could not determine this build's own program location.");
            return;
        }
        File workingDir = new File(exePath).getParentFile();
        File shortcutDir = shortcutFile.getParentFile();
        if (shortcutDir != null && !shortcutDir.exists()) {
            shortcutDir.mkdirs();
        }
        String script = "$WshShell = New-Object -ComObject WScript.Shell\r\n"
                + "$Shortcut = $WshShell.CreateShortcut('"
                + shortcutFile.getAbsolutePath().replace("'", "''") + "')\r\n"
                + "$Shortcut.TargetPath = '" + exePath.replace("'", "''") + "'\r\n"
                + "$Shortcut.WorkingDirectory = '"
                + (workingDir == null ? "" : workingDir.getAbsolutePath().replace("'", "''")) + "'\r\n"
                + "$Shortcut.Save()\r\n";
        File scriptFile = File.createTempFile("utsu2-shortcut", ".ps1");
        scriptFile.deleteOnExit();
        java.nio.file.Files.writeString(scriptFile.toPath(), script,
                java.nio.charset.StandardCharsets.UTF_8);
        Process process = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-File", scriptFile.getAbsolutePath())
                .start();
        int exitCode = process.waitFor();
        if (exitCode != 0 || !shortcutFile.exists()) {
            statusLabel.setText("Could not create " + shortcutFile.getName() + ".");
        }
    }
}
