/*
 * File: ImportItem.java
 *
 * Purpose: One file or folder chosen before grading, and whether it is usable.
 *
 * Where it fits: DashboardBackend.inspect creates these, ImportBatch holds
 * them and Set up lists them.
 */
package gradezzy.ui.backend;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * One chosen input file or folder.
 */
public class ImportItem {

    // The group this file belongs to
    private final InputKind kind;

    // The chosen file or folder
    private final Path path;

    // The student the file belongs to, or an empty string
    private final String username;

    // Whether the file can be used
    private final ImportState state;

    // Why the file has this state, in plain words
    private final String message;

    // The usernames of the students the file holds: one for a student
    // zip, several for a folder of zips or a zip of student zips
    private final List<String> students = new ArrayList<>();

    /**
     * Creates an import item for one student's file, or for a file that
     * holds no student when the username is empty.
     *
     * @param kind the group of the file
     * @param path the chosen file or folder
     * @param username the student, or an empty string
     * @param state whether the file can be used
     * @param message why the file has this state
     */
    public ImportItem(InputKind kind, Path path, String username,
            ImportState state, String message) {
        this.kind = kind;
        this.path = path;
        this.username = username;
        this.state = state;
        this.message = message;
        if (!username.isEmpty()) {
            students.add(username);
        }
    }

    /**
     * Creates an import item for a folder or zip that holds several
     * students. Its own username is empty.
     *
     * @param kind the group of the file
     * @param path the chosen file or folder
     * @param state whether the file can be used
     * @param message why the file has this state
     * @param students the usernames of the students inside, in order
     */
    public ImportItem(InputKind kind, Path path, ImportState state,
            String message, List<String> students) {
        this.kind = kind;
        this.path = path;
        this.username = "";
        this.state = state;
        this.message = message;
        this.students.addAll(students);
    }

    /**
     * Returns the group this file belongs to.
     *
     * @return the kind
     */
    public InputKind getKind() {
        return kind;
    }

    /**
     * Returns the chosen file or folder.
     *
     * @return the path
     */
    public Path getPath() {
        return path;
    }

    /**
     * Returns the file or folder name without its folders.
     *
     * @return the name
     */
    public String getName() {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return path.toString();
        }
        return fileName.toString();
    }

    /**
     * Returns the student the file belongs to.
     *
     * @return the username, or an empty string
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns whether the file can be used.
     *
     * @return the state
     */
    public ImportState getState() {
        return state;
    }

    /**
     * Returns why the file has its state.
     *
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the usernames of the students the file holds.
     *
     * @return a copy of the usernames, in order, each once, as grading
     *         grades one file per student
     */
    public List<String> getStudents() {
        return new ArrayList<>(students);
    }

    /**
     * Returns how many students the file holds.
     *
     * @return one for a student zip, more for a folder or a zip of zips
     */
    public int getStudentCount() {
        return students.size();
    }
}
