/*
 * File: FakeBackendHandler.java
 *
 * Purpose: Answers the calls CheatingRemarks makes on a DashboardBackend,
 * for a fake backend made with java.lang.reflect.Proxy.
 *
 * Where it fits: Used by UiLogicTest, so the Remarks column can be
 * checked with no window, database or real backend.
 */
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import gradezzy.model.Anomaly;
import gradezzy.model.GradingRun;
import gradezzy.plagiarism.SimilarityPair;
import gradezzy.service.PlagiarismService;
import gradezzy.ui.backend.ReviewDecision;

/**
 * Answers the backend calls CheatingRemarks makes.
 */
public class FakeBackendHandler implements InvocationHandler {

    // The backend methods the fake answers
    private static final String LIST_PAIRS = "listSimilarPairs";
    private static final String FIND_PAIR = "findSimilarPair";
    private static final String GET_DECISION = "getDecision";
    private static final String NEEDS_REVIEW = "needsReview";
    private static final String TO_STRING = "toString";

    // Where the run and the flag sit in the arguments of findSimilarPair
    private static final int RUN_ARGUMENT = 0;
    private static final int PAIR_FLAG_ARGUMENT = 1;

    // Where the flag sits in the arguments of getDecision and needsReview
    private static final int FLAG_ARGUMENT = 0;

    // The similar pairs listSimilarPairs gives
    private final List<SimilarityPair> pairs;

    // The decision of each flag, by anomaly id; a flag not here is OPEN
    private final Map<String, ReviewDecision> decisions;

    // The service findSimilarPair reads a flag's pair with
    private final PlagiarismService plagiarism;

    /**
     * Creates a handler that answers with these pairs and decisions.
     *
     * @param pairs the similar pairs of the run
     * @param decisions the decision of each flag, by anomaly id; read at
     *        each call, so later changes are seen
     * @param plagiarism the service that reads the pair of a flag
     */
    public FakeBackendHandler(List<SimilarityPair> pairs,
            Map<String, ReviewDecision> decisions,
            PlagiarismService plagiarism) {
        this.pairs = pairs;
        this.decisions = decisions;
        this.plagiarism = plagiarism;
    }

    /**
     * Answers one call made on the fake backend.
     *
     * @param proxy the fake backend, not used
     * @param method the backend method called
     * @param args the arguments of the call
     * @return the answer of the fake backend
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        String name = method.getName();
        if (name.equals(LIST_PAIRS)) {
            return pairs;
        } else if (name.equals(FIND_PAIR)) {
            return plagiarism.readPair((GradingRun) args[RUN_ARGUMENT],
                    (Anomaly) args[PAIR_FLAG_ARGUMENT]);
        } else if (name.equals(GET_DECISION)) {
            return decisionOf((Anomaly) args[FLAG_ARGUMENT]);
        } else if (name.equals(NEEDS_REVIEW)) {
            return !decisions.containsKey(((Anomaly) args[FLAG_ARGUMENT])
                    .getId());
        } else if (name.equals(TO_STRING)) {
            return "fake backend";
        }
        throw new UnsupportedOperationException(name);
    }

    // Gives a flag's decision, or OPEN when none was made.
    private ReviewDecision decisionOf(Anomaly flag) {
        ReviewDecision decision = decisions.get(flag.getId());
        if (decision == null) {
            return ReviewDecision.OPEN;
        }
        return decision;
    }
}
