/*
 * File: ClassFileMatchCheck.java
 *
 * Purpose: Checks on a background thread whether the chosen score
 * sheet, tester folder and starter folder belong with the uploads, and
 * finds a better file nearby for each one that does not.
 *
 * Where it fits: SetUpScreen starts one when the uploads or a class file
 * change, and shows what it found on the class file rows: a "Check" pill,
 * the problem, and a box offering the better file. It never stops
 * grading.
 *
 * Note on untaught Java: javafx.concurrent.Task runs the check on a
 * background thread, as TesterCheck does for the tester folder.
 */
package gradezzy.ui.flow;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javafx.concurrent.Task;

import gradezzy.service.ClassFileMatch;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.InputKind;

/**
 * A background task that checks the three class files against the
 * uploads.
 *
 * <p>Tier: SHOULD.
 */
public class ClassFileMatchCheck extends Task<List<ClassFileMatch>> {

    // The backend that checks the files
    private final DashboardBackend backend;

    // The usable uploads chosen on Set up
    private final List<ImportItem> submissions = new ArrayList<>();

    // The chosen score sheet, tester folder and starter folder, in that
    // order; null when one is not chosen
    private final Path[] classFiles;

    // Names the uploads and class files, so Set up can tell which check
    // this is
    private final String files;

    /**
     * Creates the check of the class files against the uploads.
     *
     * @param backend the backend that checks the files
     * @param submissions the usable uploads
     * @param classFiles the score sheet, tester folder and starter folder,
     *        each null when not chosen
     * @param files names the uploads and class files
     */
    public ClassFileMatchCheck(DashboardBackend backend,
            List<ImportItem> submissions, Path[] classFiles, String files) {
        this.backend = backend;
        this.submissions.addAll(submissions);
        this.classFiles = classFiles;
        this.files = files;
    }

    /**
     * Returns the names of the uploads and class files checked.
     *
     * @return the names
     */
    public String getFiles() {
        return files;
    }

    /**
     * Checks the score sheet, tester folder and starter folder in turn.
     * The demo calls this directly, as it reads no files.
     *
     * @return one result per class file, in the order given
     */
    public List<ClassFileMatch> checkAll() {
        InputKind[] kinds = {InputKind.ROSTER, InputKind.TESTERS,
            InputKind.STARTER};
        List<ClassFileMatch> matches = new ArrayList<>();
        for (int index = 0; index < kinds.length; index++) {
            if (classFiles[index] == null) {
                matches.add(ClassFileMatch.fits());
            } else {
                matches.add(backend.checkClassFileMatch(kinds[index],
                        classFiles[index], submissions));
            }
        }
        return matches;
    }

    /**
     * Checks the files. JavaFX calls this on a background thread.
     *
     * @return one result per class file
     */
    @Override
    protected List<ClassFileMatch> call() {
        return checkAll();
    }
}
