/*
 * File: ImportBatch.java
 *
 * Purpose: Holds the files chosen for the next grading run and decides
 * whether grading may start.
 *
 * Where it fits: Kept in UiState so the choices survive navigation. The
 * Submissions screen fills it and DashboardBackend.startGrading reads it.
 * It only checks what was chosen; it never opens a file.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.backend;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import gradezzy.testcase.TestCase;

/**
 * The files chosen for the next grading run.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class ImportBatch {

    // Every chosen item, in the order it was added
    private final List<ImportItem> items = new ArrayList<>();

    // Test cases typed on the Assessments screen
    private final List<TestCase> typedTestCases = new ArrayList<>();

    // Whether grading uses the typed test cases instead of a folder
    private boolean useTypedTestCases = false;

    /**
     * Creates an empty batch.
     */
    public ImportBatch() {
        // nothing to set up
    }

    /**
     * Returns the test cases typed on the Assessments screen.
     *
     * @return the typed test cases, which may be changed
     */
    public List<TestCase> getTypedTestCases() {
        return typedTestCases;
    }

    /**
     * Says whether grading uses the typed test cases.
     *
     * @return true for typed test cases, false for a tester folder
     */
    public boolean isUsingTypedTestCases() {
        return useTypedTestCases;
    }

    /**
     * Chooses between typed test cases and a tester folder.
     *
     * @param useTypedTestCases true for typed test cases
     */
    public void setUsingTypedTestCases(boolean useTypedTestCases) {
        this.useTypedTestCases = useTypedTestCases;
    }

    /**
     * Adds a chosen file. A roster, tester folder or starter folder
     * replaces the one chosen before, because a run uses only one.
     *
     * @param item the chosen file
     */
    public void add(ImportItem item) {
        if (item.getKind() != InputKind.SUBMISSION) {
            clear(item.getKind());
        }
        items.add(item);
    }

    /**
     * Removes a chosen file.
     *
     * @param item the file to remove
     */
    public void remove(ImportItem item) {
        items.remove(item);
    }

    /**
     * Puts a new file in the place of an old one.
     *
     * @param oldItem the file to replace
     * @param newItem the file that replaces it
     */
    public void replace(ImportItem oldItem, ImportItem newItem) {
        int index = items.indexOf(oldItem);
        if (index < 0) {
            add(newItem);
            return;
        }
        items.set(index, newItem);
    }

    /**
     * Removes every file of one group.
     *
     * @param kind the group to empty
     */
    public void clear(InputKind kind) {
        List<ImportItem> kept = new ArrayList<>();
        for (ImportItem item : items) {
            if (item.getKind() != kind) {
                kept.add(item);
            }
        }
        items.clear();
        items.addAll(kept);
    }

    /**
     * Removes every chosen file.
     */
    public void clearAll() {
        items.clear();
    }

    /**
     * Returns the files of one group.
     *
     * @param kind the group
     * @return the files, in the order they were added
     */
    public List<ImportItem> getItems(InputKind kind) {
        List<ImportItem> matches = new ArrayList<>();
        for (ImportItem item : items) {
            if (item.getKind() == kind) {
                matches.add(item);
            }
        }
        return matches;
    }

    /**
     * Returns the state to show for a file. A later submission for a
     * student who already has a usable one is a duplicate.
     *
     * @param item a file of this batch
     * @return the state to show
     */
    public ImportState getEffectiveState(ImportItem item) {
        if (item.getKind() != InputKind.SUBMISSION
                || !item.getState().isUsable()) {
            return item.getState();
        }

        for (ImportItem other : items) {
            if (other == item) {
                return item.getState();
            }
            if (other.getKind() == InputKind.SUBMISSION
                    && other.getState().isUsable()
                    && other.getUsername().equalsIgnoreCase(item.getUsername())) {
                return ImportState.DUPLICATE;
            }
        }
        return item.getState();
    }

    /**
     * Counts the files of one group in one state.
     *
     * @param kind the group
     * @param state the state to count
     * @return the number of files
     */
    public int count(InputKind kind, ImportState state) {
        int total = 0;
        for (ImportItem item : getItems(kind)) {
            if (getEffectiveState(item) == state) {
                total++;
            }
        }
        return total;
    }

    /**
     * Returns the submission files that will be graded.
     *
     * @return the usable uploads, including duplicates for per-file reporting
     */
    public List<Path> getSubmissionPaths() {
        List<Path> paths = new ArrayList<>();
        for (ImportItem item : getItems(InputKind.SUBMISSION)) {
            if (item.getState().isUsable()) {
                paths.add(item.getPath());
            }
        }
        return paths;
    }

    /**
     * Returns the chosen file of a single-file group.
     *
     * @param kind the roster, tester or starter group
     * @return the chosen path, or null if none is chosen
     */
    public Path getSinglePath(InputKind kind) {
        List<ImportItem> chosen = getItems(kind);
        if (chosen.isEmpty()) {
            return null;
        }
        return chosen.get(0).getPath();
    }

    /**
     * Returns every reason grading cannot start yet. An empty list means
     * the batch is ready.
     *
     * @return plain sentences, one per problem
     */
    public List<String> getBlockingReasons() {
        List<String> reasons = new ArrayList<>();
        List<ImportItem> submissions = getItems(InputKind.SUBMISSION);

        if (submissions.isEmpty()) {
            reasons.add("Add at least one student ZIP file.");
        }

        int invalid = count(InputKind.SUBMISSION, ImportState.INVALID);
        if (invalid > 0) {
            reasons.add(invalid + " ZIP file" + plural(invalid)
                    + " cannot be used. Replace or remove "
                    + pronoun(invalid) + ".");
        }

        addSingleReason(reasons, InputKind.ROSTER);

        if (!useTypedTestCases) {
            addSingleReason(reasons, InputKind.TESTERS);
        } else if (typedTestCases.isEmpty()) {
            reasons.add("Type at least one test case on the Assessments "
                    + "screen, or use a tester folder instead.");
        }
        return reasons;
    }

    /**
     * Says whether grading may start.
     *
     * @return true when nothing blocks the batch
     */
    public boolean isReady() {
        return getBlockingReasons().isEmpty();
    }

    // Adds the reason a required single-file group blocks grading.
    private void addSingleReason(List<String> reasons, InputKind kind) {
        List<ImportItem> chosen = getItems(kind);
        if (chosen.isEmpty()) {
            reasons.add("Add the " + kind.getTitle().toLowerCase() + ".");
            return;
        }
        if (!chosen.get(0).getState().isUsable()) {
            reasons.add("The " + kind.getTitle().toLowerCase()
                    + " cannot be used: " + chosen.get(0).getMessage());
        }
    }

    // Returns "s" for any count other than one.
    private String plural(int count) {
        if (count == 1) {
            return "";
        }
        return "s";
    }

    // Returns "it" or "them" for a count.
    private String pronoun(int count) {
        if (count == 1) {
            return "it";
        }
        return "them";
    }
}
