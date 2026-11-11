/*
 * File: RunAuditFilter.java
 *
 * Purpose: Picks the audit log lines that belong to one grading run, so
 * the activity log saved beside a run's score sheet holds only that run.
 *
 * Where it fits: AuditLogExporter uses it. The audit log is one table for
 * every run and every section, and most lines do not say which run they
 * are about, so the rules below work it out from what each line holds.
 */
package gradezzy.report;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import gradezzy.model.Anomaly;
import gradezzy.model.AuditEntry;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.Section;
import gradezzy.model.Submission;

/**
 * Keeps the audit log lines of one run.
 *
 * <p>A line belongs to the run when, in this order:
 * <ol>
 * <li>it has a reference (see AuditEntry.withReference): the reference
 * is the run's id or the id of a flag of one of the run's students. A
 * line whose reference names anything else is left out: it is about
 * another run, or about a flag that is gone (such as one replaced when
 * the student was graded again).</li>
 * <li>its words name a run id: it is this run's id. A line naming
 * another run, even one that was never saved, is left out.</li>
 * <li>otherwise it was written while this run was the newest run of its
 * section (from its start until the next run of the same module and
 * section started) and it names the section, such as "for CS102 G2", or
 * one of the run's students, such as "bob.2025: ...".</li>
 * <li>a line that names only a question, such as a common mistake that
 * was named or checked, belongs to the run only when no other run that
 * has that question was the newest of its own section at that time.</li>
 * </ol>
 * Every other line, such as a change to the settings, an assessment
 * chosen, a backup or a grading attempt that could not start, is about
 * no single run and is left out.
 *
 * <p>Tier: SHOULD.
 */
public class RunAuditFilter {

    // Comes between a question id and the rest of a common mistake line
    private static final String QUESTION_SEPARATOR = " ";

    // The action of a grading attempt that made no run, as GradingService
    // logs it. Its words may name a section and a student of a run that
    // was being graded at the time, but it is about no run.
    private static final String GRADING_NOT_STARTED = "Grading could not start";

    // How every run id starts, as GradingService names runs
    private static final String RUN_ID_PREFIX = "RUN-";

    // A '.', '_' or '-' is part of a name only when a letter or a digit
    // follows it, so "bob.2025." ends at the last dot
    private static final String NAME_JOINERS = "._-";

    // The run whose lines are kept
    private final GradingRun run;

    // Every known run, this one included, to find the end of each run's
    // time and the other runs a line may name
    private final List<GradingRun> runs;

    // The ids of the flags of the run's students
    private final Set<String> anomalyIds = new HashSet<>();

    // The usernames of the run's students, in lower case
    private final Set<String> usernames = new HashSet<>();

    /**
     * Creates the filter for one run.
     *
     * @param run the run whose lines are kept
     * @param otherRuns every saved run, with or without its submissions;
     *        the run itself may be among them
     */
    public RunAuditFilter(GradingRun run, List<GradingRun> otherRuns) {
        this.run = run;
        this.runs = new ArrayList<>();
        runs.add(run);
        for (GradingRun other : otherRuns) {
            boolean same = other == null || sameId(other, run);
            if (!same) {
                runs.add(other);
            }
        }
        for (Submission submission : run.getSubmissions()) {
            addUsername(submission.getUsername());
            if (submission.getStudent() != null) {
                addUsername(submission.getStudent().getUsername());
            }
            for (Anomaly anomaly : submission.getAnomalies()) {
                anomalyIds.add(anomaly.getId());
            }
        }
    }

    /**
     * Returns the lines that belong to the run, in the order given.
     *
     * @param entries every line of the audit log
     * @return the run's lines
     */
    public List<AuditEntry> keep(List<AuditEntry> entries) {
        List<AuditEntry> kept = new ArrayList<>();
        if (entries == null) {
            return kept;
        }
        for (AuditEntry entry : entries) {
            if (belongs(entry)) {
                kept.add(entry);
            }
        }
        return kept;
    }

    /**
     * Says whether one line belongs to the run, by the rules above.
     *
     * @param entry the line
     * @return true when the line is about the run
     */
    public boolean belongs(AuditEntry entry) {
        if (GRADING_NOT_STARTED.equals(entry.getAction())) {
            return false;
        }
        String reference = entry.getReference();
        if (!reference.isEmpty()) {
            return reference.equals(run.getId())
                    || anomalyIds.contains(reference);
        }

        String detail = entry.getShownDetail();
        if (detail == null) {
            detail = "";
        }
        GradingRun named = findNamedRun(detail);
        if (named != null) {
            return named == run;
        }
        if (namesUnknownRun(detail)) {
            return false;
        }
        if (!isNewestOfSection(run, entry.getTimestamp())) {
            return false;
        }

        String lowerDetail = detail.toLowerCase(Locale.ROOT);
        if (namesSection(lowerDetail) || namesStudent(lowerDetail)) {
            return true;
        }
        return isOnlyRunWithQuestion(detail, entry.getTimestamp());
    }

    // Keeps a username in lower case, so an upload named ALICE matches
    private void addUsername(String username) {
        if (username != null && !username.isEmpty()) {
            usernames.add(username.toLowerCase(Locale.ROOT));
        }
    }

    // Returns the known run whose id the words name, or null
    private GradingRun findNamedRun(String detail) {
        for (GradingRun known : runs) {
            String id = known.getId();
            boolean named = id != null && !id.isEmpty()
                    && containsId(detail, id);
            if (named) {
                return known;
            }
        }
        return null;
    }

    // Says whether the words name a run id that is not saved, such as a
    // run whose grading was stopped before it was saved
    private boolean namesUnknownRun(String detail) {
        int from = detail.indexOf(RUN_ID_PREFIX);
        while (from >= 0) {
            int next = from + RUN_ID_PREFIX.length();
            boolean digitFollows = next < detail.length()
                    && Character.isDigit(detail.charAt(next));
            if (digitFollows) {
                return true;
            }
            from = detail.indexOf(RUN_ID_PREFIX, next);
        }
        return false;
    }

    // Says whether the words name the run's module and section
    private boolean namesSection(String lowerDetail) {
        Section section = run.getSection();
        if (section == null) {
            return false;
        }
        String label = section.getLabel().toLowerCase(Locale.ROOT);
        return containsName(lowerDetail, label);
    }

    // Says whether the words name one of the run's students
    private boolean namesStudent(String lowerDetail) {
        for (String username : usernames) {
            if (containsName(lowerDetail, username)) {
                return true;
            }
        }
        return false;
    }

    // A line that starts with a question id, such as "Q2a Did not
    // compile", belongs to the run when the run has that question and
    // no other run with it was the newest of its section at that time
    private boolean isOnlyRunWithQuestion(String detail,
            LocalDateTime time) {
        int end = detail.indexOf(QUESTION_SEPARATOR);
        if (end <= 0) {
            return false;
        }
        String questionId = detail.substring(0, end);
        if (!hasQuestion(run, questionId)) {
            return false;
        }
        for (GradingRun other : runs) {
            boolean rival = other != run && hasQuestion(other, questionId)
                    && isNewestOfSection(other, time);
            if (rival) {
                return false;
            }
        }
        return true;
    }

    // Says whether a run's assessment has a question with this id
    private boolean hasQuestion(GradingRun known, String questionId) {
        if (known.getAssessment() == null) {
            return false;
        }
        for (Question question : known.getAssessment().getQuestions()) {
            if (question.getId().equals(questionId)) {
                return true;
            }
        }
        return false;
    }

    // Says whether a run was the newest run of its section at a time:
    // the time is not before it started, and no later run of the same
    // module and section had started yet. Times are compared to the
    // second, as the log may keep fewer digits than the run.
    private boolean isNewestOfSection(GradingRun known, LocalDateTime time) {
        LocalDateTime start = known.getStartedAt();
        if (start == null || time == null) {
            return false;
        }
        LocalDateTime second = time.truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime startSecond = start.truncatedTo(ChronoUnit.SECONDS);
        if (second.isBefore(startSecond)) {
            return false;
        }
        for (GradingRun other : runs) {
            boolean later = other != known && sameSection(other, known)
                    && startsBetween(other, start, second);
            if (later) {
                return false;
            }
        }
        return true;
    }

    // Says whether a run started after one time and not after another
    private boolean startsBetween(GradingRun other, LocalDateTime after,
            LocalDateTime notAfter) {
        LocalDateTime start = other.getStartedAt();
        if (start == null) {
            return false;
        }
        LocalDateTime startSecond = start.truncatedTo(ChronoUnit.SECONDS);
        return start.isAfter(after) && !startSecond.isAfter(notAfter);
    }

    // Says whether two runs are of the same module and section
    private boolean sameSection(GradingRun first, GradingRun second) {
        Section one = first.getSection();
        Section two = second.getSection();
        if (one == null || two == null) {
            return false;
        }
        return one.getLabel().equalsIgnoreCase(two.getLabel());
    }

    // Says whether two runs have the same id
    private boolean sameId(GradingRun first, GradingRun second) {
        return first.getId() != null && first.getId().equals(second.getId());
    }

    // Says whether the text holds a run id that no letter or digit goes
    // on from, so RUN-12 is found in "RUN-12-CS102-G2.csv" but not in
    // RUN-123
    private static boolean containsId(String text, String id) {
        int from = text.indexOf(id);
        while (from >= 0) {
            int before = from - 1;
            int after = from + id.length();
            boolean startsClean = from == 0
                    || !Character.isLetterOrDigit(text.charAt(before));
            boolean endsClean = after >= text.length()
                    || !Character.isLetterOrDigit(text.charAt(after));
            if (startsClean && endsClean) {
                return true;
            }
            int nextFrom = from + 1;
            from = text.indexOf(id, nextFrom);
        }
        return false;
    }

    // Says whether the text holds the name as a whole name, so bob.2025
    // is not found in bob.20251 and CS102 G2 not in CS102 G21
    private static boolean containsName(String text, String name) {
        int from = text.indexOf(name);
        while (from >= 0) {
            int before = from - 1;
            int after = from + name.length();
            boolean startsClean = from == 0 || !isNameCharAt(text, before);
            boolean endsClean = !isNameCharAt(text, after);
            if (startsClean && endsClean) {
                return true;
            }
            int nextFrom = from + 1;
            from = text.indexOf(name, nextFrom);
        }
        return false;
    }

    // Says whether the character at an index is part of a name: a letter
    // or a digit, or a joiner with a letter or a digit after it
    private static boolean isNameCharAt(String text, int index) {
        if (index < 0 || index >= text.length()) {
            return false;
        }
        char current = text.charAt(index);
        if (Character.isLetterOrDigit(current)) {
            return true;
        }
        if (NAME_JOINERS.indexOf(current) < 0) {
            return false;
        }
        int next = index + 1;
        return next < text.length()
                && Character.isLetterOrDigit(text.charAt(next));
    }
}
