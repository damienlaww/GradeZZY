/*
 * File: SimilarityGroupsView.java
 *
 * Purpose: The Similarity view of the Review step. Each group is one
 * cluster of students whose code the similarity check found alike for
 * one question, listed under its question as the errors are. A closed
 * group is one row: how many students and matches, the similarity range,
 * how strong the match is and whether it was reviewed, in columns of
 * fixed widths so the rows line up. Files handed in twice, with the same
 * Name, Email ID and code, are their own groups, marked Identical copy.
 * An open group shows an overview, its students, and two students' code
 * side by side with the matching lines marked, with Mark as reviewed and
 * Keep flagged.
 *
 * Where it fits: ReviewScreen shows it in place of the student table
 * when "Similarity" is chosen. It reads the
 * pairs through DashboardBackend.listSimilarPairs and the code through
 * getSimilarityDetail. Mark as reviewed and Keep flagged set the same
 * decisions on the group's Similar code flags as Looks fine and Keep
 * flagged do in the student panel.
 */
package gradezzy.ui.flow;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import gradezzy.model.Anomaly;
import gradezzy.model.AnomalyType;
import gradezzy.model.GradingRun;
import gradezzy.model.Question;
import gradezzy.model.Submission;
import gradezzy.plagiarism.SimilarityPair;
import gradezzy.ui.backend.ActionResult;
import gradezzy.ui.backend.Capability;
import gradezzy.ui.backend.CheatingRemarks;
import gradezzy.ui.backend.DashboardBackend;
import gradezzy.ui.backend.ReviewDecision;
import gradezzy.ui.backend.SimilarityDetail;
import gradezzy.util.TextUtils;

/**
 * Lists the similarity groups of a run, one row each, that open to
 * compare two students' code.
 *
 * <p>Tier: COULD.
 */
public class SimilarityGroupsView {

    // Turns a similarity share into a percentage
    private static final double PERCENT = 100.0;

    // A group whose least alike pair is at least this alike is very
    // strong, at least the second strong, at least the third moderate
    private static final double VERY_STRONG = 0.95;
    private static final double STRONG = 0.85;
    private static final double MODERATE = 0.70;

    // How many bars the strength meter has, and how tall each step is
    private static final int STRENGTH_BARS = 4;
    private static final double BAR_WIDTH = 3.0;
    private static final double BAR_STEP = 3.0;

    // The gap between the bars of the strength meter
    private static final double BAR_GAP = 1.0;

    // The width of the thin line between a group's words and its facts
    private static final double DIVIDER_WIDTH = 1.0;

    // How many students a group shows before "+ more"
    private static final int SHOWN_STUDENTS = 5;

    // The size of the chevron that opens a group and of other icons
    private static final double CHEVRON_SIZE = 14.0;
    private static final double ICON_SIZE = 14.0;

    // How far an open group's chevron turns, so it points down
    private static final double OPEN_ROTATION = 90.0;

    // The height of the code shown side by side in an open group
    private static final double CODE_HEIGHT = 220.0;

    // The width of the overview card of an open group
    private static final double OVERVIEW_WIDTH = 300.0;

    // The width of each list that picks a student to compare
    private static final double PICKER_WIDTH = 240.0;

    // The ranks of the strengths, how many bars each fills; an identical
    // copy is above them all and shows an icon in place of bars
    private static final int IDENTICAL_RANK = 6;
    private static final int SAME_EMAIL_RANK = 5;
    private static final int VERY_STRONG_RANK = 4;
    private static final int STRONG_RANK = 3;
    private static final int MODERATE_RANK = 2;
    private static final int WEAK_RANK = 1;

    // The widths of the columns of a group's row, so every row lines up
    // and every description is cut at the same length
    private static final double TITLE_WIDTH = 140.0;
    private static final double STUDENTS_WIDTH = 95.0;
    private static final double MATCHES_WIDTH = 105.0;
    private static final double RANGE_WIDTH = 125.0;
    private static final double STRENGTH_WIDTH = 120.0;
    private static final double STATUS_WIDTH = 115.0;

    // The fewest questions two students must be alike on to be warned
    // about
    private static final int REPEAT_QUESTIONS = 2;

    // How many such pairs the warning lists before "Show all"
    private static final int SHOWN_REPEATS = 5;

    // Separates the two usernames in the key of a pair of students
    private static final String PAIR_SEPARATOR = "|";

    // How many letters an initials badge holds
    private static final int INITIALS = 2;

    // The number of students in a group of just two
    private static final int PAIR_SIZE = 2;

    // The ways the groups can be sorted, in the order offered
    private static final String SORT_STRENGTH = "Strength "
            + "(strong → weak)";
    private static final String SORT_SIMILARITY = "Similarity "
            + "(high → low)";
    private static final String SORT_STUDENTS = "Students (most first)";

    // Comes between two numbers of a range
    private static final String RANGE_DASH = " – ";

    // The window, for the backend and sheets
    private final FlowWindow window;

    // Told after a decision on a group, so the screen counts again
    private final Runnable onDecision;

    // The question headers and their groups
    private final VBox list = new VBox(Parts.GAP_SMALL);

    // Scrolls the list
    private final ScrollPane scroll = new ScrollPane(list);

    // The run last shown, drawn again after a change
    private GradingRun currentRun = null;

    // The line above the list, such as "5 students in 2 groups"
    private String summary = "";

    // The groups that are open, by key
    private final List<String> openKeys = new ArrayList<>();

    // The groups that list all their students, by key
    private final List<String> allStudentKeys = new ArrayList<>();

    // The two students compared in each open group, by key, A then B
    private final List<String> comparedKeys = new ArrayList<>();
    private final List<String[]> comparedPairs = new ArrayList<>();

    // How the groups are sorted
    private String sortOrder = SORT_STRENGTH;

    // True when the warning lists every pair alike on several questions
    private boolean allRepeatsShown = false;

    /**
     * Creates the view.
     *
     * <p>Tier: COULD.
     *
     * @param window the window
     * @param onDecision told after a group is marked as reviewed or kept
     *        flagged, so the screen can count what still needs a look
     */
    public SimilarityGroupsView(FlowWindow window, Runnable onDecision) {
        this.window = window;
        this.onDecision = onDecision;
        scroll.setFitToWidth(true);
        scroll.getStyleClass().addAll("edge-to-edge", "mistakes-view",
                "similarity-view");
        list.getStyleClass().addAll("mistakes-list", "similarity-groups");
    }

    /**
     * Returns the view.
     *
     * <p>Tier: COULD.
     *
     * @return the scrolling list
     */
    public Node getView() {
        return scroll;
    }

    /**
     * Returns the line that sums up the groups last shown.
     *
     * <p>Tier: COULD.
     *
     * @return such as "5 students in 2 similarity groups, on 2
     *         questions."
     */
    public String getSummary() {
        return summary;
    }

    /**
     * Shows the similarity groups of a run under their questions,
     * keeping the scroll position and which groups are open.
     *
     * <p>Tier: COULD.
     *
     * @param run the run, or null for none
     */
    public void show(GradingRun run) {
        double place = scroll.getVvalue();
        currentRun = run;
        list.getChildren().clear();
        summary = "";
        if (run == null) {
            return;
        }

        DashboardBackend backend = window.getBackend();
        List<SimilarityCluster> clusters = new ArrayList<>();
        List<SimilarityPair> pairs = new ArrayList<>();
        if (backend.supports(Capability.SIMILARITY_DETAIL)) {
            pairs = backend.listSimilarPairs(run);
            clusters = buildClusters(pairs);
        }
        if (clusters.isEmpty()) {
            list.getChildren().add(Parts.subtitle(describeNone()));
            return;
        }
        sort(clusters);

        List<String> questions = orderQuestions(run, clusters);
        summary = summaryOf(clusters, questions.size());
        list.getChildren().add(topRow(clusters.size()));
        Node repeats = repeatWarning(run, pairs);
        if (repeats != null) {
            list.getChildren().add(repeats);
        }
        for (String question : questions) {
            addQuestion(run, question, clusters);
        }
        scroll.setVvalue(place);
    }

    // Says why there are no groups: the check is off, or found nothing.
    private String describeNone() {
        if (!window.getBackend().isSimilarityEnabled()) {
            return "The similarity check is off, so only identical copies "
                    + "(the same Name, Email ID and code) were looked for, "
                    + "and none were found. Switch it on in Settings, "
                    + "under Similar code, to look for similar code too.";
        }
        return "The similarity check found no similar code in this run.";
    }

    // Builds the groups: identical copies on their own, then pairs with
    // the same Email ID, so neither is hidden in a larger group of merely
    // similar code, then the rest.
    private List<SimilarityCluster> buildClusters(
            List<SimilarityPair> pairs) {
        List<SimilarityPair> identical = new ArrayList<>();
        List<SimilarityPair> sameEmail = new ArrayList<>();
        List<SimilarityPair> similar = new ArrayList<>();
        for (SimilarityPair pair : pairs) {
            if (pair.isIdentical()) {
                identical.add(pair);
            } else if (pair.isSameEmail()) {
                sameEmail.add(pair);
            } else {
                similar.add(pair);
            }
        }
        List<SimilarityCluster> clusters = SimilarityCluster.build(
                identical);
        clusters.addAll(SimilarityCluster.build(sameEmail));
        clusters.addAll(SimilarityCluster.build(similar));
        return clusters;
    }

    // Lists the questions that have groups, in the assessment's order;
    // any other question goes last.
    private List<String> orderQuestions(GradingRun run,
            List<SimilarityCluster> clusters) {
        List<String> found = new ArrayList<>();
        for (SimilarityCluster cluster : clusters) {
            if (!found.contains(cluster.getQuestionId())) {
                found.add(cluster.getQuestionId());
            }
        }
        List<String> ordered = new ArrayList<>();
        if (run.getAssessment() != null) {
            for (Question question : run.getAssessment().getQuestions()) {
                if (found.contains(question.getId())) {
                    ordered.add(question.getId());
                }
            }
        }
        for (String question : found) {
            if (!ordered.contains(question)) {
                ordered.add(question);
            }
        }
        return ordered;
    }

    // Sums up the groups, such as "5 students in 2 similarity groups, on
    // 2 questions. Similar is not proof of copying."
    private String summaryOf(List<SimilarityCluster> clusters,
            int questionCount) {
        List<String> students = new ArrayList<>();
        for (SimilarityCluster cluster : clusters) {
            for (String username : cluster.getUsernames()) {
                if (!students.contains(username)) {
                    students.add(username);
                }
            }
        }
        return Parts.plural(students.size(), "student") + " in "
                + Parts.plural(clusters.size(), "similarity group") + ", on "
                + Parts.plural(questionCount, "question") + ". Similar is "
                + "not proof of copying; marks never change.";
    }

    // Builds the line above the questions: how many groups, and the sort
    // list.
    private HBox topRow(int groupCount) {
        Label title = Parts.title(Parts.plural(groupCount,
                "similarity group"));
        ComboBox<String> sortBox = new ComboBox<>();
        sortBox.getItems().addAll(SORT_STRENGTH, SORT_SIMILARITY,
                SORT_STUDENTS);
        sortBox.setValue(sortOrder);
        sortBox.setOnAction(event -> sortChosen(sortBox.getValue()));
        Parts.tip(sortBox, "How the groups of each question are ordered.");
        Label sortLabel = Parts.muted("Sort by:");
        return Parts.row(Parts.GAP_SMALL, new Node[] {title, Parts.grow(),
            sortLabel, sortBox});
    }

    // Adds a question's header and its groups, numbered in order.
    private void addQuestion(GradingRun run, String question,
            List<SimilarityCluster> clusters) {
        List<SimilarityCluster> own = new ArrayList<>();
        for (SimilarityCluster cluster : clusters) {
            if (cluster.getQuestionId().equals(question)) {
                own.add(cluster);
            }
        }
        Label title = Parts.title(question);
        Label count = Parts.muted(Parts.plural(own.size(), "group"));
        HBox header = Parts.row(Parts.GAP_SMALL, new Node[] {title,
            count});
        header.setAlignment(Pos.BASELINE_LEFT);
        header.getStyleClass().add("similarity-question");
        list.getChildren().add(header);
        for (int index = 0; index < own.size(); index++) {
            int number = index + 1;
            list.getChildren().add(groupBox(run, own.get(index), number));
        }
    }

    // ------------------------------------------------- repeated pairs

    // Builds the warning about two students alike on more than one
    // question or part that still waits for a look, most questions
    // first, or returns null when no pair is. A question drops out once
    // it is reviewed, so a pair with one question left is not listed.
    private Node repeatWarning(GradingRun run, List<SimilarityPair> pairs) {
        CheatingRemarks reviews = new CheatingRemarks(window.getBackend());
        List<String> keys = new ArrayList<>();
        List<List<SimilarityPair>> byStudents = new ArrayList<>();
        for (SimilarityPair pair : pairs) {
            if (reviews.isWaiting(run, pair)) {
                addToStudents(keys, byStudents, pair);
            }
        }
        List<List<SimilarityPair>> repeats = new ArrayList<>();
        for (List<SimilarityPair> same : byStudents) {
            if (same.size() >= REPEAT_QUESTIONS) {
                repeats.add(same);
            }
        }
        if (repeats.isEmpty()) {
            return null;
        }
        repeats.sort((first, second) -> second.size() - first.size());
        return repeatBanner(run, repeats);
    }

    // Adds a pair to the list of its two students, starting the list when
    // it is their first.
    private void addToStudents(List<String> keys,
            List<List<SimilarityPair>> byStudents, SimilarityPair pair) {
        String key = studentsKey(pair);
        int place = keys.indexOf(key);
        if (place < 0) {
            keys.add(key);
            byStudents.add(new ArrayList<>());
            place = keys.size() - 1;
        }
        addOnce(byStudents.get(place), pair);
    }

    // Adds a pair to a student pair's list unless its question is there.
    private void addOnce(List<SimilarityPair> same, SimilarityPair pair) {
        for (SimilarityPair held : same) {
            if (held.getQuestionId().equals(pair.getQuestionId())) {
                return;
            }
        }
        same.add(pair);
    }

    // Names two students the same way whichever comes first.
    private String studentsKey(SimilarityPair pair) {
        String first = pair.getFirstUsername();
        String second = pair.getSecondUsername();
        if (first.compareTo(second) > 0) {
            return second + PAIR_SEPARATOR + first;
        }
        return first + PAIR_SEPARATOR + second;
    }

    // Builds the banner: a title, then one line per pair of students.
    private VBox repeatBanner(GradingRun run,
            List<List<SimilarityPair>> repeats) {
        Node mark = Parts.icon(Parts.WARNING, ICON_SIZE);
        mark.getStyleClass().add("repeat-icon");
        String title = Parts.plural(repeats.size(), "pair") + " of students "
                + "alike on more than one question or part";
        if (repeats.size() == 1) {
            title = "Two students alike on more than one question or part";
        }
        HBox top = Parts.row(Parts.GAP_SMALL, new Node[] {mark,
            Parts.strong(title)});
        Label help = Parts.muted("A match on several questions is worth a "
                + "closer look than one alone. A question leaves this list "
                + "once its group is reviewed. Similar is still not proof "
                + "of copying.");
        help.getStyleClass().add("small-text");
        help.setWrapText(true);

        VBox banner = new VBox(Parts.GAP_SMALL, top, help);
        int shown = repeats.size();
        if (!allRepeatsShown) {
            shown = Math.min(shown, SHOWN_REPEATS);
        }
        for (int index = 0; index < shown; index++) {
            banner.getChildren().add(repeatLine(run, repeats.get(index)));
        }
        int hidden = repeats.size() - shown;
        if (hidden > 0) {
            banner.getChildren().add(Parts.link("Show all "
                    + repeats.size() + " pairs", () -> showAllRepeats(true)));
        } else if (allRepeatsShown && repeats.size() > SHOWN_REPEATS) {
            banner.getChildren().add(Parts.link("Show fewer",
                    () -> showAllRepeats(false)));
        }
        banner.getStyleClass().addAll("banner", "warn", "repeat-warning");
        return banner;
    }

    // Lists every pair alike on several questions, or only the first
    // few, and draws the view again.
    private void showAllRepeats(boolean all) {
        allRepeatsShown = all;
        redraw();
    }

    // Builds one line of the warning, such as "Bryan Lim and Ian Chong:
    // 6 questions (Q1, Q2a, ...), 6 of them identical copies", with
    // Compare for their most alike question.
    private HBox repeatLine(GradingRun run, List<SimilarityPair> same) {
        SimilarityPair closest = same.get(0);
        List<String> questions = new ArrayList<>();
        int identical = 0;
        int sameEmail = 0;
        for (SimilarityPair pair : same) {
            questions.add(pair.getQuestionId());
            if (pair.isIdentical()) {
                identical++;
            } else if (pair.isSameEmail()) {
                sameEmail++;
            }
            if (pair.getSimilarity() > closest.getSimilarity()) {
                closest = pair;
            }
        }
        String who = SimilaritySheet.nameOf(run, closest.getFirstUsername())
                + " and " + SimilaritySheet.nameOf(run,
                        closest.getSecondUsername());
        String what = Parts.plural(same.size(), "question") + " left to "
                + "review (" + TextUtils.join(", ",
                        inQuestionOrder(run, questions)) + ")";
        if (identical == same.size()) {
            what = what + ", all identical copies";
        } else if (identical > 0) {
            what = what + ", " + identical + " of them identical copies";
        }
        if (sameEmail > 0) {
            what = what + "; the same Email ID on " + Parts.plural(
                    sameEmail, "other question");
        }
        Label words = new Label(who + ": " + what);
        words.setWrapText(true);
        words.setMinWidth(0);
        HBox.setHgrow(words, Priority.ALWAYS);

        HBox line = Parts.row(Parts.GAP, new Node[] {words});
        line.getStyleClass().add("repeat-line");
        return line;
    }

    // Puts question ids in the order of the run's assessment, such as
    // Q2b before Q3; an id the assessment does not hold comes last.
    private List<String> inQuestionOrder(GradingRun run,
            List<String> questionIds) {
        List<String> ordered = new ArrayList<>();
        if (run != null && run.getAssessment() != null) {
            for (Question question : run.getAssessment().getQuestions()) {
                if (questionIds.contains(question.getId())) {
                    ordered.add(question.getId());
                }
            }
        }
        for (String questionId : questionIds) {
            if (!ordered.contains(questionId)) {
                ordered.add(questionId);
            }
        }
        return ordered;
    }

    // Draws the view again with the run last shown.
    private void redraw() {
        show(currentRun);
    }

    // Keeps the chosen order and draws the view again.
    private void sortChosen(String order) {
        if (order == null || order.equals(sortOrder)) {
            return;
        }
        sortOrder = order;
        redraw();
    }

    // Sorts the groups in the chosen order. Ties keep the order the
    // check gave, most alike first.
    private void sort(List<SimilarityCluster> clusters) {
        if (sortOrder.equals(SORT_STUDENTS)) {
            clusters.sort((first, second) -> second.getUsernames().size()
                    - first.getUsernames().size());
        } else if (sortOrder.equals(SORT_SIMILARITY)) {
            clusters.sort((first, second) -> Double.compare(
                    second.getHighestSimilarity(),
                    first.getHighestSimilarity()));
        } else {
            clusters.sort((first, second) -> compareStrength(first,
                    second));
        }
    }

    // Puts the stronger group first, then the larger group, then the
    // more alike.
    private int compareStrength(SimilarityCluster first,
            SimilarityCluster second) {
        int byStrength = strengthRank(second) - strengthRank(first);
        if (byStrength != 0) {
            return byStrength;
        }
        int bySize = second.getUsernames().size()
                - first.getUsernames().size();
        if (bySize != 0) {
            return bySize;
        }
        return Double.compare(second.getHighestSimilarity(),
                first.getHighestSimilarity());
    }

    // ------------------------------------------------------- one group

    // Builds one group: its row, and its detail when open.
    private VBox groupBox(GradingRun run, SimilarityCluster cluster,
            int number) {
        String key = keyOf(cluster);
        boolean open = openKeys.contains(key);
        List<Anomaly> flags = findFlags(run, cluster);

        VBox box = new VBox(groupHeader(cluster, number, flags, open));
        box.getStyleClass().add("similarity-group");
        box.setUserData(key);
        if (open) {
            box.getStyleClass().add("open");
            box.getChildren().add(groupDetail(run, cluster, flags));
        }
        return box;
    }

    // Builds the row of a group: chevron, title and words, then the
    // facts, pills and status. Clicking it opens or closes the group.
    private HBox groupHeader(SimilarityCluster cluster, int number,
            List<Anomaly> flags, boolean open) {
        Node chevron = Parts.icon(Parts.CHEVRON, CHEVRON_SIZE);
        if (open) {
            chevron.setRotate(OPEN_ROTATION);
        }
        chevron.getStyleClass().add("error-chevron");

        Label title = Parts.strong(titleOf(cluster, number));
        title.getStyleClass().add("group-title");
        fixWidth(title, TITLE_WIDTH);
        Label words = Parts.muted(summaryOf(cluster));
        words.getStyleClass().add("small-text");
        words.setMinWidth(0);
        HBox.setHgrow(words, Priority.ALWAYS);
        Region titleLine = Parts.row(Parts.GAP, new Node[] {title, words});
        if (open) {
            // Open, the words go under the title, which gains the chip
            title.setMaxWidth(Region.USE_PREF_SIZE);
            HBox top = Parts.row(Parts.GAP, new Node[] {title,
                matchesChip(cluster)});
            words.setWrapText(true);
            titleLine = new VBox(Parts.GAP_TINY, top, words);
        }
        titleLine.setMinWidth(0);
        HBox.setHgrow(titleLine, Priority.ALWAYS);

        Node students = fact(Parts.PEOPLE, Parts.plural(
                cluster.getUsernames().size(), "student"), STUDENTS_WIDTH);
        Node matches = fact(Parts.LINK, Parts.plural(
                cluster.getPairs().size(), "match", "matches"),
                MATCHES_WIDTH);
        Node range = fact(Parts.CHART, rangeOf(cluster), RANGE_WIDTH);
        HBox header = Parts.row(Parts.GAP, new Node[] {chevron, titleLine,
            divider(), students, matches, range,
            cell(strengthPill(cluster), STRENGTH_WIDTH),
            cell(statusPill(flags), STATUS_WIDTH)});
        header.getStyleClass().add("similarity-group-header");
        header.setFocusTraversable(true);
        header.setAccessibleText(title.getText() + ", "
                + Parts.plural(cluster.getUsernames().size(), "student")
                + ". Press Enter to open or close.");
        String key = keyOf(cluster);
        header.setOnMouseClicked(event -> headerClicked(event, key));
        header.setOnKeyPressed(event -> headerKey(event, key));
        return header;
    }

    // Builds the open part of a group: the overview at the left, and the
    // students and the comparison at the right.
    private HBox groupDetail(GradingRun run, SimilarityCluster cluster,
            List<Anomaly> flags) {
        VBox overview = overviewCard(cluster);
        VBox compare = compareCard(run, cluster, flags);
        HBox.setHgrow(compare, Priority.ALWAYS);
        HBox detail = new HBox(Parts.GAP, overview, compare);
        detail.getStyleClass().add("similarity-group-detail");
        return detail;
    }

    // Builds the overview of an open group, one fact a line.
    private VBox overviewCard(SimilarityCluster cluster) {
        Label title = Parts.strong("Group overview");
        VBox card = new VBox(Parts.GAP, title,
                overviewLine(Parts.PEOPLE, "Students involved",
                        Parts.strong(String.valueOf(cluster.getUsernames()
                                .size()))),
                overviewLine(Parts.LINK, "Student-to-student matches",
                        Parts.strong(String.valueOf(cluster.getPairs()
                                .size()))),
                overviewLine(Parts.CHART, "Similarity range",
                        Parts.strong(rangeOf(cluster))),
                overviewLine(Parts.FILE, "Question",
                        Parts.strong(cluster.getQuestionId())),
                overviewLine(Parts.CHART, "Similarity strength",
                        strengthPill(cluster)));
        card.getStyleClass().add("similarity-overview");
        card.setMinWidth(OVERVIEW_WIDTH);
        card.setPrefWidth(OVERVIEW_WIDTH);
        card.setMaxWidth(OVERVIEW_WIDTH);
        return card;
    }

    // Builds one line of the overview: an icon, its name and, at the
    // right, its value.
    private HBox overviewLine(String icon, String name, Node value) {
        Node mark = Parts.icon(icon, ICON_SIZE);
        mark.getStyleClass().add("fact-icon");
        Label words = Parts.muted(name);
        words.setMinWidth(Region.USE_PREF_SIZE);
        return Parts.row(Parts.GAP_SMALL, new Node[] {mark, words,
            Parts.grow(), value});
    }

    // Builds the right of an open group: its students, then two of them
    // side by side, then what was found and the buttons.
    private VBox compareCard(GradingRun run, SimilarityCluster cluster,
            List<Anomaly> flags) {
        String[] compared = comparedOf(cluster);
        VBox card = new VBox(Parts.GAP, studentsLine(run, cluster),
                pickers(run, cluster, compared));

        SimilarityPair pair = pairOf(cluster, compared[0], compared[1]);
        SimilarityDetail detail = window.getBackend()
                .getSimilarityDetail(run, pair);
        if (detail == null) {
            card.getChildren().add(Parts.muted("The check returned no "
                    + "files to compare for these two students."));
            card.getChildren().add(footer(run, cluster, flags, pair,
                    null));
        } else {
            SimilarCode code = new SimilarCode(detail);
            HBox view = code.getView();
            view.setPrefHeight(CODE_HEIGHT);
            view.setMinHeight(CODE_HEIGHT);
            view.setMaxHeight(CODE_HEIGHT);
            HBox names = Parts.row(Parts.GAP, new Node[] {
                sideName(run, compared[0], code.describeSide(true)),
                sideName(run, compared[1], code.describeSide(false))});
            card.getChildren().addAll(names, view, footer(run, cluster,
                    flags, pair, code));
        }
        card.getStyleClass().add("similarity-compare");
        return card;
    }

    // Lists the students of a group as name chips, the first few and
    // "+ more", which lists them all.
    private HBox studentsLine(GradingRun run, SimilarityCluster cluster) {
        String key = keyOf(cluster);
        List<String> usernames = cluster.getUsernames();
        boolean showAll = allStudentKeys.contains(key);
        int shown = usernames.size();
        if (!showAll) {
            shown = Math.min(shown, SHOWN_STUDENTS);
        }

        FlowPane chips = new FlowPane(Parts.GAP_SMALL, Parts.GAP_SMALL);
        for (int index = 0; index < shown; index++) {
            chips.getChildren().add(studentChip(run, usernames.get(index)));
        }
        int hidden = usernames.size() - shown;
        if (hidden > 0) {
            chips.getChildren().add(Parts.link("+" + hidden + " more",
                    () -> toggle(allStudentKeys, key)));
        } else if (showAll) {
            chips.getChildren().add(Parts.link("Show fewer",
                    () -> toggle(allStudentKeys, key)));
        }
        HBox.setHgrow(chips, Priority.ALWAYS);

        Label title = Parts.strong("Affected students ("
                + usernames.size() + ")");
        title.setMinWidth(Region.USE_PREF_SIZE);
        HBox line = Parts.row(Parts.GAP, new Node[] {title, chips});
        line.setAlignment(Pos.TOP_LEFT);
        return line;
    }

    // Builds the chip of one student: their initials and name.
    private HBox studentChip(GradingRun run, String username) {
        String name = SimilaritySheet.nameOf(run, username);
        Label badge = new Label(initialsOf(name));
        badge.getStyleClass().add("initials-badge");
        Label words = new Label(name);
        HBox chip = Parts.row(Parts.GAP_TINY, new Node[] {badge, words});
        chip.getStyleClass().add("student-chip");
        Parts.tipNode(chip, username);
        return chip;
    }

    // Builds the two lists that choose the students compared, with the
    // button that swaps them.
    private HBox pickers(GradingRun run, SimilarityCluster cluster,
            String[] compared) {
        ComboBox<String> first = studentBox(run, cluster, compared[0]);
        ComboBox<String> second = studentBox(run, cluster, compared[1]);
        first.setOnAction(event -> studentChosen(cluster, first, second));
        second.setOnAction(event -> studentChosen(cluster, first, second));

        Button swap = new Button();
        swap.setGraphic(Parts.icon(Parts.SWAP, ICON_SIZE));
        swap.getStyleClass().addAll("flat", "icon-button");
        swap.setAccessibleText("Swap the two students");
        Parts.tip(swap, "Swap the two students");
        swap.setOnAction(event -> choose(cluster, compared[1],
                compared[0]));

        Label title = Parts.strong("Compare submissions");
        Label help = Parts.muted("Two students of this group side by side. "
                + "Matching lines are marked.");
        help.getStyleClass().add("small-text");
        help.setWrapText(true);
        VBox words = new VBox(Parts.GAP_TINY, title, help);
        words.setMinWidth(0);
        HBox.setHgrow(words, Priority.ALWAYS);
        HBox row = Parts.row(Parts.GAP_SMALL, new Node[] {words,
            labelled("Student A", first), swap,
            labelled("Student B", second)});
        row.setAlignment(Pos.BOTTOM_LEFT);
        return row;
    }

    // Puts a small name above a list.
    private VBox labelled(String name, Node control) {
        Label label = Parts.muted(name);
        label.getStyleClass().add("small-text");
        return new VBox(Parts.GAP_TINY, label, control);
    }

    // Builds a list of a group's students, by name and email ID.
    private ComboBox<String> studentBox(GradingRun run,
            SimilarityCluster cluster, String chosen) {
        ComboBox<String> box = new ComboBox<>();
        for (String username : cluster.getUsernames()) {
            box.getItems().add(describe(run, username));
        }
        box.setValue(describe(run, chosen));
        box.setPrefWidth(PICKER_WIDTH);
        box.setMinWidth(Region.USE_PREF_SIZE);
        return box;
    }

    // Keeps the students picked in the two lists, when they differ.
    private void studentChosen(SimilarityCluster cluster,
            ComboBox<String> first, ComboBox<String> second) {
        int firstIndex = first.getSelectionModel().getSelectedIndex();
        int secondIndex = second.getSelectionModel().getSelectedIndex();
        if (firstIndex < 0 || secondIndex < 0) {
            return;
        }
        List<String> usernames = cluster.getUsernames();
        choose(cluster, usernames.get(firstIndex),
                usernames.get(secondIndex));
    }

    // Compares two students of a group, unless they are the same one,
    // and draws the view again.
    private void choose(SimilarityCluster cluster, String first,
            String second) {
        if (first.equals(second)) {
            window.notify("Choose two different students to compare.");
            redraw();
            return;
        }
        String key = keyOf(cluster);
        int place = comparedKeys.indexOf(key);
        String[] pair = {first, second};
        if (place < 0) {
            comparedKeys.add(key);
            comparedPairs.add(pair);
        } else {
            comparedPairs.set(place, pair);
        }
        redraw();
    }

    // Names one side of the comparison, with how many lines match.
    private HBox sideName(GradingRun run, String username, String count) {
        Label name = Parts.strong(describe(run, username));
        name.setMinWidth(0);
        Label countLabel = Parts.muted(count);
        countLabel.getStyleClass().add("small-text");
        HBox side = Parts.row(Parts.GAP_SMALL, new Node[] {name,
            Parts.grow(), countLabel});
        side.setMinWidth(0);
        side.setPrefWidth(0);
        HBox.setHgrow(side, Priority.ALWAYS);
        return side;
    }

    // Builds the line under the code: how alike the two are, why they
    // are grouped, and the buttons.
    private HBox footer(GradingRun run, SimilarityCluster cluster,
            List<Anomaly> flags, SimilarityPair pair, SimilarCode code) {
        Label percent = Parts.strong("not flagged as a pair");
        if (!Double.isNaN(pair.getSimilarity())) {
            percent = Parts.strong(percentOf(pair.getSimilarity()));
            percent.getStyleClass().add("similarity-percent");
        }
        HBox similarity = Parts.row(Parts.GAP_SMALL, new Node[] {
            Parts.muted("Similarity"), percent});
        similarity.setMinWidth(Region.USE_PREF_SIZE);
        String reason = "the similarity check matched their "
                + cluster.getQuestionId() + " code; names, spacing or "
                + "comments may differ.";
        if (pair.isIdentical()) {
            reason = "both files have the same Name and Email ID in their "
                    + "header and the same code.";
        } else if (pair.isSameEmail()) {
            reason = "both files give the same Email ID in their header; "
                    + "the code is compared with names set aside.";
        }
        Label why = Parts.muted("Why grouped: " + reason);
        why.getStyleClass().add("small-text");
        why.setWrapText(true);
        why.setMinWidth(0);
        HBox.setHgrow(why, Priority.ALWAYS);

        Button full = Parts.small("Compare full code",
                () -> window.showSheet(SimilaritySheet.create(window, run,
                        pair)));
        Parts.tip(full, "Open both files in a large window, with "
                + "buttons that jump between the matching parts.");
        HBox row = Parts.row(Parts.GAP, new Node[] {similarity, why, full});
        row.getChildren().addAll(decisionButtons(flags));
        row.getStyleClass().add("similarity-footer");
        return row;
    }

    // Builds Mark as reviewed and Keep flagged, or Reopen once the group
    // is decided. A group with no flags to decide has none.
    private List<Node> decisionButtons(List<Anomaly> flags) {
        List<Node> buttons = new ArrayList<>();
        if (flags.isEmpty()) {
            return buttons;
        }
        if (countOpen(flags) == 0) {
            Button reopen = Parts.small("Reopen", () -> decide(flags,
                    ReviewDecision.OPEN));
            Parts.tip(reopen, "Take back the decision on this group.");
            buttons.add(reopen);
            return buttons;
        }

        Button reviewed = Parts.small("Mark as reviewed", () -> decide(
                flags, ReviewDecision.REJECTED), Tone.GOOD);
        Parts.tip(reviewed, "The code looked fine: the Similar code "
                + "flags of these students are rejected.");
        Button keep = Parts.small("Keep flagged", () -> decide(flags,
                ReviewDecision.REVIEWED));
        keep.getStyleClass().add("accent");
        Parts.tip(keep, "Keep these students flagged for a closer look. "
                + "Marks never change.");
        buttons.add(reviewed);
        buttons.add(keep);
        return buttons;
    }

    // Sets one decision on every flag of a group, then draws the view
    // again. The first problem, if any, is shown.
    private void decide(List<Anomaly> flags, ReviewDecision decision) {
        DashboardBackend backend = window.getBackend();
        String problem = "";
        for (Anomaly flag : flags) {
            ActionResult result = backend.setDecision(flag, decision);
            if (!result.isDone() && problem.isEmpty()) {
                problem = result.getMessage();
            }
        }
        if (!problem.isEmpty()) {
            window.notify(problem);
        }
        redraw();
        onDecision.run();
    }

    // ------------------------------------------------ opening and closing

    // Opens or closes a group, unless a button or list was clicked.
    private void headerClicked(MouseEvent event, String key) {
        if (isInControl(event.getTarget())) {
            return;
        }
        toggle(openKeys, key);
    }

    // Opens or closes a group with Enter or the space bar.
    private void headerKey(KeyEvent event, String key) {
        boolean opens = event.getCode() == KeyCode.ENTER
                || event.getCode() == KeyCode.SPACE;
        if (opens) {
            event.consume();
            toggle(openKeys, key);
        }
    }

    // Says whether a click landed on a button or list, or inside one.
    private boolean isInControl(Object target) {
        Node node = null;
        if (target instanceof Node) {
            node = (Node) target;
        }
        while (node != null && !(node instanceof ButtonBase)
                && !(node instanceof ComboBox)) {
            node = node.getParent();
        }
        return node != null;
    }

    // Adds a key to a list, or removes it when there, and draws the view
    // again.
    private void toggle(List<String> keys, String key) {
        if (keys.contains(key)) {
            keys.remove(key);
        } else {
            keys.add(key);
        }
        redraw();
    }

    // ----------------------------------------------------- what is shown

    // Names a group by its place under its question, such as Group 2.
    private String titleOf(SimilarityCluster cluster, int number) {
        return "Group " + number;
    }

    // Says whether every pair of a group is an identical copy.
    private boolean isIdentical(SimilarityCluster cluster) {
        for (SimilarityPair pair : cluster.getPairs()) {
            if (!pair.isIdentical()) {
                return false;
            }
        }
        return !cluster.getPairs().isEmpty();
    }

    // Says whether every pair of a group gives the same Email ID.
    private boolean isSameEmail(SimilarityCluster cluster) {
        for (SimilarityPair pair : cluster.getPairs()) {
            if (!pair.isSameEmail()) {
                return false;
            }
        }
        return !cluster.getPairs().isEmpty();
    }

    // Says in words what a group is.
    private String summaryOf(SimilarityCluster cluster) {
        if (isIdentical(cluster)) {
            return "The same file handed in: same Name, Email ID and "
                    + "code in the header and answer.";
        }
        if (isSameEmail(cluster)) {
            return "Their files give the same Email ID in the header, so "
                    + "one may be another's work.";
        }
        int size = cluster.getUsernames().size();
        if (size == PAIR_SIZE) {
            return "Two students whose " + cluster.getQuestionId()
                    + " code the check found alike.";
        }
        return size + " students linked by alike " + cluster.getQuestionId()
                + " code, each like at least one other.";
    }

    // Builds the chip that says how many matches a group is built on.
    private Label matchesChip(SimilarityCluster cluster) {
        Label chip = Parts.pill("Based on " + Parts.plural(
                cluster.getPairs().size(), "student-to-student match",
                "student-to-student matches"), Tone.NEUTRAL);
        chip.getStyleClass().add("matches-chip");
        chip.setMinWidth(Region.USE_PREF_SIZE);
        return chip;
    }

    // Builds one fact of a group's row, a column of a fixed width: an
    // icon and its words.
    private Node fact(String icon, String text, double width) {
        Node mark = Parts.icon(icon, ICON_SIZE);
        mark.getStyleClass().add("fact-icon");
        Label words = new Label(text);
        HBox fact = Parts.row(Parts.GAP_TINY, new Node[] {mark, words});
        fixWidth(fact, width);
        return fact;
    }

    // Puts a node in a column of a fixed width, at its left.
    private HBox cell(Node node, double width) {
        HBox cell = new HBox(node);
        cell.setAlignment(Pos.CENTER_LEFT);
        fixWidth(cell, width);
        return cell;
    }

    // Gives a node the same width in every row.
    private void fixWidth(Region node, double width) {
        node.setMinWidth(width);
        node.setPrefWidth(width);
        node.setMaxWidth(width);
    }

    // Builds the thin line between a group's words and its facts.
    private Region divider() {
        Region line = new Region();
        line.getStyleClass().add("fact-divider");
        line.setMinWidth(DIVIDER_WIDTH);
        line.setPrefWidth(DIVIDER_WIDTH);
        line.setMaxWidth(DIVIDER_WIDTH);
        return line;
    }

    // Says how alike a group's pairs are, such as "97.9% – 99.8%".
    private String rangeOf(SimilarityCluster cluster) {
        String lowest = percentOf(lowestOf(cluster));
        String highest = percentOf(cluster.getHighestSimilarity());
        if (lowest.equals(highest)) {
            return highest;
        }
        return lowest + RANGE_DASH + highest;
    }

    // Writes a share as a percentage with one decimal, such as 98.7%.
    private String percentOf(double share) {
        return String.format("%.1f", share * PERCENT) + Parts.PERCENT_SIGN;
    }

    // Returns how alike the least alike pair of a group is.
    private double lowestOf(SimilarityCluster cluster) {
        double lowest = cluster.getHighestSimilarity();
        for (SimilarityPair pair : cluster.getPairs()) {
            lowest = Math.min(lowest, pair.getSimilarity());
        }
        return lowest;
    }

    // Ranks how strong a group is, from its least alike pair, so every
    // pair in it is at least that strong: 4 very strong to 1 weak.
    private int strengthRank(SimilarityCluster cluster) {
        if (isIdentical(cluster)) {
            return IDENTICAL_RANK;
        }
        if (isSameEmail(cluster)) {
            return SAME_EMAIL_RANK;
        }
        double lowest = lowestOf(cluster);
        if (lowest >= VERY_STRONG) {
            return VERY_STRONG_RANK;
        } else if (lowest >= STRONG) {
            return STRONG_RANK;
        } else if (lowest >= MODERATE) {
            return MODERATE_RANK;
        }
        return WEAK_RANK;
    }

    // Builds the pill that says how strong a group is, with as many bars
    // filled as its rank: red when very strong, amber when strong.
    private Label strengthPill(SimilarityCluster cluster) {
        int rank = strengthRank(cluster);
        if (rank == IDENTICAL_RANK) {
            return identicalPill();
        } else if (rank == SAME_EMAIL_RANK) {
            return sameEmailPill();
        }
        String words = "Weak";
        Tone tone = Tone.NEUTRAL;
        if (rank == VERY_STRONG_RANK) {
            words = "Very strong";
            tone = Tone.BAD;
        } else if (rank == STRONG_RANK) {
            words = "Strong";
            tone = Tone.WARN;
        } else if (rank == MODERATE_RANK) {
            words = "Moderate";
        }

        Label pill = Parts.pill(words, tone);
        pill.setGraphic(strengthBars(rank));
        Parts.tip(pill, "Every pair in the group is at least "
                + percentOf(lowestOf(cluster)) + " alike. Very strong "
                + "from 95%, strong from 85%, moderate from 70%. Same Email "
                + "ID when the headers share one, and Identical copy when "
                + "the header and code are the same.");
        return pill;
    }

    // Builds the pill of an identical copy, with a copy icon in place of
    // bars, as it is certain rather than strong.
    private Label identicalPill() {
        Label pill = Parts.pill("Identical copy", Tone.BAD);
        Node mark = Parts.icon(Parts.COPY, ICON_SIZE);
        mark.getStyleClass().add("pill-icon");
        pill.setGraphic(mark);
        pill.getStyleClass().add("identical-pill");
        Parts.tip(pill, "Both files have the same Name and Email ID in "
                + "their header and the same code: one was handed in as "
                + "the other. Spaces and comments are not compared.");
        return pill;
    }

    // Builds the pill of a pair with the same Email ID, with an @ in place
    // of bars, as the header says it rather than the code.
    private Label sameEmailPill() {
        Label pill = Parts.pill("Same Email ID", Tone.BAD);
        Node mark = Parts.icon(Parts.EMAIL, ICON_SIZE);
        mark.getStyleClass().add("pill-icon");
        pill.setGraphic(mark);
        Parts.tip(pill, "Both files give the same Email ID in their "
                + "header, so one may be handed in as another's work. The "
                + "range shows how alike the code is once names are set "
                + "aside. Always flagged, whatever the settings say.");
        return pill;
    }

    // Builds the bars of a strength, with as many filled as its rank.
    private HBox strengthBars(int rank) {
        HBox bars = new HBox(BAR_GAP);
        bars.setAlignment(Pos.BOTTOM_LEFT);
        bars.setMaxHeight(Region.USE_PREF_SIZE);
        bars.setFillHeight(false);
        for (int bar = 1; bar <= STRENGTH_BARS; bar++) {
            Region step = new Region();
            double height = BAR_STEP * (bar + 1);
            step.setMinSize(BAR_WIDTH, height);
            step.setMaxSize(BAR_WIDTH, height);
            step.getStyleClass().add("strength-bar");
            if (bar <= rank) {
                step.getStyleClass().add("filled");
            }
            bars.getChildren().add(step);
        }
        return bars;
    }

    // Builds the pill that says whether a group was reviewed, with a
    // clock, a tick or a flag.
    private Label statusPill(List<Anomaly> flags) {
        String words = "Pending";
        Tone tone = Tone.WARN;
        String icon = Parts.CLOCK;
        if (!flags.isEmpty() && countOpen(flags) == 0) {
            words = "Reviewed";
            tone = Tone.GOOD;
            icon = Parts.CHECK;
            if (anyKept(flags)) {
                words = "Kept flagged";
                tone = Tone.BAD;
                icon = Parts.FLAG;
            }
        }
        Label pill = Parts.pill(words, tone);
        Node mark = Parts.icon(icon, ICON_SIZE);
        mark.getStyleClass().add("pill-icon");
        pill.setGraphic(mark);
        return pill;
    }

    // ----------------------------------------------------------- lookups

    // Returns the Similar code flags of a group's students for its
    // question.
    private List<Anomaly> findFlags(GradingRun run,
            SimilarityCluster cluster) {
        DashboardBackend backend = window.getBackend();
        List<Anomaly> flags = new ArrayList<>();
        for (Submission submission : run.getSubmissions()) {
            if (cluster.getUsernames().contains(submission.getUsername())) {
                addFlags(run, cluster, submission, backend, flags);
            }
        }
        return flags;
    }

    // Adds a student's Similar code flags for a group's question.
    private void addFlags(GradingRun run, SimilarityCluster cluster,
            Submission submission, DashboardBackend backend,
            List<Anomaly> flags) {
        for (Anomaly anomaly : submission.getAnomalies()) {
            SimilarityPair pair = null;
            if (anomaly.getType() == AnomalyType.SIMILAR_CODE) {
                pair = backend.findSimilarPair(run, anomaly);
            }
            boolean sameQuestion = pair != null && pair.getQuestionId()
                    .equals(cluster.getQuestionId());
            if (sameQuestion) {
                flags.add(anomaly);
            }
        }
    }

    // Counts the flags of a group still waiting for a decision.
    private int countOpen(List<Anomaly> flags) {
        int open = 0;
        for (Anomaly flag : flags) {
            if (window.getBackend().needsReview(flag)) {
                open++;
            }
        }
        return open;
    }

    // Says whether any flag of a group was kept flagged.
    private boolean anyKept(List<Anomaly> flags) {
        for (Anomaly flag : flags) {
            ReviewDecision decision = window.getBackend().getDecision(flag);
            if (decision == ReviewDecision.REVIEWED) {
                return true;
            }
        }
        return false;
    }

    // Returns the two students compared in a group: the ones chosen, or
    // the closest pair to start with.
    private String[] comparedOf(SimilarityCluster cluster) {
        int place = comparedKeys.indexOf(keyOf(cluster));
        if (place >= 0) {
            String[] chosen = comparedPairs.get(place);
            boolean stillThere = cluster.getUsernames().contains(chosen[0])
                    && cluster.getUsernames().contains(chosen[1]);
            if (stillThere) {
                return chosen;
            }
        }
        SimilarityPair closest = cluster.getPairs().get(0);
        String[] pair = {closest.getFirstUsername(),
            closest.getSecondUsername()};
        return pair;
    }

    // Returns the flagged pair of two students, or a pair with no
    // percentage when the check linked them only through others.
    private SimilarityPair pairOf(SimilarityCluster cluster, String first,
            String second) {
        SimilarityPair pair = cluster.findPair(first, second);
        if (pair == null) {
            return new SimilarityPair(first, second,
                    cluster.getQuestionId(), Double.NaN);
        }
        // Kept in the order chosen, so Student A is on the left
        if (!pair.getFirstUsername().equals(first)) {
            return new SimilarityPair(first, second,
                    cluster.getQuestionId(), pair.getSimilarity(),
                    pair.isIdentical(), pair.isSameEmail());
        }
        return pair;
    }

    // Names a group by its question and first student, as a question
    // may have two groups.
    private String keyOf(SimilarityCluster cluster) {
        return cluster.getQuestionId() + "|"
                + cluster.getUsernames().get(0);
    }

    // Names a student with their email ID, such as Aiden Toh
    // (aiden.toh.2025), or the email ID alone.
    private String describe(GradingRun run, String username) {
        String name = SimilaritySheet.nameOf(run, username);
        if (name.equals(username)) {
            return username;
        }
        return name + " (" + username + ")";
    }

    // Returns up to two initials of a name, such as AT for Aiden Toh or
    // AI for aiden.toh.2025.
    private String initialsOf(String name) {
        String initials = "";
        for (String word : name.split("[\\s.]+")) {
            boolean letter = !word.isEmpty()
                    && Character.isLetter(word.charAt(0));
            if (letter && initials.length() < INITIALS) {
                initials = initials + word.charAt(0);
            }
        }
        if (initials.isEmpty()) {
            return "?";
        }
        return initials.toUpperCase();
    }
}
