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

    // The store decisions are saved to, or null when there is none
    private final ResultRepository repository;

    // The application settings, which say whether results are saved
    private final AppConfig config;

    // The notes of each run, by run id, then by group key, read once
    private final Map<String, Map<String, AnswerGroupNote>> byRun =
            new HashMap<>();

    // Why the last decision could not be saved or read, or ""
    private String saveProblem = "";

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
        save(run, new AnswerGroupNote(group.getQuestionId(),
                group.getPattern(), clean, before.getCheckedUsernames()));
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
     * Says whether decisions are saved with the run.
     *
     * <p>Tier: COULD.
     *
     * @return true when a store is set and results are saved
     */
    public boolean isSaving() {
        return repository != null
                && config.getBoolean(ConfigKeys.DATABASE_ENABLED);
    }

    // Returns a run's notes, reading them from the store the first time.
    // If they cannot be read, only this session's notes show.
    private Map<String, AnswerGroupNote> notesOf(GradingRun run) {
        String id = runIdOf(run);
        Map<String, AnswerGroupNote> notes = byRun.get(id);
        if (notes != null) {
            return notes;
        }
        notes = new HashMap<>();
        if (isSaving() && !id.isEmpty()) {
            try {
                for (AnswerGroupNote note
                        : repository.listAnswerGroupNotes(id)) {
                    notes.put(keyOf(note), note);
                }
            } catch (PersistenceException exception) {
                saveProblem = exception.getMessage();
            }
        }
        byRun.put(id, notes);
        return notes;
    }

    // Returns the note of a group, or an empty one.
    private AnswerGroupNote noteOf(GradingRun run, AnswerGroup group) {
        AnswerGroupNote note = notesOf(run).get(group.getKey());
        if (note == null) {
            return new AnswerGroupNote(group.getQuestionId(),
                    group.getPattern(), "", null);
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
        saveProblem = "";
        String id = runIdOf(run);
        if (!isSaving() || id.isEmpty()) {
            return;
        }
        try {
            repository.saveAnswerGroupNote(id, note);
        } catch (PersistenceException exception) {
            saveProblem = exception.getMessage();
        }
    }

    // Returns the key of a note, the same as its group's key.
    private static String keyOf(AnswerGroupNote note) {
        return new AnswerGroup(note.getQuestionId(), note.getPattern(), "")
                .getKey();
    }

    // Returns a run's id, or "" when it has none.
    private static String runIdOf(GradingRun run) {
        if (run.getId() == null) {
            return "";
        }
        return run.getId();
    }
}
