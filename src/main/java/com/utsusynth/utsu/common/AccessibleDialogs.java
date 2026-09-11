package com.utsusynth.utsu.common;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Replacement for javafx.scene.control.Alert, because JavaFX's own Dialog/Alert classes have no
 * working accessibility implementation for their content: confirmed via
 * https://bugs.openjdk.org/browse/JDK-8292658 ("None of the accessibility client application can
 * read the content of a JavaFX dialog, because accessibility is not implemented for JavaFX
 * Dialogs"). In practice this meant NVDA announced the dialog's title and its buttons, but never
 * the actual header/content text -- confirmed directly in this app, first for the "close tab"
 * confirmation.
 *
 * <p>This uses a plain Stage with a real, focus-traversable Label instead, and moves real
 * keyboard focus onto that Label as soon as the window opens. That is the one mechanism
 * confirmed to work reliably with NVDA throughout this whole project (the same principle behind
 * this fork's note-focus fix): NVDA reads whatever has real focus, not passive "window opened,
 * here is its content" announcements, which is exactly the part JavaFX's own Dialog/Alert never
 * implemented.
 */
public final class AccessibleDialogs {
    private AccessibleDialogs() {}

    /** Shows a message with a single "OK" button and waits for it to be dismissed. */
    public static void showMessage(Window owner, String title, String message) {
        confirm(owner, title, message, "OK", null);
    }

    /**
     * Shows a message with two buttons and waits for the user's choice. Returns true if the
     * "yes"-equivalent button was chosen, false otherwise (including if the window was closed
     * without choosing, e.g. via Escape or Alt+F4).
     */
    public static boolean confirm(
            Window owner, String title, String message, String yesText, String noText) {
        Stage dialog = new Stage();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(title);

        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(320);
        messageLabel.setFocusTraversable(true);

        boolean[] result = {false};

        Button yesButton = new Button(yesText);
        yesButton.setDefaultButton(true);
        yesButton.setOnAction(event -> {
            result[0] = true;
            dialog.close();
        });

        HBox buttonRow = new HBox(10, yesButton);
        buttonRow.setPadding(new Insets(10, 0, 0, 0));

        if (noText != null) {
            Button noButton = new Button(noText);
            noButton.setCancelButton(true);
            noButton.setOnAction(event -> {
                result[0] = false;
                dialog.close();
            });
            buttonRow.getChildren().add(noButton);
        }

        VBox root = new VBox(15, messageLabel, buttonRow);
        root.setPadding(new Insets(15));

        dialog.setScene(new Scene(root));
        // Move real keyboard focus onto the message as soon as the window is shown, so NVDA
        // actually announces it -- the one thing JavaFX's own Alert never reliably does.
        dialog.setOnShown(event -> Platform.runLater(messageLabel::requestFocus));
        dialog.showAndWait();
        return result[0];
    }
}
