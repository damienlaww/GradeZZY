/*
 * File: Tone.java
 *
 * Purpose: The status colours of the interface, each tied to one meaning.
 *
 * Where it fits: StatusBadge and banners add the style class of a tone.
 * Green, amber, red and the information blue mean grading status and
 * nothing else; NEUTRAL is for states that need no attention.
 *
 * AI use: drafted with Claude Code, reviewed by Jun Sheng.
 */
package gradezzy.ui.component;

/**
 * A status colour.
 *
 * <p>Tier: SHOULD. Owner: Damien.
 */
public enum Tone {

    /** Passed, completed or approved. */
    SUCCESS("tone-success", Icon.CHECK),

    /** Needs the instructor's review. */
    WARNING("tone-warning", Icon.WARNING),

    /** Failed or could not be used. */
    ERROR("tone-error", Icon.ERROR),

    /** Information only. */
    INFO("tone-info", Icon.INFO),

    /** Nothing to act on. */
    NEUTRAL("tone-neutral", Icon.UNGRADED);

    // The CSS class that colours the icon
    private final String styleClass;

    // The icon shown when no other is given
    private final Icon defaultIcon;

    // Creates a tone constant.
    Tone(String styleClass, Icon defaultIcon) {
        this.styleClass = styleClass;
        this.defaultIcon = defaultIcon;
    }

    /**
     * Returns the CSS class that colours the icon.
     *
     * @return the style class
     */
    public String getStyleClass() {
        return styleClass;
    }

    /**
     * Returns the icon shown when no other is given.
     *
     * @return the icon
     */
    public Icon getDefaultIcon() {
        return defaultIcon;
    }
}
