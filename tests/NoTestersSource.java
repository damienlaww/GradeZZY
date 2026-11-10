/*
 * File: NoTestersSource.java
 *
 * Purpose: A tester source that gives no testers.
 *
 * Where it fits: Used by IngestRegressionTest, whose pipeline only
 * unpacks the uploads, so no tester is ever run.
 */
import java.util.ArrayList;
import java.util.List;

import gradezzy.model.Assessment;
import gradezzy.testcase.TestCaseSource;
import gradezzy.testcase.TesterFile;

/**
 * Gives no tester files, for runs where nothing is executed.
 */
public class NoTestersSource implements TestCaseSource {

    /**
     * Names this source.
     *
     * @return the name of this source
     */
    @Override
    public String getName() {
        return "No execution needed for ingestion tests";
    }

    /**
     * Gives no testers, as nothing is run.
     *
     * @param assessment the assessment, not used
     * @return an empty list
     */
    @Override
    public List<TesterFile> prepareTesters(Assessment assessment) {
        return new ArrayList<>();
    }
}
