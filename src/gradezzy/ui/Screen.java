/*
 * File: Screen.java
 *
 * Purpose: Lists every screen of the application with its FXML file and
 * window title.
 *
 * Where it fits: ScreenNavigator loads a screen by one of these constants,
 * so FXML file names are written in exactly one place. To add a screen,
 * add a constant here, an FXML file in the fxml folder and a controller.
 */
package gradezzy.ui;

/**
 * The screens of the application.
 *
 * <p>Tier: MUST. Owner: Seth.
 */
public enum Screen {

    /** The main window that hosts the other screens. Tier: MUST. */
    MAIN("main.fxml", "GradeZZY"),

    /** Upload of submissions for one section. Tier: MUST. */
    UPLOAD("upload.fxml", "Upload submissions"),

    /** Upload of testers and entry of test cases. Tier: SHOULD. */
    TEST_CASES("test-cases.fxml", "Test cases"),

    /** Progress of a running batch. Tier: MUST. */
    PROGRESS("progress.fxml", "Grading progress"),

    /** The anomaly log with approve and undo. Tier: SHOULD. */
    ANOMALY_LOG("anomaly-log.fxml", "Anomaly log"),

    /** Per-question and per-student results. Tier: MUST. */
    RESULTS("results.fxml", "Results"),

    /** The report of one student. Tier: SHOULD. */
    STUDENT_REPORT("student-report.fxml", "Student report"),

    /** The statistics dashboard. Tier: SHOULD. */
    STATISTICS("statistics.fxml", "Statistics"),

    /** Pairs of similar submissions. Tier: COULD. */
    PLAGIARISM("plagiarism.fxml", "Plagiarism check"),

    /** Earlier runs, backup and recovery. Tier: COULD. */
    HISTORY("history.fxml", "History"),

    /** The audit log. Tier: SHOULD. */
    AUDIT_LOG("audit-log.fxml", "Audit log"),

    /** The settings page. Tier: SHOULD. */
    SETTINGS("settings.fxml", "Settings");

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
