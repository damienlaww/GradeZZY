/*
 * File: TesterStrengthService.java
 *
 * Purpose: Checks how strong each tester is before grading, with broken
 * copies of the model answer, and writes the report.
 *
 * Where it fits: The console calls it before grading when
 * tester.strength.enabled is true, and alone for "run.sh console
 * --testers". The work is done by modelanswer.TesterStrengthChecker.
 */
package gradezzy.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.config.ExportFolders;
import gradezzy.exec.ProcessRunner;
import gradezzy.model.Assessment;
import gradezzy.modelanswer.ModelAnswerCopy;
import gradezzy.modelanswer.Mutant;
import gradezzy.modelanswer.QuestionStrength;
import gradezzy.modelanswer.TesterStrengthChecker;
import gradezzy.util.FileUtils;
import gradezzy.util.TextUtils;

/**
 * Checks how strong each tester is and writes the report.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public class TesterStrengthService {

    /**
     * The name of the report file, saved in the section's export folder.
     */
    public static final String REPORT_FILE_NAME = "tester-strength.txt";

    // Ends each line of the report
    private static final String LINE_END = "\n";

    // Put in front of each broken copy that got full marks
    private static final String COPY_INDENT = "    - ";

    // The settings
    private final AppConfig config;

    /**
     * Creates the service.
     *
     * <p>Tier: COULD.
     *
     * @param config the settings
     */
    public TesterStrengthService(AppConfig config) {
        this.config = config;
    }

    /**
     * Says whether the check can run: paths.model.answers.dir names a
     * folder.
     *
     * <p>Tier: COULD.
     *
     * @return true when there are model answers to break
     */
    public boolean hasModelAnswers() {
        return ModelAnswerCopy.findFolder(config) != null;
    }

    /**
     * Checks the tester of every question of a module's assessment. It
     * compiles and runs the tester once per broken copy, so it takes a
     * while: about a second per copy, shared between exec.thread.count
     * threads.
     *
     * <p>Tier: COULD.
     *
     * @param moduleCode the module, such as CS102
     * @param testersDirectory the folder holding the testers
     * @param starterDirectory the starter folder, or null to use
     *        paths.starter.dir
     * @return one result per question, or an empty list when there are
     *         no model answers
     */
    public List<QuestionStrength> checkTesters(String moduleCode,
            Path testersDirectory, Path starterDirectory) {
        return checkTesters(moduleCode, testersDirectory, starterDirectory,
                null);
    }

    /**
     * Checks the tester of every question as the method above does, with
     * the model answers folder chosen on Set up.
     *
     * <p>Tier: COULD.
     *
     * @param moduleCode the module, such as CS102
     * @param testersDirectory the folder holding the testers
     * @param starterDirectory the starter folder, or null to use
     *        paths.starter.dir
     * @param modelAnswersDirectory the model answers folder, or null to
     *        use paths.model.answers.dir
     * @return one result per question, or an empty list when there are
     *         no model answers
     */
    public List<QuestionStrength> checkTesters(String moduleCode,
            Path testersDirectory, Path starterDirectory,
            Path modelAnswersDirectory) {
        Path modelAnswers = modelAnswersDirectory;
        if (modelAnswers == null) {
            modelAnswers = ModelAnswerCopy.findFolder(config);
        }
        if (modelAnswers == null) {
            return new ArrayList<>();
        }

        Path starter = starterDirectory;
        if (starter == null) {
            starter = Paths.get(config.getString(ConfigKeys.STARTER_DIR));
        }
        Assessment assessment = new AssessmentFactory(config).create(
                moduleCode, testersDirectory);
        TesterStrengthChecker checker = new TesterStrengthChecker(config,
                new ProcessRunner(config));
        return checker.check(assessment, testersDirectory, starter,
                modelAnswers);
    }

    /**
     * Writes the results as lines of text: one summary per question, and
     * under it each broken copy that still got full marks, with the line
     * and the change, so the instructor can see what the tester misses.
     *
     * <p>Tier: COULD.
     *
     * @param results the results of checkTesters
     * @return the lines, without line endings
     */
    public List<String> describe(List<QuestionStrength> results) {
        List<String> lines = new ArrayList<>();
        for (QuestionStrength result : results) {
            lines.add(result.summarise());
            for (Mutant survivor : result.getSurvivors()) {
                lines.add(COPY_INDENT + survivor.describe());
            }
        }
        return lines;
    }

    /**
     * Saves the report in the section's export folder, replacing an
     * earlier one.
     *
     * <p>Tier: COULD.
     *
     * @param results the results of checkTesters
     * @param moduleCode the module, such as CS102
     * @param sectionCode the section, such as G2
     * @return the file written
     * @throws IOException if the file cannot be written
     */
    public Path writeReport(List<QuestionStrength> results,
            String moduleCode, String sectionCode) throws IOException {
        // The per-section folders are a newer setting that may be missing
        String bySection = null;
        if (config.hasKey(ConfigKeys.OUTPUT_DIR_BY_SECTION)) {
            bySection = config.getString(ConfigKeys.OUTPUT_DIR_BY_SECTION);
        }
        String folderText = ExportFolders.resolve(
                config.getString(ConfigKeys.OUTPUT_DIR), bySection,
                moduleCode, sectionCode);
        Path folder = Paths.get(folderText);
        Files.createDirectories(folder);

        List<String> lines = new ArrayList<>();
        lines.add("How strong each tester is: GradeZZY broke the model "
                + "answer in small ways and ran the tester on each broken "
                + "copy. A copy that still gets full marks shows something "
                + "the tester never checks. Some changes do not change "
                + "what the code does, so a few full marks can be "
                + "harmless.");
        lines.add("");
        lines.addAll(describe(results));

        Path file = folder.resolve(REPORT_FILE_NAME);
        String text = TextUtils.join(LINE_END, lines) + LINE_END;
        FileUtils.writeText(file, text);
        return file;
    }
}
