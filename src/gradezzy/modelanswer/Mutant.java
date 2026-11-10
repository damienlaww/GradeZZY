/*
 * File: Mutant.java
 *
 * Purpose: One broken copy of a model answer file: one small change,
 * such as "<" made "<=", and what the tester did with it.
 *
 * Where it fits: Made by MutantMaker, run by TesterStrengthChecker and
 * listed in the tester strength report.
 */
package gradezzy.modelanswer;

/**
 * One broken copy of a model answer file.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public class Mutant {

    // The file that was changed, such as Q1.java
    private final String fileName;

    // The line of the change, counted from 1
    private final int line;

    // The line as the model answer has it, without spaces at the ends
    private final String originalLine;

    // The change in words, such as "\"<\" changed to \"<=\""
    private final String change;

    // The whole file with the change made
    private final String source;

    // What the tester did with this copy
    private MutantOutcome outcome = MutantOutcome.NOT_RUN;

    /**
     * Creates a new broken copy that has not been run.
     *
     * <p>Tier: COULD.
     *
     * @param fileName the file that was changed
     * @param line the line of the change, counted from 1
     * @param originalLine the line as the model answer has it
     * @param change the change in words
     * @param source the whole file with the change made
     */
    public Mutant(String fileName, int line, String originalLine,
            String change, String source) {
        this.fileName = fileName;
        this.line = line;
        this.originalLine = originalLine;
        this.change = change;
        this.source = source;
    }

    /**
     * Returns the file that was changed.
     *
     * <p>Tier: COULD.
     *
     * @return the file name, such as Q1.java
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Returns the whole file with the change made.
     *
     * <p>Tier: COULD.
     *
     * @return the changed source
     */
    public String getSource() {
        return source;
    }

    /**
     * Returns what the tester did with this copy.
     *
     * <p>Tier: COULD.
     *
     * @return the outcome
     */
    public MutantOutcome getOutcome() {
        return outcome;
    }

    /**
     * Records what the tester did with this copy.
     *
     * <p>Tier: COULD.
     *
     * @param outcome the outcome
     */
    public void setOutcome(MutantOutcome outcome) {
        this.outcome = outcome;
    }

    /**
     * Describes the change for the report, such as
     * Q1.java line 14 "if (n < 0) {": "<" changed to "<=".
     *
     * <p>Tier: COULD.
     *
     * @return the description
     */
    public String describe() {
        return fileName + " line " + line + " \"" + originalLine + "\": "
                + change;
    }
}
