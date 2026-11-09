/*
 * File: ContainedFileNameComparator.java
 *
 * Purpose: Orders the files inside a folder or zip by name, ignoring
 * case, so the instructor can find a student's file quickly.
 *
 * Where it fits: SubmissionPreview sorts an ImportItem's files with it
 * before Set up lists them.
 */
package gradezzy.ui.backend;

import java.util.Comparator;

/**
 * Orders contained files by name, ignoring case.
 */
public class ContainedFileNameComparator
        implements Comparator<ContainedFile> {

    /**
     * Compares two files by name, ignoring case.
     *
     * @param first the first file
     * @param second the second file
     * @return below 0, 0 or above 0 as first comes before, with or after
     *         second
     */
    @Override
    public int compare(ContainedFile first, ContainedFile second) {
        return String.CASE_INSENSITIVE_ORDER.compare(first.getName(),
                second.getName());
    }
}
