/*
 * File: Capability.java
 *
 * Purpose: Lists every backend feature the interface uses and how ready it is.
 *
 * Where it fits: DashboardBackend.supports takes one, and the screens
 * hide what is not supported. When a service method is done, change its
 * status here from WAITING to WORKS_NOW.
 */
package gradezzy.ui.backend;

/**
 * A backend feature used by the user interface.
 */
public enum Capability {

    /** Module, sections, rubric and time limits from config.properties. */
    CONFIGURED_CONTEXT("Module, sections, rubric and limits",
            "Shows the module, sections, marks and time limits set in "
            + "config.properties.", "Jing Yuan",
            "AppConfig, AssessmentFactory.create",
            CapabilityStatus.WORKS_NOW,
            "Nothing; values come from config.properties."),

    /** Checks that a chosen file or folder can be read. */
    INPUT_CHECKS("Check chosen files and folders",
            "Tells you at once when a chosen file or folder cannot be "
            + "read.", "Seth",
            "InputValidator.isReadableFile, isReadableDirectory",
            CapabilityStatus.WORKS_NOW,
            "Nothing; unreadable files are marked before grading."),

    /** Imports a batch and grades it. */
    IMPORT_AND_GRADE("Import and grade a batch",
            "Grades every submission of a section in one batch.",
            "Jing Yuan, with Seth, Damien, Jun Sheng, Zach and Huxley",
            "GradingService.startGrading", CapabilityStatus.WORKS_NOW,
            "Nothing; every pipeline stage works."),

    /** Stops a running batch after the current submission. */
    CANCEL_GRADING("Cancel grading",
            "Stops a batch early and keeps the results already finished.",
            "Jing Yuan", "GradingService.cancel",
            CapabilityStatus.WORKS_NOW,
            "Nothing; cancelling already works."),

    /** Grades the failed and cancelled submissions of a run again. */
    RETRY_FAILED("Retry failed submissions",
            "Grades the submissions that failed or were cancelled again.",
            "Jing Yuan", "GradingService.retryFailed",
            CapabilityStatus.WORKS_NOW,
            "Nothing; it works for the last run of this session."),

    /** Continues a saved run. */
    RESUME_RUN("Resume a cancelled run",
            "Finishes a cancelled run without grading finished students "
            + "again.", "Jing Yuan, Huxley",
            "GradingService.resume", CapabilityStatus.WORKS_NOW,
            "Nothing; a run from an earlier session uses the score sheet "
            + "and testers in the settings."),

    /** Fraction done and time left while grading. */
    PROGRESS_ESTIMATE("Progress and time left",
            "Shows how far grading has got and about how long is left.",
            "Jing Yuan", "ProgressListener, ProgressEstimator",
            CapabilityStatus.WORKS_NOW,
            "Nothing; progress events and the estimate already work."),

    /** Every anomaly of a run. */
    ANOMALY_LIST("List anomalies",
            "Lists every problem the checks found, so you can review it.",
            "Jing Yuan", "AnomalyService.listAnomalies",
            CapabilityStatus.WORKS_NOW,
            "Nothing; it lists whatever the stages recorded."),

    /** Approve or undo automatic fixes, one at a time or several. */
    REPAIR_DECISIONS("Approve and undo repairs",
            "Lets you accept or undo each automatic repair of a working "
            + "copy.", "Jing Yuan, Jun Sheng",
            "AnomalyService.approveFix, undoFix",
            CapabilityStatus.WORKS_NOW,
            "Nothing; undo needs the run graded in this session."),

    /** The original and proposed value of each repair. */
    REPAIR_VALUES("Original and proposed repair values",
            "Shows what each repair changed, before and after, side by "
            + "side.", "Jun Sheng", null, CapabilityStatus.PROPOSED,
            "A way to read each FixRecord's original and proposed value "
            + "through AnomalyService."),

    /** Returns an approved or undone repair to Applied. */
    REPAIR_DECISION_UNDO("Undo a repair decision",
            "Takes back an approve or undo decision on a repair.",
            "Jing Yuan, Jun Sheng", null, CapabilityStatus.PROPOSED,
            "A method that returns an approved or undone fix to Applied."),

    /** Records a review decision on a flag. */
    FLAG_REVIEW("Mark flags reviewed or rejected",
            "Records that you looked at a flag, without changing any "
            + "mark.", "Jing Yuan", null, CapabilityStatus.PROPOSED,
            "Store an Open, Reviewed or Rejected decision for each "
            + "anomaly, with undo and an audit entry."),

    /** Searches, filters and sorts students. */
    SEARCH("Search, filter and sort",
            "Finds students by name and orders the results table.",
            "Jing Yuan", "SearchService.search", CapabilityStatus.WORKS_NOW,
            "Nothing; searching already works."),

    /** Finds students whose source code holds some text. */
    CODE_SEARCH("Search inside source code",
            "Finds the students whose code contains some text.",
            "Jing Yuan", "SearchService.searchCode",
            CapabilityStatus.WORKS_NOW,
            "Working copies on disk, which need Damien's extraction."),

    /** The folder tree and source files of a working copy. */
    SOURCE_VIEW("Show working-copy files",
            "Shows a student's files, so you can read the code behind a "
            + "mark.", "Damien", "FileUtils.readText",
            CapabilityStatus.WORKS_NOW,
            "Nothing; files are read from the working copy."),

    /** Writes the score sheet CSV or JSON. */
    EXPORT_RESULTS("Export CSV and JSON",
            "Saves the score sheet as a CSV or JSON file.",
            "Jing Yuan, Huxley", "ExportService.export",
            CapabilityStatus.WORKS_NOW,
            "Nothing; the CSV and JSON exporters work."),

    /** Exports only some students. */
    EXPORT_SCOPE("Export only chosen students",
            "Exports only the students you selected or filtered.",
            "Jing Yuan, Huxley", null, CapabilityStatus.PROPOSED,
            "An export method that takes the visible or selected "
            + "students instead of the whole run."),

    /** The statistics dashboard. */
    STATISTICS("Statistics",
            "Summarises the marks and common mistakes of a section.",
            "Jing Yuan, Huxley", "StatisticsService.summarise",
            CapabilityStatus.WORKS_NOW,
            "Nothing; it summarises the saved runs."),

    /** Earlier grading runs. */
    HISTORY("Earlier runs",
            "Lists earlier grading runs, so you can go back to one.",
            "Jing Yuan, Huxley", "HistoryService.listRuns",
            CapabilityStatus.WORKS_NOW,
            "Nothing; it lists the saved runs."),

    /** Backs up the database now. */
    BACKUP("Back up now",
            "Saves a copy of the saved results that you can restore "
            + "later.", "Jing Yuan, Huxley", "HistoryService.backupNow",
            CapabilityStatus.WORKS_NOW,
            "Nothing; backups are written to paths.backup.dir."),

    /** Puts the saved results back from a chosen backup file. */
    RESTORE("Restore a backup",
            "Puts the saved results back to a backup file you choose.",
            "Jing Yuan, Huxley", "HistoryService.restore",
            CapabilityStatus.WORKS_NOW,
            "Nothing; restore when no run is being graded."),

    /** Shows what a backup holds before restoring it. */
    RESTORE_PREVIEW("Restore preview",
            "Shows which run each backup holds and saves a recovery "
            + "snapshot first, so a restore can be undone.",
            "Jing Yuan, Huxley", null, CapabilityStatus.PROPOSED,
            "A list of backup files with the run each holds, and a "
            + "restore of one run that saves a recovery snapshot first."),

    /** The audit log. */
    AUDIT_LOG("Audit log",
            "Keeps a dated record of every action, so changes can be "
            + "checked later.", "Jing Yuan, Huxley",
            "AuditService.record, listEntries",
            CapabilityStatus.WORKS_NOW,
            "Nothing; entries are saved when audit.enabled is true."),

    /** Exports the audit log. */
    AUDIT_EXPORT("Export the audit log",
            "Saves the audit log as a file to keep or share.",
            "Jing Yuan, Huxley", null, CapabilityStatus.PROPOSED,
            "Register AuditLogExporter in ApplicationContext and add an "
            + "export method for the audit log."),

    /** Reads, checks and saves settings. */
    SETTINGS("Settings",
            "Lets you check and change the settings from the app instead "
            + "of a text file.", "Jing Yuan", "SettingsService",
            CapabilityStatus.WORKS_NOW,
            "Nothing; settings already validate and save."),

    /** Pairs of similar submissions. */
    SIMILARITY("Similarity review",
            "Lists pairs of submissions with very similar code for you to "
            + "review.", "Jing Yuan, Jun Sheng",
            "PlagiarismService.check", CapabilityStatus.WORKS_NOW,
            "Nothing; it runs when plagiarism.enabled is true."),

    /** The compared files and excerpts of one similar pair. */
    SIMILARITY_DETAIL("Compare a similar pair",
            "Shows the matching code of a pair side by side.", "Jun Sheng",
            null, CapabilityStatus.PROPOSED,
            "The compared files, matching excerpts and the excluded "
            + "starter and tester files of one pair."),

    /** Testers from a folder. */
    TESTER_FOLDER("Use a tester folder",
            "Grades with the tester files you already have in a folder.",
            "Zach", "UploadedTesterSource", CapabilityStatus.WORKS_NOW,
            "Nothing; testers are matched to questions by name."),

    /** Testers generated from typed test cases. */
    TYPED_TEST_CASES("Typed test cases",
            "Builds testers from test cases you type in the app.", "Zach",
            "TypedTestCaseSource, TesterGenerator", CapabilityStatus.WAITING,
            "TypedTestCaseSource.prepareTesters and TesterGenerator."),

    /** Runs the tester of one question again. */
    RETRY_QUESTION("Retry one question",
            "Runs one question's tester again without touching other "
            + "marks.", "Jing Yuan, Zach", null, CapabilityStatus.PROPOSED,
            "Run the tester of one question again and replace only that "
            + "question's result."),

    /** Grades one chosen submission again. */
    RETRY_SUBMISSION("Retry one submission",
            "Grades one student's submission again from the start.",
            "Jing Yuan", null, CapabilityStatus.PROPOSED,
            "Grade one chosen submission again, even when it did not "
            + "fail."),

    /** Changes a mark by hand. */
    SCORE_ADJUSTMENT("Adjust a mark by hand",
            "Lets you change a mark by hand with a reason, and undo it.",
            "Jing Yuan, Huxley", null, CapabilityStatus.PROPOSED,
            "Store a manual score with its reason and the earlier score, "
            + "record it in the audit log, and allow undo."),

    /** More than one module. */
    MULTIPLE_MODULES("More than one module",
            "Lets you grade more than one module in the same app.",
            "Jing Yuan", "AppConfig, AssessmentFactory.create",
            CapabilityStatus.WORKS_NOW,
            "Nothing; modules come from app.module.codes. Saved runs "
            + "keep the module once runs are saved."),

    /** How long each tester ran. */
    QUESTION_TIMING("Time taken per question",
            "Shows how long each question's tester took to run.", "Zach",
            null, CapabilityStatus.PROPOSED,
            "Record how long each tester ran in QuestionResult."),

    /** A report for one student. */
    STUDENT_REPORT_EXPORT("Export one student's report",
            "Saves one student's marks and evidence as a report.",
            "Jing Yuan, Huxley", null, CapabilityStatus.PROPOSED,
            "Register StudentReportExporter and add an export method for "
            + "one submission.");

    // The short name shown to the instructor
    private final String title;

    // What the feature does for the instructor
    private final String purpose;

    // The teammates whose code this feature needs
    private final String owner;

    // The service method behind the feature, or null when proposed
    private final String serviceMethod;

    // How ready the feature is
    private final CapabilityStatus status;

    // What the owner still has to provide
    private final String need;

    // Creates a capability constant.
    Capability(String title, String purpose, String owner,
            String serviceMethod, CapabilityStatus status, String need) {
        this.title = title;
        this.purpose = purpose;
        this.owner = owner;
        this.serviceMethod = serviceMethod;
        this.status = status;
        this.need = need;
    }

    /**
     * Returns the short name shown to the instructor.
     *
     * @return the title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns what the feature does for the instructor, in one sentence.
     *
     * @return the purpose
     */
    public String getPurpose() {
        return purpose;
    }

    /**
     * Returns the teammates whose code this feature needs.
     *
     * @return the owner names
     */
    public String getOwner() {
        return owner;
    }

    /**
     * Returns the service method that LiveBackend calls for this feature.
     *
     * @return the method, or "None yet" when the feature is proposed
     */
    public String getServiceMethod() {
        if (serviceMethod == null) {
            return "None yet";
        }
        return serviceMethod;
    }

    /**
     * Returns how ready this feature is.
     *
     * @return the status
     */
    public CapabilityStatus getStatus() {
        return status;
    }

    /**
     * Returns what the owner still has to provide.
     *
     * @return a plain description
     */
    public String getNeed() {
        return need;
    }

    /**
     * Says whether a service method exists, so live mode may call it.
     *
     * @return true unless the feature is only proposed
     */
    public boolean isWired() {
        return status != CapabilityStatus.PROPOSED;
    }
}
