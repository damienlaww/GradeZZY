/*
 * File: CodeShapeReader.java
 *
 * Purpose: Reads a student's answer file for a question and returns the
 * shape of its code: the same code but for names.
 *
 * Where it fits: AnswerGrouper uses it to note which students of an
 * answer group wrote code of the same shape.
 */
package gradezzy.stats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import gradezzy.model.Assessment;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.Submission;
import gradezzy.plagiarism.JavaTokenizer;
import gradezzy.testcase.QuestionFolderFinder;

/**
 * Reads the code shape of a student's answer file.
 *
 * <p>Tier: COULD.
 */
final class CodeShapeReader {

    // Only static methods; never created
    private CodeShapeReader() {
    }

    // Returns the shape of a student's file for a question, or null when
    // there is no student or the file cannot be found or read.
    static String readShape(GradingRun run, Submission submission,
            Question question) {
        if (submission == null) {
            return null;
        }
        try {
            Assessment assessment = run.getAssessment();
            Path folder = QuestionFolderFinder.find(submission, assessment,
                    question);
            if (folder == null) {
                return null;
            }
            Path file = folder.resolve(question.getFileName());
            if (!Files.isRegularFile(file)) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(file);
            String source = new String(bytes, StandardCharsets.UTF_8);
            return JavaTokenizer.shapeOf(source);
        } catch (IOException exception) {
            return null;
        }
    }
}
