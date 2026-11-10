/*
 * File: SignatureReader.java
 *
 * Purpose: Finds the methods of a model answer that can be called with
 * made-up inputs: static methods, named in the starter file, whose
 * parameters are numbers, text, true or false, characters, or arrays
 * and ArrayLists of these, and whose result can be printed.
 *
 * Where it fits: RandomInputChecker calls each method it returns with
 * random inputs, in the model answer and in each student's answer.
 */
package gradezzy.modelanswer;

import java.util.ArrayList;
import java.util.List;

import gradezzy.plagiarism.JavaTokenizer;
import gradezzy.util.TextUtils;

/**
 * Finds the methods of a model answer that can be called with made-up
 * inputs.
 *
 * <p>Methods whose parameters name a file, such as getTotal(String
 * filename), are left out: random text would only name files that do not
 * exist. So are methods taking objects of the assessment's own classes,
 * which cannot be made up.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
public class SignatureReader {

    // Words in front of a method that are not its type
    private static final String[] MODIFIERS = {
        "public", "protected", "static", "final", "synchronized"
    };

    // The words and tokens the reader looks for, besides the brackets
    // and the ";" named in TokenSearch
    private static final String CLASS_WORD = "class";
    private static final String STATIC_WORD = "static";
    private static final String PRIVATE_WORD = "private";
    private static final String MAIN_NAME = "main";
    private static final String VOID_TYPE = "void";
    private static final String FINAL_WORD = "final";
    private static final String ANNOTATION_MARK = "@";
    private static final String ASSIGN = "=";
    private static final String COMMA = ",";

    // A parameter whose name holds this names a file
    private static final String FILE_WORD = "file";

    // Taken from a size to give the last index
    private static final int LAST_OFFSET = 1;

    // A parameter needs a type and a name
    private static final int MIN_PARAMETER_WORDS = 2;

    // The number of tokens in an annotation such as @Override
    private static final int ANNOTATION_LENGTH = 2;

    /**
     * Finds the callable static methods of one class in a model answer.
     *
     * <p>Tier: COULD.
     *
     * @param source the model answer's source
     * @param className the class, such as Q1
     * @param starterSource the starter file's source, or "" to keep
     *        every method
     * @return the methods, in the order of the file
     */
    public List<MethodSignature> read(String source, String className,
            String starterSource) {
        List<String> tokens = JavaTokenizer.split(source);
        List<String> starterTokens = JavaTokenizer.split(starterSource);
        List<MethodSignature> methods = new ArrayList<>();
        int bodyStart = findClassBody(tokens, className);
        if (bodyStart == TokenSearch.NOT_FOUND) {
            return methods;
        }

        List<String> header = new ArrayList<>();
        int index = bodyStart + TokenSearch.NEXT;
        while (index < tokens.size()) {
            String text = tokens.get(index);
            if (text.equals(TokenSearch.CLOSE_BRACE)) {
                return methods;
            } else if (text.equals(TokenSearch.OPEN_BRACE)) {
                index = skipBlock(tokens, index);
                header.clear();
            } else if (text.equals(TokenSearch.STATEMENT_END)) {
                index = index + TokenSearch.NEXT;
                header.clear();
            } else if (text.equals(TokenSearch.OPEN_PAREN)) {
                index = readBrackets(tokens, index, className, header,
                        starterTokens, methods);
            } else {
                header.add(text);
                index = index + TokenSearch.NEXT;
            }
        }
        return methods;
    }

    // Reads what the "(" at an index opens: the arguments of an
    // annotation, which are skipped, or a method's parameters, adding the
    // method when it can be called and is in the starter. Returns the
    // index to read on from, or the end of the tokens when the "(" is
    // never closed.
    private int readBrackets(List<String> tokens, int openParen,
            String className, List<String> header,
            List<String> starterTokens, List<MethodSignature> methods) {
        int close = TokenSearch.findClosing(tokens, openParen,
                TokenSearch.OPEN_PAREN, TokenSearch.CLOSE_PAREN);
        if (close == TokenSearch.NOT_FOUND) {
            return tokens.size();
        }
        if (endsWithAnnotation(header)) {
            // The arguments of an annotation such as
            // @SuppressWarnings("unchecked"): the method comes after
            return close + TokenSearch.NEXT;
        }

        int firstParameter = openParen + TokenSearch.NEXT;
        List<String> parameters = tokens.subList(firstParameter, close);
        MethodSignature method = readMethod(className, header, parameters);
        if (method != null && isInStarter(method, starterTokens)) {
            methods.add(method);
        }
        int after = skipMethodBody(tokens, close);
        header.clear();
        return after;
    }

    // Returns the index of the "{" that opens a class's body, or
    // NOT_FOUND.
    private int findClassBody(List<String> tokens, String className) {
        int lastClassWord = tokens.size() - TokenSearch.NEXT;
        for (int index = 0; index < lastClassWord; index++) {
            boolean classWord = tokens.get(index).equals(CLASS_WORD);
            int nameIndex = index + TokenSearch.NEXT;
            boolean name = tokens.get(nameIndex).equals(className);
            if (classWord && name) {
                return TokenSearch.findFrom(tokens, TokenSearch.OPEN_BRACE,
                        nameIndex);
            }
        }
        return TokenSearch.NOT_FOUND;
    }

    // Reads a method from the tokens in front of its "(" and between its
    // brackets, or returns null when it cannot be called with made-up
    // inputs.
    private MethodSignature readMethod(String className, List<String> header,
            List<String> parameters) {
        List<String> words = removeAnnotations(header);
        if (words.isEmpty() || words.contains(ASSIGN)) {
            return null;
        }

        int modifierCount = countModifiers(words);
        List<String> modifiers = words.subList(0, modifierCount);
        boolean isStatic = modifiers.contains(STATIC_WORD);
        boolean isPrivate = words.contains(PRIVATE_WORD);
        int nameIndex = words.size() - LAST_OFFSET;
        String name = words.get(nameIndex);
        String returnType = TextUtils.join("", words.subList(modifierCount,
                nameIndex));
        boolean isName = JavaTokenizer.shapeToken(name).equals(
                JavaTokenizer.NAME_TOKEN);
        boolean callable = isStatic && !isPrivate && isName
                && !name.equals(MAIN_NAME) && isSupportedResult(returnType);
        if (!callable) {
            return null;
        }

        MethodSignature method = new MethodSignature(className, name,
                returnType);
        if (!readParameters(parameters, method)) {
            return null;
        }
        return method;
    }

    // Says whether a header ends with an annotation's name, such as
    // "@ SuppressWarnings", so the "(" after it opens its arguments.
    private boolean endsWithAnnotation(List<String> header) {
        int markIndex = header.size() - ANNOTATION_LENGTH;
        if (markIndex < 0) {
            return false;
        }
        return header.get(markIndex).equals(ANNOTATION_MARK);
    }

    // Removes annotations such as @Override from a method's header. The
    // arguments of an annotation never reach the header.
    private List<String> removeAnnotations(List<String> header) {
        List<String> words = new ArrayList<>();
        int index = 0;
        while (index < header.size()) {
            if (header.get(index).equals(ANNOTATION_MARK)) {
                index = index + ANNOTATION_LENGTH;
            } else {
                words.add(header.get(index));
                index = index + TokenSearch.NEXT;
            }
        }
        return words;
    }

    // Counts the modifiers at the start of a header.
    private int countModifiers(List<String> words) {
        int count = 0;
        while (isModifierAt(words, count)) {
            count++;
        }
        return count;
    }

    // Says whether there is a word at a position and it is a modifier.
    private boolean isModifierAt(List<String> words, int position) {
        if (position >= words.size()) {
            return false;
        }
        return TokenSearch.contains(MODIFIERS, words.get(position));
    }

    // Adds each parameter's type to a method. Returns false when one
    // cannot be made up or names a file.
    private boolean readParameters(List<String> parameters,
            MethodSignature method) {
        if (parameters.isEmpty()) {
            return true;
        }

        for (List<String> parameter : splitParameters(parameters)) {
            List<String> words = new ArrayList<>(parameter);
            words.remove(FINAL_WORD);
            if (words.size() < MIN_PARAMETER_WORDS) {
                return false;
            }
            int nameIndex = words.size() - LAST_OFFSET;
            String name = words.get(nameIndex).toLowerCase();
            String type = TextUtils.join("", words.subList(0, nameIndex));
            if (name.contains(FILE_WORD) || !isSupportedParameter(type)) {
                return false;
            }
            method.getParameterTypes().add(type);
        }
        return true;
    }

    // Splits the tokens between a method's brackets at each comma that is
    // not inside a type such as Map<String, Integer>.
    private List<List<String>> splitParameters(List<String> tokens) {
        List<List<String>> parameters = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int depth = 0;

        for (String token : tokens) {
            if (token.equals(TokenSearch.OPEN_ANGLE)) {
                depth++;
            } else if (token.equals(TokenSearch.CLOSE_ANGLE)) {
                depth--;
            }

            if (token.equals(COMMA) && depth == 0) {
                parameters.add(current);
                current = new ArrayList<>();
            } else {
                current.add(token);
            }
        }
        parameters.add(current);
        return parameters;
    }

    // Says whether a parameter type can be made up.
    private boolean isSupportedParameter(String type) {
        if (JavaTypes.isSimple(type)) {
            return true;
        }

        String arrayElement = JavaTypes.findArrayElement(type);
        if (arrayElement != null) {
            return JavaTypes.isSimple(arrayElement);
        }

        String listElement = JavaTypes.findListElement(type);
        if (listElement != null) {
            return JavaTypes.isListElement(listElement);
        }
        return false;
    }

    // Says whether a result type prints the same way every time: the
    // types a parameter may have and the boxed types a list may hold.
    // void results and objects of the assessment's own classes are left
    // out.
    private boolean isSupportedResult(String type) {
        if (type.equals(VOID_TYPE)) {
            return false;
        }
        boolean listElement = JavaTypes.isListElement(type);
        return listElement || isSupportedParameter(type);
    }

    // Says whether a method is named in the starter file, so it is part
    // of the question rather than a helper of the model answer. With no
    // starter, every method counts.
    private boolean isInStarter(MethodSignature method,
            List<String> starterTokens) {
        if (starterTokens.isEmpty()) {
            return true;
        }
        return starterTokens.contains(method.getMethodName());
    }

    // Returns the index after a method's body, or after the ";" of a
    // method with no body.
    private int skipMethodBody(List<String> tokens, int closeParen) {
        int index = closeParen + TokenSearch.NEXT;
        while (index < tokens.size()) {
            String text = tokens.get(index);
            if (text.equals(TokenSearch.OPEN_BRACE)) {
                return skipBlock(tokens, index);
            }
            if (text.equals(TokenSearch.STATEMENT_END)) {
                return index + TokenSearch.NEXT;
            }
            index = index + TokenSearch.NEXT;
        }
        return index;
    }

    // Returns the index after the "}" closing the block opened at an
    // index.
    private int skipBlock(List<String> tokens, int openBrace) {
        int close = TokenSearch.findClosing(tokens, openBrace,
                TokenSearch.OPEN_BRACE, TokenSearch.CLOSE_BRACE);
        if (close == TokenSearch.NOT_FOUND) {
            return tokens.size();
        }
        return close + TokenSearch.NEXT;
    }
}
