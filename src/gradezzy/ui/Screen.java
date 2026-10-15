/*
 * File: Screen.java
 *
 * Purpose: Lists every screen of the application with its FXML file and
 * window title.
 *
 * Where it fits: ScreenNavigator loads a screen by one of these constants,
 * so FXML file names are written in exactly one place. To add a screen,
 * add a constant here, an FXML file in the fxml folder and a controller.
 *
 * AI use: OVERVIEW, MODULES, VALIDATION and HELP added with Claude Code,
 * reviewed by Jun Sheng.
 */
package gradezzy.ui;

/**
 * The screens of the application.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public enum Screen {

    /** The main window that hosts the other screens. Tier: MUST. */
    MAIN("main.fxml", "GradeZZY"),

    /** Modules, sections, runs and recent activity. Tier: SHOULD. */
    OVERVIEW("overview.fxml", "Overview"),

    /** The sections of each module. Tier: SHOULD. */
    MODULES("modules.fxml", "Modules"),

    /** The rubric, testers and typed test cases. Tier: SHOULD. */
    TEST_CASES("test-cases.fxml", "Assessments"),

    /** Upload of submissions for one section. Tier: MUST. */
    UPLOAD("upload.fxml", "Submissions"),

    /** Working-copy repairs to approve or reject. Tier: SHOULD. */
    VALIDATION("validation.fxml", "Validation"),

    /** Progress of a running batch. Tier: MUST. */
    PROGRESS("progress.fxml", "Grading Progress"),

    /** The anomaly log with review decisions. Tier: SHOULD. */
    ANOMALY_LOG("anomaly-log.fxml", "Review Anomalies"),

    /** Per-question and per-student results. Tier: MUST. */
    RESULTS("results.fxml", "Results"),

    /** The evidence behind one student's result. Tier: SHOULD. */
    STUDENT_REPORT("student-report.fxml", "Student Evidence"),

    /** The statistics dashboard. Tier: SHOULD. */
    STATISTICS("statistics.fxml", "Statistics"),

    /** Pairs of similar submissions. Tier: COULD. */
    PLAGIARISM("plagiarism.fxml", "Similarity Review"),

    /** Earlier runs, backup and recovery. Tier: COULD. */
    HISTORY("history.fxml", "History"),

    /** The audit log. Tier: SHOULD. */
    AUDIT_LOG("audit-log.fxml", "Audit Log"),

    /** The settings page. Tier: SHOULD. */
    SETTINGS("settings.fxml", "Settings"),

    /** Walkthroughs, shortcuts and backend connections. Tier: SHOULD. */
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
