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
            com.utsusynth.utsu.common.AccessibleDialogs.showMessage(
                    null, "Startup Error", alertText.toString());
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
        // At every start while the Windows setting is off (not only on a "first launch": that was
        // keyed on a missing preferences file, which never happens for anyone who already used
        // any Utsu build, so the check never ran).
        if (java.util.prefs.Preferences.userRoot().node("utsu2")
                .getBoolean("checkUtf8AtStartup", true)) {
            com.utsusynth.utsu.common.WindowsUnicodeSupport.checkAndOfferFix(false);
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
        String buildNumber = System.getProperty("utsu2.buildNumber", "");
        primaryStage.setTitle(
                buildNumber.isEmpty() ? "Utsu2" : "Utsu2 (build " + buildNumber + ")");
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

    public static void main(String[] args) {
        launch(args);
    }
}



