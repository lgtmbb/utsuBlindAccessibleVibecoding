package com.utsusynth.utsu.common;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wires Label.labelFor relationships for labels declared in FXML.
 *
 * <p>FXML cannot express labelFor reliably: a "$id" reference only resolves to elements declared
 * EARLIER in the document, so a label placed before its control (the normal order) silently ended
 * up with a null labelFor and the control had no accessible name at all. Labels therefore carry
 * the target's fx:id in their userData attribute, and this class resolves them once the whole
 * tree exists.
 */
public final class LabelBinder {
    private LabelBinder() {}

    /** Binds every label under the topmost ancestor of the given node. */
    public static void bind(Node anyNodeInTree) {
        if (anyNodeInTree == null) {
            return;
        }
        Node top = anyNodeInTree;
        while (top.getParent() != null) {
            top = top.getParent();
        }
        Map<String, Node> byId = new HashMap<>();
        collect(top, byId);
        apply(top, byId);
    }

    private static void collect(Node node, Map<String, Node> byId) {
        if (node.getId() != null && !node.getId().isEmpty()) {
            byId.putIfAbsent(node.getId(), node);
        }
        for (Node child : children(node)) {
            collect(child, byId);
        }
    }

    private static void apply(Node node, Map<String, Node> byId) {
        if (node instanceof Label && node.getUserData() instanceof String) {
            Label label = (Label) node;
            Node target = byId.get((String) label.getUserData());
            if (target != null) {
                label.setLabelFor(target);
            }
        }
        for (Node child : children(node)) {
            apply(child, byId);
        }
    }

    /** Children including content a skin has not attached to the scene graph yet. */
    private static List<Node> children(Node node) {
        List<Node> result = new ArrayList<>();
        if (node instanceof TabPane) {
            for (Tab tab : ((TabPane) node).getTabs()) {
                if (tab.getContent() != null) {
                    result.add(tab.getContent());
                }
            }
        } else if (node instanceof ScrollPane) {
            Node content = ((ScrollPane) node).getContent();
            if (content != null) {
                result.add(content);
            }
        } else if (node instanceof SplitPane) {
            result.addAll(((SplitPane) node).getItems());
        } else if (node instanceof TitledPane) {
            Node content = ((TitledPane) node).getContent();
            if (content != null) {
                result.add(content);
            }
        }
        if (node instanceof Parent) {
            for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
                if (!result.contains(child)) {
                    result.add(child);
                }
            }
        }
        return result;
    }
}
