/*
 * File: TourStop.java
 *
 * Purpose: One stop of the guided tour: the tab or step it opens, the
 * part of the window it lights up, and the words on its card.
 *
 * Where it fits: GuidedTour builds the list of stops and shows them one
 * at a time.
 */
package gradezzy.ui.flow;

/**
 * One stop of the guided tour.
 *
 * <p>Tier: COULD.
 */
public class TourStop {

    /** Lights up the tab of the stop's page. */
    public static final String TAB = "tab";

    /** Lights up the stop's step in the row of four steps. */
    public static final String STEP = "step";

    // Goes between the text and the locked text
    private static final String SENTENCE_GAP = " ";

    // The tab the stop opens
    private final Page page;

    // The step of Grading the stop opens, or null for another tab
    private final Step step;

    // A CSS selector of the part lit up, TAB, STEP, or null for none
    private final String target;

    // The words above the title, such as "Set up"
    private final String area;

    // The title of the card
    private final String title;

    // One or two sentences under the title
    private final String text;

    // Added to the text while the step cannot be opened yet, or null
    private final String lockedText;

    /**
     * Creates a stop on a tab other than Grading.
     *
     * @param page the tab it opens
     * @param target the part it lights up, TAB, or null for none
     * @param area the words above the title
     * @param title the title of the card
     * @param text one or two sentences under the title
     */
    public TourStop(Page page, String target, String area, String title,
            String text) {
        this.page = page;
        this.step = null;
        this.target = target;
        this.area = area;
        this.title = title;
        this.text = text;
        this.lockedText = null;
    }

    /**
     * Creates a stop on a step of Grading.
     *
     * @param step the step it opens
     * @param target the part it lights up, STEP, or null for none
     * @param title the title of the card
     * @param text one or two sentences under the title
     * @param lockedText added to the text while the step cannot be
     *        opened yet, or null
     */
    public TourStop(Step step, String target, String title, String text,
            String lockedText) {
        this.page = Page.MARKING;
        this.step = step;
        this.target = target;
        this.area = step.getLabel();
        this.title = title;
        this.text = text;
        this.lockedText = lockedText;
    }

    /**
     * Returns the tab the stop opens.
     *
     * @return the tab
     */
    public Page getPage() {
        return page;
    }

    /**
     * Returns the step of Grading the stop opens.
     *
     * @return the step, or null for a tab other than Grading
     */
    public Step getStep() {
        return step;
    }

    /**
     * Returns the part of the window the stop lights up.
     *
     * @return a CSS selector, TAB, STEP, or null for none
     */
    public String getTarget() {
        return target;
    }

    /**
     * Returns the words above the title.
     *
     * @return the area, such as "Set up"
     */
    public String getArea() {
        return area;
    }

    /**
     * Returns the title of the card.
     *
     * @return the title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the sentences under the title.
     *
     * @param locked true while the step cannot be opened yet
     * @return the text
     */
    public String getText(boolean locked) {
        if (locked && lockedText != null) {
            return text + SENTENCE_GAP + lockedText;
        }
        return text;
    }
}
