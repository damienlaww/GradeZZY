/*
 * File: StepBar.java
 *
 * Purpose: The four numbered steps across the top of the window. Steps
 * before the current one show a tick and can be clicked to go back; the
 * current step is blue; later steps are grey, and can be clicked when
 * they were reached before. Each says in a tip what it is for, or what
 * opens it.
 *
 * Where it fits: FlowWindow puts it in the middle of the top bar and
 * updates it whenever the step changes.
 */
package gradezzy.ui.flow;

import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * Shows where the instructor is among the four steps.
 */
public class StepBar extends HBox {

    // The gap between a dot and its name
    private static final double DOT_GAP = 8.0;

    // The size of the tick inside a done step
    private static final double TICK_SIZE = 14.0;

    // Called with the step the instructor clicked
    private final Consumer<Step> onPick;

    /**
     * Creates the step bar.
     *
     * @param onPick called with a step the instructor may open
     */
    public StepBar(Consumer<Step> onPick) {
        this.onPick = onPick;
        getStyleClass().add("step-bar");
        setAlignment(Pos.CENTER);
    }

    /**
     * Redraws the bar.
     *
     * @param current the step on screen
     * @param furthest the furthest step the instructor may open
     * @param away true while History, Settings or Help is open
     */
    public void update(Step current, Step furthest, boolean away) {
        getChildren().clear();
        for (Step step : Step.values()) {
            if (step.ordinal() > 0) {
                Region line = new Region();
                line.getStyleClass().add("step-line");
                getChildren().add(line);
            }
            getChildren().add(createItem(step, current, furthest, away));
        }
    }

    // Builds one dot and name.
    private HBox createItem(Step step, Step current, Step furthest,
            boolean away) {
        StackPane dot = new StackPane();
        dot.getStyleClass().add("step-dot");
        Label name = new Label(step.getLabel());
        name.getStyleClass().add("step-name");

        boolean isCurrent = step == current && !away;
        // Only the steps before the one on screen are done: after a new
        // section is chosen, Grade and Review are not done for it yet
        boolean done = step.ordinal() < current.ordinal();
        if (isCurrent) {
            dot.getStyleClass().add("current");
            name.getStyleClass().add("current");
            dot.getChildren().add(new Label(String.valueOf(
                    step.getNumber())));
        } else if (done) {
            dot.getStyleClass().add("done");
            dot.getChildren().add(Parts.icon(Parts.CHECK, TICK_SIZE));
        } else {
            dot.getStyleClass().add("later");
            name.getStyleClass().add("later");
            dot.getChildren().add(new Label(String.valueOf(
                    step.getNumber())));
        }

        HBox item = new HBox(DOT_GAP, dot, name);
        item.setAlignment(Pos.CENTER_LEFT);
        item.getStyleClass().add("step-item");
        boolean canOpen = step.ordinal() <= furthest.ordinal()
                && (step != current || away);
        if (canOpen) {
            item.getStyleClass().add("clickable");
            item.setOnMouseClicked(event -> onPick.accept(step));
        }
        Parts.tipNode(item, tipFor(step, isCurrent, canOpen));
        return item;
    }

    // Says what a step is for, or what opens it while it is locked.
    private String tipFor(Step step, boolean isCurrent, boolean canOpen) {
        String purpose = "choose the section, the assessment and the "
                + "students' zip files.";
        String locked = "";
        if (step == Step.GRADE) {
            purpose = "watch the batch being graded.";
            locked = "Start grading on Set up first.";
        } else if (step == Step.REVIEW) {
            purpose = "check each student's marks, fixes and flags.";
            locked = "Grade a batch first, or open earlier results from "
                    + "Home or History.";
        } else if (step == Step.EXPORT) {
            purpose = "save the score sheet and reports.";
            locked = "Open results on Review first.";
        }
        String keys = " Shortcut: " + Parts.shortcut(String.valueOf(
                step.ordinal() + 1)) + ".";
        if (isCurrent) {
            return "You are here: " + purpose + keys;
        } else if (canOpen) {
            return "Open " + step.getLabel() + ": " + purpose + keys;
        }
        return locked;
    }
}
