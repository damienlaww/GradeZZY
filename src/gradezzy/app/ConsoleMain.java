/*
 * File: ConsoleMain.java
 *
 * Purpose: Runs a whole grading batch from the terminal, with no window.
 *
 * Where it fits: The console entry point. It uses no JavaFX at all, so
 * the grading core (ingest, validation, fix, exec, grading, report) can
 * be compiled, run and tested before any screen exists. Run it with
 * "run.sh console" or "run.bat console".
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
import gradezzy.grading.GradingException;
import gradezzy.ingest.RosterReader;
import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.Section;
import gradezzy.model.Submission;
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
     * is the first one in config.properties.
     *
     * @param args optional: the section, or the module and the section
     */
    public static void main(String[] args) {
        try {
            AppConfig config = AppConfig.loadDefault();
            ApplicationContext context = new ApplicationContext(config);
            GradingRun gradingRun = gradeBatch(config, context, args);

            printSummary(gradingRun);
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
            requestedModule = args[0];
            requestedSection = args[1];
        } else if (args.length == 1) {
            requestedSection = args[0];
        }

        Section section = new Section(
                chooseCode(config, ConfigKeys.MODULE_CODES, "module",
                        requestedModule),
                chooseCode(config, ConfigKeys.SECTION_CODES, "section",
                        requestedSection));

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

        return context.getServices().getGradingService().startGrading(
                inputPaths, section, testCaseSource,
                new ConsoleProgressListener());
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

        double denominator = new RosterReader(config).readDenominator(
                Paths.get(config.getString(ConfigKeys.SCORE_SHEET_FILE)));
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

    // Prints an error for the instructor and ends with a failure code.
    private static void fail(String message) {
        System.err.println("GradeZZY could not run: " + message);
        System.exit(EXIT_FAILURE);
    }
}
