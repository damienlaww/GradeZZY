/*
 * File: FileInspection.java
 *
 * Purpose: Checks the student zip files and folders, or the score sheet,
 * tester or starter folder, just chosen on a background thread, so the
 * window does not freeze while hundreds of zips are opened.
 *
 * Where it fits: SetUpScreen starts one in live mode when files are
 * chosen, dropped or filled from Settings, and adds the checked files
 * when it succeeds.
 *
 * Note on untaught Java: javafx.concurrent.Task runs the check on a
 * background thread, as SimilarityCheck does for the similar code check.
 */
package gradezzy.ui.flow;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javafx.concurrent.Task;

import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.ImportItem;
import gradezzy.ui.backend.InputKind;

/**
 * A background task that checks chosen submission files.
 */
public class FileInspection extends Task<List<ImportItem>> {

    // The backend that checks each file
    private final DashboardBackend backend;

    // What the files are: student zips, the score sheet, testers or starter
    private final InputKind kind;

    // The files and folders to check
    private final List<Path> paths;

    /**
     * Creates the check of some chosen student files.
     *
     * @param backend the backend that checks each file
     * @param paths the files and folders to check
     */
    public FileInspection(DashboardBackend backend, List<Path> paths) {
        this(backend, InputKind.SUBMISSION, paths);
    }

    /**
     * Creates the check of some chosen files of one kind.
     *
     * @param backend the backend that checks each file
     * @param kind what the files are
     * @param paths the files and folders to check
     */
    public FileInspection(DashboardBackend backend, InputKind kind,
            List<Path> paths) {
        this.backend = backend;
        this.kind = kind;
        this.paths = new ArrayList<>(paths);
    }

    /**
     * Returns what the files are.
     *
     * @return the kind, such as the score sheet or student zips
     */
    public InputKind getKind() {
        return kind;
    }

    /**
     * Returns how many files and folders are checked.
     *
     * @return the number of paths
     */
    public int getPathCount() {
        return paths.size();
    }

    /**
     * Checks each path. JavaFX calls this on a background thread.
     *
     * @return the checked files, in the order they were chosen
     */
    @Override
    protected List<ImportItem> call() {
        List<ImportItem> items = new ArrayList<>();
        for (Path path : paths) {
            items.add(backend.inspect(kind, path));
        }
        return items;
    }
}
