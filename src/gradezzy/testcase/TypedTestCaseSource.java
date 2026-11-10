/*
 * File: TypedTestCaseSource.java
 *
 * Purpose: Supplies tester files built from test cases typed into the app.
 *
 * Where it fits: The in-app alternative to uploading testers.
 */
package gradezzy.testcase;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import gradezzy.model.Assessment;
import gradezzy.model.Question;

/**
 * Supplies tester files built from test cases typed into the app.
 *
 * <p>Tier: SHOULD. Owner: Zach.
 */
public class TypedTestCaseSource implements TestCaseSource {

    /**
     * The name shown in the app.
     */
    public static final String NAME = "Test cases typed in the app";

    // The helper that writes tester source code
    private final TesterGenerator testerGenerator;

    // The folder generated testers are written to
    private final Path outputDirectory;

    // The cases typed so far
    private final List<TestCase> testCases = new ArrayList<>();

    /**
     * Creates a new typed test case source.
     *
     * @param testerGenerator the helper that writes tester source code
     * @param outputDirectory the folder generated testers are written to
     */
    public TypedTestCaseSource(TesterGenerator testerGenerator,
            Path outputDirectory) {
        this.testerGenerator = testerGenerator;
        this.outputDirectory = outputDirectory;
    }

    /**
     * Returns the cases typed so far.
     *
     * @return the cases typed so far
     */
    public List<TestCase> getTestCases() {
        return testCases;
    }

    /**
     * Adds a typed test case.
     *
     * @param testCase the test case
     */
    public void addTestCase(TestCase testCase) {
        testCases.add(testCase);
    }

    /**
     * Returns the name of this source.
     *
     * @return the name shown in the app
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Generates one tester per question from the typed cases. Each
     * question's maximum becomes what its typed cases can earn, so a
     * question with no typed case is worth nothing.
     *
     * @param assessment the assessment
     * @return one tester file per question that has tests
     * @throws TestCaseException if the operation fails
     */
    @Override
    public List<TesterFile> prepareTesters(
            Assessment assessment) throws TestCaseException {
        // A case for a question that does not exist would be dropped
        // silently, so stop and tell the instructor
        for (TestCase testCase : testCases) {
            if (assessment.findQuestion(testCase.getQuestionId()) == null) {
                throw new TestCaseException("A typed test case is for "
                        + "question '" + testCase.getQuestionId()
                        + "', which is not in this assessment.");
            }
        }

        List<TesterFile> testers = new ArrayList<>();
        List<Question> questions = assessment.getQuestions();
        for (int index = 0; index < questions.size(); index++) {
            Question question = questions.get(index);
            List<TestCase> casesOfQuestion = new ArrayList<>();
            for (TestCase testCase : testCases) {
                if (question.getId().equals(testCase.getQuestionId())) {
                    casesOfQuestion.add(testCase);
                }
            }

            double maxScore = casesOfQuestion.size()
                    * TesterGenerator.MARKS_PER_CASE;
            questions.set(index, new Question(question.getId(),
                    question.getFolderName(), question.getFileName(),
                    maxScore));

            if (!casesOfQuestion.isEmpty()) {
                Path file = testerGenerator.writeTester(question,
                        casesOfQuestion, outputDirectory);
                testers.add(new TesterFile(question.getId(),
                        testerGenerator.getClassName(question), file));
            }
        }
        return testers;
    }
}
