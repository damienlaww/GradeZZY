/*
 * File: TesterOutputScoreParser.java
 *
 * Purpose: Parses the output format of the provided tester files.
 *
 * Where it fits: Those testers print the pass marker or a fail line per test
 * case and the total score as the last line.
 */
package gradezzy.grading;

import gradezzy.config.AppConfig;
import gradezzy.exec.ProcessResult;
import gradezzy.model.Question;
import gradezzy.model.QuestionResult;

/**
 * Parses the output format of the provided tester files.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public class TesterOutputScoreParser implements ScoreParser {

    // The application settings
    private final AppConfig config;

    /**
     * Creates a new tester output score parser.
     *
     * @param config the application settings
     */
    public TesterOutputScoreParser(AppConfig config) {
        this.config = config;
    }

    /**
     * Reads the tester output for one question.
     *
     * @param question the question
     * @param processResult the process result
     * @return the score and the result of each test case
     */
    @Override
    public QuestionResult parse(Question question,
            ProcessResult processResult) {
        // TODO(Zach): implement: counting PASS_MARKER and FAIL_MARKER lines
        //     into TestCaseResults and reading the last line as the score; when
        //     the run timed out, the score is the number of passes printed
        //     before it was stopped
        return null;
    }
}
