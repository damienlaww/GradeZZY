/*
 * File: KeptBackupsView.java
 *
 * Purpose: The Kept backups card of History, Backups: one row per kept
 * backup file, newest first, with when it was made, its size and how
 * many saved runs it holds. A row opens like a dropdown to list those
 * runs, and has Restore this backup.
 *
 * Where it fits: RunsScreen shows it under Back up now and Restore from
 * a backup file. It reads the backups through
 * DashboardBackend.listBackups and hands a restore back to RunsScreen,
 * which asks first and offers Undo.
 */
package gradezzy.ui.flow;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import gradezzy.model.BackupInfo;
import gradezzy.model.BackupRun;

/**
 * Lists the kept backups and the saved runs inside each.
 *
 * <p>Tier: COULD.
 */
public class KeptBackupsView {

    // How dates are shown, such as 11 Nov 2026, 01:44
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    // The size of the chevron and the archive icon
    private static final double ICON_SIZE = 14.0;

    // How far an open row's chevron turns, so it points down
    private static final double OPEN_ROTATION = 90.0;

    // The gap between a backup's time and the line under it
    private static final double LINE_GAP = 2.0;

    // An open backup lists this many runs until Show all is clicked
    private static final int RUNS_SHOWN = 8;

    // Column widths of a run inside an open backup
    private static final double SECTION_WIDTH = 110.0;
    private static final double WHEN_WIDTH = 150.0;
    private static final double STUDENTS_WIDTH = 100.0;
    private static final double STATUS_WIDTH = 120.0;

    // Bytes in a kilobyte, and kilobytes in a megabyte
    private static final double BYTES_PER_KILOBYTE = 1024.0;

    // Writes a size in whole kilobytes, such as 840 KB, or in megabytes
    // with one decimal place, such as 21.1 MB
    private static final String KILOBYTES_FORMAT = "%.0f KB";
    private static final String MEGABYTES_FORMAT = "%.1f MB";

    // Separates parts of a line, such as a file name from its size
    private static final String DOT = " · ";

    // Shown in place of a value that was not saved
    private static final String NO_VALUE = "-";

    // Where the runs list sits in an open row, after its header
    private static final int RUNS_LIST_INDEX = 1;

    // The backups that are open, by file
    private final Set<Path> openFiles = new HashSet<>();

    // The open backups whose every run is listed, by file
    private final Set<Path> allRunsFiles = new HashSet<>();

    // Whether a row was opened or closed by hand; until then the newest
    // backup opens by itself
    private boolean touched = false;

    // Asks to restore a backup file
    private final Consumer<Path> restoreAction;

    /**
     * Creates the view.
     *
     * <p>Tier: COULD.
     *
     * @param restoreAction asks to restore a backup file, with the same
     *        confirmation and Undo as Choose a backup
     */
    public KeptBackupsView(Consumer<Path> restoreAction) {
        this.restoreAction = restoreAction;
    }

    /**
     * Builds the Kept backups card.
     *
     * <p>Tier: COULD.
     *
     * @param backups the kept backups, newest first
     * @param keepCount how many backups are kept
     * @param canRestore false while a batch is being graded, which
     *        disables Restore this backup
     * @return the card
     */
    public VBox build(List<BackupInfo> backups, int keepCount,
            boolean canRestore) {
        Label title = Parts.strong("Kept backups");
        Label count = Parts.muted(keptText(backups, keepCount));
        count.setWrapText(true);
        VBox words = new VBox(Parts.GAP_TINY, title, count);
        VBox card = Parts.card(null, new Node[] {words});
        card.getStyleClass().add("kept-backups");
        if (backups.isEmpty()) {
            return card;
        }

        if (!touched && openFiles.isEmpty()) {
            openFiles.add(backups.get(0).getFile());
        }
        VBox list = new VBox();
        list.getStyleClass().addAll("errors-card", "backup-list");
        for (int index = 0; index < backups.size(); index++) {
            BackupInfo backup = backups.get(index);
            VBox row = null;
            if (backup.isReadable()) {
                row = backupRow(backup, index == 0, canRestore);
            } else {
                row = unreadableRow(backup, index == 0);
            }
            list.getChildren().add(row);
        }
        list.getChildren().get(0).getStyleClass().add("first");
        card.getChildren().add(list);
        return card;
    }

    // Says how many backups are kept out of the limit. Copies made
    // before a restore are kept apart and do not count.
    private String keptText(List<BackupInfo> backups, int keepCount) {
        int copies = 0;
        for (BackupInfo backup : backups) {
            if (backup.isBeforeRestore()) {
                copies++;
            }
        }
        int count = backups.size() - copies;
        return countText(count, keepCount) + copiesText(copies);
    }

    // Says how many backups made with Back up now are kept
    private String countText(int count, int keepCount) {
        if (count == 0) {
            return "No backups yet. Back up now makes the first one.";
        }
        if (count > keepCount) {
            return count + " kept; the oldest are deleted at the next "
                    + "backup, leaving the newest " + keepCount + ".";
        }
        String kept = count + " of " + keepCount + " kept";
        if (count == keepCount) {
            return kept + "; the oldest is deleted at the next backup.";
        }
        return kept + "; the oldest is deleted once there are "
                + keepCount + ".";
    }

    // Says how many copies made before a restore are listed too
    private String copiesText(int copies) {
        if (copies == 0) {
            return "";
        }
        return " Also " + Parts.plural(copies, "copy", "copies")
                + " made before a restore, which do not count.";
    }

    // ---------------------------------------------------------- a backup

    // Builds one backup: a header row that opens and closes, and, when
    // open, the runs it holds.
    private VBox backupRow(BackupInfo backup, boolean newest,
            boolean canRestore) {
        boolean open = openFiles.contains(backup.getFile());
        Node chevron = Parts.icon(Parts.CHEVRON, ICON_SIZE);
        if (open) {
            chevron.setRotate(OPEN_ROTATION);
        }
        chevron.getStyleClass().add("error-chevron");

        int runCount = backup.getRuns().size();
        Label runs = Parts.muted(Parts.plural(runCount, "run"));
        runs.setMinWidth(Region.USE_PREF_SIZE);
        Button restoreButton = Parts.small("Restore this backup",
                () -> restoreAction.accept(backup.getFile()));
        restoreButton.setDisable(!canRestore);
        Parts.tip(restoreButton, "Replaces every saved result with the "
                + "copy in this backup");

        HBox header = headerRow(backup, newest);
        header.getChildren().add(0, chevron);
        header.getChildren().addAll(Parts.grow(), runs, restoreButton);
        header.getStyleClass().add("error-header");
        header.setFocusTraversable(true);
        header.setAccessibleText("Backup of " + whenText(backup) + ", "
                + runs.getText() + ". Press Enter to show its runs.");
        Parts.tipNode(header, "Click to show or hide the runs it holds");

        VBox row = new VBox(header);
        row.getStyleClass().addAll("error-row", "backup-row");
        // The file names the backup, so a check can find its row
        row.setUserData(backup.getFile());
        header.setOnMouseClicked(event -> headerClicked(event, row,
                backup, chevron));
        header.setOnKeyPressed(event -> headerKey(event, row, backup,
                chevron));
        if (open) {
            row.getStyleClass().add("open");
            row.getChildren().add(runList(backup));
        }
        return row;
    }

    // Builds a backup that cannot be read: what is known of the file and
    // why, with no runs and no Restore.
    private VBox unreadableRow(BackupInfo backup, boolean newest) {
        HBox header = headerRow(backup, newest);
        header.getChildren().addAll(Parts.grow(),
                Parts.pill("Cannot be read", Tone.BAD));
        header.getStyleClass().add("backup-plain");
        Label problem = Parts.muted("This backup cannot be read, so its "
                + "runs are not listed and it cannot be restored. "
                + backup.getProblem());
        problem.getStyleClass().add("small-text");
        problem.setWrapText(true);
        VBox row = new VBox(header, problem);
        row.getStyleClass().addAll("error-row", "backup-row", "unreadable");
        row.setUserData(backup.getFile());
        return row;
    }

    // The parts of a backup's header both kinds of row share: the
    // archive icon, when it was made, its file and size, and whether it
    // is the newest.
    private HBox headerRow(BackupInfo backup, boolean newest) {
        Node archive = Parts.icon(Parts.ARCHIVE, ICON_SIZE);
        archive.getStyleClass().add("backup-icon");
        Label when = Parts.strong(whenText(backup));
        Label file = Parts.muted(fileName(backup) + DOT
                + sizeText(backup.getSizeBytes()));
        file.getStyleClass().add("small-text");
        VBox words = new VBox(LINE_GAP, when, file);
        HBox header = Parts.row(Parts.GAP, new Node[] {archive, words});
        if (newest) {
            header.getChildren().add(Parts.pill("Newest", Tone.GOOD));
        }
        if (backup.isBeforeRestore()) {
            header.getChildren().add(Parts.pill("Before a restore",
                    Tone.NEUTRAL));
        }
        return header;
    }

    // Opens or closes a backup, unless a button in its row was clicked.
    private void headerClicked(MouseEvent event, VBox row,
            BackupInfo backup, Node chevron) {
        if (isInButton(event.getTarget())) {
            return;
        }
        toggle(row, backup, chevron);
    }

    // Opens or closes a backup with Enter or the space bar.
    private void headerKey(KeyEvent event, VBox row, BackupInfo backup,
            Node chevron) {
        boolean opens = event.getCode() == KeyCode.ENTER
                || event.getCode() == KeyCode.SPACE;
        if (opens) {
            event.consume();
            toggle(row, backup, chevron);
        }
    }

    // Says whether a click landed on a button or inside one.
    private boolean isInButton(Object target) {
        Node node = null;
        if (target instanceof Node) {
            node = (Node) target;
        }
        while (node != null && !(node instanceof ButtonBase)) {
            node = node.getParent();
        }
        return node != null;
    }

    // Opens a closed backup or closes an open one, in place.
    private void toggle(VBox row, BackupInfo backup, Node chevron) {
        touched = true;
        Path file = backup.getFile();
        if (openFiles.contains(file)) {
            openFiles.remove(file);
            row.getStyleClass().remove("open");
            row.getChildren().remove(RUNS_LIST_INDEX,
                    row.getChildren().size());
            chevron.setRotate(0);
            return;
        }
        openFiles.add(file);
        row.getStyleClass().add("open");
        row.getChildren().add(runList(backup));
        chevron.setRotate(OPEN_ROTATION);
    }

    // ------------------------------------------------------- its runs

    // Lists the runs of an open backup, newest first; a long list shows
    // the newest few with a link to show the rest.
    private VBox runList(BackupInfo backup) {
        VBox list = new VBox();
        list.getStyleClass().add("backup-runs");
        List<BackupRun> runs = backup.getRuns();
        if (runs.isEmpty()) {
            Label none = Parts.muted("This backup holds no saved runs.");
            none.getStyleClass().add("backup-run");
            list.getChildren().add(none);
            return list;
        }

        boolean showAll = allRunsFiles.contains(backup.getFile());
        int shown = runs.size();
        if (!showAll) {
            shown = Math.min(runs.size(), RUNS_SHOWN);
        }
        for (int index = 0; index < shown; index++) {
            list.getChildren().add(runLine(runs.get(index)));
        }
        if (runs.size() > RUNS_SHOWN) {
            list.getChildren().add(moreLink(list, backup, showAll));
        }
        return list;
    }

    // Shows one run: section, assessment, when it was graded, how many
    // students and whether all of them were graded.
    private HBox runLine(BackupRun run) {
        Label section = Parts.strong(sectionText(run));
        fixWidth(section, SECTION_WIDTH);
        Label assessment = new Label(textOr(run.getAssessmentName()));
        assessment.setMinWidth(0);
        assessment.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(assessment, Priority.ALWAYS);
        Label graded = Parts.muted(gradedText(run.getStartedAt()));
        fixWidth(graded, WHEN_WIDTH);
        Label students = Parts.muted(Parts.plural(run.getStudentCount(),
                "student"));
        fixWidth(students, STUDENTS_WIDTH);
        HBox line = Parts.row(Parts.GAP, new Node[] {section, assessment,
            graded, students, statusCell(run)});
        line.getStyleClass().add("backup-run");
        return line;
    }

    // Says whether every student of a run was graded.
    private Node statusCell(BackupRun run) {
        int waiting = run.getStudentCount() - run.getGradedCount();
        Node cell = Parts.muted("All graded");
        if (run.getStudentCount() == 0) {
            cell = Parts.muted(NO_VALUE);
        } else if (waiting > 0) {
            cell = Parts.pill(waiting + " not graded", Tone.WARN);
        }
        HBox box = new HBox(cell);
        fixWidth(box, STATUS_WIDTH);
        return box;
    }

    // The link under a long list: Show all 38 runs, or Show fewer.
    private Hyperlink moreLink(VBox list, BackupInfo backup,
            boolean showAll) {
        String text = "Show all " + Parts.plural(backup.getRuns().size(),
                "run");
        if (showAll) {
            text = "Show fewer";
        }
        Hyperlink link = new Hyperlink(text);
        link.getStyleClass().add("backup-more");
        link.setOnAction(event -> showMore(list, backup));
        return link;
    }

    // Lists every run of a backup, or the newest few again.
    private void showMore(VBox list, BackupInfo backup) {
        Path file = backup.getFile();
        if (allRunsFiles.contains(file)) {
            allRunsFiles.remove(file);
        } else {
            allRunsFiles.add(file);
        }
        list.getChildren().setAll(runList(backup).getChildren());
    }

    // ---------------------------------------------------------- words

    // When a backup was made, such as 11 Nov 2026, 01:44
    private String whenText(BackupInfo backup) {
        if (backup.getMadeAt() == null) {
            return "Unknown time";
        }
        return backup.getMadeAt().format(WHEN);
    }

    // The name of a backup's file
    private String fileName(BackupInfo backup) {
        boolean noName = backup.getFile() == null
                || backup.getFile().getFileName() == null;
        if (noName) {
            return NO_VALUE;
        }
        return backup.getFile().getFileName().toString();
    }

    // Writes a size such as 840 KB or 21.1 MB
    private String sizeText(long bytes) {
        double kilobytes = bytes / BYTES_PER_KILOBYTE;
        if (kilobytes < BYTES_PER_KILOBYTE) {
            return String.format(KILOBYTES_FORMAT, kilobytes);
        }
        return String.format(MEGABYTES_FORMAT,
                kilobytes / BYTES_PER_KILOBYTE);
    }

    // The module and section of a run, such as CS102 · G1
    private String sectionText(BackupRun run) {
        boolean noSection = run.getModuleCode().isEmpty()
                && run.getSectionCode().isEmpty();
        if (noSection) {
            return NO_VALUE;
        }
        return textOr(run.getModuleCode()) + DOT
                + textOr(run.getSectionCode());
    }

    // When a run was graded, or a dash when not saved
    private String gradedText(LocalDateTime startedAt) {
        if (startedAt == null) {
            return NO_VALUE;
        }
        return startedAt.format(WHEN);
    }

    // The text, or a dash when it is empty
    private String textOr(String text) {
        if (text.isEmpty()) {
            return NO_VALUE;
        }
        return text;
    }

    // Gives a cell a fixed width, so the runs line up
    private void fixWidth(Region cell, double width) {
        cell.setMinWidth(width);
        cell.setPrefWidth(width);
        cell.setMaxWidth(width);
    }
}
