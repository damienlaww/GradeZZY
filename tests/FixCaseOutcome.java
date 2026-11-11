/*
 * File: FixCaseOutcome.java
 *
 * Purpose: What one fix case of FixersTest left behind: the files after
 * the fix and after the undo, and what the fixers and validators said.
 *
 * Where it fits: Filled in by FixersTest for each case it runs, then read
 * by its checks.
 */
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import gradezzy.model.AnomalyType;

/**
 * Holds the result of one fix case.
 */
public class FixCaseOutcome {

    // The folder separator used in file keys
    private static final String SLASH = "/";

    // What text gives for a file that is not there
    private static final String MISSING = "<missing>";

    // Every file and folder after the fix, by path
    private Map<String, byte[]> afterBytes = new TreeMap<>();

    // Every file and folder after every fix was undone
    private Map<String, byte[]> afterUndo = new TreeMap<>();

    // Whether the undo gave back exactly what the student sent
    private boolean undoneEqual = false;

    // The kinds of problem whose fix was applied
    private List<AnomalyType> appliedTypes = new ArrayList<>();

    // Why each fix that could not be made was not made, by kind
    private Map<String, String> failureReasons = new TreeMap<>();

    // The problems the checks still found after the fix
    private List<String> openMessages = new ArrayList<>();

    /**
     * Says whether a file or folder is there after the fix.
     *
     * @param path the path below the working copy
     * @return true when the path or something below it is there
     */
    public boolean has(String path) {
        for (String key : afterBytes.keySet()) {
            if (key.equals(path) || key.startsWith(path + SLASH)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reads a file after the fix as UTF-8 text.
     *
     * @param path the path below the working copy
     * @return the text, or "&lt;missing&gt;" when the file is not there
     */
    public String text(String path) {
        byte[] data = afterBytes.get(path);
        if (data == null) {
            return MISSING;
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    /**
     * Says whether a fix of this kind was applied.
     *
     * @param type the kind of problem
     * @return true when its fix was applied
     */
    public boolean fixed(AnomalyType type) {
        return appliedTypes.contains(type);
    }

    /**
     * Gives every file and folder after the fix, by path.
     *
     * @return the files after the fix
     */
    public Map<String, byte[]> getAfterBytes() {
        return afterBytes;
    }

    /**
     * Sets every file and folder after the fix.
     *
     * @param afterBytes the files after the fix, by path
     */
    public void setAfterBytes(Map<String, byte[]> afterBytes) {
        this.afterBytes = afterBytes;
    }

    /**
     * Gives the paths after the fix, as one line of text.
     *
     * @return the paths after the fix
     */
    public String getAfter() {
        return afterBytes.keySet().toString();
    }

    /**
     * Gives every file and folder after every fix was undone.
     *
     * @return the files after the undo
     */
    public Map<String, byte[]> getAfterUndo() {
        return afterUndo;
    }

    /**
     * Sets every file and folder after every fix was undone.
     *
     * @param afterUndo the files after the undo, by path
     */
    public void setAfterUndo(Map<String, byte[]> afterUndo) {
        this.afterUndo = afterUndo;
    }

    /**
     * Says whether the undo gave back exactly what the student sent.
     *
     * @return true when the files are byte for byte the same
     */
    public boolean isUndoneEqual() {
        return undoneEqual;
    }

    /**
     * Sets whether the undo gave back exactly what the student sent.
     *
     * @param undoneEqual true when the files are the same
     */
    public void setUndoneEqual(boolean undoneEqual) {
        this.undoneEqual = undoneEqual;
    }

    /**
     * Notes that a fix of this kind was applied.
     *
     * @param type the kind of problem
     */
    public void addAppliedType(AnomalyType type) {
        appliedTypes.add(type);
    }

    /**
     * Gives why each fix that could not be made was not made, by kind.
     *
     * @return the reasons by kind of problem
     */
    public Map<String, String> getFailureReasons() {
        return failureReasons;
    }

    /**
     * Notes why a fix of a kind could not be made.
     *
     * @param kind the kind of problem
     * @param reason why its fix was not made
     */
    public void putFailureReason(String kind, String reason) {
        failureReasons.put(kind, reason);
    }

    /**
     * Notes a problem the checks still found after the fix.
     *
     * @param message the kind and message of the problem
     */
    public void addOpenMessage(String message) {
        openMessages.add(message);
    }

    /**
     * Gives how many problems the checks still found after the fix.
     *
     * @return the number of problems left
     */
    public int getOpenAfter() {
        return openMessages.size();
    }

    /**
     * Gives the problems the checks still found after the fix.
     *
     * @return the kind and message of each problem left
     */
    public List<String> getOpenMessages() {
        return openMessages;
    }
}
