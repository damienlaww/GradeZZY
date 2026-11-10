/*
 * File: DeletedAssessment.java
 *
 * Purpose: What a deleted assessment took with it: its saved file, with
 * its own score sheets, and the files chosen for it on Set up in every
 * module and section.
 *
 * Where it fits: AssessmentLibrary.delete returns one, and
 * AssessmentLibrary.restore puts all of it back, so Undo of a delete is
 * complete.
 */
package gradezzy.service;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gradezzy.model.SavedAssessment;

/**
 * A deleted assessment and the files chosen for it on Set up.
 *
 * <p>Tier: SHOULD.
 */
public class DeletedAssessment {

    // The assessment as it was saved, score sheets and all
    private final SavedAssessment assessment;

    // The submissions chosen for it, by key such as CS102/G1/Lab Test 3
    private final Map<String, List<Path>> chosenSubmissions;

    // The score sheet, tester and starter folder chosen for it, by key
    // such as CS102/G1/Lab Test 3|ROSTER
    private final Map<String, List<Path>> chosenClassFiles;

    /**
     * Notes what a deleted assessment took with it.
     *
     * <p>Tier: SHOULD.
     *
     * @param assessment the assessment as it was saved
     * @param chosenSubmissions the submissions chosen for it, by key
     * @param chosenClassFiles the class files chosen for it, by key
     */
    public DeletedAssessment(SavedAssessment assessment,
            Map<String, List<Path>> chosenSubmissions,
            Map<String, List<Path>> chosenClassFiles) {
        this.assessment = assessment.copy();
        this.chosenSubmissions = new HashMap<>(chosenSubmissions);
        this.chosenClassFiles = new HashMap<>(chosenClassFiles);
    }

    /**
     * Returns the assessment as it was saved.
     *
     * <p>Tier: SHOULD.
     *
     * @return a copy of the assessment
     */
    public SavedAssessment getAssessment() {
        return assessment.copy();
    }

    /**
     * Returns the submissions chosen for it on Set up.
     *
     * <p>Tier: SHOULD.
     *
     * @return the paths by key, such as CS102/G1/Lab Test 3
     */
    public Map<String, List<Path>> getChosenSubmissions() {
        return new HashMap<>(chosenSubmissions);
    }

    /**
     * Returns the score sheet, tester and starter folder chosen for it on
     * Set up.
     *
     * <p>Tier: SHOULD.
     *
     * @return the paths by key, such as CS102/G1/Lab Test 3|ROSTER
     */
    public Map<String, List<Path>> getChosenClassFiles() {
        return new HashMap<>(chosenClassFiles);
    }

    /**
     * Says whether any files were chosen for it on Set up.
     *
     * <p>Tier: SHOULD.
     *
     * @return true when some section had files chosen for it
     */
    public boolean hadChosenFiles() {
        return !chosenSubmissions.isEmpty() || !chosenClassFiles.isEmpty();
    }
}
