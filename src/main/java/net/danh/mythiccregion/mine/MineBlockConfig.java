package net.danh.mythiccregion.mine;

import org.bukkit.Material;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Configuration model for an individual mineable block inside a region.
 * Fully supports custom weighted regenerations, commands execution,
 * hardness hits, required mining powers, and item drops.
 */
@NullMarked
public class MineBlockConfig {

    private final Material material;
    private final int respawnTimeSeconds;
    private final Material replaceBlock;
    private final int hardnessHits;
    private final int requiredMiningPower;
    @Nullable
    private final String requiredPickaxeProfile;
    private final Set<String> allowedPickaxeProfiles;
    private final List<WeightedRegenBlock> regenPool;
    private final List<String> commands;
    private final List<MineDrop> drops;

    public MineBlockConfig(
            Material material,
            int respawnTimeSeconds,
            Material replaceBlock,
            int hardnessHits,
            int requiredMiningPower,
            @Nullable String requiredPickaxeProfile,
            Set<String> allowedPickaxeProfiles,
            List<WeightedRegenBlock> regenPool,
            List<String> commands,
            List<MineDrop> drops
    ) {
        this.material = material;
        this.respawnTimeSeconds = Math.max(1, respawnTimeSeconds);
        this.replaceBlock = replaceBlock != null ? replaceBlock : Material.BEDROCK;
        this.hardnessHits = Math.max(1, hardnessHits);
        this.requiredMiningPower = Math.max(0, requiredMiningPower);
        this.requiredPickaxeProfile = requiredPickaxeProfile;
        this.allowedPickaxeProfiles = allowedPickaxeProfiles != null ? Set.copyOf(allowedPickaxeProfiles) : Collections.emptySet();
        this.regenPool = regenPool != null ? List.copyOf(regenPool) : Collections.emptyList();
        this.commands = commands != null ? List.copyOf(commands) : Collections.emptyList();
        this.drops = drops != null ? List.copyOf(drops) : Collections.emptyList();
    }

    public Material getMaterial() {
        return material;
    }

    public int getRespawnTimeSeconds() {
        return respawnTimeSeconds;
    }

    public Material getReplaceBlock() {
        return replaceBlock;
    }

    public int getHardnessHits() {
        return hardnessHits;
    }

    public int getRequiredMiningPower() {
        return requiredMiningPower;
    }

    @Nullable
    public String getRequiredPickaxeProfile() {
        return requiredPickaxeProfile;
    }

    public Set<String> getAllowedPickaxeProfiles() {
        return allowedPickaxeProfiles;
    }

    public List<WeightedRegenBlock> getRegenPool() {
        return regenPool;
    }

    public List<String> getCommands() {
        return commands;
    }

    public List<MineDrop> getDrops() {
        return drops;
    }

    public record WeightedRegenBlock(Material material, double weight) {}
}
