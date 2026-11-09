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

    // Types a parameter or result may have as they are
    private static final String[] SIMPLE_TYPES = {
        "int", "long", "double", "boolean", "char", "String"
    };

    // The text type, which a list may also hold
    private static final String STRING_TYPE = "String";

    // Types a result may also have, and a list may hold
    private static final String[] BOXED_TYPES = {
        "Integer", "Long", "Double", "Boolean", "Character"
    };

    // The end of an array type, such as int[]
    private static final String ARRAY_END = "[]";

    // List types, written up to the element type, such as ArrayList<
    private static final String[] LIST_STARTS = {"ArrayList<", "List<"};

    // The end of a list type
    private static final String LIST_END = ">";

    // Words in front of a method that are not its type
    private static final String[] MODIFIERS = {
        "public", "protected", "static", "final", "synchronized"
    };

    // The words and tokens the reader looks for
    private static final String CLASS_WORD = "class";
    private static final String STATIC_WORD = "static";
    private static final String PRIVATE_WORD = "private";
    private static final String MAIN_NAME = "main";
    private static final String VOID_TYPE = "void";
    private static final String FINAL_WORD = "final";
    private static final String ANNOTATION_MARK = "@";
    private static final String ASSIGN = "=";
    private static final String COMMA = ",";
    private static final String OPEN_PAREN = "(";
    private static final String CLOSE_PAREN = ")";
    private static final String OPEN_BRACE = "{";
    private static final String CLOSE_BRACE = "}";
    private static final String OPEN_ANGLE = "<";
    private static final String CLOSE_ANGLE = ">";
    private static final String STATEMENT_END = ";";

    // What JavaTokenizer.shapeToken makes of a name
    private static final String NAME_SHAPE = "ID";

    // A parameter whose name holds this names a file
    private static final String FILE_WORD = "file";

    // The position of the token after a given one
    private static final int NEXT = 1;

    // The number of tokens in an annotation such as @Override
    private static final int ANNOTATION_LENGTH = 2;

    // The fewest words a parameter has: its type and its name
    private static final int MIN_PARAMETER_WORDS = 2;

    // Returned when no matching token is found
    private static final int NOT_FOUND = -1;

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
        if (bodyStart == NOT_FOUND) {
            return methods;
        }

        List<String> header = new ArrayList<>();
        int index = bodyStart + NEXT;
        while (index < tokens.size()) {
            String text = tokens.get(index);
            if (text.equals(CLOSE_BRACE)) {
                return methods;
            } else if (text.equals(OPEN_BRACE)) {
                index = skipBlock(tokens, index);
                header.clear();
            } else if (text.equals(STATEMENT_END)) {
                index = index + NEXT;
                header.clear();
            } else if (text.equals(OPEN_PAREN)) {
                index = readBrackets(tokens, index, className, header,
                        starterTokens, methods);
            } else {
                header.add(text);
                index = index + NEXT;
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
        int close = findClosing(tokens, openParen, OPEN_PAREN, CLOSE_PAREN);
        if (close == NOT_FOUND) {
            return tokens.size();
        }
        if (endsWithAnnotation(header)) {
            // The arguments of an annotation such as
            // @SuppressWarnings("unchecked"): the method comes after
            return close + NEXT;
        }

        int firstParameter = openParen + NEXT;
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
        for (int index = 0; index + NEXT < tokens.size(); index++) {
            boolean classWord = tokens.get(index).equals(CLASS_WORD);
            int nameIndex = index + NEXT;
            boolean name = tokens.get(nameIndex).equals(className);
            if (classWord && name) {
                return findFrom(tokens, OPEN_BRACE, nameIndex);
            }
        }
        return NOT_FOUND;
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
        int nameIndex = words.size() - NEXT;
        String name = words.get(nameIndex);
        String returnType = TextUtils.join("", words.subList(modifierCount,
                nameIndex));
        boolean isName = JavaTokenizer.shapeToken(name).equals(NAME_SHAPE);
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
                index = index + NEXT;
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
        return contains(MODIFIERS, words.get(position));
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
            int nameIndex = words.size() - NEXT;
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
            if (token.equals(OPEN_ANGLE)) {
                depth++;
            } else if (token.equals(CLOSE_ANGLE)) {
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
        if (contains(SIMPLE_TYPES, type)) {
            return true;
        }
        if (type.endsWith(ARRAY_END)) {
            int elementEnd = type.length() - ARRAY_END.length();
            return contains(SIMPLE_TYPES, type.substring(0, elementEnd));
        }
        for (String listStart : LIST_STARTS) {
            boolean isList = type.startsWith(listStart)
                    && type.endsWith(LIST_END);
            if (isList) {
                int elementEnd = type.length() - LIST_END.length();
                String element = type.substring(listStart.length(),
                        elementEnd);
                boolean boxed = contains(BOXED_TYPES, element);
                return boxed || element.equals(STRING_TYPE);
            }
        }
        return false;
    }

    // Says whether a result type prints the same way every time: the
    // types a parameter may have and the boxed numbers. void results and
    // objects of the assessment's own classes are left out.
    private boolean isSupportedResult(String type) {
        if (type.equals(VOID_TYPE)) {
            return false;
        }
        return contains(BOXED_TYPES, type) || isSupportedParameter(type);
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
        int index = closeParen + NEXT;
        while (index < tokens.size()) {
            String text = tokens.get(index);
            if (text.equals(OPEN_BRACE)) {
                return skipBlock(tokens, index);
            }
            if (text.equals(STATEMENT_END)) {
                return index + NEXT;
            }
            index = index + NEXT;
        }
        return index;
    }

    // Returns the index after the "}" closing the block opened at an
    // index.
    private int skipBlock(List<String> tokens, int openBrace) {
        int close = findClosing(tokens, openBrace, OPEN_BRACE, CLOSE_BRACE);
        if (close == NOT_FOUND) {
            return tokens.size();
        }
        return close + NEXT;
    }

    // Returns the index of the token closing the bracket at an index, or
    // NOT_FOUND.
    private int findClosing(List<String> tokens, int openIndex, String open,
            String close) {
        int depth = 0;
        for (int index = openIndex; index < tokens.size(); index++) {
            String text = tokens.get(index);
            if (text.equals(open)) {
                depth++;
            } else if (text.equals(close)) {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return NOT_FOUND;
    }

    // Returns the index of the first token with a text from an index on,
    // or NOT_FOUND.
    private int findFrom(List<String> tokens, String text, int from) {
        for (int index = from; index < tokens.size(); index++) {
            if (tokens.get(index).equals(text)) {
                return index;
            }
        }
        return NOT_FOUND;
    }

    // Says whether a list holds a text.
    private boolean contains(String[] values, String text) {
        for (String value : values) {
            if (value.equals(text)) {
                return true;
            }
        }
        return false;
    }
}
