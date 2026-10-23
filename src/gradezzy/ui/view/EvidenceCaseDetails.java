/*
 * File: EvidenceCaseDetails.java
 *
 * Purpose: Fills the panel beside the Cases table on Student Evidence.
 *
 * Where it fits: EvidenceCasesTab calls show whenever a row is selected.
 * The time limit shown is for one tester run, not for one case.
 */
package gradezzy.ui.view;

import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import gradezzy.model.Question;
import gradezzy.model.QuestionResult;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.component.Labels;

/**
 * The details of the case selected on the Cases tab.
 */
public final class EvidenceCaseDetails {

    // The number of output lines shown in the log excerpt
    private static final int EXCERPT_LINES = 6;

    // The backend, for the configured time limit
    private final DashboardBackend backend;

    // The heading, such as "Q1a case 3"
    private final Label title;

    // Holds the outcome badge
    private final HBox badgeHolder;

    // The values and the excerpt, hidden when no case is selected
    private final VBox facts;

    // What the tester expected
    private final Label expected;

    // What the code produced
    private final Label actual;

    // The time limit of one tester run
    private final Label limit;

    // The marks the question kept
    private final Label kept;

    // The last lines of the output
    private final TextArea excerpt;

    // Plain advice for the instructor
    private final Label advice;

    /**
     * Creates the details over the nodes they fill.
     *
     * @param backend the backend, for the configured time limit
     * @param title the heading, such as "Q1a case 3"
     * @param badgeHolder holds the outcome badge
     * @param facts the values and excerpt, hidden when no case is selected
     * @param expected what the tester expected
     * @param actual what the code produced
     * @param limit the time limit of one tester run
     * @param kept the marks the question kept
     * @param excerpt the last lines of the output
     * @param advice plain advice for the instructor
     */
    public EvidenceCaseDetails(DashboardBackend backend, Label title,
            HBox badgeHolder, VBox facts, Label expected, Label actual,
            Label limit, Label kept, TextArea excerpt, Label advice) {
        this.backend = backend;
        this.title = title;
        this.badgeHolder = badgeHolder;
        this.facts = facts;
        this.expected = expected;
        this.actual = actual;
        this.limit = limit;
        this.kept = kept;
        this.excerpt = excerpt;
        this.advice = advice;
    }

    /**
     * Shows one case, or explains that none is selected.
     *
     * @param row the selected case, or null
     * @param anyCases whether the table lists any case at all
     */
    public void show(EvidenceCaseRow row, boolean anyCases) {
        badgeHolder.getChildren().clear();
        boolean chosen = row != null;
        facts.setVisible(chosen);
        facts.setManaged(chosen);
        if (!chosen) {
            title.setText("No case selected");
            excerpt.setText("");
            if (anyCases) {
                advice.setText("Select a row to see what the tester "
                        + "expected and what the code printed.");
            } else {
                advice.setText("No tester has run for this student yet, "
                        + "so there are no cases to explain.");
            }
            return;
        }

        QuestionResult result = row.getResult();
        Question question = result.getQuestion();
        title.setText(row.getTitle());
        badgeHolder.getChildren().add(row.createBadge());
        expected.setText(row.getExpected());
        actual.setText(row.getActual());
        limit.setText(backend.getRunTimeoutSeconds()
                + " s per tester run (exec.run.timeout.seconds)");
        kept.setText(Labels.formatMarks(result.getScore(),
                question.getMaxScore()) + " for " + question.getId());
        excerpt.setText(lastLines(result.getOutput(), EXCERPT_LINES));
        advice.setText(row.getAdvice());
    }

    // Returns the last lines of a text, without reading the rest.
    private static String lastLines(String text, int count) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String body = text;
        while (body.endsWith("\n")) {
            body = body.substring(0, body.length() - 1);
        }
        int cut = body.length();
        int lines = 0;
        while (cut > 0 && lines < count) {
            cut = body.lastIndexOf('\n', cut - 1);
            lines++;
        }
        if (cut < 0) {
            return body;
        }
        return body.substring(cut + 1);
    }
}
