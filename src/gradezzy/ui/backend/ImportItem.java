/*
 * File: ImportItem.java
 *
 * Purpose: One file or folder the instructor chose before grading, with
 * whether it can be used.
 *
 * Where it fits: DashboardBackend.inspect creates these; ImportBatch
 * holds them; the Submissions screen lists them.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.backend;

import java.nio.file.Path;

/**
 * One chosen input file or folder.
 *
 * <p>Tier: MUST. Owner: Damien.
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

    /**
     * Creates an import item.
     *
     * @param kind the group this file belongs to
     * @param path the chosen file or folder
     * @param username the student the file belongs to, or an empty string
     * @param state whether the file can be used
     * @param message why the file has this state, in plain words
     */
    public ImportItem(InputKind kind, Path path, String username,
            ImportState state, String message) {
        this.kind = kind;
        this.path = path;
        this.username = username;
        this.state = state;
        this.message = message;
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
}
