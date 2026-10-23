/*
 * File: AnomalyFilters.java
 *
 * Purpose: The search, filters and sort of the Review Anomalies screen.
 *
 * Where it fits: The Review Anomalies screen creates one in onAttach. Each
 * choice is kept in UiState, so it comes back after navigation.
 */
package gradezzy.ui.view;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

import org.controlsfx.control.textfield.TextFields;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Severity;
import gradezzy.ui.SortOrder;
import gradezzy.ui.UiState;
import gradezzy.ui.backend.ReviewDecision;
import gradezzy.ui.component.Labels;

/**
 * The search, filters and sort of the flag list.
 */
public final class AnomalyFilters {

    // The most search suggestions shown
    private static final int MAX_SUGGESTIONS = 8;

    // The words shown for "no filter" in the type filter
    private static final String ALL_TYPES = "All types";

    // The words shown for "no filter" in the severity filter
    private static final String ALL_SEVERITIES = "All";

    // The words shown for "no filter" in the decision filter
    private static final String ALL_DECISIONS = "All decisions";

    // The search field
    private final TextField searchField;

    // The anomaly type filter
    private final ComboBox<AnomalyType> typeFilter;

    // The severity filter
    private final ComboBox<Severity> severityFilter;

    // The review decision filter
    private final ComboBox<ReviewDecision> decisionFilter;

    // The sort order
    private final ComboBox<SortOrder> sortBox;

    // The state that keeps the choices
    private final UiState state;

    // The students of the run, for names and suggestions
    private final ReviewStudents students;

    // Whether the controls are being set by code
    private boolean restoring = false;

    /**
     * Fills the filter and sort choices.
     *
     * @param searchField the search field
     * @param typeFilter the anomaly type filter
     * @param severityFilter the severity filter
     * @param decisionFilter the review decision filter
     * @param sortBox the sort order
     * @param state the state that keeps the choices
     * @param students the students of the run
     */
    public AnomalyFilters(TextField searchField,
            ComboBox<AnomalyType> typeFilter,
            ComboBox<Severity> severityFilter,
            ComboBox<ReviewDecision> decisionFilter,
            ComboBox<SortOrder> sortBox, UiState state,
            ReviewStudents students) {
        this.searchField = searchField;
        this.typeFilter = typeFilter;
        this.severityFilter = severityFilter;
        this.decisionFilter = decisionFilter;
        this.sortBox = sortBox;
        this.state = state;
        this.students = students;

        ReviewChoices.fill(typeFilter, Arrays.asList(AnomalyType.values()),
                ALL_TYPES, type -> Labels.anomalyTitle(type));
        ReviewChoices.fill(severityFilter, Arrays.asList(Severity.ERROR,
                Severity.WARNING, Severity.INFO), ALL_SEVERITIES,
                severity -> Labels.severityLabel(severity));
        ReviewChoices.fill(decisionFilter,
                Arrays.asList(ReviewDecision.values()), ALL_DECISIONS,
                decision -> decision.getLabel());
        sortBox.setItems(FXCollections.observableArrayList(
                SortOrder.SEVERITY, SortOrder.NAME_ASCENDING,
                SortOrder.NAME_DESCENDING));
    }

    /**
     * Suggests usernames and saves each change to UiState, then reloads.
     *
     * @param onChange reloads the table
     */
    public void listen(Runnable onChange) {
        TextFields.bindAutoCompletion(searchField,
                request -> suggestions(request.getUserText()));
        searchField.textProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (!restoring) {
                        state.setAnomalyQuery(textOf(newValue));
                        onChange.run();
                    }
                });
        typeFilter.valueProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (!restoring) {
                        state.setAnomalyTypeFilter(newValue);
                        onChange.run();
                    }
                });
        severityFilter.valueProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (!restoring) {
                        state.setSeverityFilter(newValue);
                        onChange.run();
                    }
                });
        decisionFilter.valueProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (!restoring) {
                        state.setDecisionFilter(newValue);
                        onChange.run();
                    }
                });
        sortBox.valueProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (!restoring && newValue != null) {
                        state.setAnomalySort(newValue);
                        onChange.run();
                    }
                });
    }

    /**
     * Copies the choices from UiState into the controls, without reloading.
     */
    public void showState() {
        restoring = true;
        searchField.setText(state.getAnomalyQuery());
        typeFilter.setValue(state.getAnomalyTypeFilter());
        severityFilter.setValue(state.getSeverityFilter());
        decisionFilter.setValue(state.getDecisionFilter());
        sortBox.setValue(state.getAnomalySort());
        restoring = false;
    }

    /**
     * Clears the search and filters in UiState, but keeps the sort order.
     */
    public void clear() {
        state.setAnomalyQuery("");
        state.setAnomalyTypeFilter(null);
        state.setSeverityFilter(null);
        state.setDecisionFilter(null);
    }

    /**
     * Moves keyboard focus to the search field.
     */
    public void focusSearch() {
        searchField.requestFocus();
        searchField.selectAll();
    }

    /**
     * Returns the flags that pass the search and filters, in the chosen order.
     *
     * @param anomalies every flag of the run
     * @param decisionOf returns a flag's review decision
     * @return the flags to show
     */
    public List<Anomaly> apply(List<Anomaly> anomalies,
            Function<Anomaly, ReviewDecision> decisionOf) {
        List<Anomaly> shown = new ArrayList<>();
        for (Anomaly anomaly : anomalies) {
            if (matches(anomaly, decisionOf.apply(anomaly))) {
                shown.add(anomaly);
            }
        }
        shown.sort(comparator());
        return shown;
    }

    // Says whether a flag passes the search and every filter.
    private boolean matches(Anomaly anomaly, ReviewDecision decision) {
        AnomalyType type = state.getAnomalyTypeFilter();
        if (type != null && anomaly.getType() != type) {
            return false;
        }
        Severity severity = state.getSeverityFilter();
        if (severity != null && anomaly.getSeverity() != severity) {
            return false;
        }
        ReviewDecision wantedDecision = state.getDecisionFilter();
        if (wantedDecision != null && decision != wantedDecision) {
            return false;
        }

        String wanted = textOf(state.getAnomalyQuery()).trim()
                .toLowerCase();
        if (wanted.isEmpty()) {
            return true;
        }
        return contains(anomaly.getUsername(), wanted)
                || contains(students.fullName(anomaly.getUsername()), wanted)
                || contains(Labels.anomalyTitle(anomaly.getType()), wanted)
                || contains(anomaly.getMessage(), wanted);
    }

    // Returns the order chosen in the Sort menu.
    private Comparator<Anomaly> comparator() {
        SortOrder order = state.getAnomalySort();
        if (order == SortOrder.NAME_ASCENDING) {
            return new UsernameComparator();
        } else if (order == SortOrder.NAME_DESCENDING) {
            return new UsernameComparator().reversed();
        }
        return new SeverityComparator();
    }

    // Returns the usernames that hold the typed text.
    private List<String> suggestions(String text) {
        List<String> matches = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return matches;
        }
        String wanted = text.trim().toLowerCase();
        for (String username : students.getUsernames()) {
            if (matches.size() >= MAX_SUGGESTIONS) {
                return matches;
            }
            if (username.toLowerCase().contains(wanted)
                    && !username.equalsIgnoreCase(text.trim())) {
                matches.add(username);
            }
        }
        return matches;
    }

    // Says whether some text holds the wanted lower-case text.
    private static boolean contains(String text, String wanted) {
        return text != null && text.toLowerCase().contains(wanted);
    }

    // Returns the text, or an empty string for null.
    private static String textOf(String text) {
        if (text == null) {
            return "";
        }
        return text;
    }

    /**
     * Orders flags by severity, errors first, then by username.
     */
    private static final class SeverityComparator
            implements Comparator<Anomaly> {

        /**
         * Compares two flags.
         *
         * @param first the first flag
         * @param second the second flag
         * @return the order
         */
        @Override
        public int compare(Anomaly first, Anomaly second) {
            int order = Integer.compare(
                    Labels.severityRank(first.getSeverity()),
                    Labels.severityRank(second.getSeverity()));
            if (order != 0) {
                return order;
            }
            return first.getUsername().compareToIgnoreCase(
                    second.getUsername());
        }
    }

    /**
     * Orders flags by username, then by severity.
     */
    private static final class UsernameComparator
            implements Comparator<Anomaly> {

        /**
         * Compares two flags.
         *
         * @param first the first flag
         * @param second the second flag
         * @return the order
         */
        @Override
        public int compare(Anomaly first, Anomaly second) {
            int order = first.getUsername().compareToIgnoreCase(
                    second.getUsername());
            if (order != 0) {
                return order;
            }
            return Integer.compare(Labels.severityRank(first.getSeverity()),
                    Labels.severityRank(second.getSeverity()));
        }
    }
}
