/*
 * File: Screen.java
 *
 * Purpose: Lists every screen with its FXML file and title.
 *
 * Where it fits: ScreenNavigator loads a screen by one of these constants.
 * To add a screen, add a constant, an FXML file and a controller.
 */
package gradezzy.ui;

/**
 * The screens of the application.
 */
public enum Screen {

    /** The main window that hosts the other screens. */
    MAIN("main.fxml", "GradeZZY"),

    /** Modules, sections, runs and recent activity. */
    OVERVIEW("overview.fxml", "Overview"),

    /** The sections of each module. */
    MODULES("modules.fxml", "Modules"),

    /** The rubric, testers and typed test cases. */
    TEST_CASES("test-cases.fxml", "Assessments"),

    /** Upload of submissions for one section. */
    UPLOAD("upload.fxml", "Submissions"),

    /** Working-copy repairs to approve or reject. */
    VALIDATION("validation.fxml", "Validation"),

    /** Progress of a running batch. */
    PROGRESS("progress.fxml", "Grading Progress"),

    /** The anomaly log with review decisions. */
    ANOMALY_LOG("anomaly-log.fxml", "Review Anomalies"),

    /** Per-question and per-student results. */
    RESULTS("results.fxml", "Results"),

    /** The evidence behind one student's result. */
    STUDENT_REPORT("student-report.fxml", "Student Evidence"),

    /** The statistics dashboard. */
    STATISTICS("statistics.fxml", "Statistics"),

    /** Pairs of similar submissions. */
    PLAGIARISM("plagiarism.fxml", "Similarity Review"),

    /** Earlier runs, backup and recovery. */
    HISTORY("history.fxml", "History"),

    /** The audit log. */
    AUDIT_LOG("audit-log.fxml", "Audit Log"),

    /** The settings page. */
    SETTINGS("settings.fxml", "Settings"),

    /** Walkthroughs, shortcuts and backend connections. */
    HELP("help.fxml", "Help");

    // The classpath folder that holds every FXML file
    private static final String FXML_FOLDER = "/gradezzy/ui/fxml/";

    // The name of the FXML file of this screen
    private final String fxmlFile;

    // The title shown for this screen
    private final String title;

    // Creates a screen constant.
    Screen(String fxmlFile, String title) {
        this.fxmlFile = fxmlFile;
        this.title = title;
    }

    /**
     * Returns the classpath location of the FXML file of this screen.
     *
     * @return the resource path, starting with a slash
     */
    public String getFxmlPath() {
        return FXML_FOLDER + fxmlFile;
    }

    /**
     * Returns the title shown for this screen.
     *
     * @return the title
     */
    public String getTitle() {
        return title;
    }
}
