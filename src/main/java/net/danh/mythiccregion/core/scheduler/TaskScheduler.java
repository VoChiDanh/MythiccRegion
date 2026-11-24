package net.danh.mythiccregion.core.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Universal cross-platform scheduler compatibility abstraction.
 * Automatically detects whether the server is running on Paper/Spigot (single main thread)
 * or Folia (regionized multi-threading).
 *
 * <p>Folia Architecture Conformance:</p>
 * <ul>
 *   <li>Entity modifications (Inventory, Health, Velocity, Teleportation) run on the EntityScheduler.</li>
 *   <li>Block operations and Entity spawning run on the RegionScheduler of the specific Location.</li>
 *   <li>Console command dispatching runs on the GlobalRegionScheduler.</li>
 *   <li>Asynchronous tasks run on the AsyncScheduler.</li>
 * </ul>
 */
@NullMarked
public final class TaskScheduler {

    private static final boolean IS_FOLIA;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        IS_FOLIA = folia;
    }

    private TaskScheduler() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Determines whether the server is running on a Folia-based regionized server.
     *
     * @return true if running on Folia, false for standard Paper/Spigot.
     */
    public static boolean isFolia() {
        return IS_FOLIA;
    }

    /**
     * Executes a task on the thread owning the specified entity.
     * Essential on Folia when modifying player inventories, health, or potion effects.
     *
     * @param plugin Plugin instance.
     * @param entity Target entity.
     * @param task Task to execute.
     * @return TaskHandle for tracking and cancellation.
     */
    public static TaskHandle runAtEntity(Plugin plugin, Entity entity, Runnable task) {
        if (IS_FOLIA) {
            ScheduledTask st = entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
            return new FoliaScheduledTaskHandle(st);
        } else {
            BukkitTask bt = Bukkit.getScheduler().runTask(plugin, task);
            return new BukkitTaskHandle(bt);
        }
    }

    /**
     * Executes a delayed task on the entity's thread.
     *
     * @param plugin Plugin instance.
     * @param entity Target entity.
     * @param task Task to execute.
     * @param delayTicks Delay in ticks.
     * @return TaskHandle for tracking and cancellation.
     */
    public static TaskHandle runAtEntityDelayed(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            ScheduledTask st = entity.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, Math.max(1L, delayTicks));
            return new FoliaScheduledTaskHandle(st);
        } else {
            BukkitTask bt = Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, delayTicks));
            return new BukkitTaskHandle(bt);
        }
    }

    /**
     * Executes a task on the region thread owning the target block location.
     * Required on Folia for chunk operations, block modifications, and entity spawning.
     *
     * @param plugin Plugin instance.
     * @param location Target block location.
     * @param task Task to execute.
     * @return TaskHandle for tracking and cancellation.
     */
    public static TaskHandle runAtLocation(Plugin plugin, Location location, Runnable task) {
        if (IS_FOLIA) {
            ScheduledTask st = Bukkit.getRegionScheduler().run(plugin, location, scheduledTask -> task.run());
            return new FoliaScheduledTaskHandle(st);
        } else {
            BukkitTask bt = Bukkit.getScheduler().runTask(plugin, task);
            return new BukkitTaskHandle(bt);
        }
    }

    /**
     * Executes a delayed task on the region thread owning the target block location.
     *
     * @param plugin Plugin instance.
     * @param location Target block location.
     * @param task Task to execute.
     * @param delayTicks Delay in ticks.
     * @return TaskHandle for tracking and cancellation.
     */
    public static TaskHandle runAtLocationDelayed(Plugin plugin, Location location, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            ScheduledTask st = Bukkit.getRegionScheduler().runDelayed(plugin, location, scheduledTask -> task.run(), Math.max(1L, delayTicks));
            return new FoliaScheduledTaskHandle(st);
        } else {
            BukkitTask bt = Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, delayTicks));
            return new BukkitTaskHandle(bt);
        }
    }

    /**
     * Executes a task on the Global Region Scheduler.
     * Used for console command dispatch, server broadcasts, and non-localized global logic.
     *
     * @param plugin Plugin instance.
     * @param task Task to execute.
     * @return TaskHandle for tracking and cancellation.
     */
    public static TaskHandle runGlobal(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            ScheduledTask st = Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
            return new FoliaScheduledTaskHandle(st);
        } else {
            BukkitTask bt = Bukkit.getScheduler().runTask(plugin, task);
            return new BukkitTaskHandle(bt);
        }
    }

    /**
     * Executes a delayed task on the Global Region Scheduler.
     *
     * @param plugin Plugin instance.
     * @param task Task to execute.
     * @param delayTicks Delay in ticks.
     * @return TaskHandle for tracking and cancellation.
     */
    public static TaskHandle runGlobalDelayed(Plugin plugin, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            ScheduledTask st = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), Math.max(1L, delayTicks));
            return new FoliaScheduledTaskHandle(st);
        } else {
            BukkitTask bt = Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, delayTicks));
            return new BukkitTaskHandle(bt);
        }
    }

    /**
     * Executes a recurring task on the Global Region Scheduler.
     *
     * @param plugin Plugin instance.
     * @param task Task to execute.
     * @param initialDelayTicks Initial delay in ticks.
     * @param periodTicks Repeat interval in ticks.
     * @return TaskHandle for tracking and cancellation.
     */
    public static TaskHandle runGlobalRepeating(Plugin plugin, Runnable task, long initialDelayTicks, long periodTicks) {
        if (IS_FOLIA) {
            ScheduledTask st = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                    plugin,
                    scheduledTask -> task.run(),
                    Math.max(1L, initialDelayTicks),
                    Math.max(1L, periodTicks)
            );
            return new FoliaScheduledTaskHandle(st);
        } else {
            BukkitTask bt = Bukkit.getScheduler().runTaskTimer(
                    plugin,
                    task,
                    Math.max(1L, initialDelayTicks),
                    Math.max(1L, periodTicks)
            );
            return new BukkitTaskHandle(bt);
        }
    }

    /**
     * Executes an asynchronous task off all region/server threads.
     *
     * @param plugin Plugin instance.
     * @param task Task to execute.
     * @return TaskHandle for tracking and cancellation.
     */
    public static TaskHandle runAsync(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            ScheduledTask st = Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
            return new FoliaScheduledTaskHandle(st);
        } else {
            BukkitTask bt = Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            return new BukkitTaskHandle(bt);
        }
    }

    /**
     * Executes a delayed asynchronous task off all region/server threads.
     *
     * @param plugin Plugin instance.
     * @param task Task to execute.
     * @param delayTicks Delay in ticks (approx. 50ms per tick).
     * @return TaskHandle for tracking and cancellation.
     */
    public static TaskHandle runAsyncDelayed(Plugin plugin, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            ScheduledTask st = Bukkit.getAsyncScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayTicks * 50L, TimeUnit.MILLISECONDS);
            return new FoliaScheduledTaskHandle(st);
        } else {
            BukkitTask bt = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
            return new BukkitTaskHandle(bt);
        }
    }

    /**
     * Asynchronously teleports an entity to a destination and executes post-teleport logic
     * inside the callback on the entity's thread.
     *
     * @param plugin Plugin instance.
     * @param entity Entity to teleport.
     * @param destination Target destination.
     * @param callback Callback executed after teleport completes with success status.
     */
    public static void teleportAsync(Plugin plugin, Entity entity, Location destination, @Nullable Consumer<Boolean> callback) {
        entity.teleportAsync(destination).thenAccept(success -> {
            if (callback != null) {
                runAtEntity(plugin, entity, () -> callback.accept(success));
            }
        });
    }

    /**
     * Asynchronously loads a chunk before safely providing it to the consumer callback
     * on the region thread of that chunk.
     *
     * @param plugin Plugin instance.
     * @param world Target world.
     * @param chunkX Chunk X coordinate.
     * @param chunkZ Chunk Z coordinate.
     * @param callback Callback receiving the loaded chunk.
     */
    public static void getChunkAtAsync(Plugin plugin, World world, int chunkX, int chunkZ, Consumer<Chunk> callback) {
        world.getChunkAtAsync(chunkX, chunkZ).thenAccept(chunk -> {
            if (chunk != null) {
                Location centerLoc = new Location(world, (chunkX << 4) + 8, 64, (chunkZ << 4) + 8);
                runAtLocation(plugin, centerLoc, () -> callback.accept(chunk));
            }
        });
    }

    /**
     * Folia ScheduledTask wrapper implementing TaskHandle.
     */
    private static class FoliaScheduledTaskHandle implements TaskHandle {
        @Nullable
        private final ScheduledTask task;

        FoliaScheduledTaskHandle(@Nullable ScheduledTask task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            if (task != null) {
                task.cancel();
            }
        }

        @Override
        public boolean isCancelled() {
            return task != null && task.isCancelled();
        }
    }

    /**
     * Standard BukkitTask wrapper implementing TaskHandle.
     */
    private static class BukkitTaskHandle implements TaskHandle {
        @Nullable
        private final BukkitTask task;

        BukkitTaskHandle(@Nullable BukkitTask task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            if (task != null) {
                task.cancel();
            }
        }

        @Override
        public boolean isCancelled() {
            return task != null && task.isCancelled();
        }
    }
}
