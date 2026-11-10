/*
 * File: JavaTypes.java
 *
 * Purpose: The parameter types random inputs can be made for, and how
 * their names are read: the simple types, the boxed types a list holds,
 * and the element type of an array such as int[] or a list such as
 * ArrayList<Integer>.
 *
 * Where it fits: SignatureReader uses it to tell which methods can be
 * called with made-up inputs, and InputMaker to make a value of each
 * of those types.
 */
package gradezzy.modelanswer;

/**
 * Reads the names of the types random inputs can be made for.
 *
 * <p>Tier: COULD. Owner: Jing Yuan.
 */
final class JavaTypes {

    // The simple types
    static final String INT_TYPE = "int";
    static final String LONG_TYPE = "long";
    static final String DOUBLE_TYPE = "double";
    static final String BOOLEAN_TYPE = "boolean";
    static final String CHAR_TYPE = "char";
    static final String STRING_TYPE = "String";

    // The end of an array type, such as int[]
    static final String ARRAY_END = "[]";

    // The simple types, and at the same place the boxed type each is
    // held in a list as
    private static final String[] SIMPLE_TYPES = {
        INT_TYPE, LONG_TYPE, DOUBLE_TYPE, BOOLEAN_TYPE, CHAR_TYPE,
        STRING_TYPE
    };
    private static final String[] BOXED_TYPES = {
        "Integer", "Long", "Double", "Boolean", "Character", STRING_TYPE
    };

    // List types, written up to the element type, such as ArrayList<
    private static final String[] LIST_STARTS = {"ArrayList<", "List<"};

    // The end of a list type
    private static final String LIST_END = ">";

    // Only static methods; never created
    private JavaTypes() {
    }

    // Says whether a type is one of the simple types, such as int.
    static boolean isSimple(String type) {
        return TokenSearch.contains(SIMPLE_TYPES, type);
    }

    // Says whether a list may hold a type: a boxed type such as Integer,
    // or String.
    static boolean isListElement(String type) {
        return TokenSearch.contains(BOXED_TYPES, type);
    }

    // Returns the element type of an array type, such as int for int[],
    // or null for any other type.
    static String findArrayElement(String type) {
        if (!type.endsWith(ARRAY_END)) {
            return null;
        }
        int elementEnd = type.length() - ARRAY_END.length();
        return type.substring(0, elementEnd);
    }

    // Returns the element type of a list type, such as Integer for
    // ArrayList<Integer>, or null for any other type.
    static String findListElement(String type) {
        for (String listStart : LIST_STARTS) {
            boolean isList = type.startsWith(listStart)
                    && type.endsWith(LIST_END);
            if (isList) {
                int elementEnd = type.length() - LIST_END.length();
                return type.substring(listStart.length(), elementEnd);
            }
        }
        return null;
    }

    // Returns the simple type a list's boxed element type holds, such as
    // int for Integer, or the type itself when it is not boxed.
    static String simpleTypeOf(String boxedType) {
        int index = TokenSearch.indexOf(BOXED_TYPES, boxedType);
        if (index == TokenSearch.NOT_FOUND) {
            return boxedType;
        }
        return SIMPLE_TYPES[index];
    }
}
