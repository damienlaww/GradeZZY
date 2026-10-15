/*
 * File: Labels.java
 *
 * Purpose: Turns enum values, marks and times into the plain words the
 * instructor sees, in one place so every screen uses the same wording.
 *
 * Where it fits: Used by StatusBadge and every controller. Wording
 * follows instructor terms ("Timed out", "Needs review") and never shows
 * raw enum names, exit codes or stack traces.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import gradezzy.model.AnomalyType;
import gradezzy.model.FixStatus;
import gradezzy.model.Severity;
import gradezzy.ui.backend.ImportState;
import gradezzy.ui.backend.ResultState;
import gradezzy.ui.backend.ReviewDecision;

/**
 * Plain-language labels.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public final class Labels {

    /** The text shown for a mark that does not exist yet. */
    public static final String NO_MARK = "\u2013";

    // Seconds in one minute
    private static final long SECONDS_PER_MINUTE = 60L;

    // One hundred percent
    private static final double FULL_PERCENT = 100.0;

    // How dates and times are written
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    // How times of day are written in logs
    private static final DateTimeFormatter TIME_OF_DAY =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    // Utility class, never instantiated.
    private Labels() {
        // nothing to set up
    }

    /**
     * Returns the plain name of an anomaly type.
     *
     * @param type the type
     * @return the name, such as "Timed out"
     */
    public static String anomalyTitle(AnomalyType type) {
        if (type == AnomalyType.MISSING_WRAPPER_FOLDER) {
            return "Missing student folder";
        } else if (type == AnomalyType.WRONG_FOLDER_NAME) {
            return "Wrong folder name";
        } else if (type == AnomalyType.MISSING_QUESTION_FOLDER) {
            return "Missing question folder";
        } else if (type == AnomalyType.MISSING_FILE) {
            return "Missing file";
        } else if (type == AnomalyType.WRONG_FILE_NAME) {
            return "Wrong file name";
        } else if (type == AnomalyType.MISSING_HEADER_NAME) {
            return "Missing name in header";
        } else if (type == AnomalyType.UNKNOWN_STUDENT) {
            return "Not on the roster";
        } else if (type == AnomalyType.INVALID_ARCHIVE) {
            return "Unreadable ZIP file";
        } else if (type == AnomalyType.COMPILE_ERROR) {
            return "Compile error";
        } else if (type == AnomalyType.TIMEOUT) {
            return "Timed out";
        } else if (type == AnomalyType.RUNTIME_ERROR) {
            return "Runtime error";
        } else if (type == AnomalyType.UNTOUCHED_STARTER) {
            return "Untouched starter file";
        } else if (type == AnomalyType.EMPTY_ANSWER) {
            return "Empty answer";
        } else if (type == AnomalyType.HARD_CODED_ANSWER) {
            return "Possible hard-coded answer";
        } else if (type == AnomalyType.SIMILAR_CODE) {
            return "Similar code";
        }
        return "Could not grade";
    }

    /**
     * Returns the word shown for a severity.
     *
     * @param severity the severity
     * @return "Note", "Review" or "Error"
     */
    public static String severityLabel(Severity severity) {
        if (severity == Severity.ERROR) {
            return "Error";
        } else if (severity == Severity.WARNING) {
            return "Review";
        }
        return "Note";
    }

    /**
     * Returns a number for sorting severities, errors first.
     *
     * @param severity the severity
     * @return 0 for errors, 1 for warnings, 2 for notes
     */
    public static int severityRank(Severity severity) {
        if (severity == Severity.ERROR) {
            return 0;
        } else if (severity == Severity.WARNING) {
            return 1;
        }
        return 2;
    }

    /**
     * Returns the words shown for a repair state.
     *
     * @param status the fix status
     * @return the label
     */
    public static String fixLabel(FixStatus status) {
        if (status == FixStatus.APPLIED) {
            return "Applied, waiting for review";
        } else if (status == FixStatus.APPROVED) {
            return "Approved";
        } else if (status == FixStatus.UNDONE) {
            return "Rejected and undone";
        } else if (status == FixStatus.PENDING) {
            return "Proposed, not applied";
        }
        return "No automatic repair";
    }

    /**
     * Returns the tone of a result state.
     *
     * @param state the state
     * @return the tone
     */
    public static Tone toneOf(ResultState state) {
        if (state == ResultState.COMPLETED) {
            return Tone.SUCCESS;
        } else if (state == ResultState.NEEDS_REVIEW) {
            return Tone.WARNING;
        } else if (state == ResultState.ZERO
                || state == ResultState.UNGRADED) {
            return Tone.NEUTRAL;
        }
        return Tone.ERROR;
    }

    /**
     * Returns the icon of a result state. Every state has its own shape.
     *
     * @param state the state
     * @return the icon
     */
    public static Icon iconOf(ResultState state) {
        if (state == ResultState.COMPLETED) {
            return Icon.CHECK;
        } else if (state == ResultState.NEEDS_REVIEW) {
            return Icon.WARNING;
        } else if (state == ResultState.ZERO) {
            return Icon.ZERO;
        } else if (state == ResultState.UNGRADED) {
            return Icon.UNGRADED;
        } else if (state == ResultState.TIMED_OUT) {
            return Icon.TIMEOUT;
        }
        return Icon.ERROR;
    }

    /**
     * Returns the tone of a severity.
     *
     * @param severity the severity
     * @return the tone
     */
    public static Tone toneOf(Severity severity) {
        if (severity == Severity.ERROR) {
            return Tone.ERROR;
        } else if (severity == Severity.WARNING) {
            return Tone.WARNING;
        }
        return Tone.INFO;
    }

    /**
     * Returns the tone of a repair state.
     *
     * @param status the fix status
     * @return the tone
     */
    public static Tone toneOf(FixStatus status) {
        if (status == FixStatus.APPROVED) {
            return Tone.SUCCESS;
        } else if (status == FixStatus.UNDONE) {
            return Tone.NEUTRAL;
        }
        return Tone.WARNING;
    }

    /**
     * Returns the tone of a review decision.
     *
     * @param decision the decision
     * @return the tone
     */
    public static Tone toneOf(ReviewDecision decision) {
        if (decision == ReviewDecision.OPEN) {
            return Tone.WARNING;
        } else if (decision == ReviewDecision.REJECTED) {
            return Tone.NEUTRAL;
        }
        return Tone.SUCCESS;
    }

    /**
     * Returns the tone of an import state.
     *
     * @param state the state
     * @return the tone
     */
    public static Tone toneOf(ImportState state) {
        if (state == ImportState.ACCEPTED) {
            return Tone.SUCCESS;
        } else if (state == ImportState.INVALID) {
            return Tone.ERROR;
        }
        return Tone.WARNING;
    }

    /**
     * Writes a mark without a decimal point when it is whole.
     *
     * @param score the mark
     * @return the text, such as "3" or "2.5"
     */
    public static String formatScore(double score) {
        if (score == Math.rint(score)) {
            return String.valueOf((long) score);
        }
        return String.valueOf(score);
    }

    /**
     * Writes a mark out of a maximum.
     *
     * @param score the mark
     * @param max the maximum
     * @return the text, such as "2 of 3"
     */
    public static String formatMarks(double score, double max) {
        return formatScore(score) + " of " + formatScore(max);
    }

    /**
     * Writes a share as a whole percentage.
     *
     * @param fraction the share, from 0 to 1
     * @return the text, such as "67%"
     */
    public static String formatPercent(double fraction) {
        return Math.round(fraction * FULL_PERCENT) + "%";
    }

    /**
     * Writes a duration in minutes and seconds.
     *
     * @param seconds the duration, or a negative number when unknown
     * @return the text, such as "1 min 5 s", or "Estimating"
     */
    public static String formatDuration(long seconds) {
        if (seconds < 0) {
            return "Estimating";
        }
        if (seconds < SECONDS_PER_MINUTE) {
            return seconds + " s";
        }
        return (seconds / SECONDS_PER_MINUTE) + " min "
                + (seconds % SECONDS_PER_MINUTE) + " s";
    }

    /**
     * Writes a date and time.
     *
     * @param time the time
     * @return the text, such as "15 Oct 2026, 09:42"
     */
    public static String formatDateTime(LocalDateTime time) {
        if (time == null) {
            return "";
        }
        return time.format(DATE_TIME);
    }

    /**
     * Writes a time of day with seconds.
     *
     * @param time the time
     * @return the text, such as "09:42:06"
     */
    public static String formatTime(LocalDateTime time) {
        if (time == null) {
            return "";
        }
        return time.format(TIME_OF_DAY);
    }

    /**
     * Returns "s" for any count other than one.
     *
     * @param count the count
     * @return "" or "s"
     */
    public static String plural(int count) {
        if (count == 1) {
            return "";
        }
        return "s";
    }
}
