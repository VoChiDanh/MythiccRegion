package net.danh.mythiccregion.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Thread-safe native WorldGuard integration hook.
 * Directly registers and queries WorldGuard custom flags:
 * <ul>
 *   <li>'mine-region' & 'custom-mine': StateFlag (default: DENY). Set to ALLOW via '/rg flag &lt;region&gt; mine-region allow'.</li>
 *   <li>'mine-id': StringFlag. Links region directly to a specific block config section from mines.yml.</li>
 * </ul>
 */
@NullMarked
public final class WorldGuardHook {

    private static final AtomicBoolean enabled = new AtomicBoolean(false);

    @Nullable
    public static StateFlag MINE_REGION_FLAG = null;
    @Nullable
    public static StateFlag CUSTOM_MINE_FLAG = null;
    @Nullable
    public static StringFlag MINE_ID_FLAG = null;

    private WorldGuardHook() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Registers custom flags into WorldGuard's FlagRegistry.
     * Must be invoked inside JavaPlugin#onLoad().
     */
    public static void registerCustomFlags() {
        try {
            FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();

            // 1. Register 'mine-region' StateFlag
            try {
                StateFlag flag = new StateFlag("mine-region", false);
                registry.register(flag);
                MINE_REGION_FLAG = flag;
            } catch (FlagConflictException e) {
                Flag<?> existing = registry.get("mine-region");
                if (existing instanceof StateFlag stateFlag) {
                    MINE_REGION_FLAG = stateFlag;
                }
            }

            // 2. Register 'custom-mine' StateFlag (backwards compatibility / alias)
            try {
                StateFlag flag = new StateFlag("custom-mine", false);
                registry.register(flag);
                CUSTOM_MINE_FLAG = flag;
            } catch (FlagConflictException e) {
                Flag<?> existing = registry.get("custom-mine");
                if (existing instanceof StateFlag stateFlag) {
                    CUSTOM_MINE_FLAG = stateFlag;
                }
            }

            // 3. Register 'mine-id' StringFlag (for specific mine profile binding)
            try {
                StringFlag strFlag = new StringFlag("mine-id");
                registry.register(strFlag);
                MINE_ID_FLAG = strFlag;
            } catch (FlagConflictException e) {
                Flag<?> existing = registry.get("mine-id");
                if (existing instanceof StringFlag stringFlag) {
                    MINE_ID_FLAG = stringFlag;
                }
            }
        } catch (Throwable t) {
            // WorldGuard not present or registry already initialized
        }
    }

    /**
     * Initializes the hook state on plugin enable.
     */
    public static void init() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
        enabled.set(plugin != null && plugin.isEnabled());
    }

    public static boolean isEnabled() {
        if (!enabled.get()) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
            enabled.set(plugin != null && plugin.isEnabled());
        }
        return enabled.get();
    }

    /**
     * Checks if the given location allows mining via the 'mine-region' or 'custom-mine' flag.
     *
     * @param player The player performing the mining action.
     * @param location The block location being mined.
     * @return true if the 'mine-region' or 'custom-mine' flag is set to ALLOW.
     */
    public static boolean isMineRegionAllowed(@Nullable Player player, @Nullable Location location) {
        if (!isEnabled() || location == null) {
            return false;
        }

        try {
            LocalPlayer localPlayer = player != null ? WorldGuardPlugin.inst().wrapPlayer(player) : null;
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            ApplicableRegionSet set = container.createQuery().getApplicableRegions(BukkitAdapter.adapt(location));

            if (MINE_REGION_FLAG != null && set.queryState(localPlayer, MINE_REGION_FLAG) == StateFlag.State.ALLOW) {
                return true;
            }
            if (CUSTOM_MINE_FLAG != null && set.queryState(localPlayer, CUSTOM_MINE_FLAG) == StateFlag.State.ALLOW) {
                return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Retrieves the optional custom 'mine-id' set on this region.
     */
    @Nullable
    public static String getCustomMineId(@Nullable Location location) {
        if (!isEnabled() || location == null || MINE_ID_FLAG == null) {
            return null;
        }

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            ApplicableRegionSet set = container.createQuery().getApplicableRegions(BukkitAdapter.adapt(location));
            return set.queryValue(null, MINE_ID_FLAG);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Returns all WorldGuard region IDs overlapping the specified block location.
     */
    public static Set<String> getRegionsAt(@Nullable Location location) {
        if (!isEnabled() || location == null) {
            return Collections.emptySet();
        }

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            ApplicableRegionSet set = container.createQuery().getApplicableRegions(BukkitAdapter.adapt(location));

            return set.getRegions().stream()
                    .map(ProtectedRegion::getId)
                    .collect(Collectors.toSet());
        } catch (Throwable t) {
            return Collections.emptySet();
        }
    }
}
