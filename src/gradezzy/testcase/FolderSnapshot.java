/*
 * File: FolderSnapshot.java
 *
 * Purpose: Remembers the files of a question folder and puts them back if
 * anything changed them.
 *
 * Where it fits: CompileStage saves a snapshot once a question compiles,
 * and ExecuteStage restores it right before the tester runs. Off macOS
 * nothing stops the code of one question from writing into the folder of
 * the next one. The bytes are kept in GradeZZY's memory, which student
 * code cannot reach.
 */
package gradezzy.testcase;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gradezzy.util.FileUtils;

/**
 * Remembers the compiled classes and data files of a question folder.
 * Only these can change what a tester does, and they are small, so a
 * large stray file in a submission costs no memory.
 *
 * <p>Tier: SHOULD. Owner: Zach.
 */
public class FolderSnapshot {

    // Extension of a compiled class file
    private static final String CLASS_EXTENSION = ".class";

    // The folder this snapshot belongs to
    private final Path folder;

    // The names of the tester data files
    private final List<String> dataFileNames;

    // The bytes of each remembered file, by file name
    private final Map<String, byte[]> savedFiles = new HashMap<>();

    // The names of the remembered files, kept beside the map
    private final List<String> savedNames = new ArrayList<>();

    /**
     * Reads the compiled classes and data files of a folder into memory.
     *
     * @param folder the folder
     * @param dataFileNames the names of the tester data files
     * @throws IOException if a file cannot be read
     */
    public FolderSnapshot(Path folder, List<String> dataFileNames)
            throws IOException {
        this.folder = folder;
        this.dataFileNames = dataFileNames;

        for (Path file : listWatchedFiles()) {
            String name = file.getFileName().toString();
            byte[] bytes = Files.readAllBytes(file);
            savedNames.add(name);
            savedFiles.put(name, bytes);
        }
    }

    /**
     * Makes the folder match this snapshot again: a changed or missing
     * file is written back, and a file that was added is deleted.
     *
     * @return the names of the files that had to be put right, sorted
     * @throws IOException if a file cannot be read or written
     */
    public List<String> restore() throws IOException {
        List<String> changed = new ArrayList<>();
        Files.createDirectories(folder);

        for (Path file : listWatchedFiles()) {
            String name = file.getFileName().toString();
            if (!savedFiles.containsKey(name)) {
                Files.delete(file);
                changed.add(name);
            }
        }

        for (String name : savedNames) {
            Path file = folder.resolve(name);
            byte[] saved = savedFiles.get(name);
            if (!matches(file, saved)) {
                // Also removes a folder or link left in the file's place
                FileUtils.deleteDirectory(file);
                Files.write(file, saved);
                changed.add(name);
            }
        }

        Collections.sort(changed);
        return changed;
    }

    /**
     * Checks whether a path is a plain file with exactly these bytes.
     *
     * @param file the path
     * @param saved the bytes it should hold
     * @return true if it matches
     * @throws IOException if the file cannot be read
     */
    private boolean matches(Path file, byte[] saved) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        return Arrays.equals(saved, Files.readAllBytes(file));
    }

    /**
     * Lists the compiled classes and data files directly in the folder.
     * Testers have no package, so nothing deeper can be loaded by them.
     *
     * @return the files
     * @throws IOException if the folder cannot be read
     */
    private List<Path> listWatchedFiles() throws IOException {
        List<Path> watched = new ArrayList<>();
        DirectoryStream<Path> children = null;

        try {
            children = Files.newDirectoryStream(folder);
            for (Path child : children) {
                String name = child.getFileName().toString();
                boolean isWatched = name.endsWith(CLASS_EXTENSION)
                        || dataFileNames.contains(name);
                boolean keep = isWatched && !Files.isDirectory(child,
                        LinkOption.NOFOLLOW_LINKS);
                if (keep) {
                    watched.add(child);
                }
            }
        } finally {
            FileUtils.closeIfOpen(children);
        }
        return watched;
    }
}
