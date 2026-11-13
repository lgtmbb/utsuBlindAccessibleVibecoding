package com.utsusynth.utsu.controller;

import com.google.inject.Inject;
import com.utsusynth.utsu.common.i18n.Localizable;
import com.utsusynth.utsu.common.i18n.Localizer;
import com.utsusynth.utsu.files.PreferencesManager;
import com.utsusynth.utsu.view.config.EditorPreferencesEditor;
import com.utsusynth.utsu.view.config.EnginePreferencesEditor;
import com.utsusynth.utsu.view.config.PreferencesEditor;
import com.utsusynth.utsu.view.config.ThemePreferencesEditor;
import com.utsusynth.utsu.view.config.WindowsShortcutPreferencesEditor;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ResourceBundle;

/**
 * 'PreferencesScene.fxml' Controller Class
 */
public class PreferencesController implements Localizable {
    private final PreferencesManager preferencesManager;
    private final ThemePreferencesEditor themeEditor;
    private final EditorPreferencesEditor editorEditor;
    private final EnginePreferencesEditor engineEditor;
    private final WindowsShortcutPreferencesEditor windowsShortcutEditor;
    private final Localizer localizer;

    @FXML // fx:id="root"
    private BorderPane root; // Value injected by FXMLLoader

    @FXML // fx:id="vBoxLeft"
    private VBox vBoxLeft;

    @FXML // fx:id="vBoxRight"
    private VBox vBoxRight; // Value injected by FXMLLoader

    @FXML // fx:id="applyButton"
    private Button applyButton; // Value injected by FXMLLoader

    @FXML // fx:id="cancelButton"
    private Button cancelButton; // Value injected by FXMLLoader

    private TreeView<PreferencesEditor> tableOfContents;

    @Inject
    public PreferencesController(
            PreferencesManager preferencesManager,
            ThemePreferencesEditor themeEditor,
            EditorPreferencesEditor editorEditor,
            EnginePreferencesEditor engineEditor,
            WindowsShortcutPreferencesEditor windowsShortcutEditor,
            Localizer localizer) {
        this.preferencesManager = preferencesManager;
        this.themeEditor = themeEditor;
        this.editorEditor = editorEditor;
        this.engineEditor = engineEditor;
        this.windowsShortcutEditor = windowsShortcutEditor;
        this.localizer = localizer;
    }

    public void initialize() {
        // Initialize each internal preferences editor.
        themeEditor.initialize();
        editorEditor.initialize();
        engineEditor.initialize();
        windowsShortcutEditor.initialize();
        // Set up table of contents.
        TreeItem<PreferencesEditor> root = new TreeItem<>(null);
        root.getChildren().add(new TreeItem<>(themeEditor));
        root.getChildren().add(new TreeItem<>(editorEditor));
        root.getChildren().add(new TreeItem<>(engineEditor));
        root.getChildren().add(new TreeItem<>(windowsShortcutEditor));

        tableOfContents = new TreeView<>(root);
        tableOfContents.setPrefWidth(vBoxLeft.getPrefWidth());
        tableOfContents.setShowRoot(false);
        tableOfContents.setAccessibleText("Settings categories");
        tableOfContents.setCellFactory(preferencesEditor -> {
            TreeCell<PreferencesEditor> treeCell = new TreeCell<>() {
                @Override
                public void updateItem(PreferencesEditor item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || getItem() == null) {
                        setText(null);
                        setAccessibleText(null);
                        return;
                    }
                    setText(getItem().getDisplayName());
                    int total = getTreeView() == null || getTreeView().getRoot() == null
                            ? 0 : getTreeView().getRoot().getChildren().size();
                    setAccessibleText(getItem().getDisplayName() + ", " + (getIndex() + 1)
                            + " of " + total);
                }
            };
            return treeCell;
        });
        vBoxLeft.getChildren().add(tableOfContents);
        VBox.setVgrow(tableOfContents, Priority.ALWAYS);
        // Pages used to switch only in a mouse-click handler on each cell, so the arrow keys
        // moved the highlight but never the page. Driving it from the selection fixes that.
        tableOfContents.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldItem, newItem) -> {
                    if (newItem != null && newItem.getValue() != null) {
                        vBoxRight.getChildren().setAll(newItem.getValue().getView());
                    }
                });
        tableOfContents.getSelectionModel().selectFirst();

        // Escape closes the dialog like the Cancel button (it only worked via the button).
        this.root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, keyEvent -> {
                    if (keyEvent.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                        keyEvent.consume();
                        cancelButton.fire();
                    }
                });
            }
        });

        // Set up localization.
        localizer.localize(this);
    }

    @Override
    public void localize(ResourceBundle bundle) {
        if (root.getScene() != null) {
            Stage currentStage = (Stage) root.getScene().getWindow();
            currentStage.setTitle(bundle.getString("preferences.title"));
        }
        themeEditor.setDisplayName(bundle.getString("preferences.colorScheme"));
        editorEditor.setDisplayName(bundle.getString("preferences.editor"));
        engineEditor.setDisplayName(bundle.getString("preferences.engine"));
        tableOfContents.refresh(); // Lazy way to hard refresh TreeView with new names.

        applyButton.setText(bundle.getString("general.apply"));
        cancelButton.setText(bundle.getString("general.cancel"));
    }

    /* Handler for when modal is closed without saving. */
    public boolean onCloseWindow() {
        Stage currentStage = (Stage) root.getScene().getWindow();
        if (!themeEditor.onCloseEditor(currentStage)
                || !editorEditor.onCloseEditor(currentStage)
                || !engineEditor.onCloseEditor(currentStage)
                || !windowsShortcutEditor.onCloseEditor(currentStage)) {
            return false;
        }
        themeEditor.revertToPreferences();
        editorEditor.revertToPreferences();
        engineEditor.revertToPreferences();
        windowsShortcutEditor.revertToPreferences();
        return true;
    }

    @FXML
    void closePreferences(ActionEvent event) {
        if (!onCloseWindow()) {
            return;
        }
        Stage currentStage = (Stage) root.getScene().getWindow();
        currentStage.close();
    }

    @FXML
    void applyPreferences(ActionEvent event) {
        Stage currentStage = (Stage) root.getScene().getWindow();
        if (!themeEditor.onCloseEditor(currentStage)
                || !editorEditor.onCloseEditor(currentStage)
                || !engineEditor.onCloseEditor(currentStage)
                || !windowsShortcutEditor.onCloseEditor(currentStage)) {
            return;
        }
        themeEditor.savePreferences();
        editorEditor.savePreferences();
        engineEditor.savePreferences();
        windowsShortcutEditor.savePreferences();
        preferencesManager.saveToFile();
        currentStage.close();
    }
}

