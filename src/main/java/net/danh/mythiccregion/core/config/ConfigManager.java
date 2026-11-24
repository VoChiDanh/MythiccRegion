package net.danh.mythiccregion.core.config;

import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages multiple configuration files (config.yml, messages.yml, data.yml, etc.).
 */
@NullMarked
public class ConfigManager {

    private final Plugin plugin;
    private final Map<String, ConfigFile> configs = new ConcurrentHashMap<>();

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads or gets a configuration file by name.
     */
    public ConfigFile load(String fileName) {
        return configs.computeIfAbsent(fileName, name -> new ConfigFile(plugin, name));
    }

    @Nullable
    public ConfigFile get(String fileName) {
        return configs.get(fileName);
    }

    /**
     * Reloads all tracked configuration files.
     */
    public void reloadAll() {
        for (ConfigFile cfg : configs.values()) {
            cfg.reload();
        }
    }

    /**
     * Saves all tracked configuration files to disk.
     */
    public void saveAll() {
        for (ConfigFile cfg : configs.values()) {
            cfg.save();
        }
    }
}
