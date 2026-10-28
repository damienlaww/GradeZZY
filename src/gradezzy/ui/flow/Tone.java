/*
 * File: Tone.java
 *
 * Purpose: The four status colours of the guided window, each with one
 * meaning.
 *
 * Where it fits: Parts.pill adds the style class of a tone. Colour in the
 * guided window means status and nothing else.
 */
package gradezzy.ui.flow;

/**
 * A status colour.
 */
public enum Tone {

    /** Done, passed or approved. */
    GOOD("good"),

    /** The instructor should check this. */
    WARN("warn"),

    /** Failed, did not compile or cannot be used. */
    BAD("bad"),

    /** Waiting or nothing to act on. */
    NEUTRAL("neutral");

    // The CSS class that colours a pill
    private final String styleClass;

    // Creates a tone constant.
    Tone(String styleClass) {
        this.styleClass = styleClass;
    }

    /**
     * Returns the CSS class that colours a pill.
     *
     * @return the style class
     */
    public String getStyleClass() {
        return styleClass;
    }
}
