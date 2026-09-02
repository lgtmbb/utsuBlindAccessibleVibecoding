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
import javafx.scene.control.TextInputControl;
import javafx.scene.input.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.io.InputStream;

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
     * not something specific to this program. If misconfigured, shows a one-time explanation of
     * how to fix it via Windows' own system locale setting; does not attempt to change any
     * system setting itself.
     */
    private void checkWindowsUnicodeFilenameSupport() {
        String osName = System.getProperty("os.name", "");
        if (!osName.toLowerCase(java.util.Locale.ROOT).contains("windows")) {
            return;
        }
        String jnuEncoding = System.getProperty("sun.jnu.encoding", "");
        if (jnuEncoding.toUpperCase(java.util.Locale.ROOT).contains("UTF-8")
                || jnuEncoding.toUpperCase(java.util.Locale.ROOT).contains("UTF8")) {
            return; // Already configured correctly; nothing to warn about.
        }
        String message = "Windows is currently set to translate file names using the \""
                + jnuEncoding + "\" character set instead of Unicode (UTF-8). This means "
                + "voicebank folder and file names containing Japanese (or other non-Latin) "
                + "characters may appear as unreadable garbled text, both in this program and "
                + "in File Explorer.\n\n"
                + "To fix this system-wide (no file renaming needed):\n"
                + "1. Open Settings, then \"Time & language\", then \"Language & region\".\n"
                + "2. Under \"Related settings\", choose \"Administrative language settings\".\n"
                + "3. Click \"Change system locale...\".\n"
                + "4. Check \"Beta: Use Unicode UTF-8 for worldwide language support\".\n"
                + "5. Restart Windows.\n\n"
                + "This message only appears once, on first launch.";
        Alert alert = new Alert(AlertType.WARNING, message);
        alert.setTitle("Windows Unicode file name support");
        alert.setHeaderText("File names with Japanese or other non-Latin characters may not "
                + "display correctly");
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
