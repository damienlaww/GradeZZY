/*
 * File: SettingsService.java
 *
 * Purpose: Reads, checks and saves settings for the settings page.
 *
 * Where it fits: Keeps the settings page backed by config.properties.
 */
package gradezzy.service;

import java.io.IOException;

import gradezzy.config.AppConfig;

/**
 * Reads, checks and saves settings for the settings page.
 *
 * <p>Tier: SHOULD. Owner: Jing Yuan.
 */
public class SettingsService {

    // The application settings
    private final AppConfig config;

    /**
     * Creates a new settings service.
     *
     * @param config the application settings
     */
    public SettingsService(AppConfig config) {
        this.config = config;
    }

    /**
     * Returns the current value of a setting.
     *
     * @param key the key
     * @return the value, or an empty string if it is not set
     */
    public String getSetting(String key) {
        // TODO(Jing Yuan): implement: reading the value from config
        return "";
    }

    /**
     * Checks a new value before it is accepted.
     *
     * @param key the key
     * @param value the value
     * @return an error message, or an empty string if the value is valid
     */
    public String validate(String key, String value) {
        // TODO(Jing Yuan): implement: rules per key, for example a positive
        //     whole number for time limits
        return "";
    }

    /**
     * Changes a setting in memory.
     *
     * @param key the key
     * @param value the value
     */
    public void updateSetting(String key, String value) {
        // TODO(Jing Yuan): implement: validating and then storing the value
    }

    /**
     * Writes the settings back to config.properties.
     *
     * @throws IOException if the operation fails
     */
    public void save() throws IOException {
        // TODO(Jing Yuan): implement: saving the properties file, keeping the
        //     comments
    }
}
