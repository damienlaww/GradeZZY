/*
 * File: StatusBadge.java
 *
 * Purpose: Shows a status as an icon and a label, so it never relies on
 * colour alone.
 *
 * Where it fits: Lists and inspectors use it for every kind of status.
 * Only the icon is coloured.
 */
package gradezzy.ui.component;

import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import gradezzy.model.FixStatus;
import gradezzy.model.Severity;
import gradezzy.ui.backend.ImportState;
import gradezzy.ui.backend.ResultState;
import gradezzy.ui.backend.ReviewDecision;

/**
 * An icon and a label that show one status.
 */
public final class StatusBadge extends HBox {

    // The text next to the icon
    private final Label label = new Label();

    /**
     * Creates a badge with the tone's usual icon.
     *
     * @param text the status in words
     * @param tone the status colour
     */
    public StatusBadge(String text, Tone tone) {
        this(text, tone, tone.getDefaultIcon());
    }

    /**
     * Creates a badge with a chosen icon.
     *
     * @param text the status in words
     * @param tone the status colour
     * @param icon the icon
     */
    public StatusBadge(String text, Tone tone, Icon icon) {
        getStyleClass().addAll("status-badge", tone.getStyleClass());
        setAlignment(Pos.CENTER_LEFT);
        label.setText(text);
        label.setMinWidth(USE_PREF_SIZE);
        getChildren().addAll(icon.create(), label);
        setAccessibleRole(AccessibleRole.TEXT);
        setAccessibleText(text);
    }

    /**
     * Returns the status in words.
     *
     * @return the text
     */
    public String getText() {
        return label.getText();
    }

    /**
     * Creates the badge of a result state.
     *
     * @param state the state
     * @return the badge
     */
    public static StatusBadge forResult(ResultState state) {
        return new StatusBadge(state.getLabel(), Labels.toneOf(state),
                Labels.iconOf(state));
    }

    /**
     * Creates the badge of an anomaly severity.
     *
     * @param severity the severity
     * @return the badge
     */
    public static StatusBadge forSeverity(Severity severity) {
        return new StatusBadge(Labels.severityLabel(severity),
                Labels.toneOf(severity));
    }

    /**
     * Creates the badge of a repair state.
     *
     * @param status the fix status
     * @return the badge
     */
    public static StatusBadge forFix(FixStatus status) {
        return new StatusBadge(Labels.fixLabel(status),
                Labels.toneOf(status));
    }

    /**
     * Creates the badge of a review decision.
     *
     * @param decision the decision
     * @return the badge
     */
    public static StatusBadge forDecision(ReviewDecision decision) {
        return new StatusBadge(decision.getLabel(), Labels.toneOf(decision));
    }

    /**
     * Creates the badge of an import state.
     *
     * @param state the state
     * @return the badge
     */
    public static StatusBadge forImport(ImportState state) {
        return new StatusBadge(state.getLabel(), Labels.toneOf(state));
    }
}
