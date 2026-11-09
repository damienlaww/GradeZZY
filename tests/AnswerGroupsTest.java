import java.util.Arrays;
import java.util.List;

import gradezzy.plagiarism.JavaTokenizer;

/** Run after compilation: javac -cp classes -d /tmp/gradezzy-tests tests/AnswerGroupsTest.java
 *  then: java -cp classes:lib/*:/tmp/gradezzy-tests AnswerGroupsTest
 */
public class AnswerGroupsTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        tokenizer();
        System.out.println("AnswerGroupsTest: " + checks + " checks passed");
    }

    private static void tokenizer() {
        List<String> tokens = JavaTokenizer.split("int a = b+1; // x");
        check(tokens.equals(Arrays.asList("int", "a", "=", "b", "+", "1", ";")),
                "split drops spaces and comments: " + tokens);
        check(JavaTokenizer.shapeOf("int total = n + 1;")
                .equals(JavaTokenizer.shapeOf("int sum = k + 1;")),
                "renaming variables keeps the shape");
        check(!JavaTokenizer.shapeOf("if (a < b)")
                .equals(JavaTokenizer.shapeOf("if (a <= b)")),
                "< and <= give different shapes");
        check(JavaTokenizer.shapeOf("s = \"hi\";")
                .equals(JavaTokenizer.shapeOf("t = \"bye\";")),
                "string literals do not change the shape");
        check(!JavaTokenizer.shapeOf("x = 1;")
                .equals(JavaTokenizer.shapeOf("x = 2;")),
                "numbers change the shape");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
