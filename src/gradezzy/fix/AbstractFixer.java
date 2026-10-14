/*
 * File: AbstractFixer.java
 *
 * Purpose: Shared code for all fixers.
 *
 * Where it fits: Holds the name and the backup helper every fixer needs before
 * it changes a file.
 */
package gradezzy.fix;

import java.nio.file.Path;

/**
 * Shared code for all fixers.
 *
 * <p>Tier: MUST. Owner: Jun Sheng.
 */
public abstract class AbstractFixer implements Fixer {

    // The name shown in the anomaly log
    private final String name;

    /**
     * Creates a new abstract fixer.
     *
     * @param name the name shown in the anomaly log
     */
    protected AbstractFixer(String name) {
        this.name = name;
    }

    /**
     * Returns the name of this fixer.
     *
     * @return the name of this fixer
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Copies a file or folder aside before it is changed.
     *
     * @param fileOrFolder the file or folder
     * @return the path of the backup
     * @throws FixException if the operation fails
     */
    protected Path backup(Path fileOrFolder) throws FixException {
        // TODO(Jun Sheng): implement: copying to a backup location inside the
        //     working copy
        return null;
    }
}
