/*
 * File: AppConfig.java
 *
 * Purpose: Loads config.properties and hands out its values as text,
 * numbers, flags and lists.
 *
 * Where it fits: The single place configuration is read. Every other class
 * asks this class for a setting by a name from ConfigKeys, so no value is
 * hard-coded in the logic.
 */
package gradezzy.config;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Loads config.properties and gives typed access to its settings.
 *
 * <p>Tier: MUST. Owner: Jing Yuan.
 */
public class AppConfig {

    /**
     * The configuration file used when no other file is named.
     */
    public static final String DEFAULT_CONFIG_FILE = "config.properties";

    /**
     * The system property that can name a different configuration file,
     * for example java -Dgradezzy.config=other.properties.
     */
    public static final String CONFIG_FILE_PROPERTY = "gradezzy.config";

    // The character between the items of a list setting
    private static final String LIST_SEPARATOR = ",";

    // The settings read from the file
    private final Properties properties = new Properties();

    // The file the settings were read from
    private final String configFile;

    /**
     * Loads the settings from a file.
     *
     * @param configFile the path of the properties file
     * @throws IOException if the file cannot be read
     */
    public AppConfig(String configFile) throws IOException {
        this.configFile = configFile;
        FileReader reader = new FileReader(configFile);

        try {
            properties.load(reader);
        } finally {
            reader.close();
        }
    }

    /**
     * Loads the default configuration file, or the file named by the
     * gradezzy.config system property when it is set.
     *
     * @return the loaded configuration
     * @throws IOException if the file cannot be read
     */
    public static AppConfig loadDefault() throws IOException {
        String fileName = System.getProperty(CONFIG_FILE_PROPERTY,
                DEFAULT_CONFIG_FILE);
        return new AppConfig(fileName);
    }

    /**
     * Returns the file the settings were read from.
     *
     * @return the path of the properties file
     */
    public String getConfigFile() {
        return configFile;
    }

    /**
     * Says whether a setting is present in the file.
     *
     * @param key the name of the setting, from ConfigKeys
     * @return true if the setting exists, even if it is empty
     */
    public boolean hasKey(String key) {
        return properties.getProperty(key) != null;
    }

    /**
     * Returns a setting as text.
     *
     * @param key the name of the setting, from ConfigKeys
     * @return the value with spaces at both ends removed
     * @throws ConfigException if the setting is missing
     */
    public String getString(String key) {
        String value = properties.getProperty(key);

        if (value == null) {
            throw new ConfigException("Missing setting '" + key + "' in "
                    + configFile);
        }

        return value.trim();
    }

    /**
     * Returns a setting as a whole number.
     *
     * @param key the name of the setting, from ConfigKeys
     * @return the value as an int
     * @throws ConfigException if the setting is missing or not a number
     */
    public int getInt(String key) {
        String value = getString(key);

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new ConfigException(describeBadValue(key, value,
                    "a whole number"));
        }
    }

    /**
     * Returns a setting as a large whole number.
     *
     * @param key the name of the setting, from ConfigKeys
     * @return the value as a long
     * @throws ConfigException if the setting is missing or not a number
     */
    public long getLong(String key) {
        String value = getString(key);

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new ConfigException(describeBadValue(key, value,
                    "a whole number"));
        }
    }

    /**
     * Returns a setting as a decimal number.
     *
     * @param key the name of the setting, from ConfigKeys
     * @return the value as a double
     * @throws ConfigException if the setting is missing or not a number
     */
    public double getDouble(String key) {
        String value = getString(key);

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new ConfigException(describeBadValue(key, value,
                    "a number"));
        }
    }

    /**
     * Returns a setting as a flag. The value must be true or false, in any
     * case. Anything else, such as yes or on, is an error rather than
     * being read as false, so a typing mistake cannot quietly switch off
     * the database or a check.
     *
     * @param key the name of the setting, from ConfigKeys
     * @return the value as a boolean
     * @throws ConfigException if the setting is missing or not true or
     *         false
     */
    public boolean getBoolean(String key) {
        String value = getString(key).trim();

        if (value.equalsIgnoreCase(Boolean.TRUE.toString())) {
            return true;
        }

        if (value.equalsIgnoreCase(Boolean.FALSE.toString())) {
            return false;
        }

        throw new ConfigException(describeBadValue(key, value,
                "true or false"));
    }

    /**
     * Returns a comma separated setting as a list. Empty items are left
     * out, so an empty setting gives an empty list.
     *
     * @param key the name of the setting, from ConfigKeys
     * @return the items, with spaces around each one removed
     * @throws ConfigException if the setting is missing
     */
    public List<String> getList(String key) {
        List<String> items = new ArrayList<>();
        String[] parts = getString(key).split(LIST_SEPARATOR);

        for (String part : parts) {
            String item = part.trim();

            if (!item.isEmpty()) {
                items.add(item);
            }
        }

        return items;
    }

    /**
     * Changes a setting in memory. The file is not written; see
     * SettingsService.save for that.
     *
     * @param key the name of the setting, from ConfigKeys
     * @param value the new value
     */
    public void setString(String key, String value) {
        properties.setProperty(key, value);
    }

    // Builds the message used when a value has the wrong form.
    private String describeBadValue(String key, String value,
            String expected) {
        return "Setting '" + key + "' in " + configFile + " must be "
                + expected + " but is '" + value + "'";
    }
}
