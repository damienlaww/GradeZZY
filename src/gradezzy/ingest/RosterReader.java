/*
 * File: RosterReader.java
 *
 * Purpose: Reads the provided score sheet CSV into Student objects.
 *
 * Where it fits: Gives the pipeline the list of expected students so
 * submissions can be matched to a roster entry.
 */
package gradezzy.ingest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import gradezzy.config.AppConfig;
import gradezzy.model.Student;

/**
 * Reads the provided score sheet CSV into Student objects.
 *
 * <p>Tier: MUST. Owner: Damien.
 */
public class RosterReader {

    // The application settings
    private final AppConfig config;

    /**
     * Creates a new roster reader.
     *
     * @param config the application settings
     */
    public RosterReader(AppConfig config) {
        this.config = config;
    }

    /**
     * Reads every student row of the score sheet.
     *
     * @param scoreSheetFile the score sheet file
     * @return the students in file order
     * @throws IngestException if the operation fails
     */
    public List<Student> readRoster(
            Path scoreSheetFile) throws IngestException {
        // TODO(Damien): implement: reading the CSV (columns OrgDefinedId,
        //     Username, Last Name, First Name, Email, ...) and removing the id
        //     prefix
        return new ArrayList<>();
    }

    /**
     * Finds the roster entry for a username.
     *
     * @param roster the roster
     * @param username the username
     * @return the student, or null if not on the roster
     */
    public Student findByUsername(List<Student> roster, String username) {
        // TODO(Damien): implement: a search of the roster by username
        return null;
    }
}
