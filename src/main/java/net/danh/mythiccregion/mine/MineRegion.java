package net.danh.mythiccregion.mine;

import org.bukkit.Material;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * Represents a mine-enabled region configuration.
 */
@NullMarked
public class MineRegion {

    private final String regionId;
    private final Map<Material, MineBlockConfig> blockConfigs;

    public MineRegion(String regionId, Map<Material, MineBlockConfig> blockConfigs) {
        this.regionId = regionId;
        this.blockConfigs = blockConfigs != null ? Map.copyOf(blockConfigs) : Collections.emptyMap();
    }

    public String getRegionId() {
        return regionId;
    }

    @Nullable
    public MineBlockConfig getBlockConfig(Material material) {
        return blockConfigs.get(material);
    }

    public boolean hasBlock(Material material) {
        return blockConfigs.containsKey(material);
    }

    public Map<Material, MineBlockConfig> getBlockConfigs() {
        return blockConfigs;
    }
}
