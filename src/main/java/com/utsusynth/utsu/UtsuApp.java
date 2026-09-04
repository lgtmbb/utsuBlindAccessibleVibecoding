package com.utsusynth.utsu;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.utsusynth.utsu.common.dialog.StartupDialog;
import com.utsusynth.utsu.common.i18n.Localizer;
import com.utsusynth.utsu.controller.UtsuController;
import com.utsusynth.utsu.files.AssetManager;
import com.utsusynth.utsu.files.CacheManager;
import com.utsusynth.utsu.files.PreferencesManager;
import com.utsusynth.utsu.files.ThemeManager;
import com.utsusynth.utsu.model.ModelModule;
import com.utsusynth.utsu.view.ViewModule;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * UTAU-ish Thingy with Some Updates (UTSU)
 */
public class UtsuApp extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        // Set up Guice.
        Injector injector =
                Guice.createInjector(new UtsuModule(), new ModelModule(), new ViewModule());

        // Initialize settings directory. Show alert if directory can't be created.
        PreferencesManager preferencesManager = injector.getInstance(PreferencesManager.class);
        ThemeManager themeManager = injector.getInstance(ThemeManager.class);
        AssetManager assetManager = injector.getInstance(AssetManager.class);
        CacheManager cacheManager = injector.getInstance(CacheManager.class);
        StringBuilder alertText = new StringBuilder();
        try {
            preferencesManager.initializePreferences();
            themeManager.initialize(preferencesManager.getTheme().getId());
            if (!assetManager.initializeAssets() || !cacheManager.initializeCache()) {
                alertText.append("Could not initialize settings directory.");
            }
        } catch (Exception e) {
            alertText
                    .append("Could not initialize settings directory.\n")
                    .append(e.getMessage())
                    .append("\n");
            for (StackTraceElement element : e.getStackTrace()) {
                alertText.append(element).append("\n");
            }
        }
        if (alertText.length() > 0) {
            Alert alert = new Alert(AlertType.ERROR, alertText.toString());
            alert.showAndWait();
            // Close program.
            primaryStage.show();
            primaryStage.close();
            return;
        }

        // Set language.
        Localizer localizer = injector.getInstance(Localizer.class);
        localizer.setLocale(preferencesManager.getLocale());

        // If there is no pre-existing preferences file, prompt user for preferences.
        boolean isFirstLaunch = !preferencesManager.hasPreferencesFile();
        if (isFirstLaunch) {
            StartupDialog startupDialog = injector.getInstance(StartupDialog.class);
            if (startupDialog.popup().equals(StartupDialog.Decision.CANCEL)) {
                // Close program.
                primaryStage.show();
                primaryStage.close();
                return;
            }
        }

        // First-launch only: on Windows, warn if the system is not configured to correctly
        // decode non-Latin (e.g. Japanese) file names, since this causes voicebank folder and
        // file names to display as unreadable garbage. This is a one-time check, not a
        // per-session one, since it reflects a system-wide setting that will not change between
        // runs of this program.
        if (isFirstLaunch) {
            checkWindowsUnicodeFilenameSupport();
        }

        // Construct scene.
        FXMLLoader loader = injector.getInstance(FXMLLoader.class);
        InputStream fxml = getClass().getResourceAsStream("/fxml/UtsuScene.fxml");
        BorderPane pane = loader.load(fxml);
        Scene scene = new Scene(pane);

        // Apply style and theme.
        themeManager.setPrimaryTheme(scene);

        // Set the stage.
        primaryStage.setScene(scene);
        primaryStage.setTitle("Utsu");
        primaryStage.show();

        UtsuController controller = loader.getController();

        // Set up an event that runs every time a non-text-input key is pressed.
        primaryStage.addEventFilter(KeyEvent.KEY_PRESSED, keyEvent -> {
            if (!(keyEvent.getTarget() instanceof TextInputControl)
                    || new KeyCodeCombination(KeyCode.TAB).match(keyEvent)) {
                if (controller.onKeyPressed(keyEvent)) {
                    keyEvent.consume();
                }
            }
        });

        // Set up an event that runs every time a mouse scroll occurs.
        primaryStage.addEventFilter(ScrollEvent.SCROLL, scrollEvent ->{
            if (controller.onScroll(scrollEvent)){
                scrollEvent.consume();
            }
        });

        // Set up an event that runs when the program is closed.
        primaryStage.setOnCloseRequest(windowEvent -> {
            if (!controller.onCloseWindow()) {
                windowEvent.consume();
            }
        });
    }

    /**
     * Checks, on Windows only, whether the OS is configured to correctly translate non-Latin
     * (e.g. Japanese) file names for non-Unicode-aware programs. When it is not, Java (and
     * therefore this program) receives already-mangled versions of such file names -- this is
     * the same setting responsible for garbled Japanese voicebank folder/file names in general,
     * not something specific to this program. If misconfigured, offers to fix it directly via
     * the same registry values Windows' own "Beta: Use Unicode UTF-8" checkbox controls.
     *
     * <p>Two things about this fix cannot be made invisible, because they are enforced by
     * Windows itself and not by this program: changing this value is a machine-wide setting, so
     * Windows will show its own "Do you want to allow this app to make changes" permission
     * prompt (this is the standard, documented way for a program to request that kind of
     * change -- not something being bypassed); and the new value only takes effect after a
     * restart.
     */
    private void checkWindowsUnicodeFilenameSupport() {
        String osName = System.getProperty("os.name", "");
        if (!osName.toLowerCase(java.util.Locale.ROOT).contains("windows")) {
            return;
        }
        if (isWindowsUtf8CodePageEnabled()) {
            return; // Already configured correctly; nothing to do.
        }

        ButtonType fixNowButton = new ButtonType("Fix now");
        ButtonType notNowButton = new ButtonType("Not now", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert confirm = new Alert(
                AlertType.CONFIRMATION,
                "Windows is currently set to translate file names using a non-Unicode character "
                        + "set. This means voicebank folder and file names containing Japanese "
                        + "(or other non-Latin) characters may appear as unreadable garbled "
                        + "text, both in this program and in File Explorer.\n\n"
                        + "This program can fix that by changing the relevant Windows system "
                        + "setting (the same one controlled by \"Beta: Use Unicode UTF-8 for "
                        + "worldwide language support\" in Windows Settings). Two things will "
                        + "happen that this program cannot skip, because Windows itself enforces "
                        + "them: a Windows permission prompt will appear, asking to allow this "
                        + "change (choose Yes); and a restart is needed afterward for the change "
                        + "to take effect.\n\n"
                        + "This message only appears once, on first launch.",
                fixNowButton, notNowButton);
        confirm.setTitle("Windows Unicode file name support");
        confirm.setHeaderText("File names with Japanese or other non-Latin characters may not "
                + "display correctly");
        confirm.getButtonTypes().setAll(fixNowButton, notNowButton);
        confirm.showAndWait().ifPresent(choice -> {
            if (choice == fixNowButton) {
                applyWindowsUtf8CodePageFix();
            }
        });
    }

    /**
     * Reads the two registry values that "Beta: Use Unicode UTF-8 for worldwide language
     * support" controls (HKLM\SYSTEM\CurrentControlSet\Control\Nls\CodePage, values ACP and
     * OEMCP). Reading the registry does not require administrator rights. Returns true only if
     * both are already set to 65001 (UTF-8).
     */
    private boolean isWindowsUtf8CodePageEnabled() {
        try {
            String acp = readRegistryValue("ACP");
            String oemcp = readRegistryValue("OEMCP");
            return "65001".equals(acp) && "65001".equals(oemcp);
        } catch (IOException | InterruptedException e) {
            // If this can't be determined, don't offer a fix for a problem that could not be
            // confirmed to exist.
            return true;
        }
    }

    private String readRegistryValue(String valueName) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "reg", "query",
                "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Nls\\CodePage",
                "/v", valueName)
                .redirectErrorStream(true)
                .start();
        String output;
        try (InputStream in = process.getInputStream()) {
            output = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        process.waitFor();
        for (String line : output.split("\\R")) {
            if (line.trim().startsWith(valueName)) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length > 0) {
                    return parts[parts.length - 1];
                }
            }
        }
        return "";
    }

    /**
     * Writes ACP and OEMCP to 65001 (UTF-8) via a single elevated helper process, so Windows
     * only shows one permission prompt for both values. This is done by writing a small,
     * temporary PowerShell script and asking Windows to run it elevated (Start-Process -Verb
     * RunAs), which is the standard, documented mechanism for a program to request elevation for
     * one specific action -- the program itself is not, and does not become, elevated.
     */
    private void applyWindowsUtf8CodePageFix() {
        try {
            File script = File.createTempFile("utsu2-utf8-codepage-fix", ".ps1");
            script.deleteOnExit();
            String scriptContents =
                    "reg add \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\Nls\\CodePage\" "
                            + "/v ACP /t REG_SZ /d 65001 /f\r\n"
                            + "reg add \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\Nls\\CodePage\" "
                            + "/v OEMCP /t REG_SZ /d 65001 /f\r\n"
                            + "exit $LASTEXITCODE\r\n";
            Files.writeString(script.toPath(), scriptContents,
                    java.nio.charset.StandardCharsets.UTF_8);

            String launcherCommand = String.format(
                    "$p = Start-Process -FilePath powershell.exe -ArgumentList "
                            + "'-NoProfile','-ExecutionPolicy','Bypass','-File','%s' "
                            + "-Verb RunAs -Wait -PassThru; exit $p.ExitCode",
                    script.getAbsolutePath().replace("'", "''"));
            Process elevated = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-Command", launcherCommand)
                    .inheritIO()
                    .start();
            int exitCode = elevated.waitFor();

            if (exitCode == 0 && isWindowsUtf8CodePageEnabled()) {
                ButtonType restartButton = new ButtonType("Restart now");
                ButtonType laterButton =
                        new ButtonType("Restart later", ButtonBar.ButtonData.CANCEL_CLOSE);
                Alert done = new Alert(
                        AlertType.INFORMATION,
                        "The Windows setting was updated successfully. A restart is required "
                                + "before file names display correctly.",
                        restartButton, laterButton);
                done.setTitle("Setting updated");
                done.setHeaderText("Restart required");
                done.getButtonTypes().setAll(restartButton, laterButton);
                done.showAndWait().ifPresent(choice -> {
                    if (choice == restartButton) {
                        try {
                            new ProcessBuilder("shutdown", "/r", "/t", "5").start();
                        } catch (IOException e) {
                            // Not fatal; the user can still restart manually.
                        }
                    }
                });
            } else {
                showFixFailedAlert(null);
            }
        } catch (IOException | InterruptedException e) {
            showFixFailedAlert(e);
        }
    }

    private void showFixFailedAlert(Exception e) {
        String message = "Could not change the Windows setting automatically (the permission "
                + "prompt may have been declined, or something else went wrong"
                + (e == null ? "" : ": " + e.getMessage()) + ").\n\n"
                + "You can still do it manually:\n"
                + "1. Open Settings, then \"Time & language\", then \"Language & region\".\n"
                + "2. Under \"Related settings\", choose \"Administrative language settings\".\n"
                + "3. Click \"Change system locale...\".\n"
                + "4. Check \"Beta: Use Unicode UTF-8 for worldwide language support\".\n"
                + "5. Restart Windows.";
        Alert alert = new Alert(AlertType.WARNING, message);
        alert.setTitle("Windows Unicode file name support");
        alert.setHeaderText("Automatic fix did not complete");
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

