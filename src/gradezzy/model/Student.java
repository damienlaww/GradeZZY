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
     * @return the id from the OrgDefinedId column
     */
    public String getOrgDefinedId() {
        return orgDefinedId;
    }

    /**
     * Returns the student's username, such as ping.lee.2023.
     *
     * @return the student's username, such as ping.lee.2023
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the value of the Last Name column.
     *
     * @return the value of the Last Name column
     */
    public String getLastName() {
        return lastName;
    }

    /**
     * Returns the value of the First Name column.
     *
     * @return the value of the First Name column
     */
    public String getFirstName() {
        return firstName;
    }

    /**
     * Returns the student's email address.
     *
     * @return the student's email address
     */
    public String getEmail() {
        return email;
    }
}
