/*
 * File: FolderMoveRecord.java
 *
 * Purpose: A fix record for question folders moved up out of an extra
 * folder in one fix, or for loose files moved into new question folders.
 *
 * Where it fits: FolderHierarchyFixer makes one, so the move shows as one
 * fix however many question folders or files it moved, and can be undone.
 */
package gradezzy.fix;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import gradezzy.model.Anomaly;

/**
 * A fix record that also keeps each question folder or file that was
 * moved, and each question folder the fix made.
 */
public class FolderMoveRecord extends FixRecord {

    // Where each moved folder or file was
    private final List<Path> pathsBefore;

    // Where each moved folder or file is now, in the same order
    private final List<Path> pathsAfter;

    // The question folders the fix made, empty when it made none
    private final List<Path> newFolders;

    /**
     * Creates a new folder move record.
     *
     * @param anomaly the anomaly that was fixed
     * @param fixerName the name of the fixer that did it
     * @param description what was changed, in plain words
     * @param workingCopy the working copy the fix changed
     * @param extraFolder the extra folder the folders were in
     * @param contentRoot the folder they were moved up into
     * @param valueBefore what the student had
     * @param valueAfter what the working copy holds after the fix
     * @param pathsBefore where each moved folder was
     * @param pathsAfter where each moved folder is now, in the same order
     */
    public FolderMoveRecord(Anomaly anomaly, String fixerName,
            String description, Path workingCopy, Path extraFolder,
            Path contentRoot, String valueBefore, String valueAfter,
            List<Path> pathsBefore, List<Path> pathsAfter) {
        super(anomaly, fixerName, description, workingCopy, extraFolder,
                contentRoot, null, valueBefore, valueAfter);
        this.pathsBefore = new ArrayList<>(pathsBefore);
        this.pathsAfter = new ArrayList<>(pathsAfter);
        this.newFolders = new ArrayList<>();
    }

    /**
     * Creates a new record for loose files moved into question folders
     * that the fix made.
     *
     * @param anomaly the anomaly that was fixed
     * @param fixerName the name of the fixer that did it
     * @param description what was changed, in plain words
     * @param workingCopy the working copy the fix changed
     * @param contentRoot the folder that held the loose files
     * @param valueBefore what the student had
     * @param valueAfter what the working copy holds after the fix
     * @param pathsBefore where each moved file was
     * @param pathsAfter where each moved file is now, in the same order
     * @param newFolders the question folders the fix made
     */
    public FolderMoveRecord(Anomaly anomaly, String fixerName,
            String description, Path workingCopy, Path contentRoot,
            String valueBefore, String valueAfter, List<Path> pathsBefore,
            List<Path> pathsAfter, List<Path> newFolders) {
        super(anomaly, fixerName, description, workingCopy, contentRoot,
                contentRoot, null, valueBefore, valueAfter);
        this.pathsBefore = new ArrayList<>(pathsBefore);
        this.pathsAfter = new ArrayList<>(pathsAfter);
        this.newFolders = new ArrayList<>(newFolders);
    }

    /**
     * Returns where each moved folder or file was.
     *
     * @return the paths before the move
     */
    public List<Path> getPathsBefore() {
        return new ArrayList<>(pathsBefore);
    }

    /**
     * Returns where each moved folder or file is now, in the same order.
     *
     * @return the paths after the move
     */
    public List<Path> getPathsAfter() {
        return new ArrayList<>(pathsAfter);
    }

    /**
     * Returns the question folders the fix made for loose files.
     *
     * @return the new folders, empty when the fix made none
     */
    public List<Path> getNewFolders() {
        return new ArrayList<>(newFolders);
    }

    /**
     * Gives the path a file or folder had before the move. Only a moved
     * file, or what is inside a moved folder, had another path.
     *
     * @param pathAfter a path in the working copy after the move
     * @return the same file or folder's path before the move
     */
    @Override
    public Path findPathBefore(Path pathAfter) {
        for (int i = 0; i < pathsAfter.size(); i++) {
            Path movedFolder = pathsAfter.get(i);
            if (pathAfter.startsWith(movedFolder)) {
                return pathsBefore.get(i).resolve(
                        movedFolder.relativize(pathAfter));
            }
        }
        return pathAfter;
    }
}
