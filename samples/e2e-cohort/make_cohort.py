"""Builds a realistic G3 cohort for an end-to-end GradeZZY test.

Usage: python3 -I samples/e2e-cohort/make_cohort.py .  (from GradeZZY)
Writes <GradeZZY>/samples/e2e-cohort/{submissions,testers,roster-G3.csv}.
"""
import os
import re
import shutil
import sys
import zipfile

ROOT = os.path.abspath(sys.argv[1])
SAMPLES = os.path.join(ROOT, "samples")
STARTER = os.path.join(SAMPLES, "RenameToYourUsername")
OUT = os.path.join(SAMPLES, "e2e-cohort")
SUBS = os.path.join(OUT, "submissions")
TESTERS = os.path.join(OUT, "testers")
PREFIX = "2024-2025-"
DOMAIN = "@smu.edu.sg"


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


def build_files(student, answers, blank_header=False, package=None):
    """Returns {relative path: text} for the student's five answers."""
    name = student["display"]
    email = student["email"]
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
    files["Q3/ShapeComparator.java"] = comparator(
        answers.get("cmp"), name, email)
    if blank_header and answers.get("cmp") is not None:
        files["Q3/ShapeComparator.java"] = (
            "/*\n * Name:\n * Email ID:\n */\n\n" + answers["cmp"])
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


def write_zip(zip_name, entries, mac_junk=False):
    """entries: {path in zip: str or bytes}. Adds folder entries too."""
    path = os.path.join(SUBS, zip_name)
    folders = set()
    for entry in entries:
        parts = entry.split("/")[:-1]
        for depth in range(1, len(parts) + 1):
            folders.add("/".join(parts[:depth]) + "/")
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        for folder in sorted(folders):
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


def submission(student, answers, wrapper="username", mac_junk=False,
               blank_header=False, package=None, drop=(), rename=None,
               stale_class=False, zip_name=None, crlf=False):
    files = build_files(student, answers, blank_header, package)
    if crlf:
        for key in list(files):
            files[key] = files[key].replace("\r\n", "\n").replace(
                "\n", "\r\n")
    files.update(provided_files())
    for rel in drop:
        for key in list(files):
            if key == rel or key.startswith(rel + "/"):
                del files[key]
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
        top = student["username"] + "/"
    elif wrapper is None:
        top = ""
    else:
        top = wrapper + "/"
    entries = {top + rel: data for rel, data in files.items()}
    name = zip_name or (PREFIX + student["username"] + ".zip")
    return write_zip(name, entries, mac_junk)


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
    for marker in sorted(NAME_POOLS, key=len, reverse=True):
        pool = NAME_POOLS[marker]
        text = text.replace(marker, pool[index % len(pool)])
    return text


def good_for(username, **changes):
    """The correct answers this student would write, with any bugs."""
    index = [row[0] for row in STUDENTS].index(username)
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


STUDENTS = [
    # username, org id, last name, first name
    ("alicia.tan.2024", "01400101", "TAN", "ALICIA"),
    ("bryan.ng.2024", "01400102", "NG", "BRYAN"),
    ("clara.wong.2023", "01400103", "WONG", "CLARA"),
    ("darren.lim.2024", "01400104", "LIM", "DARREN"),
    ("elaine.koh.2024", "01400105", "KOH", "ELAINE"),
    ("farhan.ismail.2023", "01400106", "ISMAIL", "FARHAN"),
    ("grace.chua.2024", "01400107", "CHUA", "GRACE"),
    ("huimin.lee.2024", "01400108", "LEE", "HUI MIN"),
    ("ivan.goh.2024", "01400109", "GOH", "IVAN"),
    ("jasmine.ong.2023", "01400110", "ONG", "JASMINE"),
    ("kenneth.yeo.2024", "01400111", "YEO", "KENNETH"),
    ("lucas.teo.2024", "01400112", "TEO", "LUCAS"),
    ("meiling.chen.2024", "01400113", "CHEN", "MEI LING"),
    ("nathan.chan.2024", "01400114", "CHAN", "NATHAN"),
    ("olivia.sim.2024", "01400115", "SIM", "OLIVIA"),
    ("priya.nair.2024", "01400116", "NAIR", "PRIYA"),
    ("quentin.lau.2024", "01400117", "LAU", "QUENTIN"),
    ("rachel.tan.2024", "01400118", "TAN", "RACHEL"),
]


def student(username):
    for user, org, last, first in STUDENTS:
        if user == username:
            return {"username": user, "org": org,
                    "display": first.title() + " " + last.title(),
                    "email": user + DOMAIN}
    raise KeyError(username)


def write_roster():
    lines = ["OrgDefinedId,Username,Last Name,First Name,Email,"
             "Calculated Final Grade Numerator,"
             "Calculated Final Grade Denominator,End-of-Line Indicator"]
    for user, org, last, first in STUDENTS:
        lines.append("#%s,#%s,%s,%s,%s,,20,#" % (
            org, user, last, first, user + DOMAIN))
    with open(os.path.join(OUT, "roster-G3.csv"), "w", newline="") as f:
        f.write("\r\n".join(lines) + "\r\n")


def write_testers():
    os.makedirs(TESTERS)
    source = os.path.join(SAMPLES, "Tester-Files")
    for name in sorted(os.listdir(source)):
        if name.endswith(".java"):
            shutil.copy(os.path.join(source, name), TESTERS)
    # The Q2 testers read these; in the provided samples they only exist
    # inside student zips, so the instructor supplies them here.
    ping = os.path.join(SAMPLES, "student-submission",
                        "2023-2024-ping.lee.2023.zip")
    with zipfile.ZipFile(ping) as archive:
        for data in ["personstester.txt", "studentstester.txt"]:
            with open(os.path.join(TESTERS, data), "wb") as handle:
                handle.write(archive.read("ping.lee.2023/Q2/" + data))


def main():
    # Keep this script and README.md; rebuild only what it generates.
    for generated in (SUBS, TESTERS):
        if os.path.exists(generated):
            shutil.rmtree(generated)
    os.makedirs(SUBS)
    write_roster()
    write_testers()

    # 1. Model answer, IDE leftovers (.class files) in the folder.
    submission(student("alicia.tan.2024"), good_for("alicia.tan.2024"),
               stale_class=True)
    # 2. Two small logic bugs (4.0 counted as even; comparator ignores
    #    perimeter on ties).
    submission(student("bryan.ng.2024"),
               good_for("bryan.ng.2024", q1b=Q1B_NUMBER_BUG,
                        cmp=COMPARATOR_AREA_ONLY))
    # 3. Zipped the Q folders directly on a Mac (no wrapper, __MACOSX).
    submission(student("clara.wong.2023"), good_for("clara.wong.2023"),
               wrapper=None, mac_junk=True)
    # 4. Never renamed the starter folder and never filled the headers;
    #    Q2a compares surnames case-sensitively.
    submission(student("darren.lim.2024"),
               good_for("darren.lim.2024", q2a=Q2A_CASE_BUG),
               wrapper="RenameToYourUsername", blank_header=True)
    # 5. Q1b does not compile (missing semicolon).
    submission(student("elaine.koh.2024"),
               good_for("elaine.koh.2024", q1b=Q1B_SYNTAX))
    # 6. Q2a loops forever on any file that exists.
    submission(student("farhan.ismail.2023"),
               good_for("farhan.ismail.2023", q2a=Q2A_INFINITE))
    # 7. Q1a hard-coded to the examples in main.
    submission(student("grace.chua.2024"),
               good_for("grace.chua.2024", q1a=Q1A_HARDCODED))
    # 8 and 9. Ivan copied Hui Min's whole submission (same Q2b tie bug)
    #    and named the folder with his student ID.
    shared = good_for("huimin.lee.2024", q2b=Q2B_TIE_BUG)
    submission(student("huimin.lee.2024"), shared)
    ivan = dict(shared)
    ivan["q1a"] = ivan["q1a"].replace("        return ",
                                      "        // done\n        return ", 1)
    submission(student("ivan.goh.2024"), ivan, wrapper="01400109")
    # 10. Submitted the starter untouched, only the folder renamed.
    submission(student("jasmine.ong.2023"), {"cmp": None},
               blank_header=True)
    # 11. Saved Q1a as q1a.java and the Q3 folder as q3.
    submission(student("kenneth.yeo.2024"), good_for("kenneth.yeo.2024"),
               rename={"Q1/Q1a.java": "Q1/q1a.java", "Q3": "q3"})
    # 12. The IDE added "package Q1;" to both Q1 files.
    submission(student("lucas.teo.2024"), good_for("lucas.teo.2024"),
               package="Q1")
    # 13. Q1a only checks neighbouring letters and prints debug lines
    #     that start with "Passed"; Q2b calls System.exit.
    submission(student("meiling.chen.2024"),
               good_for("meiling.chen.2024", q1a=Q1A_DEBUG, q2b=Q2B_EXIT))
    # 14. Did not attempt Q3 (folder missing); Q2b never throws.
    submission(student("nathan.chan.2024"),
               good_for("nathan.chan.2024", q2b=Q2B_NO_EXCEPTION),
               drop=("Q3",))
    # 15. Downloaded twice, so the browser added " (1)".
    submission(student("olivia.sim.2024"), good_for("olivia.sim.2024"),
               zip_name=PREFIX + "olivia.sim.2024 (1).zip")
    # 16. A correct submission whose upload was cut off half way.
    priya = submission(student("priya.nair.2024"),
                       good_for("priya.nair.2024"))
    with open(priya, "rb") as handle:
        data = handle.read()
    with open(priya, "wb") as handle:
        handle.write(data[: len(data) // 2])
    # 17. Wrote everything on Windows: CRLF line endings throughout.
    submission(student("quentin.lau.2024"), good_for("quentin.lau.2024"),
               crlf=True)
    # 18. rachel.tan.2024 is on the roster but submitted nothing.

    # Finder leaves this in any folder it has opened.
    with open(os.path.join(SUBS, ".DS_Store"), "wb") as handle:
        handle.write(b"\x00\x00\x00\x01Bud1")
    print("Wrote", OUT)


main()
