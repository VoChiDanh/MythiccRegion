package net.danh.mythiccregion;

import net.danh.mythiccregion.command.MythiccRegionCommand;
import net.danh.mythiccregion.core.color.ColorParser;
import net.danh.mythiccregion.core.config.ConfigFile;
import net.danh.mythiccregion.core.config.ConfigManager;
import net.danh.mythiccregion.hook.MythicMobsHook;
import net.danh.mythiccregion.hook.WorldGuardHook;
import net.danh.mythiccregion.mine.BlockHardnessAndRegenHandler;
import net.danh.mythiccregion.mine.MineBlockListener;
import net.danh.mythiccregion.mine.MineManager;
import net.danh.mythiccregion.pickaxe.PickaxeManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main plugin class for MythiccRegion.
 * Built 100% on Paper API, integrating WorldGuard Custom Flags, FAWE, and MythicMobs.
 */
public final class MythiccRegion extends JavaPlugin implements Listener {

    private static MythiccRegion instance;
    private ConfigManager configManager;
    private ConfigFile mainConfig;
    private ConfigFile minesConfig;
    private ConfigFile pickaxesConfig;

    private PickaxeManager pickaxeManager;
    private MineManager mineManager;
    private BlockHardnessAndRegenHandler regenHandler;

    // Track players who toggled bypass mode (/mr bypass)
    private final Set<UUID> bypassPlayers = ConcurrentHashMap.newKeySet();

    @Override
    public void onLoad() {
        // Register custom WorldGuard flags in onLoad before WorldGuard locks registry
        WorldGuardHook.registerCustomFlags();
    }

    @Override
    public void onEnable() {
        instance = this;

        // 1. Initialize External Hooks
        WorldGuardHook.init();
        MythicMobsHook.init();

        if (WorldGuardHook.isEnabled()) {
            getComponentLogger().info(ColorParser.parse("<green>[Hook] WorldGuard hooked successfully with custom flags ('mine-region', 'custom-mine', 'mine-id')!"));
        } else {
            getComponentLogger().warn(ColorParser.parse("<yellow>[Hook] WorldGuard not active yet (will auto-hook if loaded later)."));
        }

        if (MythicMobsHook.isEnabled()) {
            getComponentLogger().info(ColorParser.parse("<green>[Hook] MythicMobs hooked successfully!"));
        } else {
            getComponentLogger().warn(ColorParser.parse("<yellow>[Hook] MythicMobs not active yet (will auto-hook if loaded later)."));
        }

        // 2. Load Configuration Files
        this.configManager = new ConfigManager(this);
        this.mainConfig = configManager.load("config.yml");
        this.minesConfig = configManager.load("mines.yml");
        this.pickaxesConfig = configManager.load("pickaxes.yml");

        // 3. Initialize Managers & Handlers
        this.pickaxeManager = new PickaxeManager(this, pickaxesConfig);
        this.mineManager = new MineManager(this, minesConfig);
        this.regenHandler = new BlockHardnessAndRegenHandler(this);

        // 4. Register Event Listeners
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(
                new MineBlockListener(this, mineManager, pickaxeManager, regenHandler, mainConfig),
                this
        );

        // 5. Register Modern Paper Brigadier Commands
        MythiccRegionCommand.register(this);

        getComponentLogger().info(ColorParser.parse("<green>[MythiccRegion] Plugin successfully enabled!"));
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        String name = event.getPlugin().getName();
        if (name.equalsIgnoreCase("WorldGuard")) {
            WorldGuardHook.init();
            getComponentLogger().info(ColorParser.parse("<green>[Hook] WorldGuard dynamically hooked!"));
        } else if (name.equalsIgnoreCase("MythicMobs")) {
            MythicMobsHook.init();
            getComponentLogger().info(ColorParser.parse("<green>[Hook] MythicMobs dynamically hooked!"));
        }
    }

    @Override
    public void onDisable() {
        if (regenHandler != null) {
            regenHandler.cleanup();
        }
        if (configManager != null) {
            configManager.saveAll();
        }
        bypassPlayers.clear();
        getComponentLogger().info(ColorParser.parse("<red>[MythiccRegion] Plugin disabled!"));
    }

    public boolean isBypassing(UUID uuid) {
        return bypassPlayers.contains(uuid);
    }

    public boolean toggleBypass(UUID uuid) {
        if (bypassPlayers.contains(uuid)) {
            bypassPlayers.remove(uuid);
            return false;
        } else {
            bypassPlayers.add(uuid);
            return true;
        }
    }

    public static MythiccRegion getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ConfigFile getMainConfig() {
        return mainConfig;
    }

    public ConfigFile getMinesConfig() {
        return minesConfig;
    }

    public ConfigFile getPickaxesConfig() {
        return pickaxesConfig;
    }

    public PickaxeManager getPickaxeManager() {
        return pickaxeManager;
    }

    public MineManager getMineManager() {
        return mineManager;
    }

    public BlockHardnessAndRegenHandler getRegenHandler() {
        return regenHandler;
    }
}
