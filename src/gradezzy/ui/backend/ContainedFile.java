/*
 * File: ContainedFile.java
 *
 * Purpose: One file found inside a chosen folder or zip of student zips,
 * with a short note when it is not graded.
 *
 * Where it fits: SubmissionPreview lists these on an ImportItem, and Set
 * up shows them under the folder's row when the instructor opens the
 * file list.
 */
package gradezzy.ui.backend;

/**
 * One file inside a chosen folder or zip.
 *
 * <p>Tier: SHOULD.
 */
public class ContainedFile {

    // The file or folder name, without its folders
    private final String name;

    // Why the file is not graded, such as "Skipped", or "" when it is
    private final String note;

    /**
     * Creates a contained file.
     *
     * @param name the file or folder name
     * @param note why it is not graded, or an empty string when it is
     */
    public ContainedFile(String name, String note) {
        this.name = name;
        this.note = note;
    }

    /**
     * Returns the file or folder name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns why the file is not graded.
     *
     * @return the note, or an empty string when the file is graded
     */
    public String getNote() {
        return note;
    }

    /**
     * Says whether the file is graded.
     *
     * @return true when there is no note
     */
    public boolean isGraded() {
        return note.isEmpty();
    }
}
