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
import gradezzy.model.GradingRun;
import gradezzy.model.Section;
import gradezzy.model.Submission;
import gradezzy.testcase.TestCaseSource;
import gradezzy.testcase.UploadedTesterSource;

/**
 * Console entry point: grades the submissions folder named in
 * config.properties and prints a summary.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class ConsoleMain {

    // Exit code used when the batch could not be run
    private static final int EXIT_FAILURE = 1;

    /**
     * Not used: this class only holds the main method.
     */
    private ConsoleMain() {
        // never instantiated
    }

    /**
     * Grades one batch. The section code may be given as the first
     * argument (for example G2); otherwise the first section in
     * config.properties is used.
     *
     * @param args optional: the section code
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
        }
    }

    // Starts grading with the folders named in the configuration.
    private static GradingRun gradeBatch(AppConfig config,
            ApplicationContext context, String[] args)
            throws GradingException {
        Section section = new Section(
                config.getString(ConfigKeys.MODULE_CODE),
                chooseSectionCode(config, args));

        List<Path> inputPaths = new ArrayList<>();
        inputPaths.add(Paths.get(
                config.getString(ConfigKeys.SUBMISSIONS_DIR)));

        TestCaseSource testCaseSource = new UploadedTesterSource(
                Paths.get(config.getString(ConfigKeys.TESTERS_DIR)));

        System.out.println("GradeZZY console, section "
                + section.getLabel());

        return context.getServices().getGradingService().startGrading(
                inputPaths, section, testCaseSource,
                new ConsoleProgressListener());
    }

    // Takes the section from the arguments, or the first configured one.
    private static String chooseSectionCode(AppConfig config,
            String[] args) {
        if (args.length > 0) {
            // TODO(Jing Yuan): implement: reject the argument with a clear
            //     message when InputValidator.isValidSectionCode is false
            return args[0];
        }

        List<String> sectionCodes = config.getList(
                ConfigKeys.SECTION_CODES);

        if (sectionCodes.isEmpty()) {
            throw new ConfigException("Setting '"
                    + ConfigKeys.SECTION_CODES + "' lists no section.");
        }

        return sectionCodes.get(0);
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
