"""Builds the 42-student end-to-end test set for GradeZZY (section G4).

Usage, from the GradeZZY folder:
    python3 -I samples/e2e/make_e2e.py .
    python3 -I samples/e2e/make_e2e.py . --reference DIR

Writes samples/e2e/{upload-1-main, upload-2-late, roster-G4.csv,
roster-G4-excel.csv, bad-rosters/}. The testers are not copied: grade
with the configured samples/Tester-Files ("Use Configured").

--reference DIR also writes every gradable zip as a careful human marker
would see it (no wrapper problems, right file names, plain UTF-8), one
folder per zip, so the expected marks in README.md can be checked by
running the provided testers by hand.
"""
import os
import re
import shutil
import sys
import zipfile

ROOT = os.path.abspath(sys.argv[1])
SAMPLES = os.path.join(ROOT, "samples")
STARTER = os.path.join(SAMPLES, "RenameToYourUsername")
OUT = os.path.join(SAMPLES, "e2e")
MAIN = os.path.join(OUT, "upload-1-main")
LATE = os.path.join(OUT, "upload-2-late")
BAD_ROSTERS = os.path.join(OUT, "bad-rosters")
BULK_WORK = os.path.join(OUT, ".bulk-work")
PREFIX = "2024-2025-"
DOMAIN = "@smu.edu.sg"
BULK_NAME = "eLearn-G4-Lab-Test-download.zip"
REFERENCE = None
if "--reference" in sys.argv:
    REFERENCE = os.path.abspath(sys.argv[sys.argv.index("--reference") + 1])

def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def starter(rel):
    return read(os.path.join(STARTER, rel))


def replace_method(source, name, new_method):
    """Replaces the whole static method `name` (signature to closing brace)."""
    match = re.search(r"\n([ \t]*)public static [^\n]*\b" + name + r"\(",
                      source)
    if not match:
        raise ValueError("no method " + name)
    start = match.start() + 1
    depth = 0
    index = source.index("{", start)
    while True:
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                break
        index += 1
    return source[:start] + new_method.strip("\n") + source[index + 1:]


def header(source, name, email):
    source = source.replace(" * Name:\n", " * Name: " + name + "\n", 1)
    return source.replace(" * Email ID:\n", " * Email ID: " + email + "\n", 1)


# ---------------------------------------------------------------- answers

Q1A_GOOD = """
    public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
        ArrayList<String> result = new ArrayList<>();
        for (String word : inputs) {
            String lower = word.toLowerCase();
            boolean isogram = true;
            for (int i = 0; i < lower.length() && isogram; i++) {
                if (lower.indexOf(lower.charAt(i), i + 1) != -1) {
                    isogram = false;
                }
            }
            if (isogram) {
                result.add(word);
            }
        }
        return result;
    }
"""

# Same logic, different shape (a set), so honest students do not look alike.
Q1A_SET = """
    public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
        ArrayList<String> answer = new ArrayList<String>();
        for (int i = 0; i < inputs.size(); i++) {
            String w = inputs.get(i);
            Set<Character> seen = new HashSet<Character>();
            boolean ok = true;
            for (char c : w.toLowerCase().toCharArray()) {
                if (!seen.add(c)) {
                    ok = false;
                }
            }
            if (ok) {
                answer.add(w);
            }
        }
        return answer;
    }
"""

# Hard-coded to the examples in the starter's main method.
Q1A_HARDCODED = """
    public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
        ArrayList<String> result = new ArrayList<>();
        if (inputs.contains("tiger")) {
            result.add("cat");
            result.add("dog");
            result.add("tiger");
        } else if (inputs.contains("evan")) {
            result.add("ben");
            result.add("cindy");
            result.add("evan");
        } else if (inputs.contains("Evelyne")) {
            result.add("Ben");
            result.add("Cindy");
        } else if (inputs.contains("alice")) {
            result.add("alice");
        }
        return result;
    }
"""

# Leaves debugging prints in, one of which starts with "Passed".
Q1A_DEBUG = """
    public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
        ArrayList<String> result = new ArrayList<>();
        for (String word : inputs) {
            String lower = word.toLowerCase();
            boolean isogram = true;
            // a repeated letter is always next to itself, right?
            for (int i = 0; i < lower.length() - 1; i++) {
                if (lower.charAt(i) == lower.charAt(i + 1)) {
                    isogram = false;
                }
            }
            if (isogram) {
                System.out.println("Passed check: " + word);
                result.add(word);
            } else {
                System.out.println("DEBUG rejected " + word);
            }
        }
        return result;
    }
"""

Q1B_GOOD = """
    public static int getSumOfEvenIntegers(ArrayList<Object> inputs) {
        int sum = 0;
        for (Object item : inputs) {
            if (item instanceof Integer) {
                int value = (Integer) item;
                if (value % 2 == 0) {
                    sum += value;
                }
            }
        }
        return sum;
    }
"""

# Treats any Number as an integer, so 4.0 is counted.
Q1B_NUMBER_BUG = """
    public static int getSumOfEvenIntegers(ArrayList<Object> inputs) {
        int total = 0;
        for (Object o : inputs) {
            if (o instanceof Number) {
                int n = ((Number) o).intValue();
                if (n % 2 == 0) {
                    total += n;
                }
            }
        }
        return total;
    }
"""

# Missing semicolon: does not compile.
Q1B_SYNTAX = """
    public static int getSumOfEvenIntegers(ArrayList<Object> inputs) {
        int sum = 0;
        for (Object item : inputs) {
            if (item instanceof Integer) {
                int value = (Integer) item
                if (value % 2 == 0) {
                    sum += value;
                }
            }
        }
        return sum;
    }
"""

Q2A_GOOD = """
    public static double getAverageAge(String filename, String surname) {
        double total = 0;
        int count = 0;
        try (Scanner sc = new Scanner(new File(filename))) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                int dash = line.lastIndexOf('-');
                String[] names = line.substring(0, dash).split(" ");
                int age = Integer.parseInt(line.substring(dash + 1).trim());
                if (names[0].equalsIgnoreCase(surname)
                        || names[names.length - 1].equalsIgnoreCase(surname)) {
                    total += age;
                    count++;
                }
            }
        } catch (FileNotFoundException e) {
            return -1.0;
        }
        if (count == 0) {
            return 0.0;
        }
        return total / count;
    }
"""

# Compares surnames with equals, so "Wong" does not match "WONG".
Q2A_CASE_BUG = """
    public static double getAverageAge(String filename, String surname) {
        double total = 0;
        int count = 0;
        try {
            Scanner sc = new Scanner(new File(filename));
            while (sc.hasNextLine()) {
                String[] parts = sc.nextLine().split("-");
                String[] names = parts[0].split(" ");
                if (names[0].equals(surname)
                        || names[names.length - 1].equals(surname)) {
                    total += Integer.parseInt(parts[1]);
                    count++;
                }
            }
            sc.close();
        } catch (FileNotFoundException e) {
            return -1.0;
        }
        if (count == 0) {
            return 0.0;
        }
        return total / count;
    }
"""

# Forgets i++ in the inner loop: hangs on any file that exists.
Q2A_INFINITE = """
    public static double getAverageAge(String filename, String surname) {
        double total = 0;
        int count = 0;
        try {
            Scanner sc = new Scanner(new File(filename));
            while (sc.hasNextLine()) {
                String[] parts = sc.nextLine().split("-");
                String[] names = parts[0].split(" ");
                int i = 0;
                while (i < names.length) {
                    if (names[i].equalsIgnoreCase(surname)
                            && (i == 0 || i == names.length - 1)) {
                        total += Integer.parseInt(parts[1]);
                        count++;
                    }
                }
            }
            sc.close();
        } catch (FileNotFoundException e) {
            return -1.0;
        }
        if (count == 0) {
            return 0.0;
        }
        return total / count;
    }
"""

Q2B_GOOD = """
    public static String getTopStudent(String filename, String courseName) {
        String best = null;
        double bestGpa = -1;
        try (Scanner sc = new Scanner(new File(filename))) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] nameAndCourses = line.split(",");
                for (String course : nameAndCourses[1].split("-")) {
                    String[] codeAndGpa = course.split("#");
                    double gpa = Double.parseDouble(codeAndGpa[1]);
                    if (codeAndGpa[0].equals(courseName) && gpa > bestGpa) {
                        best = nameAndCourses[0];
                        bestGpa = gpa;
                    }
                }
            }
        } catch (FileNotFoundException e) {
            throw new DataException();
        }
        if (best == null) {
            throw new DataException();
        }
        return best + "-" + bestGpa;
    }
"""

# Uses >= so a tie goes to the last student, not the first.
Q2B_TIE_BUG = """
    public static String getTopStudent(String filename, String courseName) {
        String topName = "";
        double topGpa = -1;
        try {
            Scanner scanner = new Scanner(new File(filename));
            while (scanner.hasNextLine()) {
                // name,COURSE#gpa-COURSE#gpa
                String[] tokens = scanner.nextLine().split(",");
                String[] courses = tokens[1].split("-");
                for (int k = 0; k < courses.length; k++) {
                    String[] pair = courses[k].split("#");
                    double gpa = Double.parseDouble(pair[1]);
                    if (pair[0].equals(courseName) && gpa >= topGpa) {
                        topGpa = gpa;
                        topName = tokens[0];
                    }
                }
            }
            scanner.close();
        } catch (FileNotFoundException e) {
            throw new DataException();
        }
        if (topGpa < 0) {
            throw new DataException();
        }
        return topName + "-" + topGpa;
    }
"""

# Returns "" instead of throwing DataException.
Q2B_NO_EXCEPTION = """
    public static String getTopStudent(String filename, String courseName) {
        String best = "";
        double bestGpa = -1;
        try {
            Scanner sc = new Scanner(new File(filename));
            while (sc.hasNextLine()) {
                String[] parts = sc.nextLine().split(",");
                String[] courses = parts[1].split("-");
                for (String c : courses) {
                    String code = c.split("#")[0];
                    double gpa = Double.parseDouble(c.split("#")[1]);
                    if (code.equals(courseName) && gpa > bestGpa) {
                        best = parts[0] + "-" + gpa;
                        bestGpa = gpa;
                    }
                }
            }
            sc.close();
        } catch (FileNotFoundException e) {
            System.out.println("File not found");
        }
        return best;
    }
"""

# Calls System.exit when the file is missing.
Q2B_EXIT = """
    public static String getTopStudent(String filename, String courseName) {
        String best = null;
        double bestGpa = -1;
        Scanner sc = null;
        try {
            sc = new Scanner(new File(filename));
        } catch (FileNotFoundException e) {
            System.out.println("Cannot open " + filename);
            System.exit(1);
        }
        while (sc.hasNextLine()) {
            String[] nameAndCourses = sc.nextLine().split(",");
            for (String course : nameAndCourses[1].split("-")) {
                String[] codeAndGpa = course.split("#");
                double gpa = Double.parseDouble(codeAndGpa[1]);
                if (codeAndGpa[0].equals(courseName) && gpa > bestGpa) {
                    best = nameAndCourses[0];
                    bestGpa = gpa;
                }
            }
        }
        sc.close();
        if (best == null) {
            throw new DataException();
        }
        return best + "-" + bestGpa;
    }
"""

Q3_GOOD = """
    public static List<Shape> sortShapes(List<Shape> shapeList) {
        List<Shape> result = new ArrayList<>();
        for (Shape shape : shapeList) {
            if (shape.getArea() <= 1000) {
                result.add(shape);
            }
        }
        Collections.sort(result, new ShapeComparator());
        return result;
    }
"""

COMPARATOR_GOOD = """import java.util.*;

public class ShapeComparator implements Comparator<Shape> {

    public int compare(Shape first, Shape second) {
        int byArea = Double.compare(second.getArea(), first.getArea());
        if (byArea != 0) {
            return byArea;
        }
        return Double.compare(second.getPerimeter(), first.getPerimeter());
    }
}
"""

# Only compares area, so ties keep the input order.
COMPARATOR_AREA_ONLY = """import java.util.*;

public class ShapeComparator implements Comparator<Shape> {

    public int compare(Shape s1, Shape s2) {
        if (s1.getArea() > s2.getArea()) {
            return -1;
        } else if (s1.getArea() < s2.getArea()) {
            return 1;
        }
        return 0;
    }
}
"""


def comparator(body, name, email):
    head = "/*\n * Name: " + name + "\n * Email ID: " + email + "\n */\n\n"
    if body is None:
        return starter("Q3/ShapeComparator.java")
    return head + body




# ------------------------------------------------- answers new in this set

# Starts the outer loop at 1, so a repeat of the first letter is missed.
Q1A_SKIP_FIRST = """
    public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
        ArrayList<String> result = new ArrayList<>();
        for (String word : inputs) {
            String lower = word.toLowerCase();
            boolean unique = true;
            for (int i = 1; i < lower.length(); i++) {
                for (int j = i + 1; j < lower.length(); j++) {
                    if (lower.charAt(i) == lower.charAt(j)) {
                        unique = false;
                    }
                }
            }
            if (unique) {
                result.add(word);
            }
        }
        return result;
    }
"""

# Tries to fool the grader: prints the tester's pass marker and returns
# nothing useful.
Q1A_FAKE_PASS = """
    public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
        System.out.println("Passed");
        System.out.println("Passed");
        System.out.println("Passed");
        return new ArrayList<String>();
    }
"""

# Tries to fool the grader: prints a full score as the last line and
# stops the JVM before the tester prints the real one.
Q1B_FAKE_SCORE_EXIT = """
    public static int getSumOfEvenIntegers(ArrayList<Object> inputs) {
        System.out.println("Passed");
        System.out.println(3.0);
        System.exit(0);
        return 0;
    }
"""

# Forgets i++ and prints a debug line every time round: endless output.
Q1B_ENDLESS_OUTPUT = """
    public static int getSumOfEvenIntegers(ArrayList<Object> inputs) {
        int sum = 0;
        int i = 0;
        while (i < inputs.size()) {
            Object item = inputs.get(i);
            System.out.println("checking " + item);
            if (item instanceof Integer && (Integer) item % 2 == 0) {
                sum += (Integer) item;
            }
        }
        return sum;
    }
"""

# Returns 0 instead of -1 when the file does not exist.
Q2A_MISSING_FILE_ZERO = """
    public static double getAverageAge(String filename, String surname) {
        double sum = 0;
        int people = 0;
        try {
            Scanner in = new Scanner(new File(filename));
            while (in.hasNextLine()) {
                String row = in.nextLine();
                int dash = row.lastIndexOf('-');
                String[] names = row.substring(0, dash).trim().split(" ");
                if (names[0].equalsIgnoreCase(surname)
                        || names[names.length - 1].equalsIgnoreCase(surname)) {
                    sum += Integer.parseInt(row.substring(dash + 1).trim());
                    people++;
                }
            }
            in.close();
        } catch (FileNotFoundException e) {
            return 0;
        }
        if (people == 0) {
            return 0;
        }
        return sum / people;
    }
"""

# Keeps shapes whose area is below 1000 only, and sorts smallest first.
Q3_ASCENDING = """
    public static List<Shape> sortShapes(List<Shape> shapeList) {
        List<Shape> kept = new ArrayList<>();
        for (Shape s : shapeList) {
            if (s.getArea() < 1000) {
                kept.add(s);
            }
        }
        Collections.sort(kept, new ShapeComparator());
        Collections.reverse(kept);
        return kept;
    }
"""

# A scratch file left in Q3. It is not part of the answer and does not
# compile (missing semicolon).
Q3_SCRATCH_FILE = """public class Test {
    public static void main(String[] args) {
        Shape c = new Circle(2.0)
        System.out.println(c.getArea());
    }
}
"""
# ------------------------------------------------- per-student variety
# Real students who all get a question right still write it differently.
# Each correct answer has several implementations; $X markers are local
# names that change per student, so honest work is not near-identical.

Q1A_VARIANTS = [Q1A_GOOD.replace("result", "$R").replace("word", "$W"),
                Q1A_SET.replace("answer", "$R").replace(" w ", " $W ")
                .replace("(w)", "($W)").replace("w.", "$W."),
                """
    public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
        ArrayList<String> $R = new ArrayList<>();
        for (int idx = 0; idx < inputs.size(); idx++) {
            String $W = inputs.get(idx);
            int[] counts = new int[26];
            boolean repeated = false;
            for (char ch : $W.toLowerCase().toCharArray()) {
                if (Character.isLetter(ch)) {
                    counts[ch - 'a']++;
                    if (counts[ch - 'a'] > 1) {
                        repeated = true;
                    }
                }
            }
            if (!repeated) {
                $R.add($W);
            }
        }
        return $R;
    }
""", """
    public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
        ArrayList<String> $R = new ArrayList<>();
        for (String $W : inputs) {
            String letters = "";
            boolean unique = true;
            for (int k = 0; k < $W.length(); k++) {
                String c = $W.substring(k, k + 1).toUpperCase();
                if (letters.contains(c)) {
                    unique = false;
                    break;
                }
                letters += c;
            }
            if (unique) {
                $R.add($W);
            }
        }
        return $R;
    }
"""]

Q1B_VARIANTS = [Q1B_GOOD.replace("sum", "$T").replace("item", "$W"), """
    public static int getSumOfEvenIntegers(ArrayList<Object> inputs) {
        int $T = 0;
        int i = 0;
        while (i < inputs.size()) {
            Object $W = inputs.get(i);
            if ($W instanceof Integer && ((Integer) $W) % 2 == 0) {
                $T = $T + (Integer) $W;
            }
            i++;
        }
        return $T;
    }
""", """
    public static int getSumOfEvenIntegers(ArrayList<Object> inputs) {
        int $T = 0;
        for (Object $W : inputs) {
            if ($W.getClass() == Integer.class) {
                Integer value = (Integer) $W;
                if (value.intValue() % 2 == 0) {
                    $T += value.intValue();
                }
            }
        }
        return $T;
    }
"""]

Q2A_VARIANTS = [Q2A_GOOD.replace("total", "$T").replace("count", "$C")
                .replace("line", "$L"), """
    public static double getAverageAge(String filename, String surname) {
        int $T = 0;
        int $C = 0;
        Scanner $S = null;
        try {
            $S = new Scanner(new File(filename));
        } catch (FileNotFoundException e) {
            return -1;
        }
        String wanted = surname.toUpperCase();
        while ($S.hasNextLine()) {
            String $L = $S.nextLine();
            String[] nameAge = $L.split("-");
            String[] words = nameAge[0].trim().toUpperCase().split(" ");
            if (words[0].equals(wanted)
                    || words[words.length - 1].equals(wanted)) {
                $T += Integer.parseInt(nameAge[1].trim());
                $C++;
            }
        }
        $S.close();
        if ($C == 0) {
            return 0;
        }
        return (double) $T / $C;
    }
""", """
    public static double getAverageAge(String filename, String surname) {
        ArrayList<Integer> ages = new ArrayList<>();
        try (BufferedReader $S = new BufferedReader(new FileReader(filename))) {
            String $L = $S.readLine();
            while ($L != null) {
                String person = $L.substring(0, $L.indexOf('-'));
                String first = person.substring(0, person.indexOf(' '));
                String last = person.substring(person.lastIndexOf(' ') + 1);
                if (first.equalsIgnoreCase(surname)
                        || last.equalsIgnoreCase(surname)) {
                    ages.add(Integer.valueOf(
                            $L.substring($L.indexOf('-') + 1).trim()));
                }
                $L = $S.readLine();
            }
        } catch (IOException e) {
            return -1.0;
        }
        if (ages.isEmpty()) {
            return 0.0;
        }
        double $T = 0;
        for (int age : ages) {
            $T += age;
        }
        return $T / ages.size();
    }
"""]

Q2B_VARIANTS = [Q2B_GOOD.replace("best", "$B").replace("line", "$L"), """
    public static String getTopStudent(String filename, String courseName) {
        String $B = "";
        double highest = -1.0;
        boolean found = false;
        Scanner $S;
        try {
            $S = new Scanner(new File(filename));
        } catch (FileNotFoundException ex) {
            throw new DataException();
        }
        while ($S.hasNextLine()) {
            String $L = $S.nextLine();
            int comma = $L.indexOf(',');
            String studentName = $L.substring(0, comma);
            String[] records = $L.substring(comma + 1).split("-");
            for (String rec : records) {
                int hash = rec.indexOf('#');
                if (rec.substring(0, hash).equals(courseName)) {
                    double gpa = Double.parseDouble(rec.substring(hash + 1));
                    if (!found || gpa > highest) {
                        highest = gpa;
                        $B = studentName;
                        found = true;
                    }
                }
            }
        }
        $S.close();
        if (!found) {
            throw new DataException();
        }
        return $B + "-" + highest;
    }
""", """
    public static String getTopStudent(String filename, String courseName) {
        ArrayList<String> names = new ArrayList<>();
        ArrayList<Double> gpas = new ArrayList<>();
        try (BufferedReader $S = new BufferedReader(new FileReader(filename))) {
            String $L;
            while (($L = $S.readLine()) != null) {
                String[] cols = $L.split(",");
                for (String part : cols[1].split("-")) {
                    String[] codeGpa = part.split("#");
                    if (codeGpa[0].equals(courseName)) {
                        names.add(cols[0]);
                        gpas.add(Double.valueOf(codeGpa[1]));
                    }
                }
            }
        } catch (IOException e) {
            throw new DataException();
        }
        if (names.isEmpty()) {
            throw new DataException();
        }
        int $B = 0;
        for (int j = 1; j < gpas.size(); j++) {
            if (gpas.get(j) > gpas.get($B)) {
                $B = j;
            }
        }
        return names.get($B) + "-" + gpas.get($B);
    }
"""]

Q3_VARIANTS = [Q3_GOOD.replace("result", "$R").replace("shape ", "$W ")
               .replace("shape.", "$W.").replace("(shape)", "($W)"), """
    public static List<Shape> sortShapes(List<Shape> shapeList) {
        ArrayList<Shape> $R = new ArrayList<Shape>(shapeList);
        Iterator<Shape> $W = $R.iterator();
        while ($W.hasNext()) {
            if ($W.next().getArea() > 1000) {
                $W.remove();
            }
        }
        $R.sort(new ShapeComparator());
        return $R;
    }
""", """
    public static List<Shape> sortShapes(List<Shape> shapeList) {
        Comparator<Shape> order = new ShapeComparator();
        List<Shape> $R = new ArrayList<>();
        for (int n = 0; n < shapeList.size(); n++) {
            Shape $W = shapeList.get(n);
            if (!($W.getArea() > 1000)) {
                $R.add($W);
            }
        }
        Collections.sort($R, order);
        return $R;
    }
"""]

CMP_VARIANTS = [COMPARATOR_GOOD.replace("first", "$A").replace("second", "$W"),
                """import java.util.*;

public class ShapeComparator implements Comparator<Shape> {

    @Override
    public int compare(Shape $A, Shape $W) {
        if ($A.getArea() > $W.getArea()) {
            return -1;
        }
        if ($A.getArea() < $W.getArea()) {
            return 1;
        }
        if ($A.getPerimeter() > $W.getPerimeter()) {
            return -1;
        }
        if ($A.getPerimeter() < $W.getPerimeter()) {
            return 1;
        }
        return 0;
    }
}
""", """import java.util.*;

public class ShapeComparator implements Comparator<Shape> {

    // bigger area first, then bigger perimeter first
    public int compare(Shape $A, Shape $W) {
        double diff = $W.getArea() - $A.getArea();
        if (diff == 0) {
            diff = $W.getPerimeter() - $A.getPerimeter();
        }
        if (diff > 0) {
            return 1;
        } else if (diff < 0) {
            return -1;
        }
        return 0;
    }
}
"""]

NAME_POOLS = {
    "$R": ["result", "isograms", "answer", "output", "list", "valid",
           "words", "res", "ans", "filtered", "keep", "out", "matches",
           "found", "collected", "accepted", "goodWords"],
    "$W": ["word", "w", "s", "str", "current", "item", "token", "entry",
           "candidate", "x", "elem", "value2", "obj", "thing", "cur", "e2",
           "el"],
    "$T": ["sum", "total", "acc", "runningTotal", "tally", "s1", "result2",
           "agg", "evenSum", "accum", "tot", "amount", "summ", "count2",
           "subtotal", "grand", "ttl"],
    "$C": ["count", "n", "num", "matches2", "people", "howMany", "cnt",
           "k2", "persons", "freq", "matched", "c", "found2", "hits",
           "total2", "size", "numPeople"],
    "$L": ["line", "row", "record", "data", "text", "ln", "currentLine",
           "raw", "input", "content", "str2", "lineRead", "rec2", "next",
           "l", "fileLine", "entryLine"],
    "$S": ["sc", "scanner", "reader", "in", "fileScanner", "br", "input2",
           "scan", "s2", "fs", "fileIn", "rd", "src", "inFile", "sc2",
           "inp", "readerIn"],
    "$B": ["best", "top", "topName", "winner", "bestStudent", "max",
           "leader", "bestName", "first2", "highestName", "champ", "maxName",
           "who", "topStu", "bestIdx", "pick", "chosen"],
    "$A": ["first", "a", "s1x", "left", "one", "shapeA", "x1", "lhs", "p",
           "o1", "sa", "m", "u", "f", "aa", "alpha", "shp1"],
}


def personalise(text, index):
    # Past the end of a pool the names repeat with a number added
    # (sum2, line3), so no two of the 44 students share local names.
    for marker in sorted(NAME_POOLS, key=len, reverse=True):
        pool = NAME_POOLS[marker]
        name = pool[index % len(pool)]
        if index >= len(pool):
            name += str(index // len(pool) + 1)
        text = text.replace(marker, name)
    return text




# ----------------------------------- bugs made from a personalised answer
# These start from a correct variant, so the $X names still change per
# student and honest students do not look copied.

def changed(text, old, new):
    if old not in text:
        raise ValueError("not found: " + old)
    return text.replace(old, new, 1)


# Adds whole ages in an int and divides two ints, so 27.5 would become
# 27.0. The testers' averages happen to be whole numbers, so no mark is
# lost: a bug the testers cannot see.
Q2A_INT_DIVISION = """
    public static double getAverageAge(String filename, String surname) {
        int $T = 0;
        int $C = 0;
        try {
            Scanner $S = new Scanner(new File(filename));
            while ($S.hasNextLine()) {
                String $L = $S.nextLine();
                String who = $L.substring(0, $L.indexOf("-")).trim();
                String[] bits = who.split(" ");
                boolean match = bits[0].equalsIgnoreCase(surname);
                if (bits[bits.length - 1].equalsIgnoreCase(surname)) {
                    match = true;
                }
                if (match) {
                    $T = $T + Integer.parseInt(
                            $L.substring($L.indexOf("-") + 1).trim());
                    $C = $C + 1;
                }
            }
            $S.close();
        } catch (FileNotFoundException e) {
            return -1.0;
        }
        if ($C == 0) {
            return 0.0;
        }
        return $T / $C;
    }
"""

# Correct, but left a "press Enter" pause in from testing at home.
Q2A_WAITS_FOR_ENTER = changed(
    Q2A_VARIANTS[1], "        int $T = 0;\n",
    "        Scanner keyboard = new Scanner(System.in);\n"
    "        System.out.print(\"Press Enter to read \" + filename + \": \");\n"
    "        keyboard.nextLine();\n"
    "        int $T = 0;\n")

# Prints the GPA with two decimals: "Jason LEE-4.00", not "Jason LEE-4.0".
Q2B_TWO_DECIMALS = changed(
    Q2B_VARIANTS[1], "return $B + \"-\" + highest;",
    "return $B + \"-\" + String.format(\"%.2f\", highest);")

# Correct, but starts a thread that never stops, so the JVM never exits
# after the tester prints its score.
Q3_RUNAWAY_THREAD = changed(
    Q3_VARIANTS[2], "        Collections.sort($R, order);\n",
    "        Collections.sort($R, order);\n"
    "        Thread logger = new Thread(new Runnable() {\n"
    "            public void run() {\n"
    "                while (true) {\n"
    "                    Thread.onSpinWait();\n"
    "                }\n"
    "            }\n"
    "        });\n"
    "        logger.start();\n")


def good_for(username, **changes):
    """The correct answers this student would write, with any bugs."""
    index = [row[0] for row in STUDENTS + STRANGERS].index(username)
    answers = {
        "q1a": Q1A_VARIANTS[index % len(Q1A_VARIANTS)],
        "q1b": Q1B_VARIANTS[(index + 1) % len(Q1B_VARIANTS)],
        "q2a": Q2A_VARIANTS[(index + 2) % len(Q2A_VARIANTS)],
        "q2b": Q2B_VARIANTS[index % len(Q2B_VARIANTS)],
        "q3": Q3_VARIANTS[(index + 1) % len(Q3_VARIANTS)],
        "cmp": CMP_VARIANTS[(index + 2) % len(CMP_VARIANTS)],
    }
    answers.update(changes)
    for key in answers:
        if answers[key] is not None:
            answers[key] = personalise(answers[key], index)
    return answers


# --------------------------------------------------------------- students

STUDENTS = [
    # username, org id, last name, first name; all on the G4 roster
    ("aaron.lee.2024", "01500101", "LEE", "AARON"),
    ("amelia.tan.2024", "01500102", "TAN", "AMELIA"),
    ("benjamin.koh.2024", "01500103", "KOH", "BENJAMIN"),
    ("chloe.ng.2024", "01500104", "NG", "CHLOE"),
    ("daniel.wong.2024", "01500105", "WONG", "DANIEL"),
    ("dinesh.kumar.2023", "01500106", "KUMAR", "DINESH"),
    ("emily.chua.2024", "01500107", "CHUA", "EMILY"),
    ("fatimah.ali.2024", "01500108", "ALI", "FATIMAH"),
    ("gabriel.ong.2024", "01500109", "ONG", "GABRIEL"),
    ("hannah.goh.2024", "01500110", "GOH", "HANNAH"),
    ("isaac.teo.2024", "01500111", "TEO", "ISAAC"),
    ("jiahui.lim.2024", "01500112", "LIM", "JIA HUI"),
    ("kaixuan.chen.2024", "01500113", "CHEN", "KAI XUAN"),
    ("lakshmi.raj.2024", "01500114", "RAJ", "LAKSHMI"),
    ("marcus.yeo.2023", "01500115", "YEO", "MARCUS"),
    ("natalie.sim.2024", "01500116", "SIM", "NATALIE"),
    ("owen.tan.2024", "01500117", "TAN", "OWEN"),
    ("priya.menon.2024", "01500118", "MENON", "PRIYA"),
    ("qihao.zhang.2024", "01500119", "ZHANG", "QI HAO"),
    ("rachel.lim.2024", "01500120", "LIM", "RACHEL"),
    ("samuel.chan.2024", "01500121", "CHAN", "SAMUEL"),
    ("shuting.wee.2024", "01500122", "WEE", "SHU TING"),
    ("zoe.lefevre.2024", "01500123", "LEFEVRE", "ZOE"),
    ("timothy.ho.2024", "01500124", "HO", "TIMOTHY"),
    ("umar.farooq.2024", "01500125", "FAROOQ", "UMAR"),
    ("valerie.chong.2024", "01500126", "CHONG", "VALERIE"),
    ("wenjie.liu.2024", "01500127", "LIU", "WEN JIE"),
    ("xavier.pang.2024", "01500128", "PANG", "XAVIER"),
    ("yasmin.noor.2024", "01500129", "NOOR", "YASMIN"),
    ("yuki.tanaka.2024", "01500130", "TANAKA", "YUKI"),
    ("zhiwei.koh.2024", "01500131", "KOH", "ZHI WEI"),
    ("bella.quek.2024", "01500132", "QUEK", "BELLA"),
    ("cheryl.foo.2024", "01500133", "FOO", "CHERYL"),
    ("darius.tan.2024", "01500134", "TAN", "DARIUS"),
    ("weiming.lau.2024", "01500135", "LAU", "WEI MING, ALVIN"),
    ("junwei.tan.2024", "01500136", "TAN", "JUN WEI"),
    ("kelvin.ong.2024", "01500137", "ONG", "KELVIN"),
    ("nicole.yap.2024", "01500138", "YAP", "NICOLE"),
    ("grace.lee.2024", "01500139", "LEE", "GRACE"),
    ("harith.rahman.2024", "01500140", "RAHMAN", "HARITH"),
    ("meiling.ho.2024", "01500141", "HO", "MEI LING"),
    ("peter.chia.2024", "01500142", "CHIA", "PETER"),
]

# Uploads from people who are not on the G4 roster
STRANGERS = [
    ("peter.chia.2042", "", "CHIA", "PETER"),
    ("visitor.g2.2024", "", "LOW", "VICTOR"),
]

# Display names written in a header that differ from the roster
DISPLAY_NAMES = {"zoe.lefevre.2024": "Zoé Lefèvre"}


def student(username):
    for user, org, last, first in STUDENTS + STRANGERS:
        if user == username:
            display = DISPLAY_NAMES.get(user, first.title() + " "
                                        + last.title())
            return {"username": user, "org": org, "display": display,
                    "email": user + DOMAIN}
    raise KeyError(username)


# ------------------------------------------------------------ zip writing

def build_files(person, answers, blank_header=False, package=None):
    """Returns {relative path: text} for the student's six source files."""
    name = person["display"]
    email = person["email"]
    files = {}
    plan = [
        ("Q1/Q1a.java", "getIsogramWords", answers.get("q1a")),
        ("Q1/Q1b.java", "getSumOfEvenIntegers", answers.get("q1b")),
        ("Q2/Q2a.java", "getAverageAge", answers.get("q2a")),
        ("Q2/Q2b.java", "getTopStudent", answers.get("q2b")),
        ("Q3/Q3.java", "sortShapes", answers.get("q3")),
    ]
    for rel, method, body in plan:
        text = starter(rel)
        if body is not None:
            text = replace_method(text, method, body)
        if not blank_header:
            text = header(text, name, email)
        if package and rel.startswith("Q1/"):
            text = text.replace("\nimport java.util.*;",
                                "\npackage Q1;\n\nimport java.util.*;", 1)
        files[rel] = text
    if blank_header and answers.get("cmp") is not None:
        files["Q3/ShapeComparator.java"] = (
            "/*\n * Name:\n * Email ID:\n */\n\n" + answers["cmp"])
    else:
        files["Q3/ShapeComparator.java"] = comparator(
            answers.get("cmp"), name, email)
    return files


def provided_files():
    """Binary and data files every student received in the starter."""
    result = {}
    for rel in ["Q2/DataException.class", "Q2/persons.txt",
                "Q2/students.txt", "Q3/Shape.class", "Q3/Circle.class",
                "Q3/Rectangle.class"]:
        with open(os.path.join(STARTER, rel), "rb") as handle:
            result[rel] = handle.read()
    return result


def folders_of(entries):
    folders = set()
    for entry in entries:
        parts = entry.split("/")[:-1]
        for depth in range(1, len(parts) + 1):
            folders.add("/".join(parts[:depth]) + "/")
    return folders


def write_zip(path, entries, mac_junk=False):
    """entries: {path in zip: str or bytes}. Adds folder entries too."""
    folders = folders_of(entries)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        for folder in sorted(folders):
            if not folder.startswith(".."):
                archive.writestr(folder, b"")
        for entry in sorted(entries):
            data = entries[entry]
            if isinstance(data, str):
                data = data.encode("utf-8")
            archive.writestr(entry, data)
            if mac_junk:
                junk = "__MACOSX/" + os.path.dirname(entry) + "/._" + \
                    os.path.basename(entry)
                archive.writestr(junk.replace("//", "/"), b"\x00\x05\x16\x07")
        if mac_junk:
            for folder in sorted(folders):
                archive.writestr(folder + ".DS_Store", b"\x00\x00\x00\x01Bud1")
    return path


def write_folder(path, entries):
    for entry, data in entries.items():
        target = os.path.join(path, *entry.split("/"))
        os.makedirs(os.path.dirname(target), exist_ok=True)
        if isinstance(data, str):
            data = data.encode("utf-8")
        with open(target, "wb") as handle:
            handle.write(data)


def write_reference(out_dir, zip_name, files):
    """The files a careful marker grades, in REFERENCE/<upload>/<zip>/."""
    if REFERENCE is None:
        return
    upload = os.path.basename(out_dir)
    if out_dir == BULK_WORK:
        upload = os.path.basename(MAIN) + "/" + BULK_NAME
    stem = zip_name[:-len(".zip")] if zip_name.endswith(".zip") else zip_name
    write_folder(os.path.join(REFERENCE, upload, stem), files)


def submission(out_dir, person, answers, wrapper="username", mac_junk=False,
               blank_header=False, package=None, drop=(), rename=None,
               stale_class=False, zip_name=None, crlf=False, bom=False,
               cp1252=False, header_of=None, extra=None, raw_entries=None,
               as_folder=False, gradable=True):
    """Writes one student's upload into out_dir and returns its path.

    header_of: another student whose name is in the headers (copied work).
    extra: more files inside the student's folder, {relative path: data}.
    raw_entries: zip entries written as they are, outside any wrapper.
    as_folder: an unzipped folder instead of a zip.
    gradable: False for uploads a marker cannot open (no reference).
    """
    named = header_of or person
    files = build_files(named, answers, blank_header, package)
    files.update(provided_files())
    for rel in drop:
        for key in list(files):
            if key == rel or key.startswith(rel + "/"):
                del files[key]
    if extra:
        files.update(extra)
    name = zip_name or (PREFIX + person["username"] + ".zip")
    if gradable:
        clean = {key: data for key, data in files.items()
                 if not key.endswith("Test.java")}
        write_reference(out_dir, name, clean)

    if crlf:
        for key in list(files):
            if key.endswith(".java"):
                files[key] = files[key].replace("\r\n", "\n").replace(
                    "\n", "\r\n")
    for key in list(files):
        if not key.endswith(".java"):
            continue
        if cp1252:
            files[key] = files[key].encode("cp1252")
        elif bom:
            files[key] = ("﻿" + files[key]).encode("utf-8")
    if rename:
        for old, new in rename.items():
            for key in list(files):
                if key == old or key.startswith(old + "/"):
                    files[new + key[len(old):]] = files.pop(key)
    if stale_class:
        # Leftovers from running the code in an IDE; they do not match the
        # source, so grading must recompile instead of trusting them.
        files["Q1/Q1a.class"] = b"\xca\xfe\xba\xbe\x00\x00\x00\x41stale"
        files["Q3/ShapeComparator.class"] = b"\xca\xfe\xba\xbe\x00stale"
    if wrapper == "username":
        top = person["username"] + "/"
    elif wrapper is None:
        top = ""
    else:
        top = wrapper + "/"
    entries = {top + rel: data for rel, data in files.items()}
    if raw_entries:
        entries.update(raw_entries)
    path = os.path.join(out_dir, name)
    if as_folder:
        write_folder(path, entries)
        return path
    return write_zip(path, entries, mac_junk)


def truncate(path):
    """Cuts a zip off half way, as an interrupted upload does."""
    with open(path, "rb") as handle:
        data = handle.read()
    with open(path, "wb") as handle:
        handle.write(data[: len(data) // 2])


# ---------------------------------------------------------------- rosters

HEADER = ("OrgDefinedId,Username,Last Name,First Name,Email,"
          "Calculated Final Grade Numerator,"
          "Calculated Final Grade Denominator,End-of-Line Indicator")


def csv_field(value):
    if "," in value or '"' in value:
        return '"' + value.replace('"', '""') + '"'
    return value


def roster_rows(end_marker=True):
    rows = []
    for user, org, last, first in STUDENTS:
        row = "#%s,#%s,%s,%s,%s,,20" % (org, user, csv_field(last),
                                         csv_field(first), user + DOMAIN)
        if end_marker:
            row += ",#"
        rows.append(row)
    return rows


def write_text(path, text, encoding="utf-8"):
    with open(path, "w", encoding=encoding, newline="") as handle:
        handle.write(text)


def write_rosters():
    # As exported by eLearn: CRLF, every value with its # in front.
    write_text(os.path.join(OUT, "roster-G4.csv"),
               "\r\n".join([HEADER] + roster_rows()) + "\r\n")
    # The same sheet opened and saved again in Excel: a byte order mark,
    # LF line endings and the empty last column dropped from each row.
    write_text(os.path.join(OUT, "roster-G4-excel.csv"),
               "\n".join([HEADER] + roster_rows(False)) + "\n",
               "utf-8-sig")
    os.makedirs(BAD_ROSTERS)
    # One student pasted in twice.
    rows = roster_rows()
    rows.insert(10, rows[0])
    write_text(os.path.join(BAD_ROSTERS, "duplicate-row.csv"),
               "\r\n".join([HEADER] + rows) + "\r\n")
    # A marks list someone else made, not an eLearn score sheet.
    lines = ["Student,Q1,Q2,Q3,Total"]
    for user, org, last, first in STUDENTS[:5]:
        lines.append("%s,,,," % user)
    write_text(os.path.join(BAD_ROSTERS, "not-a-score-sheet.csv"),
               "\r\n".join(lines) + "\r\n")


# ------------------------------------------------------------------- main

def build_main():
    """Upload 1: the section's folder of zips, with a bulk zip inside."""
    s = student
    # 1. Model answer, with IDE leftovers (.class files) in the folders.
    submission(MAIN, s("aaron.lee.2024"), good_for("aaron.lee.2024"),
               stale_class=True)
    # 2. Model answer. An older, buggy copy with the same file name is
    #    also inside the bulk zip (see build_bulk).
    submission(MAIN, s("amelia.tan.2024"), good_for("amelia.tan.2024"))
    # 3. Counts 4.0 as an even integer.
    submission(MAIN, s("benjamin.koh.2024"),
               good_for("benjamin.koh.2024", q1b=Q1B_NUMBER_BUG))
    # 4. Comparator ignores perimeter on ties.
    submission(MAIN, s("chloe.ng.2024"),
               good_for("chloe.ng.2024", cmp=COMPARATOR_AREA_ONLY))
    # 5. Q1a misses a repeated first letter; Q2b calls System.exit.
    submission(MAIN, s("daniel.wong.2024"),
               good_for("daniel.wong.2024", q1a=Q1A_SKIP_FIRST,
                        q2b=Q2B_EXIT))
    # 6. Q2a divides two ints; Q3 keeps area < 1000 and sorts ascending.
    submission(MAIN, s("dinesh.kumar.2023"),
               good_for("dinesh.kumar.2023", q2a=Q2A_INT_DIVISION,
                        q3=Q3_ASCENDING))
    # 7. Q2b prints the GPA with two decimals.
    submission(MAIN, s("emily.chua.2024"),
               good_for("emily.chua.2024", q2b=Q2B_TWO_DECIMALS))
    # 8. Zipped the Q folders directly on a Mac (no wrapper, __MACOSX).
    submission(MAIN, s("fatimah.ali.2024"), good_for("fatimah.ali.2024"),
               wrapper=None, mac_junk=True)
    # 9. Never renamed the starter folder and never filled the headers.
    submission(MAIN, s("gabriel.ong.2024"), good_for("gabriel.ong.2024"),
               wrapper="RenameToYourUsername", blank_header=True)
    # 10. Folder named with the OrgDefinedId; Q2a is case-sensitive.
    submission(MAIN, s("hannah.goh.2024"),
               good_for("hannah.goh.2024", q2a=Q2A_CASE_BUG),
               wrapper="01500110")
    # 11. A username folder inside a username folder.
    submission(MAIN, s("isaac.teo.2024"), good_for("isaac.teo.2024"),
               wrapper="isaac.teo.2024/isaac.teo.2024")
    # 12. Saved q1a.java and a q3 folder (wrong case).
    submission(MAIN, s("jiahui.lim.2024"), good_for("jiahui.lim.2024"),
               rename={"Q1/Q1a.java": "Q1/q1a.java", "Q3": "q3"})
    # 13. The IDE added "package Q1;" to both Q1 files.
    submission(MAIN, s("kaixuan.chen.2024"), good_for("kaixuan.chen.2024"),
               package="Q1")
    # 14. Q1b misses a semicolon. A fixed zip comes in the late upload.
    submission(MAIN, s("lakshmi.raj.2024"),
               good_for("lakshmi.raj.2024", q1b=Q1B_SYNTAX))
    # 15. Q2a loops forever on any file that exists.
    submission(MAIN, s("marcus.yeo.2023"),
               good_for("marcus.yeo.2023", q2a=Q2A_INFINITE))
    # 16. Q1a hard-coded to the examples in main.
    submission(MAIN, s("natalie.sim.2024"),
               good_for("natalie.sim.2024", q1a=Q1A_HARDCODED))
    # 17. Cheat: Q1a prints "Passed" three times and returns nothing.
    submission(MAIN, s("owen.tan.2024"),
               good_for("owen.tan.2024", q1a=Q1A_FAKE_PASS))
    # 18. Cheat: Q1b prints "3.0" and calls System.exit(0).
    submission(MAIN, s("priya.menon.2024"),
               good_for("priya.menon.2024", q1b=Q1B_FAKE_SCORE_EXIT))
    # 19. Q2a waits for Enter on System.in before reading the file.
    submission(MAIN, s("qihao.zhang.2024"),
               good_for("qihao.zhang.2024", q2a=Q2A_WAITS_FOR_ENTER))
    # 20. Q3 is right but leaves a thread running, so Java never exits.
    submission(MAIN, s("rachel.lim.2024"),
               good_for("rachel.lim.2024", q3=Q3_RUNAWAY_THREAD))
    # 21. Q1b never moves on and prints a line each time: endless output.
    submission(MAIN, s("samuel.chan.2024"),
               good_for("samuel.chan.2024", q1b=Q1B_ENDLESS_OUTPUT))
    # 22. Saved in Notepad: every .java file starts with a UTF-8 BOM.
    submission(MAIN, s("shuting.wee.2024"), good_for("shuting.wee.2024"),
               bom=True)
    # 23. Windows: CRLF, files in Windows-1252 with "Zoe" accented.
    submission(MAIN, s("zoe.lefevre.2024"), good_for("zoe.lefevre.2024"),
               crlf=True, cp1252=True)
    # 24. Forgot to include ShapeComparator.java.
    submission(MAIN, s("timothy.ho.2024"), good_for("timothy.ho.2024"),
               drop=("Q3/ShapeComparator.java",))
    # 25. Left a scratch Test.java in Q3 that does not compile.
    submission(MAIN, s("umar.farooq.2024"), good_for("umar.farooq.2024"),
               extra={"Q3/Test.java": Q3_SCRATCH_FILE})
    # 26. A zip entry that points outside the folder (zip slip).
    submission(MAIN, s("valerie.chong.2024"), good_for("valerie.chong.2024"),
               raw_entries={"../../outside-the-working-copy.txt":
                            "This file must never be written.\n"})
    # 27. Submitted the starter untouched, only the folder renamed.
    submission(MAIN, s("wenjie.liu.2024"), {"cmp": None}, blank_header=True)
    # 28. A zip with nothing in it: only the 22-byte end record.
    with open(os.path.join(MAIN, PREFIX + "xavier.pang.2024.zip"),
              "wb") as handle:
        handle.write(b"PK\x05\x06" + b"\x00" * 18)
    # 29. A text file named .zip.
    write_text(os.path.join(MAIN, PREFIX + "yasmin.noor.2024.zip"),
               "This is not a zip file.\n")
    # 30. A correct zip whose upload was cut off half way.
    truncate(submission(MAIN, s("yuki.tanaka.2024"),
                        good_for("yuki.tanaka.2024"), gradable=False))
    # 31. No Q2 folder and no Q1b.java.
    submission(MAIN, s("zhiwei.koh.2024"), good_for("zhiwei.koh.2024"),
               drop=("Q2", "Q1/Q1b.java"))
    # 32. An unzipped folder: the browser opened the zip on download.
    submission(MAIN, s("bella.quek.2024"), good_for("bella.quek.2024"),
               zip_name=PREFIX + "bella.quek.2024", as_folder=True)
    # 33. Uploaded twice: Q2b never throws in the first, " (1)" is fixed.
    submission(MAIN, s("cheryl.foo.2024"),
               good_for("cheryl.foo.2024", q2b=Q2B_NO_EXCEPTION))
    submission(MAIN, s("cheryl.foo.2024"), good_for("cheryl.foo.2024"),
               zip_name=PREFIX + "cheryl.foo.2024 (1).zip")
    # 34. Uploaded three times; the newest, " (2)", broke the comparator.
    submission(MAIN, s("darius.tan.2024"),
               good_for("darius.tan.2024", q1b=Q1B_SYNTAX))
    submission(MAIN, s("darius.tan.2024"), good_for("darius.tan.2024"),
               zip_name=PREFIX + "darius.tan.2024 (1).zip")
    submission(MAIN, s("darius.tan.2024"),
               good_for("darius.tan.2024", cmp=COMPARATOR_AREA_ONLY),
               zip_name=PREFIX + "darius.tan.2024 (2).zip")
    # 35. Model answer. An older zip whose name has capital letters is
    #     inside the bulk zip (see build_bulk).
    submission(MAIN, s("weiming.lau.2024"), good_for("weiming.lau.2024"))
    # 36 to 38. Kelvin copied Jun Wei's whole submission, renamed a few
    #     variables and kept Jun Wei's name in the headers. Nicole copied
    #     only Jun Wei's Q2b and Q3. All three share the Q2b tie bug.
    junwei = good_for("junwei.tan.2024", q2b=Q2B_TIE_BUG)
    submission(MAIN, s("junwei.tan.2024"), junwei)
    kelvin = {}
    for key, body in junwei.items():
        kelvin[key] = body.replace("topName", "nameOfTop").replace(
            "topGpa", "highestGpa")
    submission(MAIN, s("kelvin.ong.2024"), kelvin,
               header_of=s("junwei.tan.2024"))
    nicole = good_for("nicole.yap.2024")
    nicole["q2b"] = junwei["q2b"]
    nicole["q3"] = junwei["q3"]
    nicole["cmp"] = junwei["cmp"]
    submission(MAIN, s("nicole.yap.2024"), nicole)
    # 39. Model answer plus a 60 MB file of zeros (a zip bomb).
    submission(MAIN, s("grace.lee.2024"), good_for("grace.lee.2024"),
               extra={"Q3/notes.bin": b"\x00" * (60 * 1024 * 1024)},
               gradable=False)
    # Not on the roster: a typo in the year, and a student from G2.
    submission(MAIN, s("peter.chia.2042"), good_for("peter.chia.2042"))
    submission(MAIN, s("visitor.g2.2024"),
               good_for("visitor.g2.2024", q1b=Q1B_NUMBER_BUG))
    # Finder leaves this in any folder it has opened.
    with open(os.path.join(MAIN, ".DS_Store"), "wb") as handle:
        handle.write(b"\x00\x00\x00\x01Bud1")
    write_text(os.path.join(MAIN, "marking notes.txt"),
               "Lab test G4. Remember to grade the late ones too.\n")


def build_bulk():
    """The bulk download inside upload 1, made like eLearn's."""
    os.makedirs(BULK_WORK)
    # 40. Q2a returns 0 instead of -1 when the file is missing.
    submission(BULK_WORK, student("harith.rahman.2024"),
               good_for("harith.rahman.2024", q2a=Q2A_MISSING_FILE_ZERO))
    # An older copy of Amelia's zip, same file name as the one in the
    # folder, with a Q2a bug.
    submission(BULK_WORK, student("amelia.tan.2024"),
               good_for("amelia.tan.2024", q2a=Q2A_CASE_BUG))
    # An older zip of Wei Ming's whose name differs from the one in the
    # folder only in case (Q1b bug). macOS and Windows cannot keep both
    # in one folder, which is why it is in here.
    submission(BULK_WORK, student("weiming.lau.2024"),
               good_for("weiming.lau.2024", q1b=Q1B_NUMBER_BUG),
               zip_name=PREFIX + "Weiming.Lau.2024.zip",
               wrapper="Weiming.Lau.2024")
    with open(os.path.join(BULK_WORK, "index.html"), "w") as handle:
        handle.write("<html><body>Lab Test submissions</body></html>\n")
    with zipfile.ZipFile(os.path.join(MAIN, BULK_NAME), "w",
                         zipfile.ZIP_DEFLATED) as archive:
        for name in sorted(os.listdir(BULK_WORK)):
            archive.write(os.path.join(BULK_WORK, name), name)
    shutil.rmtree(BULK_WORK)


def build_late():
    """Upload 2: zips that came in after the section was graded."""
    s = student
    # 41. Missed the main upload; submits for the first time.
    submission(LATE, s("meiling.ho.2024"), good_for("meiling.ho.2024"))
    # Lakshmi sends a fixed zip under the same name as before.
    submission(LATE, s("lakshmi.raj.2024"), good_for("lakshmi.raj.2024"))
    # Chloe sends a new zip, but it arrives damaged.
    truncate(submission(LATE, s("chloe.ng.2024"),
                        good_for("chloe.ng.2024"), gradable=False))
    # Aaron sends the very same zip again.
    shutil.copy(os.path.join(MAIN, PREFIX + "aaron.lee.2024.zip"), LATE)
    # 42. peter.chia.2024 never submits under his own name.


def main():
    # Keep this script and README.md; rebuild only what it generates.
    for generated in (MAIN, LATE, BAD_ROSTERS, BULK_WORK):
        if os.path.exists(generated):
            shutil.rmtree(generated)
    for name in ("roster-G4.csv", "roster-G4-excel.csv"):
        if os.path.exists(os.path.join(OUT, name)):
            os.remove(os.path.join(OUT, name))
    if REFERENCE and os.path.exists(REFERENCE):
        shutil.rmtree(REFERENCE)
    os.makedirs(MAIN)
    os.makedirs(LATE)
    write_rosters()
    build_main()
    build_bulk()
    build_late()
    print("Wrote", OUT)


main()
