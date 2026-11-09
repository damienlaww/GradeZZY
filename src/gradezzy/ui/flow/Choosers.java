/*
 * File: Choosers.java
 *
 * Purpose: Remembers the last folder a file or folder was chosen from,
 * so the next file dialog opens there instead of at the top.
 *
 * Where it fits: Every screen that opens a file or folder dialog calls
 * prepare before showing it and remember after a pick.
 */
package gradezzy.ui.flow;

import java.io.File;

import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

/**
 * The last folder used by a file dialog, kept while GradeZZY is open.
 *
 * <p>Tier: SHOULD.
 */
public final class Choosers {

    // The folder of the last pick, or null before the first one
    private static File lastFolder = null;

    // Not created; every method is static.
    private Choosers() {
    }

    /**
     * Opens a file dialog at the last folder used, if it still exists.
     *
     * @param chooser the file dialog
     */
    public static void prepare(FileChooser chooser) {
        if (hasLastFolder()) {
            chooser.setInitialDirectory(lastFolder);
        }
    }

    /**
     * Opens a folder dialog at the last folder used, if it still exists.
     *
     * @param chooser the folder dialog
     */
    public static void prepare(DirectoryChooser chooser) {
        if (hasLastFolder()) {
            chooser.setInitialDirectory(lastFolder);
        }
    }

    /**
     * Keeps the folder of a pick: a file's folder, or the folder itself.
     *
     * @param picked the chosen file or folder, or null when cancelled
     */
    public static void remember(File picked) {
        if (picked == null) {
            return;
        }
        if (picked.isDirectory()) {
            lastFolder = picked;
        } else if (picked.getParentFile() != null) {
            lastFolder = picked.getParentFile();
        }
    }

    // Says whether the last folder is known and still there.
    private static boolean hasLastFolder() {
        return lastFolder != null && lastFolder.isDirectory();
    }
}
