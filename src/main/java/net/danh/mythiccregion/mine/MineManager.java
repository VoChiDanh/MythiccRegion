package net.danh.mythiccregion.mine;

import net.danh.mythiccregion.core.config.ConfigFile;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages loading and caching of all mine regions from mines.yml.
 * Thread-safe collections used throughout to guarantee zero ConcurrentModificationExceptions.
 */
@NullMarked
public class MineManager {

    private final Plugin plugin;
    private final ConfigFile minesConfig;
    private final Map<String, MineRegion> regions = new ConcurrentHashMap<>();

    public MineManager(Plugin plugin, ConfigFile minesConfig) {
        this.plugin = plugin;
        this.minesConfig = minesConfig;
        loadMines();
    }

    /**
     * Reloads and parses all mine regions from the configuration file.
     */
    public synchronized void loadMines() {
        regions.clear();
        minesConfig.reload();

        ConfigurationSection regionsSection = minesConfig.getConfigurationSection("regions");
        if (regionsSection == null) {
            return;
        }

        for (String regionKey : regionsSection.getKeys(false)) {
            ConfigurationSection sec = regionsSection.getConfigurationSection(regionKey);
            if (sec == null) continue;

            String wgRegionId = sec.getString("worldguard-region", regionKey);
            ConfigurationSection blocksSec = sec.getConfigurationSection("blocks");
            if (blocksSec == null) continue;

            Map<Material, MineBlockConfig> blockConfigs = new ConcurrentHashMap<>();

            for (String matKey : blocksSec.getKeys(false)) {
                ConfigurationSection bSec = blocksSec.getConfigurationSection(matKey);
                if (bSec == null) continue;

                Material mat = Material.matchMaterial(matKey);
                if (mat == null) {
                    plugin.getLogger().warning("Invalid material for mine block: " + matKey + " in region: " + regionKey);
                    continue;
                }

                int respawnTime = bSec.getInt("respawn-time", 10);
                String replaceMatName = bSec.getString("replace-block", "BEDROCK");
                Material replaceMat = Material.matchMaterial(replaceMatName);
                if (replaceMat == null) replaceMat = Material.BEDROCK;

                int hardnessHits = bSec.getInt("hardness-hits", 1);
                int reqPower = bSec.getInt("required-mining-power", 0);
                String reqProfile = bSec.getString("required-pickaxe-profile");

                Set<String> allowedProfiles = ConcurrentHashMap.newKeySet();
                for (String p : bSec.getStringList("allowed-pickaxe-profiles")) {
                    allowedProfiles.add(p.toLowerCase(Locale.ROOT));
                }

                // Load custom regen pool (e.g., COAL_ORE: 30, STONE: 60)
                List<MineBlockConfig.WeightedRegenBlock> regenPool = new CopyOnWriteArrayList<>();
                ConfigurationSection regenSec = bSec.getConfigurationSection("regen-pool");
                if (regenSec != null) {
                    for (String rMatKey : regenSec.getKeys(false)) {
                        Material rMat = Material.matchMaterial(rMatKey);
                        if (rMat != null) {
                            double weight = regenSec.getDouble(rMatKey, 1.0);
                            regenPool.add(new MineBlockConfig.WeightedRegenBlock(rMat, weight));
                        }
                    }
                }

                // Load custom commands
                List<String> commands = new CopyOnWriteArrayList<>(bSec.getStringList("commands"));

                // Load drop specifications
                List<MineDrop> drops = new CopyOnWriteArrayList<>();
                ConfigurationSection dropsSec = bSec.getConfigurationSection("drops");
                if (dropsSec != null) {
                    for (String dropKey : dropsSec.getKeys(false)) {
                        ConfigurationSection dSec = dropsSec.getConfigurationSection(dropKey);
                        if (dSec == null) continue;

                        String mythicItem = dSec.getString("mythic-item");
                        String mythicDropTable = dSec.getString("mythic-droptable");
                        String vanillaMat = dSec.getString("vanilla-material");
                        int min = dSec.getInt("min-amount", 1);
                        int max = dSec.getInt("max-amount", 1);
                        double chance = dSec.getDouble("chance", 100.0);

                        // Load multiplier chances
                        List<MineDrop.MultiplierChance> multipliers = new CopyOnWriteArrayList<>();
                        ConfigurationSection multSec = dSec.getConfigurationSection("multipliers");
                        if (multSec != null) {
                            for (String mKey : multSec.getKeys(false)) {
                                try {
                                    int mult = Integer.parseInt(mKey);
                                    double mChance = multSec.getDouble(mKey, 0.0);
                                    multipliers.add(new MineDrop.MultiplierChance(mult, mChance));
                                } catch (NumberFormatException ignored) {}
                            }
                            multipliers.sort((a, b) -> Integer.compare(b.multiplier(), a.multiplier()));
                        }

                        drops.add(new MineDrop(mythicItem, mythicDropTable, vanillaMat, min, max, chance, multipliers));
                    }
                }

                blockConfigs.put(mat, new MineBlockConfig(
                        mat,
                        respawnTime,
                        replaceMat,
                        hardnessHits,
                        reqPower,
                        reqProfile,
                        allowedProfiles,
                        regenPool,
                        commands,
                        drops
                ));
            }

            regions.put(wgRegionId.toLowerCase(Locale.ROOT), new MineRegion(wgRegionId, blockConfigs));
        }

        plugin.getLogger().info("Loaded " + regions.size() + " mine regions.");
    }

    @Nullable
    public MineRegion getRegion(String regionId) {
        return regions.get(regionId.toLowerCase(Locale.ROOT));
    }

    public Map<String, MineRegion> getRegions() {
        return regions;
    }
}
