/*
 * File: ConsoleMain.java
 *
 * Purpose: Runs a whole grading batch from the terminal, with no window.
 *
 * Where it fits: The console entry point. It uses no JavaFX at all, so
 * the grading core (ingest, validation, fix, exec, grading, report) can
 * be compiled, run and tested before any screen exists. Run it with
 * "run.sh console" or "run.bat console". With the similar code check on,
 * the similar pairs are printed after the summary. When a model answers
 * folder is set, each tester is first checked with broken copies of the
 * model answer; "run.sh console --testers" runs only that check.
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
import gradezzy.modelanswer.QuestionStrength;
import gradezzy.plagiarism.SimilarityPair;
import gradezzy.testcase.TestCaseSource;
import gradezzy.service.AssessmentFactory;
import gradezzy.service.TesterStrengthService;
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

    // The first argument that runs only the tester strength check, and
    // how many arguments it takes up
    private static final String TESTERS_OPTION = "--testers";
    private static final int OPTION_COUNT = 1;

    // Put in front of each line of the tester strength results
    private static final String RESULT_INDENT = "  ";

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
     * is the first one in config.properties. With --testers first, only
     * the testers are checked with broken copies of the model answer,
     * and nothing is graded.
     *
     * @param args optional: --testers, then the section, or the module
     *        and the section
     */
    public static void main(String[] args) {
        try {
            AppConfig config = AppConfig.loadDefault();
            ApplicationContext context = new ApplicationContext(config);
            boolean testersOnly = args.length > 0
                    && args[0].equals(TESTERS_OPTION);
            String[] codes = args;
            if (testersOnly) {
                codes = dropFirst(args);
            }
            Section section = chooseSection(config, codes);
            Path testersDirectory = Paths.get(config.getString(
                    ConfigKeys.TESTERS_DIR));
            System.out.println("GradeZZY console, section "
                    + section.getLabel());

            if (testersOnly) {
                checkTesters(context, section, testersDirectory, true);
                context.close();
                return;
            }
            if (config.getBoolean(ConfigKeys.TESTER_STRENGTH_ENABLED)) {
                checkTesters(context, section, testersDirectory, false);
            }

            GradingRun gradingRun = gradeBatch(config, context, section,
                    testersDirectory);
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

    // Returns the arguments after the option.
    private static String[] dropFirst(String[] args) {
        String[] rest = new String[args.length - OPTION_COUNT];
        for (int index = 0; index < rest.length; index++) {
            int argumentIndex = index + OPTION_COUNT;
            rest[index] = args[argumentIndex];
        }
        return rest;
    }

    // Returns the section the arguments name: the section, or the module
    // and the section, or the first ones in the settings.
    private static Section chooseSection(AppConfig config, String[] args) {
        if (args.length > MODULE_AND_SECTION) {
            throw new ConfigException("Too many arguments. Use: run.sh "
                    + "console [--testers] [module] [section], for example "
                    + "run.sh console CS102 G2.");
        }

        String requestedModule = "";
        String requestedSection = "";

        if (args.length == MODULE_AND_SECTION) {
            requestedModule = args[0];
            requestedSection = args[1];
        } else if (args.length == 1) {
            requestedSection = args[0];
        }

        return new Section(
                chooseCode(config, ConfigKeys.MODULE_CODES, "module",
                        requestedModule),
                chooseCode(config, ConfigKeys.SECTION_CODES, "section",
                        requestedSection));
    }

    // Checks each tester with broken copies of the model answer, prints
    // the results and saves the report. When there are no model answers
    // it says so only if the check was asked for on its own.
    private static void checkTesters(ApplicationContext context,
            Section section, Path testersDirectory, boolean asked) {
        TesterStrengthService service = context.getServices()
                .getTesterStrengthService();
        if (!service.hasModelAnswers()) {
            if (asked) {
                System.out.println("No model answers to check the testers "
                        + "with: set paths.model.answers.dir to a folder "
                        + "laid out like the starter folder.");
            }
            return;
        }

        System.out.println("Checking each tester with broken copies of "
                + "the model answer. This compiles and runs the tester "
                + "once per copy, so it takes a minute or two.");
        List<QuestionStrength> results = service.checkTesters(
                section.getModuleCode(), testersDirectory, null);
        System.out.println("Tester strength:");
        for (String line : service.describe(results)) {
            System.out.println(RESULT_INDENT + line);
        }

        try {
            Path report = service.writeReport(results,
                    section.getModuleCode(), section.getSectionCode());
            System.out.println("Saved to " + report + ".");
        } catch (IOException exception) {
            System.out.println("The report could not be saved: "
                    + exception.getMessage());
        }
        System.out.println();
    }

    // Starts grading with the folders named in the configuration.
    private static GradingRun gradeBatch(AppConfig config,
            ApplicationContext context, Section section,
            Path testersDirectory) throws GradingException {
        List<Path> inputPaths = new ArrayList<>();
        inputPaths.add(Paths.get(
                config.getString(ConfigKeys.SUBMISSIONS_DIR)));

        TestCaseSource testCaseSource = new UploadedTesterSource(
                testersDirectory, config);

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
                + "'. Choose one of: " + TextUtils.join(", ", codes)
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
                + TextUtils.join(", ", marks) + "; total "
                + assessment.getMaxTotal() + ".");

        // The sheet startGrading reads: the section's own, else the one
        // for everyone
        double denominator = new RosterReader(config).readDenominator(
                ExportFolders.findScoreSheet(config, section.getModuleCode(),
                        section.getSectionCode()));
        if (denominator < 0) {
            System.out.println("Warning: the score sheet has no single "
                    + "denominator to check this total against.");
        } else if (denominator != assessment.getMaxTotal()) {
            System.out.println("Warning: the score sheet's denominator is "
                    + denominator + ", but the questions add up to "
                    + assessment.getMaxTotal() + ".");
        }

        for (String key : factory.findIgnoredMaxScoreSettings()) {
            System.out.println("Note: " + key + " in config.properties is "
                    + "ignored. Each maximum now comes from the tester.");
        }
    }

    // Prints one line per submission and the totals.
    private static void printSummary(GradingRun gradingRun) {
        System.out.println();
        System.out.println("Summary for "
                + gradingRun.getAssessment().getName() + ":");

        for (Submission submission : gradingRun.getSubmissions()) {
            System.out.println("  " + submission.getUsername() + ": "
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
            System.out.println("  " + pair.getQuestionId() + ": "
                    + pair.getFirstUsername() + " and "
                    + pair.getSecondUsername() + ", "
                    + Math.round(pair.getSimilarity() * PERCENT) + "%");
        }
    }

    // Prints an error for the instructor and ends with a failure code.
    private static void fail(String message) {
        System.err.println("GradeZZY could not run: " + message);
        System.exit(EXIT_FAILURE);
    }
}
