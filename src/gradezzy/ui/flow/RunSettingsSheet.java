/*
 * File: RunSettingsSheet.java
 *
 * Purpose: A sheet that shows what one run was graded with: its
 * assessment, score sheet, tester and starter folders, and the grading
 * settings it started with, such as the time limit and how similar code
 * was compared, so two runs of the same section can be told apart.
 *
 * Where it fits: Opened from a run's Settings button on History. The
 * settings come from GradingRun.getGradingSettings, read with
 * RunSettings; a run graded before they were kept says so.
 */
package gradezzy.ui.flow;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import gradezzy.config.ConfigKeys;
import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.service.RunSettings;

/**
 * Builds the sheet of one run's settings.
 *
 * <p>Tier: COULD.
 */
public final class RunSettingsSheet {

    // The width of the sheet and of the name column
    private static final double WIDTH = 620.0;
    private static final double NAME_WIDTH = 230.0;

    // The tallest the list may get before it scrolls
    private static final double LIST_HEIGHT = 420.0;

    // The size of the close icon
    private static final double CLOSE_SIZE = 16.0;

    // What a setting the run did not keep shows
    private static final String NOT_RECORDED = "Not recorded";

    // The words of true and false settings
    private static final String TRUE = "true";
    private static final String ON = "On";
    private static final String OFF = "Off";

    // The plagiarism.algorithm value of the first version
    private static final String TRIGRAMS = "trigrams";

    // Turns a similarity share into a percentage
    private static final double PERCENT = 100.0;

    // The memory limit that means none
    private static final String NO_MEMORY_LIMIT = "0";

    // How the day and time of a run are written, such as 10 Nov 2026, 19:51
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    // The separator between facts on one line
    private static final String DOT = "  ·  ";

    // The window that shows the sheet
    private final FlowWindow window;

    // The run shown
    private final GradingRun run;

    // Not used: create builds the sheet.
    private RunSettingsSheet(FlowWindow window, GradingRun run) {
        this.window = window;
        this.run = run;
    }

    /**
     * Builds the sheet.
     *
     * @param window the window that shows it
     * @param run the run whose settings are shown
     * @return the sheet's content
     */
    public static Node create(FlowWindow window, GradingRun run) {
        return new RunSettingsSheet(window, run).build();
    }

    // Builds the title, the two groups and the button.
    private Node build() {
        Button close = new Button();
        close.setGraphic(Parts.icon(Parts.CLOSE, CLOSE_SIZE));
        close.getStyleClass().addAll("flat", "icon-button");
        close.setOnAction(event -> window.closeSheet());
        Parts.tip(close, "Close");
        HBox top = Parts.row(Parts.GAP, new Node[] {
            Parts.title("Run settings"), Parts.grow(), close});

        Label about = Parts.subtitle(describeRun());
        about.setWrapText(true);
        about.setMaxWidth(WIDTH);

        VBox list = new VBox();
        list.getStyleClass().add("case-list");
        addFiles(list);
        addSettings(list);
        if (!list.getChildren().isEmpty()) {
            list.getChildren().get(0).getStyleClass().add("first");
        }
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMaxHeight(LIST_HEIGHT);
        scroll.getStyleClass().add("page-scroll");

        Button done = Parts.primary("Done");
        done.setOnAction(event -> window.closeSheet());
        HBox bottom = Parts.row(Parts.GAP, new Node[] {Parts.grow(), done});

        VBox body = new VBox(Parts.GAP, top, about, scroll, bottom);
        body.setPrefWidth(WIDTH);
        body.getStyleClass().add("assessments-sheet");
        return body;
    }

    // Names the run: its section, assessment and when it was graded.
    private String describeRun() {
        String text = "";
        if (run.getSection() != null) {
            text = run.getSection().getLabel() + DOT;
        }
        Assessment assessment = run.getAssessment();
        if (assessment != null) {
            String questions = Parts.plural(
                    assessment.getQuestions().size(), "question");
            String marks = Parts.mark(assessment.getMaxTotal());
            text += assessment.getName() + DOT + questions + ", " + marks
                    + " marks" + DOT;
        }
        if (run.getStartedAt() == null) {
            return text + "graded on an unknown date.";
        }
        return text + "graded " + run.getStartedAt().format(WHEN) + ".";
    }

    // Adds the files the run was graded with.
    private void addFiles(VBox list) {
        list.getChildren().add(heading("Files"));
        list.getChildren().add(row("Score sheet",
                pathText(run.getScoreSheetFile())));
        list.getChildren().add(row("Tester files",
                pathText(run.getTestersDirectory())));
        list.getChildren().add(row("Starter files",
                pathText(run.getStarterDirectory())));
    }

    // Adds the grading settings the run started with, or says they were
    // not kept.
    private void addSettings(VBox list) {
        list.getChildren().add(heading("Grading settings"));
        String settings = run.getGradingSettings();
        if (settings.isEmpty()) {
            Label note = Parts.muted("Not recorded for this run: it was "
                    + "graded before GradeZZY kept each run's settings. "
                    + "Runs graded from now on show them here.");
            note.setWrapText(true);
            note.getStyleClass().add("small-text");
            VBox box = new VBox(note);
            box.getStyleClass().add("case-row");
            list.getChildren().add(box);
            return;
        }
        for (String key : RunSettings.keys()) {
            String value = RunSettings.valueOf(settings, key);
            list.getChildren().add(row(nameOf(key), describe(key, value)));
        }
    }

    // Returns a small heading between the groups.
    private VBox heading(String text) {
        Label label = Parts.strong(text);
        VBox box = new VBox(label);
        box.getStyleClass().add("case-row");
        return box;
    }

    // Returns one row: the setting's name, then its value.
    private HBox row(String name, String value) {
        Label nameLabel = Parts.muted(name);
        nameLabel.setMinWidth(NAME_WIDTH);
        nameLabel.setPrefWidth(NAME_WIDTH);
        Label valueLabel = new Label(value);
        valueLabel.setWrapText(true);
        valueLabel.setMinWidth(0);
        HBox.setHgrow(valueLabel, Priority.ALWAYS);
        HBox row = Parts.row(Parts.GAP, new Node[] {nameLabel,
            valueLabel});
        row.getStyleClass().add("case-row");
        return row;
    }

    // Writes a folder or file of the run as Settings does, from the
    // GradeZZY folder when it is inside it, or says it was not kept.
    private static String pathText(Path path) {
        if (path == null) {
            return NOT_RECORDED;
        }
        return Parts.configPath(path);
    }

    // Names a setting as Settings does.
    private static String nameOf(String key) {
        if (key.equals(ConfigKeys.RUN_TIMEOUT_SECONDS)) {
            return "Time limit per tester";
        } else if (key.equals(ConfigKeys.COMPILE_TIMEOUT_SECONDS)) {
            return "Time limit to compile";
        } else if (key.equals(ConfigKeys.MAX_MEMORY_MB)) {
            return "Memory per tester";
        } else if (key.equals(ConfigKeys.MAX_HANG_RERUNS)) {
            return "Reruns after a hang";
        } else if (key.equals(ConfigKeys.AUTO_FIX_ENABLED)) {
            return "Fix small mistakes";
        } else if (key.equals(ConfigKeys.DETECT_UNTOUCHED_ENABLED)) {
            return "Flag untouched starter files";
        } else if (key.equals(ConfigKeys.DETECT_EMPTY_ENABLED)) {
            return "Flag empty answers";
        } else if (key.equals(ConfigKeys.DETECT_HARD_CODED_ENABLED)) {
            return "Flag hard-coded answers";
        } else if (key.equals(ConfigKeys.MODEL_ANSWERS_DIR)) {
            return "Model answers";
        } else if (key.equals(ConfigKeys.RANDOM_INPUTS_ENABLED)) {
            return "Try random inputs";
        } else if (key.equals(ConfigKeys.RANDOM_INPUTS_COUNT)) {
            return "Random inputs per method";
        } else if (key.equals(ConfigKeys.PLAGIARISM_ENABLED)) {
            return "Look for similar code";
        } else if (key.equals(ConfigKeys.PLAGIARISM_ALGORITHM)) {
            return "How code is compared";
        } else if (key.equals(ConfigKeys.PLAGIARISM_THRESHOLD)) {
            return "Similarity that counts";
        } else if (key.equals(ConfigKeys.PLAGIARISM_MIN_LINES)) {
            return "Shortest answer compared";
        }
        return key;
    }

    // Writes a setting's value in words, such as "10 s" or "On".
    private static String describe(String key, String value) {
        if (value == null) {
            return NOT_RECORDED;
        }
        String text = value.trim();
        boolean seconds = key.equals(ConfigKeys.RUN_TIMEOUT_SECONDS)
                || key.equals(ConfigKeys.COMPILE_TIMEOUT_SECONDS);
        boolean onOff = key.equals(ConfigKeys.AUTO_FIX_ENABLED)
                || key.equals(ConfigKeys.DETECT_UNTOUCHED_ENABLED)
                || key.equals(ConfigKeys.DETECT_EMPTY_ENABLED)
                || key.equals(ConfigKeys.DETECT_HARD_CODED_ENABLED)
                || key.equals(ConfigKeys.RANDOM_INPUTS_ENABLED)
                || key.equals(ConfigKeys.PLAGIARISM_ENABLED);
        if (seconds) {
            return text + " s";
        } else if (onOff && TRUE.equalsIgnoreCase(text)) {
            return ON;
        } else if (onOff) {
            return OFF;
        } else if (key.equals(ConfigKeys.MAX_MEMORY_MB)) {
            return describeMemory(text);
        } else if (key.equals(ConfigKeys.MODEL_ANSWERS_DIR)
                && text.isEmpty()) {
            return "None";
        } else if (key.equals(ConfigKeys.PLAGIARISM_ALGORITHM)) {
            return describeAlgorithm(text);
        } else if (key.equals(ConfigKeys.PLAGIARISM_THRESHOLD)) {
            return describeShare(text);
        } else if (key.equals(ConfigKeys.PLAGIARISM_MIN_LINES)) {
            return text + " lines";
        }
        return text;
    }

    // Writes a memory limit, where 0 means none.
    private static String describeMemory(String text) {
        if (text.equals(NO_MEMORY_LIMIT)) {
            return "No limit";
        }
        return text + " MB";
    }

    // Names the similarity algorithm as the Settings list does.
    private static String describeAlgorithm(String text) {
        if (text.equals(TRIGRAMS)) {
            return "Trigrams: only copies that keep the names";
        }
        return "Winnowing: also catches renamed copies";
    }

    // Writes a share from 0 to 1 as a percentage, such as "80% alike".
    private static String describeShare(String text) {
        try {
            double share = Double.parseDouble(text);
            long percent = Math.round(share * PERCENT);
            return percent + Parts.PERCENT_SIGN + " alike";
        } catch (NumberFormatException exception) {
            return text;
        }
    }
}
