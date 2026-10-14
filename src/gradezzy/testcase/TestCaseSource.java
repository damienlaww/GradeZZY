/*
 * File: TestCaseSource.java
 *
 * Purpose: A place tester files can come from.
 *
 * Where it fits: Lets the instructor both upload tester files and type test
 * cases: each way is one implementation, and more can be added.
 */
package gradezzy.testcase;

import java.util.List;

import gradezzy.model.Assessment;

/**
 * A place tester files can come from.
 *
 * <p>Tier: MUST. Owner: Zach.
 */
public interface TestCaseSource {

    /**
     * Returns the name of this source.
     *
     * @return the name shown in the app
     */
    String getName();

    /**
     * Produces the tester files for an assessment.
     *
     * @param assessment the assessment
     * @return one tester file per question that has tests
     * @throws TestCaseException if the operation fails
     */
    List<TesterFile> prepareTesters(
            Assessment assessment) throws TestCaseException;
}
