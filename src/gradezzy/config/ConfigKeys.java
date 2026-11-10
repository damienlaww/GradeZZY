/*
 * File: ConfigKeys.java
 *
 * Purpose: Names of every setting in config.properties, as constants.
 *
 * Where it fits: AppConfig and every class that reads a setting use these
 * names, so a key is typed in exactly one place.
 */
package gradezzy.config;

/**
 * Names of every setting in config.properties, as constants.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public final class ConfigKeys {

    /**
     * Comma separated course codes the instructor can pick from. The
     * first is the default.
     */
    public static final String MODULE_CODES = "app.module.codes";

    /**
     * Comma separated section codes the instructor can pick from.
     */
    public static final String SECTION_CODES = "app.section.codes";

    /**
     * Modules that have only their own sections, not those of
     * SECTION_CODES, such as a module added on Home. Comma separated.
     */
    public static final String OWN_SECTIONS_ONLY_MODULES =
            "app.module.codes.own.sections.only";

    /**
     * Sections of one module only, such as "CS102:G5|G6;IS442:G1". They
     * are added to SECTION_CODES for that module.
     */
    public static final String MODULE_SECTION_CODES =
            "app.section.codes.by.module";

    /**
     * Folder of submissions used by the console entry point.
     */
    public static final String SUBMISSIONS_DIR = "paths.submissions.dir";

    /**
     * Folder holding the uploaded tester files.
     */
    public static final String TESTERS_DIR = "paths.testers.dir";

    /**
     * The provided score sheet CSV used as roster and export template.
     */
    public static final String SCORE_SHEET_FILE = "paths.score.sheet.file";

    /**
     * The score sheet of each section, such as
     * "CS102/G1=Test Data/G1.csv;CS102/G2=Test Data/G2.csv". A section
     * with none uses SCORE_SHEET_FILE.
     */
    public static final String SCORE_SHEET_BY_SECTION =
            "paths.score.sheet.by.section";

    /**
     * Folder holding the starter files given to students.
     */
    public static final String STARTER_DIR = "paths.starter.dir";

    /**
     * Folder holding the model answers, laid out like the starter
     * folder. Empty to skip the checks that use them.
     */
    public static final String MODEL_ANSWERS_DIR = "paths.model.answers.dir";

    /**
     * Folder where working copies of submissions are created.
     */
    public static final String WORKING_DIR = "paths.working.dir";

    /**
     * Folder where exports are written.
     */
    public static final String OUTPUT_DIR = "paths.output.dir";

    /**
     * Export folders of single modules or sections, such as
     * "CS102/G1=workspace/g1-exports". Others use OUTPUT_DIR.
     */
    public static final String OUTPUT_DIR_BY_SECTION =
            "paths.output.dir.by.section";

    /**
     * Folder where database backups are kept.
     */
    public static final String BACKUP_DIR = "paths.backup.dir";

    /**
     * Folder where saved assessments are kept, one file each.
     */
    public static final String ASSESSMENTS_DIR = "paths.assessments.dir";

    /**
     * File where the submissions chosen for each section are kept, so
     * Set up lists them again after GradeZZY is reopened.
     */
    public static final String CHOSEN_SUBMISSIONS_FILE =
            "paths.chosen.submissions.file";

    /**
     * Pattern of the text in front of the username in a zip file name.
     */
    public static final String ZIP_NAME_PREFIX_PATTERN =
            "ingest.zip.name.prefix.pattern";

    /**
     * File and folder names skipped while reading a submission.
     */
    public static final String IGNORED_FILE_NAMES = "ingest.ignored.file.names";

    /**
     * Largest unzipped size accepted for one submission.
     */
    public static final String MAX_ZIP_SIZE_MB = "ingest.max.zip.size.mb";

    /**
     * Largest number of entries accepted in one zip file.
     */
    public static final String MAX_ZIP_ENTRIES = "ingest.max.zip.entries";

    /**
     * Name of the assessment.
     */
    public static final String ASSESSMENT_NAME = "assessment.name";

    /**
     * Comma separated question ids, in score sheet order.
     */
    public static final String QUESTION_IDS = "assessment.question.ids";

    /**
     * Starter folder names that mean the student forgot to rename it.
     */
    public static final String PLACEHOLDER_FOLDER_NAMES =
            "assessment.placeholder.folder.names";

    /**
     * Compiled files given to students that must be kept.
     */
    public static final String PROVIDED_CLASS_FILES =
            "assessment.provided.class.files";

    /**
     * Data files the testers read, copied next to each tester.
     */
    public static final String TESTER_DATA_FILES =
            "assessment.tester.data.files";

    /**
     * Label that precedes the student name in a file header.
     */
    public static final String HEADER_NAME_LABEL =
            "validation.header.name.label";

    /**
     * Label that precedes the student email in a file header.
     */
    public static final String HEADER_EMAIL_LABEL =
            "validation.header.email.label";

    /**
     * How many lines at the top of a file count as its header.
     */
    public static final String HEADER_SCAN_LINES =
            "validation.header.scan.lines";

    /**
     * Whether fixes are applied to the working copy automatically.
     */
    public static final String AUTO_FIX_ENABLED = "fix.auto.apply.enabled";

    /**
     * Command used to compile.
     */
    public static final String JAVAC_COMMAND = "exec.javac.command";

    /**
     * Command used to run a tester.
     */
    public static final String JAVA_COMMAND = "exec.java.command";

    /**
     * Time limit for compiling one question folder.
     */
    public static final String COMPILE_TIMEOUT_SECONDS =
            "exec.compile.timeout.seconds";

    /**
     * Time limit for one tester run.
     */
    public static final String RUN_TIMEOUT_SECONDS = "exec.run.timeout.seconds";

    /**
     * How many submissions may be graded at the same time.
     */
    public static final String THREAD_COUNT = "exec.thread.count";

    /**
     * Longest program output kept, to protect memory.
     */
    public static final String MAX_OUTPUT_CHARACTERS =
            "exec.max.output.characters";

    /**
     * Most memory, in MB, one tester run may use for its objects.
     */
    public static final String MAX_MEMORY_MB = "exec.max.memory.mb";

    /**
     * How many times a tester is run again after a test case hangs.
     */
    public static final String MAX_HANG_RERUNS = "exec.max.hang.reruns";

    /**
     * Text added to a question id to form its tester class name.
     */
    public static final String TESTER_CLASS_SUFFIX =
            "grading.tester.class.suffix";

    /**
     * Line a tester prints when a test case passes.
     */
    public static final String PASS_MARKER = "grading.pass.marker";

    /**
     * Start of the line a tester prints when a test case fails.
     */
    public static final String FAIL_MARKER = "grading.fail.marker";

    /**
     * Start of the line a tester prints before each test case.
     */
    public static final String TEST_HEADER_MARKER =
            "grading.test.header.marker";

    /**
     * Whether the console checks each tester with broken copies of the
     * model answer before grading.
     */
    public static final String TESTER_STRENGTH_ENABLED =
            "tester.strength.enabled";

    /**
     * The most broken copies made of each question's model answer.
     */
    public static final String TESTER_STRENGTH_MAX_COPIES =
            "tester.strength.max.copies";

    /**
     * Time limit, in seconds, for the tester on one broken copy.
     */
    public static final String TESTER_STRENGTH_TIMEOUT_SECONDS =
            "tester.strength.timeout.seconds";

    /**
     * Whether students' methods are called with random inputs after
     * grading and compared with the model answer.
     */
    public static final String RANDOM_INPUTS_ENABLED =
            "random.inputs.enabled";

    /**
     * How many inputs each method is called with.
     */
    public static final String RANDOM_INPUTS_COUNT = "random.inputs.count";

    /**
     * Whether untouched starter files are flagged.
     */
    public static final String DETECT_UNTOUCHED_ENABLED =
            "detection.untouched.enabled";

    /**
     * Whether empty answers are flagged.
     */
    public static final String DETECT_EMPTY_ENABLED = "detection.empty.enabled";

    /**
     * Whether hard-coded answers are flagged.
     */
    public static final String DETECT_HARD_CODED_ENABLED =
            "detection.hardcoded.enabled";

    /**
     * Whether the similarity check runs after grading.
     */
    public static final String PLAGIARISM_ENABLED = "plagiarism.enabled";

    /**
     * Similarity from 0 to 1 at which a pair is flagged.
     */
    public static final String PLAGIARISM_THRESHOLD =
            "plagiarism.similarity.threshold";

    /**
     * How answers are compared: winnowing (rename-proof fingerprints) or
     * trigrams (runs of three tokens as written).
     */
    public static final String PLAGIARISM_ALGORITHM = "plagiarism.algorithm";

    /**
     * The fewest lines of new code each answer needs before it is
     * compared: blank lines, comments, lines of only braces and lines
     * also in the starter file are not counted.
     */
    public static final String PLAGIARISM_MIN_LINES = "plagiarism.min.lines";

    /**
     * Line ending of the score sheet: CRLF or LF.
     */
    public static final String CSV_LINE_ENDING = "export.csv.line.ending";

    /**
     * Character in front of the id and username columns.
     */
    public static final String CSV_ID_PREFIX = "export.csv.id.prefix";

    /**
     * Value of the last column of every score sheet row.
     */
    public static final String CSV_END_OF_LINE_MARKER =
            "export.csv.end.of.line.marker";

    /**
     * Percentage of the maximum mark that counts as a pass.
     */
    public static final String PASS_MARK_PERCENT = "stats.pass.mark.percent";

    /**
     * Width of each mark band in the statistics chart.
     */
    public static final String MARK_BAND_WIDTH_PERCENT =
            "stats.mark.band.width.percent";

    /**
     * Whether grading state is saved to SQLite.
     */
    public static final String DATABASE_ENABLED = "database.enabled";

    /**
     * Location of the SQLite file.
     */
    public static final String DATABASE_FILE = "database.file";

    /**
     * How many backups are kept before the oldest is removed.
     */
    public static final String BACKUP_KEEP_COUNT = "database.backup.keep.count";

    /**
     * Whether instructor actions are written to the audit log.
     */
    public static final String AUDIT_ENABLED = "audit.enabled";

    /**
     * Theme of the window: light or dark.
     */
    public static final String UI_THEME = "ui.theme";

    /**
     * Starting window width.
     */
    public static final String WINDOW_WIDTH = "ui.window.width";

    /**
     * Starting window height.
     */
    public static final String WINDOW_HEIGHT = "ui.window.height";

    /**
     * Whether subtle animations are shown.
     */
    public static final String ANIMATIONS_ENABLED = "ui.animations.enabled";

    /**
     * Start of every per-question key, followed by the question id.
     */
    public static final String QUESTION_PREFIX = "assessment.question.";

    /**
     * End of the key naming the folder of a question.
     */
    public static final String QUESTION_FOLDER_SUFFIX = ".folder";

    /**
     * End of the key naming the source file of a question.
     */
    public static final String QUESTION_FILE_SUFFIX = ".file";

    /**
     * Not used: the class only holds constants.
     */
    private ConfigKeys() {
        // utility class, never instantiated
    }
}
