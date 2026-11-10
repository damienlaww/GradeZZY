/*
 * File: AnswerGroupService.java
 *
 * Purpose: Lists the answer groups of a run with the names the marker
 * gave the mistakes and who was checked, and saves those decisions.
 *
 * Where it fits: LiveBackend calls it from Review's Mistakes view and
 * Statistics; the demo keeps its own copy that saves nothing. The
 * common mistakes export and the JSON read it through
 * AnswerGroupSource. When results are not saved, or a save fails, the
 * decisions last until GradeZZY closes, as with marks changed by hand.
 * The methods are synchronized, so an export thread and the window can
 * call them together.
 */
package gradezzy.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gradezzy.config.AppConfig;
import gradezzy.config.ConfigKeys;
import gradezzy.model.AnswerGroup;
import gradezzy.model.AnswerGroupNote;
import gradezzy.model.GradingRun;
import gradezzy.persistence.PersistenceException;
import gradezzy.persistence.ResultRepository;
import gradezzy.report.AnswerGroupSource;
import gradezzy.stats.AnswerGrouper;

/**
 * Keeps the marker's names and checks for answer groups.
 *
 * <p>Tier: COULD.
 */
public class AnswerGroupService implements AnswerGroupSource {

    // Line breaks in a name become this, so a name stays one line
    private static final String LINE_BREAK = "[\\r\\n]+";

    // What a line break is replaced with
    private static final String SPACE = " ";

    // The name of a group with no name yet
    private static final String NO_LABEL = "";

    // The title given to the group made only to work out a note's key,
    // which does not use it
    private static final String NO_TITLE = "";

    // Says a decision is kept only until GradeZZY closes
    private static final String SESSION_ONLY = " For this session only; "
            + "saving results is switched off.";

    // Starts the reason a decision was not saved
    private static final String NOT_SAVED = " It could not be saved, so "
            + "it lasts until GradeZZY closes: ";

    // Says why nothing is saved for a run whose notes were not read
    private static final String NOT_READ = "The saved names and checks of "
            + "this run could not be read, so this change lasts until "
            + "GradeZZY closes. ";

    // The store decisions are saved to, or null when there is none
    private final ResultRepository repository;

    // The application settings, which say whether results are saved
    private final AppConfig config;

    // The notes of each run, by run id, then by group key, read once
    private final Map<String, Map<String, AnswerGroupNote>> byRun =
            new HashMap<>();

    // Why the last decision could not be saved or read, or ""
    private String saveProblem = "";

    // The runs whose saved notes could not be read yet. Their notes are
    // read again before each use, and nothing is saved for them until a
    // read works, so a failed read never overwrites saved decisions.
    private final List<String> unreadRuns = new ArrayList<>();

    /**
     * Creates the service.
     *
     * <p>Tier: COULD.
     *
     * @param repository the store, or null to keep decisions only until
     *        GradeZZY closes
     * @param config the application settings
     */
    public AnswerGroupService(ResultRepository repository,
            AppConfig config) {
        this.repository = repository;
        this.config = config;
    }

    /**
     * Lists the answer groups of a run with their names and checks.
     *
     * <p>Tier: COULD.
     *
     * @param run the run, or null
     * @return the groups, in question order, largest first
     */
    @Override
    public synchronized List<AnswerGroup> listGroups(GradingRun run) {
        List<AnswerGroup> groups = AnswerGrouper.group(run);
        if (run == null) {
            return groups;
        }
        Map<String, AnswerGroupNote> notes = notesOf(run);
        for (AnswerGroup group : groups) {
            AnswerGroupNote note = notes.get(group.getKey());
            if (note != null) {
                group.setLabel(note.getLabel());
                group.setCheckedUsernames(note.getCheckedUsernames());
            }
        }
        return groups;
    }

    /**
     * Names the mistake of a group, or removes the name. The name is
     * trimmed and kept on one line.
     *
     * <p>Tier: COULD.
     *
     * @param run the run the group belongs to
     * @param group the group
     * @param label the name; null or blank removes it
     */
    public synchronized void setLabel(GradingRun run, AnswerGroup group,
            String label) {
        String clean = "";
        if (label != null) {
            clean = label.replaceAll(LINE_BREAK, SPACE).trim();
        }
        AnswerGroupNote before = noteOf(run, group);
        AnswerGroupNote after = new AnswerGroupNote(group.getQuestionId(),
                group.getPattern(), clean, before.getCheckedUsernames());
        save(run, after);
        group.setLabel(clean);
    }

    /**
     * Marks a group as checked, keeping who is in it now, or takes the
     * check back.
     *
     * <p>Tier: COULD.
     *
     * @param run the run the group belongs to
     * @param group the group
     * @param checked true to check it, false to take the check back
     */
    public synchronized void setChecked(GradingRun run, AnswerGroup group,
            boolean checked) {
        List<String> students = null;
        if (checked) {
            students = group.getUsernames();
        }
        setChecked(run, group, students);
    }

    /**
     * Marks a group as checked for the students the marker saw. A student
     * now in the group who is not among them counts as new since the
     * check.
     *
     * <p>Tier: COULD.
     *
     * @param run the run the group belongs to
     * @param group the group
     * @param students the students checked; null or empty takes the
     *        check back
     */
    public synchronized void setChecked(GradingRun run, AnswerGroup group,
            List<String> students) {
        AnswerGroupNote before = noteOf(run, group);
        AnswerGroupNote after = new AnswerGroupNote(group.getQuestionId(),
                group.getPattern(), before.getLabel(), students);
        save(run, after);
        group.setCheckedUsernames(after.getCheckedUsernames());
    }

    /**
     * Says why the last decision could not be saved or read.
     *
     * <p>Tier: COULD.
     *
     * @return the reason, or "" when it was saved
     */
    public synchronized String getSaveProblem() {
        return saveProblem;
    }

    /**
     * Says, after a decision, whether it was saved, as a sentence to add
     * to the notice.
     *
     * <p>Tier: COULD.
     *
     * @return "" when it was saved; else a sentence starting with a
     *         space saying it lasts only until GradeZZY closes, and why
     */
    public synchronized String describeSaving() {
        if (!isSaving()) {
            return SESSION_ONLY;
        }
        if (saveProblem.isEmpty()) {
            return "";
        }
        return NOT_SAVED + saveProblem;
    }

    /**
     * Forgets every note read or made, so the next use reads the store
     * again, as after a backup is restored.
     *
     * <p>Tier: COULD.
     */
    public synchronized void forgetAll() {
        byRun.clear();
        unreadRuns.clear();
        saveProblem = "";
    }

    /**
     * Says whether decisions are saved with the run.
     *
     * <p>Tier: COULD.
     *
     * @return true when a store is set and results are saved
     */
    public boolean isSaving() {
        if (repository == null) {
            return false;
        }
        return config.getBoolean(ConfigKeys.DATABASE_ENABLED);
    }

    // Returns a run's notes, reading them from the store the first time,
    // and again each time while a read has failed. If they cannot be
    // read, only this session's notes show and the reason is kept.
    private Map<String, AnswerGroupNote> notesOf(GradingRun run) {
        String id = runIdOf(run);
        Map<String, AnswerGroupNote> notes = byRun.get(id);
        boolean known = notes != null;
        if (known && !unreadRuns.contains(id)) {
            return notes;
        }
        if (!known) {
            notes = new HashMap<>();
            byRun.put(id, notes);
        }
        if (canSave(id)) {
            readNotes(id, notes);
        }
        return notes;
    }

    // Reads a run's saved notes under this session's notes, which win.
    // If they cannot be read, the run stays unread and the reason
    // becomes the save problem.
    private void readNotes(String id, Map<String, AnswerGroupNote> notes) {
        try {
            List<AnswerGroupNote> saved = repository.listAnswerGroupNotes(id);
            for (AnswerGroupNote note : saved) {
                String key = keyOf(note);
                if (!notes.containsKey(key)) {
                    notes.put(key, note);
                }
            }
            unreadRuns.remove(id);
        } catch (PersistenceException exception) {
            saveProblem = exception.getMessage();
            if (!unreadRuns.contains(id)) {
                unreadRuns.add(id);
            }
        }
    }

    // Says whether the notes of a run with this id are saved: results
    // are saved and the run has an id.
    private boolean canSave(String id) {
        if (!isSaving()) {
            return false;
        }
        return !id.isEmpty();
    }

    // Returns the note of a group, or an empty one.
    private AnswerGroupNote noteOf(GradingRun run, AnswerGroup group) {
        AnswerGroupNote note = notesOf(run).get(group.getKey());
        if (note == null) {
            return new AnswerGroupNote(group.getQuestionId(),
                    group.getPattern(), NO_LABEL, null);
        }
        return note;
    }

    // Keeps a note for the session and saves it with the run.
    private void save(GradingRun run, AnswerGroupNote note) {
        Map<String, AnswerGroupNote> notes = notesOf(run);
        if (note.isEmpty()) {
            notes.remove(keyOf(note));
        } else {
            notes.put(keyOf(note), note);
        }
        String id = runIdOf(run);
        if (!canSave(id)) {
            saveProblem = "";
            return;
        }
        if (unreadRuns.contains(id)) {
            saveProblem = NOT_READ + saveProblem;
            return;
        }
        saveProblem = "";
        try {
            repository.saveAnswerGroupNote(id, note);
        } catch (PersistenceException exception) {
            saveProblem = exception.getMessage();
        }
    }

    // Returns the key of a note, the same as its group's key.
    private static String keyOf(AnswerGroupNote note) {
        AnswerGroup group = new AnswerGroup(note.getQuestionId(),
                note.getPattern(), NO_TITLE);
        return group.getKey();
    }

    // Returns a run's id, or "" when it has none.
    private static String runIdOf(GradingRun run) {
        if (run.getId() == null) {
            return "";
        }
        return run.getId();
    }
}
