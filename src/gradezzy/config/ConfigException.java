/*
 * File: ConfigException.java
 *
 * Purpose: Signals that config.properties is missing a setting or holds a
 * value of the wrong kind.
 *
 * Where it fits: Thrown by AppConfig. It is unchecked so that a broken
 * configuration stops the application at start-up with a clear message,
 * instead of every caller having to handle it.
 */
package gradezzy.config;

/**
 * Signals a missing or invalid setting in config.properties.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class ConfigException extends RuntimeException {

    /**
     * Creates the exception with a message for the instructor.
     *
     * <p>Tier: MUST.
     *
     * @param message what is wrong with the configuration
     */
    public ConfigException(String message) {
        super(message);
    }
}
