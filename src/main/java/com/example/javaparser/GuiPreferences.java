package com.example.javaparser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GuiPreferences {
    private static final Logger log = LoggerFactory.getLogger(GuiPreferences.class);
    private static final String CONFIG_DIR = ".javaparser-modifier";
    private static final String CONFIG_FILE = "gui.properties";
    private static final String KEY_DIFF_SPLIT_RATIO = "diff.split.ratio";
    private static final String KEY_THEME = "ui.theme";

    private final Path configPath;

    public GuiPreferences() {
        this.configPath = resolveConfigPath();
    }

    public double loadDiffSplitRatio(double fallback) {
        Properties properties = loadProperties();
        String value = properties.getProperty(KEY_DIFF_SPLIT_RATIO);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            log.warn("Invalid diff split ratio in config: {}", value);
            return fallback;
        }
    }

    public void saveDiffSplitRatio(double ratio) {
        Properties properties = loadProperties();
        properties.setProperty(KEY_DIFF_SPLIT_RATIO, Double.toString(ratio));
        saveProperties(properties);
    }

    public String loadTheme(String fallback) {
        Properties properties = loadProperties();
        String value = properties.getProperty(KEY_THEME);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    public void saveTheme(String theme) {
        if (theme == null || theme.isBlank()) {
            return;
        }
        Properties properties = loadProperties();
        properties.setProperty(KEY_THEME, theme.trim());
        saveProperties(properties);
    }

    private Properties loadProperties() {
        Properties properties = new Properties();
        if (!Files.exists(configPath)) {
            return properties;
        }
        try (InputStream input = Files.newInputStream(configPath)) {
            properties.load(input);
        } catch (IOException ex) {
            log.warn("Failed to read config: {}", configPath, ex);
        }
        return properties;
    }

    private void saveProperties(Properties properties) {
        try {
            Files.createDirectories(configPath.getParent());
            try (OutputStream output = Files.newOutputStream(configPath)) {
                properties.store(output, "JavaParser Modifier GUI Preferences");
            }
        } catch (IOException ex) {
            log.warn("Failed to write config: {}", configPath, ex);
        }
    }

    private static Path resolveConfigPath() {
        String home = System.getProperty("user.home");
        Path dir = home == null || home.isBlank()
            ? Path.of(System.getProperty("java.io.tmpdir"), CONFIG_DIR)
            : Path.of(home, CONFIG_DIR);
        return dir.resolve(CONFIG_FILE);
    }
}
