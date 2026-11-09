/*
 * File: CopiedHeaderDetector.java
 *
 * Purpose: Flags a submission whose file headers name another student:
 * an Email ID with another username, or the name of another student on
 * the score sheet.
 *
 * Where it fits: A student who copies a friend's files often forgets to
 * change the "Name:" and "Email ID:" lines. Never changes a mark: it adds
 * a COPIED_HEADER flag for the instructor to judge, like
 * HARD_CODED_ANSWER.
 */
package gradezzy.detection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.Assessment;
import gradezzy.model.Question;
import gradezzy.model.Severity;
import gradezzy.model.Student;
import gradezzy.model.Submission;
import gradezzy.testcase.QuestionFolderFinder;
import gradezzy.util.FileUtils;
import gradezzy.util.TextUtils;

/**
 * Flags file headers that name another student.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class CopiedHeaderDetector implements RosterCheck {

    /**
     * The name of this check.
     */
    public static final String NAME = "Header names another student";

    // The text in front of the id of each flag
    private static final String ID_PREFIX = "copied-header-";

    // What the instructor can do about the flag
    private static final String SUGGESTED_ACTION = "Compare this work "
            + "with the other student's before deciding. This flag changes "
            + "no mark.";

    // Ends the username part of an email address
    private static final String EMAIL_AT = "@";

    // Every username has a dot, as in ping.lee.2023; a student number
    // typed as the Email ID has none, so it is not taken for a username
    private static final String USERNAME_DOT = ".";

    // Any run of spaces between the words of a name
    private static final String SPACES = "\\s+";

    // Put between the words of a name
    private static final String SPACE = " ";

    // Put between the files and the names listed in the message
    private static final String LIST_SEPARATOR = ", ";

    // Separates a question folder from its file
    private static final String FOLDER_SEPARATOR = "/";

    // The character every line ending becomes after normalising
    private static final String NEW_LINE = "\n";

    // The application settings, which name the header labels
    private final AppConfig config;

    /**
     * Creates the check.
     *
     * <p>Tier: SHOULD. Owner: Jing Yuan.
     *
     * @param config the application settings
     */
    public CopiedHeaderDetector(AppConfig config) {
        this.config = config;
    }

    /**
     * Returns the name of this check.
     *
     * <p>Tier: SHOULD. Owner: Jing Yuan.
     *
     * @return the name of this check
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Reads the header of each answer file and flags the submission once
     * when any header gives another student's username as its Email ID,
     * or another student's name from the roster as its Name. The
     * student's own name and username never count.
     *
     * <p>Tier: SHOULD. Owner: Jing Yuan.
     *
     * @param submission the submission
     * @param assessment the assessment
     * @param roster the students on the run's score sheet
     * @return one flag naming the files and the other students, or none
     */
    @Override
    public List<Anomaly> check(Submission submission, Assessment assessment,
            List<Student> roster) {
        List<String> files = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (Question question : assessment.getQuestions()) {
            List<String> header = readHeader(findAnswerFile(submission,
                    assessment, question));
            List<String> named = findOthers(header, submission, roster);
            if (!named.isEmpty()) {
                files.add(question.getFolderName() + FOLDER_SEPARATOR
                        + question.getFileName());
            }
            for (String other : named) {
                if (!others.contains(other)) {
                    others.add(other);
                }
            }
        }

        List<Anomaly> flags = new ArrayList<>();
        if (files.isEmpty()) {
            return flags;
        }

        String reason = "The work may be copied.";
        boolean offRoster = !roster.isEmpty()
                && findByUsername(roster, submission.getUsername()) == null;
        if (offRoster) {
            reason = submission.getUsername() + " is not on the score "
                    + "sheet, so the zip file's name may be wrong.";
        }
        flags.add(createFlag(submission, "The header of "
                + TextUtils.join(LIST_SEPARATOR, files) + " names "
                + TextUtils.join(LIST_SEPARATOR, others) + ", not "
                + submission.getUsername() + ". " + reason));
        return flags;
    }

    // Finds the answer file of a question, or returns null if missing.
    private Path findAnswerFile(Submission submission,
            Assessment assessment, Question question) {
        try {
            Path folder = QuestionFolderFinder.find(submission, assessment,
                    question);
            if (folder == null) {
                return null;
            }
            return folder.resolve(question.getFileName());
        } catch (IOException exception) {
            return null;
        }
    }

    // Reads the first lines of a file, as far as headers are looked for,
    // or none when the file is missing or cannot be read.
    private List<String> readHeader(Path file) {
        List<String> header = new ArrayList<>();
        boolean readable = file != null
                && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS);
        if (!readable) {
            return header;
        }

        int scanLines = config.getInt(ConfigKeys.HEADER_SCAN_LINES);
        try {
            String text = TextUtils.normaliseLineEndings(
                    FileUtils.readText(file));
            String[] lines = text.split(NEW_LINE);
            for (int i = 0; i < lines.length && i < scanLines; i++) {
                header.add(lines[i]);
            }
        } catch (IOException exception) {
            // An unreadable file has no header to check
        }
        return header;
    }

    // Lists who a header names that is not the student: the Email ID's
    // username, and the roster student whose name the Name line gives.
    private List<String> findOthers(List<String> header,
            Submission submission, List<Student> roster) {
        List<String> others = new ArrayList<>();
        String username = submission.getUsername();

        String email = findValueAfter(header,
                config.getString(ConfigKeys.HEADER_EMAIL_LABEL));
        String emailUser = usernameOf(email);
        boolean otherEmail = emailUser.contains(USERNAME_DOT)
                && !emailUser.equalsIgnoreCase(username);
        if (otherEmail) {
            Student owner = findByUsername(roster, emailUser);
            String shown = emailUser;
            if (owner != null) {
                shown = describe(owner);
            }
            others.add(shown);
        }

        String name = normaliseName(findValueAfter(header,
                config.getString(ConfigKeys.HEADER_NAME_LABEL)));
        Student named = findByName(roster, name);
        boolean otherName = named != null
                && !named.getUsername().equalsIgnoreCase(username)
                && !others.contains(describe(named));
        if (otherName) {
            others.add(describe(named));
        }
        return others;
    }

    // Names a roster student with the username, as "Alicia Tan
    // (alicia.tan.2025)".
    private String describe(Student student) {
        return student.getFullName() + " (" + student.getUsername() + ")";
    }

    // Finds the roster student with a username, in any case, or null.
    private Student findByUsername(List<Student> roster, String username) {
        for (Student student : roster) {
            if (username.equalsIgnoreCase(student.getUsername())) {
                return student;
            }
        }
        return null;
    }

    // Returns the text after a label on the first line holding it,
    // trimmed, or an empty string when no line holds it.
    private String findValueAfter(List<String> header, String label) {
        for (String line : header) {
            int start = line.indexOf(label);
            if (start >= 0) {
                int valueStart = start + label.length();
                return line.substring(valueStart).trim();
            }
        }
        return "";
    }

    // Returns the part of an email address before the @, in lower case.
    private String usernameOf(String email) {
        String user = email;
        int at = email.indexOf(EMAIL_AT);
        if (at >= 0) {
            user = email.substring(0, at);
        }
        return user.trim().toLowerCase(Locale.ROOT);
    }

    // Returns a name in lower case with single spaces between its words.
    private String normaliseName(String name) {
        String trimmed = name.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return "";
        }
        String[] words = trimmed.split(SPACES);
        return TextUtils.join(SPACE, words);
    }

    // Finds the roster student with a name, first name first or last
    // name first, or returns null.
    private Student findByName(List<Student> roster, String name) {
        if (name.isEmpty()) {
            return null;
        }
        for (Student student : roster) {
            String first = textOf(student.getFirstName());
            String last = textOf(student.getLastName());
            String firstLast = normaliseName(first + SPACE + last);
            String lastFirst = normaliseName(last + SPACE + first);
            if (name.equals(firstLast) || name.equals(lastFirst)) {
                return student;
            }
        }
        return null;
    }

    // Returns a name part, or an empty string for a missing one.
    private String textOf(String namePart) {
        if (namePart == null) {
            return "";
        }
        return namePart;
    }

    // Builds one flag for the instructor. It never changes a score.
    private Anomaly createFlag(Submission submission, String message) {
        String id = ID_PREFIX + UUID.randomUUID();
        return new Anomaly(id, AnomalyType.COPIED_HEADER, Severity.WARNING,
                submission.getUsername(), message, SUGGESTED_ACTION);
    }
}
