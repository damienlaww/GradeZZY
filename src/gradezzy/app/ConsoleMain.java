/*
 * File: ConsoleMain.java
 *
 * Purpose: Runs a whole grading batch from the terminal, with no window.
 *
 * Where it fits: The console entry point. It uses no JavaFX at all, so
 * the grading core (ingest, validation, fix, exec, grading, report) can
 * be compiled, run and tested before any screen exists. Run it with
 * "run.sh console" or "run.bat console". With the similar code check on,
 * the similar pairs are printed after the summary.
 */
package gradezzy.app;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigException;
import gradezzy.config.ConfigKeys;
import gradezzy.config.ExportFolders;
import gradezzy.grading.GradingException;
import gradezzy.ingest.IngestException;
import gradezzy.ingest.RosterReader;
import gradezzy.ingest.SubmissionLoader;
import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.plagiarism.SimilarityPair;
import gradezzy.testcase.TestCaseSource;
import gradezzy.service.AssessmentFactory;
import gradezzy.testcase.UploadedTesterSource;
import gradezzy.util.TextUtils;

/**
 * Console entry point: grades the submissions folder named in
 * config.properties and prints a summary.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class ConsoleMain {

    // Exit code used when the batch could not be run
    private static final int EXIT_FAILURE = 1;

    // Number of arguments when both the module and the section are given
    private static final int MODULE_AND_SECTION = 2;

    // Turns a similarity from 0 to 1 into a percentage
    private static final int PERCENT = 100;

    // Separates the names in the list of skipped files
    private static final String SKIPPED_SEPARATOR = ", ";

    // Separates the codes and the marks in a printed list
    private static final String LIST_SEPARATOR = ", ";

    // Position of the module when both the module and the section are
    // given
    private static final int MODULE_ARGUMENT = 0;

    // Position of the section when both the module and the section are
    // given
    private static final int SECTION_ARGUMENT = 1;

    // Indent of each line under a heading in the summary
    private static final String LINE_INDENT = "  ";

    // Printed after a similarity shown as a percentage
    private static final String PERCENT_SIGN = "%";

    // The oldest JDK GradeZZY runs on: the testers are started with
    // options that older versions reject
    private static final int MIN_JDK_VERSION = 25;

    /**
     * Not used: this class only holds the main method.
     */
    private ConsoleMain() {
        // never instantiated
    }

    /**
     * Grades one batch. With one argument it is the section code (for
     * example G2); with two they are the module and the section (for
     * example CS102 G2). Any case is accepted. Each must be listed in
     * app.module.codes or app.section.codes; a value that is not given
     * is the first one in config.properties. On a JDK older than 25 it
     * says so and ends at once.
     *
     * @param args optional: the section, or the module and the section
     */
    public static void main(String[] args) {
        int jdkVersion = Runtime.version().feature();
        if (jdkVersion < MIN_JDK_VERSION) {
            System.err.println("GradeZZY needs JDK " + MIN_JDK_VERSION
                    + " or newer; this is JDK " + jdkVersion + ".");
            System.exit(EXIT_FAILURE);
        }

        try {
            AppConfig config = AppConfig.loadDefault();
            ApplicationContext context = new ApplicationContext(config);
            GradingRun gradingRun = gradeBatch(config, context, args);

            printSummary(gradingRun);
            printSimilarPairs(config, context, gradingRun);
            context.close();
        } catch (IOException exception) {
            fail("The configuration file could not be read: "
                    + exception.getMessage());
        } catch (ConfigException exception) {
            fail(exception.getMessage());
        } catch (GradingException exception) {
            fail(exception.getMessage());
        } catch (IllegalArgumentException exception) {
            // For example a path setting holding a character that is not
            // allowed in a path
            fail("A setting is not valid: " + exception.getMessage());
        }
    }

    // Starts grading with the folders named in the configuration.
    private static GradingRun gradeBatch(AppConfig config,
            ApplicationContext context, String[] args)
            throws GradingException {
        if (args.length > MODULE_AND_SECTION) {
            throw new ConfigException("Too many arguments. Use: run.sh "
                    + "console [module] [section], for example run.sh "
                    + "console CS102 G2.");
        }

        String requestedModule = "";
        String requestedSection = "";

        if (args.length == MODULE_AND_SECTION) {
            requestedModule = args[MODULE_ARGUMENT];
            requestedSection = args[SECTION_ARGUMENT];
        } else if (args.length == 1) {
            requestedSection = args[0];
        }

        String moduleCode = chooseCode(config, ConfigKeys.MODULE_CODES,
                "module", requestedModule);
        String sectionCode = chooseCode(config, ConfigKeys.SECTION_CODES,
                "section", requestedSection);
        Section section = new Section(moduleCode, sectionCode);

        List<Path> inputPaths = new ArrayList<>();
        inputPaths.add(Paths.get(
                config.getString(ConfigKeys.SUBMISSIONS_DIR)));

        Path testersDirectory = Paths.get(config.getString(
                ConfigKeys.TESTERS_DIR));
        TestCaseSource testCaseSource = new UploadedTesterSource(
                testersDirectory, config);

        System.out.println("GradeZZY console, section "
                + section.getLabel());
        printMarks(config, section, testersDirectory);
        printSkipped(config, inputPaths);

        return context.getServices().getGradingService().startGrading(
                inputPaths, section, testCaseSource,
                new ConsoleProgressListener());
    }

    // Names the files and folders in the submissions folder that are not
    // graded, as the window does, such as a .rar file or a marking note.
    // Hidden files such as .DS_Store are never named.
    private static void printSkipped(AppConfig config,
            List<Path> inputPaths) {
        SubmissionLoader loader = new SubmissionLoader(config);
        List<String> names = new ArrayList<>();
        for (Path input : inputPaths) {
            try {
                for (Path skipped : loader.listSkipped(input)) {
                    names.add(String.valueOf(skipped.getFileName()));
                }
            } catch (IngestException exception) {
                names.add(input + " (could not be read)");
            }
        }

        if (!names.isEmpty()) {
            System.out.println("Skipped, because only ZIP files and "
                    + "submission folders are graded: "
                    + TextUtils.join(SKIPPED_SEPARATOR, names) + ".");
        }
    }

    // Returns the typed module or section, or the first configured one
    // when none was typed. A typed code must be listed in the setting;
    // any case is accepted and the configured spelling is returned.
    private static String chooseCode(AppConfig config, String key,
            String kind, String requested) {
        List<String> codes = config.getList(key);

        if (codes.isEmpty()) {
            throw new ConfigException("Setting '" + key + "' lists no "
                    + kind + ".");
        }

        String typed = requested.trim();

        if (typed.isEmpty()) {
            return codes.get(0);
        }

        for (String code : codes) {
            if (code.equalsIgnoreCase(typed)) {
                return code;
            }
        }

        throw new ConfigException("Unknown " + kind + " '" + typed
                + "'. Choose one of: " + TextUtils.join(LIST_SEPARATOR, codes)
                + " (setting '" + key + "').");
    }

    // Prints each question's maximum mark as read from the testers, and
    // warns when the total does not match the score sheet's denominator.
    // The console cannot ask, so it shows what the window would confirm.
    private static void printMarks(AppConfig config, Section section,
            Path testersDirectory) {
        AssessmentFactory factory = new AssessmentFactory(config);
        Assessment assessment = factory.create(section.getModuleCode(),
                testersDirectory);

        List<String> marks = new ArrayList<>();
        for (Question question : assessment.getQuestions()) {
            marks.add(question.getId() + " " + question.getMaxScore());
        }
        System.out.println("Maximum marks from the testers: "
                + TextUtils.join(LIST_SEPARATOR, marks) + "; total "
                + assessment.getMaxTotal() + ".");

        printDenominatorWarning(config, section, assessment);

        for (String key : factory.findIgnoredMaxScoreSettings()) {
            System.out.println("Note: " + key + " in config.properties is "
                    + "ignored. Each maximum now comes from the tester.");
        }
    }

    // Warns when the score sheet's denominator does not match the total
    // of the questions, or when the sheet gives no single denominator.
    private static void printDenominatorWarning(AppConfig config,
            Section section, Assessment assessment) {
        // The sheet startGrading reads: the section's own, else the one
        // for everyone
        Path scoreSheet = ExportFolders.findScoreSheet(config,
                section.getModuleCode(), section.getSectionCode());
        double denominator = new RosterReader(config).readDenominator(
                scoreSheet);
        if (denominator < 0) {
            System.out.println("Warning: the score sheet has no single "
                    + "denominator to check this total against.");
        } else if (denominator != assessment.getMaxTotal()) {
            System.out.println("Warning: the score sheet's denominator is "
                    + denominator + ", but the questions add up to "
                    + assessment.getMaxTotal() + ".");
        }
    }

    // Prints one line per submission and the totals.
    private static void printSummary(GradingRun gradingRun) {
        System.out.println();
        System.out.println("Summary for "
                + gradingRun.getAssessment().getName() + ":");

        for (Submission submission : gradingRun.getSubmissions()) {
            System.out.println(LINE_INDENT + submission.getUsername() + ": "
                    + submission.getTotalScore() + " ("
                    + submission.getStatus() + ")");
        }

        System.out.println(gradingRun.getSubmissions().size()
                + " submission(s) processed.");
    }

    // Prints the similar pairs that grading flagged, when the similar
    // code check is switched on. The flags are added once the whole batch
    // is graded, so they are not in the lines printed per student.
    private static void printSimilarPairs(AppConfig config,
            ApplicationContext context, GradingRun gradingRun) {
        if (!config.getBoolean(ConfigKeys.PLAGIARISM_ENABLED)) {
            return;
        }

        List<SimilarityPair> pairs = context.getServices()
                .getPlagiarismService().listPairs(gradingRun);
        if (pairs.isEmpty()) {
            System.out.println("Similar code: no pair found.");
            return;
        }

        System.out.println("Similar code (a review aid, not a finding):");
        for (SimilarityPair pair : pairs) {
            long percent = Math.round(pair.getSimilarity() * PERCENT);
            System.out.println(LINE_INDENT + pair.getQuestionId() + ": "
                    + pair.getFirstUsername() + " and "
                    + pair.getSecondUsername() + ", " + percent
                    + PERCENT_SIGN);
        }
    }

    // Prints an error for the instructor and ends with a failure code.
    private static void fail(String message) {
        System.err.println("GradeZZY could not run: " + message);
        System.exit(EXIT_FAILURE);
    }
}
