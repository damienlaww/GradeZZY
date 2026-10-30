/*
 * File: SearchService.java
 *
 * Purpose: Searches, filters and sorts the submissions of a run.
 *
 * Where it fits: Used by the results and anomaly screens.
 */
package gradezzy.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.Submission;

/**
 * Searches, filters and sorts the submissions of a run. Every method
 * returns a new list and leaves the run unchanged.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class SearchService {

    // Extension of the source files searched by searchCode
    private static final String SOURCE_EXTENSION = ".java";

    // The application settings, for the tester class suffix
    private final AppConfig config;

    /**
     * Creates a new search service.
     *
     * @param config the application settings
     */
    public SearchService(AppConfig config) {
        this.config = config;
    }

    /**
     * Finds submissions whose source contains a piece of code. Only the
     * .java files in each working copy are searched, and the match is
     * case sensitive. The testers copied into each working copy are
     * skipped, because every submission holds the same ones. A file that
     * cannot be read is skipped.
     *
     * @param gradingRun the grading run
     * @param codeFragment the code fragment
     * @return the matching submissions, or an empty list if the run or
     *         the fragment is missing or empty
     */
    public List<Submission> searchCode(GradingRun gradingRun,
            String codeFragment) {
        List<Submission> matches = new ArrayList<>();

        if (gradingRun == null || codeFragment == null
                || codeFragment.isEmpty()) {
            return matches;
        }

        List<String> testerFiles = listTesterFiles(gradingRun);

        for (Submission submission : gradingRun.getSubmissions()) {
            Path workingCopy = submission.getWorkingCopyPath();

            if (workingCopy != null && containsCode(workingCopy.toFile(),
                    codeFragment, testerFiles)) {
                matches.add(submission);
            }
        }

        return matches;
    }

    // Says whether a .java file in or under a folder holds the fragment.
    private boolean containsCode(File folder, String codeFragment,
            List<String> testerFiles) {
        File[] entries = folder.listFiles();

        if (entries == null) {
            return false;
        }

        for (File entry : entries) {
            // Links are skipped so a link to a parent folder cannot loop.
            boolean isLink = Files.isSymbolicLink(entry.toPath());

            if (!isLink && entry.isDirectory()
                    && containsCode(entry, codeFragment, testerFiles)) {
                return true;
            }

            if (!isLink && isSourceFile(entry)
                    && !testerFiles.contains(entry.getName())
                    && fileContains(entry, codeFragment)) {
                return true;
            }
        }

        return false;
    }

    // Lists the tester source file names, for example Q1aTester.java.
    private List<String> listTesterFiles(GradingRun gradingRun) {
        List<String> names = new ArrayList<>();
        String suffix = config.getString(ConfigKeys.TESTER_CLASS_SUFFIX);

        for (Question question : gradingRun.getAssessment().getQuestions()) {
            names.add(question.getId() + suffix + SOURCE_EXTENSION);
        }

        return names;
    }

    // Says whether a file is a Java source file.
    private boolean isSourceFile(File file) {
        return file.isFile() && file.getName().endsWith(SOURCE_EXTENSION);
    }

    // Says whether a file's text holds the fragment.
    private boolean fileContains(File file, String codeFragment) {
        try {
            String text = Files.readString(file.toPath());
            return text.contains(codeFragment);
        } catch (IOException exception) {
            // An unreadable file, or one that is not UTF-8, cannot
            // match; the search carries on with the other files.
            return false;
        }
    }
}
