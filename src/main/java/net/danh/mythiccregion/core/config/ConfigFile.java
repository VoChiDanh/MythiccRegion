package net.danh.mythiccregion.core.config;

import net.danh.mythiccregion.core.color.ColorParser;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Robust Yaml configuration manager based on Paper API with UTF-8 support,
 * automated saving, default template creation, and color-aware getters.
 */
@NullMarked
public class ConfigFile {

    private final Plugin plugin;
    private final String fileName;
    private final File file;
    private YamlConfiguration config;

    public ConfigFile(Plugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName.endsWith(".yml") ? fileName : fileName + ".yml";
        this.file = new File(plugin.getDataFolder(), this.fileName);
        this.config = new YamlConfiguration();
        reload();
    }

    /**
     * Reloads configuration from disk. If the file does not exist, copies the embedded resource if present,
     * or creates a new empty file.
     */
    public synchronized void reload() {
        if (!file.exists()) {
            createFile();
        }

        try {
            config = YamlConfiguration.loadConfiguration(file);

            // Copy missing default keys from jar resource if available
            InputStream resourceStream = plugin.getResource(fileName);
            if (resourceStream != null) {
                try (InputStreamReader reader = new InputStreamReader(resourceStream, StandardCharsets.UTF_8)) {
                    YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
                    config.setDefaults(defaults);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load configuration file: " + file.getName(), e);
        }
    }

    /**
     * Saves the current YamlConfiguration to disk.
     */
    public synchronized boolean save() {
        try {
            config.save(file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save configuration file: " + file.getName(), e);
            return false;
        }
    }

    private void createFile() {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (InputStream in = plugin.getResource(fileName)) {
            if (in != null) {
                Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                file.createNewFile();
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create file: " + fileName, e);
        }
    }

    public YamlConfiguration getConfig() {
        return config;
    }

    public File getFile() {
        return file;
    }

    public String getFileName() {
        return fileName;
    }

    // --- Type-safe & Color-aware getters ---

    @Nullable
    public Object get(String path) {
        return config.get(path);
    }

    public String getString(String path, String def) {
        return config.getString(path, def);
    }

    @Nullable
    public String getString(String path) {
        return config.getString(path);
    }

    public int getInt(String path, int def) {
        return config.getInt(path, def);
    }

    public double getDouble(String path, double def) {
        return config.getDouble(path, def);
    }

    public boolean getBoolean(String path, boolean def) {
        return config.getBoolean(path, def);
    }

    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }

    @Nullable
    public ConfigurationSection getConfigurationSection(String path) {
        return config.getConfigurationSection(path);
    }

    public boolean contains(String path) {
        return config.contains(path);
    }

    public void set(String path, @Nullable Object value) {
        config.set(path, value);
    }

    /**
     * Gets a formatted Component using MiniMessage or legacy codes.
     */
    public Component getComponent(String path, String def) {
        String raw = config.getString(path, def);
        return ColorParser.parse(raw);
    }

    /**
     * Gets a formatted Component or null if not found.
     */
    @Nullable
    public Component getComponent(String path) {
        String raw = config.getString(path);
        return raw != null ? ColorParser.parse(raw) : null;
    }

    /**
     * Gets a list of formatted Components using MiniMessage or legacy codes.
     */
    public List<Component> getComponentList(String path) {
        List<String> list = config.getStringList(path);
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        return ColorParser.parseList(list);
    }
}
