/*
 * File: SimilarityPairList.java
 *
 * Purpose: The list of similar pairs on the Similarity Review screen.
 *
 * Where it fits: The Similarity Review screen creates one in onAttach.
 * Similarity is a review aid and never a finding.
 */
package gradezzy.ui.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;

import gradezzy.config.ConfigKeys;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.GradingRun;
import gradezzy.plagiarism.SimilarityPair;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.ReviewDecision;
import gradezzy.ui.component.Labels;
import gradezzy.ui.component.StatusBadge;

/**
 * The list of similar pairs.
 */
public final class SimilarityPairList {

    // The most pairs the list shows before it scrolls
    private static final int VISIBLE_PAIRS = 4;

    // The height of one pair in the list
    private static final double PAIR_ROW_HEIGHT = 62.0;

    // The space the list adds around its rows
    private static final double LIST_PADDING = 4.0;

    // The space between the lines of a pair
    private static final double LINE_GAP = 4.0;

    // The value returned when the threshold setting cannot be read
    private static final double NO_THRESHOLD = -1.0;

    // Separates the parts of a pair's key
    private static final String KEY_SEPARATOR = "|";

    // The list of pairs
    private final ListView<SimilarityPair> list;

    // The backend that checks the run and knows each flag's decision
    private final DashboardBackend backend;

    // Run after the instructor chose another pair
    private final Runnable onChosen;

    // The pairs at or above the threshold, most similar first
    private final List<SimilarityPair> pairs = new ArrayList<>();

    // The Similar code flags of the run, by username
    private final Map<String, List<Anomaly>> similarFlags = new HashMap<>();

    // The run the pairs were checked for
    private GradingRun checkedRun = null;

    // The key of the pair chosen by the instructor
    private String chosenKey = "";

    // Whether the selection is being set by code
    private boolean restoring = false;

    /**
     * Sets up the list of pairs.
     *
     * @param list the list
     * @param backend the backend that checks the run
     * @param onChosen run after the instructor chose another pair
     */
    public SimilarityPairList(ListView<SimilarityPair> list,
            DashboardBackend backend, Runnable onChosen) {
        this.list = list;
        this.backend = backend;
        this.onChosen = onChosen;
        list.setCellFactory(view -> new PairCell());
        list.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> pairChosen(newValue));
    }

    /**
     * Says whether the pairs were already checked for a run.
     *
     * @param run the run, or null
     * @return true when the pairs belong to this run
     */
    public boolean isChecked(GradingRun run) {
        return run == checkedRun;
    }

    /**
     * Loads the pairs of a run at or above the threshold.
     *
     * @param run the run, or null
     */
    public void load(GradingRun run) {
        checkedRun = run;
        pairs.clear();
        if (run == null) {
            return;
        }
        double threshold = threshold();
        for (SimilarityPair pair : backend.listSimilarPairs(run)) {
            if (pair.getSimilarity() >= threshold) {
                pairs.add(pair);
            }
        }
    }

    /**
     * Returns how many pairs are listed.
     *
     * @return the count
     */
    public int size() {
        return pairs.size();
    }

    /**
     * Chooses the first listed pair that holds a student, if any.
     *
     * @param username the student
     */
    public void prefer(String username) {
        for (SimilarityPair pair : pairs) {
            if (pair.getFirstUsername().equals(username)
                    || pair.getSecondUsername().equals(username)) {
                chosenKey = keyOf(pair);
                return;
            }
        }
    }

    /**
     * Shows the pairs and selects the chosen pair again, or the first.
     *
     * @param run the run of the section, or null
     */
    public void show(GradingRun run) {
        rememberFlags(run);
        int rows = Math.max(1, Math.min(pairs.size(), VISIBLE_PAIRS));
        list.setPrefHeight(rows * PAIR_ROW_HEIGHT + LIST_PADDING);
        restoring = true;
        list.setItems(FXCollections.observableArrayList(pairs));
        int index = indexOf(chosenKey);
        if (index < 0 && !pairs.isEmpty()) {
            index = 0;
        }
        if (index >= 0) {
            list.getSelectionModel().select(index);
            chosenKey = keyOf(pairs.get(index));
        }
        restoring = false;
        list.refresh();
    }

    /**
     * Returns the chosen pair.
     *
     * @return the pair, or null when none is listed
     */
    public SimilarityPair getSelected() {
        return list.getSelectionModel().getSelectedItem();
    }

    /**
     * Returns the Similar code flags of both students of a pair.
     *
     * @param pair the pair
     * @return the flags, possibly none
     */
    public List<Anomaly> flagsOf(SimilarityPair pair) {
        List<Anomaly> flags = new ArrayList<>();
        addFlags(flags, pair.getFirstUsername());
        addFlags(flags, pair.getSecondUsername());
        return flags;
    }

    /**
     * Returns the decision shown for a pair: Open while any of its flags
     * is open or when it has none, otherwise the first flag's decision.
     *
     * @param pair the pair
     * @return the decision
     */
    public ReviewDecision decisionOf(SimilarityPair pair) {
        List<Anomaly> flags = flagsOf(pair);
        if (flags.isEmpty()) {
            return ReviewDecision.OPEN;
        }
        for (Anomaly flag : flags) {
            if (backend.getDecision(flag) == ReviewDecision.OPEN) {
                return ReviewDecision.OPEN;
            }
        }
        return backend.getDecision(flags.get(0));
    }

    /**
     * Describes the threshold in words.
     *
     * @return the text, such as "the 80% threshold"
     */
    public String thresholdText() {
        double threshold = threshold();
        if (threshold < 0) {
            return "the threshold in Settings";
        }
        return "the " + Labels.formatPercent(threshold) + " threshold";
    }

    /**
     * Names both students of a pair.
     *
     * @param pair the pair
     * @return the text, such as "learner.alpha and learner.echo"
     */
    public static String nameOf(SimilarityPair pair) {
        return pair.getFirstUsername() + " and " + pair.getSecondUsername();
    }

    // Remembers the pair the instructor chose and tells the screen.
    private void pairChosen(SimilarityPair pair) {
        if (restoring) {
            return;
        }
        if (pair != null) {
            chosenKey = keyOf(pair);
        }
        onChosen.run();
    }

    // Keeps the Similar code flags of the run, by username.
    private void rememberFlags(GradingRun run) {
        similarFlags.clear();
        for (Anomaly anomaly : backend.listAnomalies(run)) {
            if (anomaly.getType() == AnomalyType.SIMILAR_CODE) {
                List<Anomaly> flags = similarFlags.get(anomaly.getUsername());
                if (flags == null) {
                    flags = new ArrayList<>();
                    similarFlags.put(anomaly.getUsername(), flags);
                }
                flags.add(anomaly);
            }
        }
    }

    // Adds one student's Similar code flags to a list.
    private void addFlags(List<Anomaly> flags, String username) {
        List<Anomaly> found = similarFlags.get(username);
        if (found != null) {
            flags.addAll(found);
        }
    }

    // Reads the threshold setting, a share from 0 to 1.
    private double threshold() {
        String value = backend.getSetting(ConfigKeys.PLAGIARISM_THRESHOLD);
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException exception) {
            return NO_THRESHOLD;
        }
    }

    // Finds the listed pair with a key, or returns -1.
    private int indexOf(String key) {
        for (int index = 0; index < pairs.size(); index++) {
            if (keyOf(pairs.get(index)).equals(key)) {
                return index;
            }
        }
        return -1;
    }

    // Returns the text that identifies a pair across reloads.
    private static String keyOf(SimilarityPair pair) {
        return pair.getFirstUsername() + KEY_SEPARATOR
                + pair.getSecondUsername() + KEY_SEPARATOR
                + pair.getQuestionId();
    }

    /**
     * One pair in the list, with its similarity and decision.
     */
    private final class PairCell extends ListCell<SimilarityPair> {

        /**
         * Shows one pair.
         *
         * @param pair the pair
         * @param empty whether the row is empty
         */
        @Override
        protected void updateItem(SimilarityPair pair, boolean empty) {
            super.updateItem(pair, empty);
            setText(null);
            if (empty || pair == null) {
                setGraphic(null);
                setAccessibleText(null);
                return;
            }
            Label names = new Label(nameOf(pair));
            names.getStyleClass().add("context-value");
            String similarity = pair.getQuestionId() + ", "
                    + Labels.formatPercent(pair.getSimilarity()) + " similar";
            Label detail = new Label(similarity);
            detail.getStyleClass().add("secondary-text");
            StatusBadge badge = StatusBadge.forDecision(decisionOf(pair));
            VBox box = new VBox(LINE_GAP, names, detail, badge);
            setGraphic(box);
            setAccessibleText(nameOf(pair) + ", " + similarity + ", "
                    + badge.getText());
        }
    }
}
