/*
 * File: UiState.java
 *
 * Purpose: Holds everything the interface must remember while the
 * instructor moves between screens: module, section, run, filters,
 * sort, selection, theme and the animation setting.
 *
 * Where it fits: One object is created by MainApp and given to every
 * controller. Screens read it when they are shown and write to it when
 * the instructor changes something, so navigation and theme switches
 * never lose context.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.Section;
import gradezzy.model.Severity;
import gradezzy.ui.backend.ImportBatch;
import gradezzy.ui.backend.ResultState;
import gradezzy.ui.backend.ReviewDecision;

/**
 * The state shared by every screen.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class UiState {

    /** The window width below which the layout becomes compact. */
    public static final double COMPACT_WIDTH = 1100.0;

    /** The window width below which the sidebar becomes a menu. */
    public static final double NARROW_WIDTH = 760.0;

    // The module shown in the context bar
    private final StringProperty moduleCode = new SimpleStringProperty("");

    // The section shown in the context bar
    private final StringProperty sectionCode =
            new SimpleStringProperty("");

    // The screen on show
    private final ObjectProperty<Screen> currentScreen =
            new SimpleObjectProperty<>(Screen.OVERVIEW);

    // Whether the dark theme is on
    private final BooleanProperty dark = new SimpleBooleanProperty(false);

    // Whether interface animations are on
    private final BooleanProperty animationsEnabled =
            new SimpleBooleanProperty(true);

    // Whether the window is narrower than COMPACT_WIDTH
    private final BooleanProperty compact = new SimpleBooleanProperty(false);

    // Whether the window is narrower than NARROW_WIDTH
    private final BooleanProperty narrow = new SimpleBooleanProperty(false);

    // Whether the instructor opened the inspector in a compact window
    private final BooleanProperty inspectorRequested =
            new SimpleBooleanProperty(false);

    // Goes up whenever results, decisions or the shown run change
    private final IntegerProperty dataVersion = new SimpleIntegerProperty(0);

    // The assessment in the context bar, set by the context bar
    private final ObjectProperty<Assessment> assessment =
            new SimpleObjectProperty<>(null);

    // The run of the section in the context bar, or null when it has none
    private final ObjectProperty<GradingRun> run =
            new SimpleObjectProperty<>(null);

    // The files chosen for the next run
    private final ImportBatch importBatch = new ImportBatch();

    // The progress of the run in the background
    private final GradingMonitor monitor = new GradingMonitor();

    // The student selected on Results and shown in Student Evidence
    private String selectedUsername = "";

    // The Results search text
    private String resultsQuery = "";

    // Whether the Results search looks inside source code
    private boolean resultsCodeSearch = false;

    // The Results state filter, or null for every state
    private ResultState resultsFilter = null;

    // The Results sort order
    private SortOrder resultsSort = SortOrder.NAME_ASCENDING;

    // The usernames selected on Results
    private final Set<String> selectedResults = new LinkedHashSet<>();

    // The Review Anomalies search text
    private String anomalyQuery = "";

    // The anomaly type filter, or null for every type
    private AnomalyType anomalyTypeFilter = null;

    // The severity filter, or null for every severity
    private Severity severityFilter = null;

    // The decision filter, or null for every decision
    private ReviewDecision decisionFilter = null;

    // The Review Anomalies sort order
    private SortOrder anomalySort = SortOrder.SEVERITY;

    // The anomaly ids selected on Review Anomalies
    private final Set<String> selectedAnomalies = new LinkedHashSet<>();

    // The anomaly ids selected on Validation
    private final Set<String> selectedRepairs = new LinkedHashSet<>();

    // The decision each anomaly had before its latest change, for Undo
    private final Map<String, ReviewDecision> previousDecisions =
            new HashMap<>();

    // The tab shown on Student Evidence
    private String evidenceTab = "";

    // The question chosen on Student Evidence
    private String evidenceQuestionId = "";

    /**
     * Creates the state with nothing chosen yet.
     */
    public UiState() {
        // nothing to set up
    }

    /**
     * Returns the chosen module and section.
     *
     * @return the section, built from the context bar choices
     */
    public Section getSection() {
        return new Section(moduleCode.get(), sectionCode.get());
    }

    /**
     * Records that results, decisions or the shown run changed, so every
     * screen reloads when it is next shown.
     */
    public void markDataChanged() {
        dataVersion.set(dataVersion.get() + 1);
    }

    /**
     * Returns the module code property.
     *
     * @return the property
     */
    public StringProperty moduleCodeProperty() {
        return moduleCode;
    }

    /**
     * Returns the section code property.
     *
     * @return the property
     */
    public StringProperty sectionCodeProperty() {
        return sectionCode;
    }

    /**
     * Returns the property of the screen on show.
     *
     * @return the property
     */
    public ObjectProperty<Screen> currentScreenProperty() {
        return currentScreen;
    }

    /**
     * Returns the dark theme property.
     *
     * @return the property
     */
    public BooleanProperty darkProperty() {
        return dark;
    }

    /**
     * Returns the animation property.
     *
     * @return the property
     */
    public BooleanProperty animationsEnabledProperty() {
        return animationsEnabled;
    }

    /**
     * Returns the property that is true below COMPACT_WIDTH.
     *
     * @return the property
     */
    public BooleanProperty compactProperty() {
        return compact;
    }

    /**
     * Returns the property that is true below NARROW_WIDTH.
     *
     * @return the property
     */
    public BooleanProperty narrowProperty() {
        return narrow;
    }

    /**
     * Returns the property that is true when the instructor opened the
     * inspector in a compact window.
     *
     * @return the property
     */
    public BooleanProperty inspectorRequestedProperty() {
        return inspectorRequested;
    }

    /**
     * Returns the data version property.
     *
     * @return the property
     */
    public IntegerProperty dataVersionProperty() {
        return dataVersion;
    }

    /**
     * Returns the assessment shown in the context bar. The context bar
     * keeps it up to date; screens only read it.
     *
     * @return the assessment property
     */
    public ObjectProperty<Assessment> assessmentProperty() {
        return assessment;
    }

    /**
     * Returns the grading run of the section shown in the context bar,
     * or null when the section has none. The context bar keeps it up to
     * date; screens only read it.
     *
     * @return the run property
     */
    public ObjectProperty<GradingRun> runProperty() {
        return run;
    }

    /**
     * Returns the files chosen for the next run.
     *
     * @return the batch
     */
    public ImportBatch getImportBatch() {
        return importBatch;
    }

    /**
     * Returns the progress of the run in the background.
     *
     * @return the monitor
     */
    public GradingMonitor getMonitor() {
        return monitor;
    }

    /**
     * Returns the selected student.
     *
     * @return the username, or an empty string
     */
    public String getSelectedUsername() {
        return selectedUsername;
    }

    /**
     * Selects a student.
     *
     * @param selectedUsername the username, or an empty string
     */
    public void setSelectedUsername(String selectedUsername) {
        this.selectedUsername = selectedUsername;
    }

    /**
     * Returns the Results search text.
     *
     * @return the text
     */
    public String getResultsQuery() {
        return resultsQuery;
    }

    /**
     * Sets the Results search text.
     *
     * @param resultsQuery the text
     */
    public void setResultsQuery(String resultsQuery) {
        this.resultsQuery = resultsQuery;
    }

    /**
     * Says whether the Results search looks inside source code.
     *
     * @return true for a code search
     */
    public boolean isResultsCodeSearch() {
        return resultsCodeSearch;
    }

    /**
     * Chooses whether the Results search looks inside source code.
     *
     * @param resultsCodeSearch true for a code search
     */
    public void setResultsCodeSearch(boolean resultsCodeSearch) {
        this.resultsCodeSearch = resultsCodeSearch;
    }

    /**
     * Returns the Results state filter.
     *
     * @return the state, or null for every state
     */
    public ResultState getResultsFilter() {
        return resultsFilter;
    }

    /**
     * Sets the Results state filter.
     *
     * @param resultsFilter the state, or null for every state
     */
    public void setResultsFilter(ResultState resultsFilter) {
        this.resultsFilter = resultsFilter;
    }

    /**
     * Returns the Results sort order.
     *
     * @return the order
     */
    public SortOrder getResultsSort() {
        return resultsSort;
    }

    /**
     * Sets the Results sort order.
     *
     * @param resultsSort the order
     */
    public void setResultsSort(SortOrder resultsSort) {
        this.resultsSort = resultsSort;
    }

    /**
     * Returns the usernames selected on Results.
     *
     * @return the selection, which may be changed
     */
    public Set<String> getSelectedResults() {
        return selectedResults;
    }

    /**
     * Returns the Review Anomalies search text.
     *
     * @return the text
     */
    public String getAnomalyQuery() {
        return anomalyQuery;
    }

    /**
     * Sets the Review Anomalies search text.
     *
     * @param anomalyQuery the text
     */
    public void setAnomalyQuery(String anomalyQuery) {
        this.anomalyQuery = anomalyQuery;
    }

    /**
     * Returns the anomaly type filter.
     *
     * @return the type, or null for every type
     */
    public AnomalyType getAnomalyTypeFilter() {
        return anomalyTypeFilter;
    }

    /**
     * Sets the anomaly type filter.
     *
     * @param anomalyTypeFilter the type, or null for every type
     */
    public void setAnomalyTypeFilter(AnomalyType anomalyTypeFilter) {
        this.anomalyTypeFilter = anomalyTypeFilter;
    }

    /**
     * Returns the severity filter.
     *
     * @return the severity, or null for every severity
     */
    public Severity getSeverityFilter() {
        return severityFilter;
    }

    /**
     * Sets the severity filter.
     *
     * @param severityFilter the severity, or null for every severity
     */
    public void setSeverityFilter(Severity severityFilter) {
        this.severityFilter = severityFilter;
    }

    /**
     * Returns the decision filter.
     *
     * @return the decision, or null for every decision
     */
    public ReviewDecision getDecisionFilter() {
        return decisionFilter;
    }

    /**
     * Sets the decision filter.
     *
     * @param decisionFilter the decision, or null for every decision
     */
    public void setDecisionFilter(ReviewDecision decisionFilter) {
        this.decisionFilter = decisionFilter;
    }

    /**
     * Returns the Review Anomalies sort order.
     *
     * @return the order
     */
    public SortOrder getAnomalySort() {
        return anomalySort;
    }

    /**
     * Sets the Review Anomalies sort order.
     *
     * @param anomalySort the order
     */
    public void setAnomalySort(SortOrder anomalySort) {
        this.anomalySort = anomalySort;
    }

    /**
     * Returns the anomaly ids selected on Review Anomalies.
     *
     * @return the selection, which may be changed
     */
    public Set<String> getSelectedAnomalies() {
        return selectedAnomalies;
    }

    /**
     * Returns the anomaly ids selected on Validation.
     *
     * @return the selection, which may be changed
     */
    public Set<String> getSelectedRepairs() {
        return selectedRepairs;
    }

    /**
     * Returns the decision each anomaly had before its latest change.
     *
     * @return the decisions by anomaly id, which may be changed
     */
    public Map<String, ReviewDecision> getPreviousDecisions() {
        return previousDecisions;
    }

    /**
     * Returns the tab shown on Student Evidence.
     *
     * @return the tab name, or an empty string for the first tab
     */
    public String getEvidenceTab() {
        return evidenceTab;
    }

    /**
     * Sets the tab shown on Student Evidence.
     *
     * @param evidenceTab the tab name
     */
    public void setEvidenceTab(String evidenceTab) {
        this.evidenceTab = evidenceTab;
    }

    /**
     * Returns the question chosen on Student Evidence.
     *
     * @return the question id, or an empty string
     */
    public String getEvidenceQuestionId() {
        return evidenceQuestionId;
    }

    /**
     * Sets the question chosen on Student Evidence.
     *
     * @param evidenceQuestionId the question id
     */
    public void setEvidenceQuestionId(String evidenceQuestionId) {
        this.evidenceQuestionId = evidenceQuestionId;
    }
}
