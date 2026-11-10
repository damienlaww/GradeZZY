/*
 * File: StepBar.java
 *
 * Purpose: The four numbered steps across the top of the window. Steps
 * that are done show a tick: those before the current one, and, for the
 * run on screen, those before the furthest step reached, so going back
 * to a graded section's Grade keeps Grade ticked. The current step is
 * blue, with a tick when it is done; later steps are grey, and can be
 * clicked when they were reached before. Each says in a tip what it is
 * for, or what opens it.
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
 *
 * <p>Tier: SHOULD.
 */
public class StepBar extends HBox {

    // The gap between a dot and its name
    private static final double DOT_GAP = 8.0;

    // The size of the tick inside a done step
    private static final double TICK_SIZE = 14.0;

    // Comes between a step's name and what it is for in its tip
    private static final String NAME_SEPARATOR = ": ";

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
     * @param doneBefore every step before this one is done, for the run
     *        on screen; Set up when no run of the chosen section is open
     * @param away true while History, Settings or Help is open
     */
    public void update(Step current, Step furthest, Step doneBefore,
            boolean away) {
        getChildren().clear();
        Step[] steps = Step.values();
        getChildren().add(createItem(steps[0], current, furthest,
                doneBefore, away));
        for (int index = 1; index < steps.length; index++) {
            Region line = new Region();
            line.getStyleClass().add("step-line");
            getChildren().add(line);
            getChildren().add(createItem(steps[index], current, furthest,
                    doneBefore, away));
        }
    }

    // Builds one dot and name.
    private HBox createItem(Step step, Step current, Step furthest,
            Step doneBefore, boolean away) {
        StackPane dot = new StackPane();
        dot.getStyleClass().add("step-dot");
        Label name = new Label(step.getLabel());
        name.getStyleClass().add("step-name");

        boolean isCurrent = step == current && !away;
        // A step is done once the instructor moved past it, also when
        // they went back to it. A section not graded yet has no run on
        // screen, so its Grade and Review are not done.
        boolean done = step.ordinal() < current.ordinal()
                || step.ordinal() < doneBefore.ordinal();
        if (isCurrent && done) {
            dot.getStyleClass().addAll("current", "done");
            name.getStyleClass().add("current");
            dot.getChildren().add(Parts.icon(Parts.CHECK, TICK_SIZE));
        } else if (isCurrent) {
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
        int keyNumber = step.ordinal() + 1;
        String keys = " Shortcut: " + Parts.shortcut(String.valueOf(
                keyNumber)) + ".";
        if (isCurrent) {
            return "You are here: " + purpose + keys;
        } else if (canOpen) {
            return "Open " + step.getLabel() + NAME_SEPARATOR + purpose
                    + keys;
        }
        return locked;
    }
}
