/*
 * File: Student.java
 *
 * Purpose: One student from the score sheet roster.
 *
 * Where it fits: Created by RosterReader, attached to a Submission and written
 * back by the CSV exporter.
 */
package gradezzy.model;

/**
 * One student from the score sheet roster.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class Student {

    // What the roster puts in a name column that has no name
    private static final String EMPTY_NAME_PART = "_";

    // Goes between the first and the last name
    private static final String NAME_SEPARATOR = " ";

    // The id from the OrgDefinedId column
    private final String orgDefinedId;

    // The student's username, such as ping.lee.2023
    private final String username;

    // The value of the Last Name column
    private final String lastName;

    // The value of the First Name column
    private final String firstName;

    // The student's email address
    private final String email;

    /**
     * Creates a new student.
     *
     * <p>Tier: MUST.
     *
     * @param orgDefinedId the id from the OrgDefinedId column
     * @param username the student's username, such as ping.lee.2023
     * @param lastName the value of the Last Name column
     * @param firstName the value of the First Name column
     * @param email the student's email address
     */
    public Student(String orgDefinedId, String username, String lastName,
            String firstName, String email) {
        this.orgDefinedId = orgDefinedId;
        this.username = username;
        this.lastName = lastName;
        this.firstName = firstName;
        this.email = email;
    }

    /**
     * Returns the id from the OrgDefinedId column.
     *
     * <p>Tier: MUST.
     *
     * @return the id from the OrgDefinedId column
     */
    public String getOrgDefinedId() {
        return orgDefinedId;
    }

    /**
     * Returns the student's username, such as ping.lee.2023.
     *
     * <p>Tier: MUST.
     *
     * @return the student's username, such as ping.lee.2023
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the value of the Last Name column.
     *
     * <p>Tier: MUST.
     *
     * @return the value of the Last Name column
     */
    public String getLastName() {
        return lastName;
    }

    /**
     * Returns the value of the First Name column.
     *
     * <p>Tier: MUST.
     *
     * @return the value of the First Name column
     */
    public String getFirstName() {
        return firstName;
    }

    /**
     * Returns the student's email address.
     *
     * <p>Tier: MUST.
     *
     * @return the student's email address
     */
    public String getEmail() {
        return email;
    }

    /**
     * Returns the first and last name, leaving out a part that is blank
     * or the roster's "_".
     *
     * <p>Tier: MUST.
     *
     * @return the full name, or an empty string when there is none
     */
    public String getFullName() {
        boolean hasFirst = isName(firstName);
        boolean hasLast = isName(lastName);

        if (hasFirst && hasLast) {
            return firstName.trim() + NAME_SEPARATOR + lastName.trim();
        } else if (hasFirst) {
            return firstName.trim();
        } else if (hasLast) {
            return lastName.trim();
        }
        return "";
    }

    // Says whether a name column holds a name, not blank and not "_".
    private boolean isName(String part) {
        if (part == null) {
            return false;
        }
        String trimmed = part.trim();
        return !trimmed.isEmpty() && !trimmed.equals(EMPTY_NAME_PART);
    }
}
