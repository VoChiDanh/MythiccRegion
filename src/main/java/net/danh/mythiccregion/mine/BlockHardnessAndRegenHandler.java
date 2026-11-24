package net.danh.mythiccregion.mine;

import net.danh.mythiccregion.core.scheduler.TaskHandle;
import net.danh.mythiccregion.core.scheduler.TaskScheduler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-performance tracker for block damage progression and asynchronous regeneration.
 * Supports weighted regeneration back into diverse block types.
 *
 * <p>Folia & Thread-Safety Design:</p>
 * <ul>
 *   <li>All collections use thread-safe data structures (ConcurrentHashMap).</li>
 *   <li>Scheduled tasks leverage cross-platform {@link TaskScheduler} with {@link TaskHandle}.</li>
 *   <li>Damage hit tracking includes timestamps to automatically purge stale entries and eliminate memory leaks.</li>
 *   <li>World unloading cleans up all associated locations to prevent World object leaks.</li>
 * </ul>
 */
@NullMarked
public class BlockHardnessAndRegenHandler {

    private final Plugin plugin;

    /**
     * Hit tracker record storing accumulated damage hits and the epoch time of the last hit.
     */
    public record HitRecord(int hits, long lastHitTime) {
        public HitRecord withAddedDamage(int damage) {
            return new HitRecord(this.hits + Math.max(1, damage), System.currentTimeMillis());
        }
    }

    // Tracks accumulated hit damage and timestamp per block location
    private final Map<Location, HitRecord> blockHits = new ConcurrentHashMap<>();

    // Tracks active regeneration tasks via universal TaskHandle
    private final Map<Location, TaskHandle> activeRegenTasks = new ConcurrentHashMap<>();

    // Tracks original blocks to safely restore them on reload/disable
    private final Map<Location, Material> originalBlockTypes = new ConcurrentHashMap<>();

    // Thread-safe counter for currently pending respawns
    private final AtomicInteger pendingRespawnCount = new AtomicInteger(0);

    public BlockHardnessAndRegenHandler(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Applies damage to a block's hardness progression.
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

        long now = System.currentTimeMillis();
        HitRecord record = blockHits.compute(loc, (k, existing) -> {
            if (existing == null) {
                return new HitRecord(Math.max(1, damage), now);
            }
            return existing.withAddedDamage(damage);
        });

        if (record != null && record.hits() >= requiredHardness) {
            blockHits.remove(loc);
            return true;
        }
        return false;
    }

    public int getCurrentHits(Location loc) {
        HitRecord rec = blockHits.get(loc);
        return rec != null ? rec.hits() : 0;
    }

    public void resetHits(Location loc) {
        blockHits.remove(loc);
    }

    public boolean isPendingRespawn(Location loc) {
        return activeRegenTasks.containsKey(loc);
    }

    public int getPendingRespawnCount() {
        return pendingRespawnCount.get();
    }

    /**
     * Replaces the block with a temporary block and schedules respawn using weighted pools.
     * Dispatched to the region thread owning the block location to guarantee Folia compliance.
     */
    public void scheduleRegeneration(
            Block block,
            Material defaultMaterial,
            Material replaceMaterial,
            List<MineBlockConfig.WeightedRegenBlock> regenPool,
            int delaySeconds
    ) {
        Location loc = block.getLocation();

        TaskHandle existing = activeRegenTasks.remove(loc);
        if (existing != null) {
            existing.cancel();
            pendingRespawnCount.decrementAndGet();
        }

        // Store target type in case plugin disables before respawn finishes
        Material targetMaterial = determineNextMaterial(defaultMaterial, regenPool);
        originalBlockTypes.put(loc, targetMaterial);

        // Immediately replace with temporary replace block (e.g., BEDROCK) without physics updates
        block.setType(replaceMaterial, false);
        pendingRespawnCount.incrementAndGet();

        // Schedule regeneration task on the location's region thread
        long delayTicks = Math.max(1L, delaySeconds * 20L);
        TaskHandle task = TaskScheduler.runAtLocationDelayed(plugin, loc, () -> {
            try {
                loc.getBlock().setType(targetMaterial, false);
            } catch (Throwable ignored) {}
            activeRegenTasks.remove(loc);
            originalBlockTypes.remove(loc);
            pendingRespawnCount.decrementAndGet();
        }, delayTicks);

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
     * Purges stale block hit entries older than the specified max age to prevent memory leakage.
     *
     * @param maxAgeMillis Age in milliseconds before an untouched hit record is pruned.
     */
    public void cleanStaleHits(long maxAgeMillis) {
        long now = System.currentTimeMillis();
        blockHits.entrySet().removeIf(entry -> (now - entry.getValue().lastHitTime()) > maxAgeMillis);
    }

    /**
     * Cleans up all tracked entries for an unloaded world to prevent world memory leaks.
     *
     * @param world The world being unloaded.
     */
    public void onWorldUnload(World world) {
        blockHits.keySet().removeIf(loc -> world.equals(loc.getWorld()));
        activeRegenTasks.keySet().removeIf(loc -> {
            if (world.equals(loc.getWorld())) {
                TaskHandle h = activeRegenTasks.get(loc);
                if (h != null) h.cancel();
                return true;
            }
            return false;
        });
        originalBlockTypes.keySet().removeIf(loc -> world.equals(loc.getWorld()));
    }

    /**
     * Cleans up all tracked tasks and immediately restores original block types to prevent Bedrock leftover.
     * Safe across Folia region shutdown and Paper reload cycles.
     */
    public void cleanup() {
        for (TaskHandle task : activeRegenTasks.values()) {
            task.cancel();
        }
        activeRegenTasks.clear();
        pendingRespawnCount.set(0);

        for (Map.Entry<Location, Material> entry : originalBlockTypes.entrySet()) {
            Location loc = entry.getKey();
            Material mat = entry.getValue();
            try {
                // Try immediate restore
                loc.getBlock().setType(mat, false);
            } catch (Throwable t) {
                // If on Folia and not on region thread, dispatch to region thread
                TaskScheduler.runAtLocation(plugin, loc, () -> {
                    try {
                        loc.getBlock().setType(mat, false);
                    } catch (Throwable ignored) {}
                });
            }
        }
        originalBlockTypes.clear();
        blockHits.clear();
    }
}
