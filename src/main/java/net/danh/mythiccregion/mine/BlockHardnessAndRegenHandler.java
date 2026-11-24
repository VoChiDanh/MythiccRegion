package net.danh.mythiccregion.mine;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * High-performance tracker for block damage progression and asynchronous regeneration.
 * Supports weighted regeneration back into diverse block types.
 */
@NullMarked
public class BlockHardnessAndRegenHandler {

    private final Plugin plugin;
    // Tracks accumulated hit damage per block location
    private final Map<Location, Integer> blockHits = new ConcurrentHashMap<>();
    // Tracks active regeneration tasks
    private final Map<Location, ScheduledTask> activeRegenTasks = new ConcurrentHashMap<>();
    // Tracks original blocks to safely restore them on reload/disable
    private final Map<Location, Material> originalBlockTypes = new ConcurrentHashMap<>();

    public BlockHardnessAndRegenHandler(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Applies damage to a block's hardness.
     *
     * @param loc Block location.
     * @param damage Damage dealt by the pickaxe.
     * @param requiredHardness Total required hardness hits.
     * @return true if the block has reached or exceeded required hardness and should break.
     */
    public boolean applyDamage(Location loc, int damage, int requiredHardness) {
        if (requiredHardness <= 1) {
            blockHits.remove(loc);
            return true;
        }

        int newTotal = blockHits.compute(loc, (k, current) -> (current == null ? 0 : current) + Math.max(1, damage));
        if (newTotal >= requiredHardness) {
            blockHits.remove(loc);
            return true;
        }
        return false;
    }

    public int getCurrentHits(Location loc) {
        return blockHits.getOrDefault(loc, 0);
    }

    public void resetHits(Location loc) {
        blockHits.remove(loc);
    }

    public boolean isPendingRespawn(Location loc) {
        return activeRegenTasks.containsKey(loc);
    }

    /**
     * Replaces the block with a temporary block and schedules respawn using weighted pools.
     */
    public void scheduleRegeneration(
            Block block,
            Material defaultMaterial,
            Material replaceMaterial,
            List<MineBlockConfig.WeightedRegenBlock> regenPool,
            int delaySeconds
    ) {
        Location loc = block.getLocation();

        ScheduledTask existing = activeRegenTasks.remove(loc);
        if (existing != null) {
            existing.cancel();
        }

        // Store target type in case plugin disables before respawn finishes
        Material targetMaterial = determineNextMaterial(defaultMaterial, regenPool);
        originalBlockTypes.put(loc, targetMaterial);

        // Immediately replace with temporary replace block (e.g., BEDROCK) without physics updates
        block.setType(replaceMaterial, false);

        ScheduledTask task = plugin.getServer().getRegionScheduler().runDelayed(plugin, loc, scheduledTask -> {
            block.setType(targetMaterial, false);
            activeRegenTasks.remove(loc);
            originalBlockTypes.remove(loc);
        }, Math.max(1L, delaySeconds * 20L));

        activeRegenTasks.put(loc, task);
    }

    private Material determineNextMaterial(Material defaultMaterial, List<MineBlockConfig.WeightedRegenBlock> pool) {
        if (pool.isEmpty()) {
            return defaultMaterial;
        }

        double totalWeight = 0.0;
        for (MineBlockConfig.WeightedRegenBlock wrb : pool) {
            totalWeight += wrb.weight();
        }

        if (totalWeight <= 0.0) {
            return defaultMaterial;
        }

        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        double current = 0.0;
        for (MineBlockConfig.WeightedRegenBlock wrb : pool) {
            current += wrb.weight();
            if (roll <= current) {
                return wrb.material();
            }
        }

        return pool.getFirst().material();
    }

    /**
     * Cleans up all tracked tasks and immediately restores original block types to prevent Bedrock leftover.
     */
    public void cleanup() {
        for (ScheduledTask task : activeRegenTasks.values()) {
            task.cancel();
        }
        activeRegenTasks.clear();

        for (Map.Entry<Location, Material> entry : originalBlockTypes.entrySet()) {
            try {
                entry.getKey().getBlock().setType(entry.getValue());
            } catch (Throwable ignored) {}
        }
        originalBlockTypes.clear();
        blockHits.clear();
    }
}
