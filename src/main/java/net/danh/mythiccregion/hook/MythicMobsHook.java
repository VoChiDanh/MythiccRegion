package net.danh.mythiccregion.hook;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.adapters.AbstractItemStack;
import io.lumine.mythic.api.drops.DropMetadata;
import io.lumine.mythic.api.drops.IItemDrop;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.drops.Drop;
import io.lumine.mythic.core.drops.DropMetadataImpl;
import io.lumine.mythic.core.drops.DropTable;
import io.lumine.mythic.core.drops.LootBag;
import io.lumine.mythic.core.items.MythicItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pure MythicMobs hook providing item checks, item generation, and DropTable resolution.
 * Uses AtomicBoolean for thread-safe status tracking across Folia region threads.
 */
@NullMarked
public final class MythicMobsHook {

    private static final AtomicBoolean enabled = new AtomicBoolean(false);

    private MythicMobsHook() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Initializes the MythicMobs hook status.
     */
    public static void init() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("MythicMobs");
        enabled.set(plugin != null && plugin.isEnabled());
    }

    /**
     * Checks if MythicMobs is present and enabled.
     */
    public static boolean isEnabled() {
        if (!enabled.get()) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("MythicMobs");
            enabled.set(plugin != null && plugin.isEnabled());
        }
        return enabled.get();
    }

    /**
     * Retrieves the MythicMobs item type identifier from an ItemStack if recognized.
     *
     * @param item The Bukkit ItemStack.
     * @return Mythic item type name, or null if not a Mythic item.
     */
    @Nullable
    public static String getMythicType(@Nullable ItemStack item) {
        if (!isEnabled() || item == null || item.getType().isAir()) {
            return null;
        }

        try {
            return MythicBukkit.inst().getItemManager().getMythicTypeFromItem(item);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Validates whether the given ItemStack matches the specified Mythic item type.
     */
    public static boolean matchesMythicItem(@Nullable ItemStack item, String mythicItemId) {
        if (!isEnabled() || item == null) {
            return false;
        }
        String type = getMythicType(item);
        return type != null && type.equalsIgnoreCase(mythicItemId);
    }

    /**
     * Creates an ItemStack based on a MythicMobs item ID.
     */
    @Nullable
    public static ItemStack getItem(String mythicItemId, int amount, @Nullable Player player) {
        if (!isEnabled()) {
            return null;
        }

        try {
            Optional<MythicItem> opt = MythicBukkit.inst().getItemManager().getItem(mythicItemId);
            if (opt.isPresent()) {
                ItemStack stack = MythicBukkit.inst().getItemManager().getItemStack(mythicItemId);
                if (stack != null) {
                    stack.setAmount(amount);
                    return stack;
                }
            }
        } catch (Throwable t) {
            // Silently fallback if item cannot be generated
        }
        return null;
    }

    /**
     * Generates item drops using a MythicMobs DropTable name.
     *
     * @param dropTableName Name of the configured DropTable in MythicMobs/droptables.
     * @param player Triggering player for metadata context.
     * @return List of generated ItemStacks.
     */
    public static List<ItemStack> getDropTableItems(String dropTableName, @Nullable Player player) {
        if (!isEnabled()) {
            return Collections.emptyList();
        }

        try {
            Optional<DropTable> tableOpt = MythicBukkit.inst().getDropManager().getDropTable(dropTableName);
            if (tableOpt.isEmpty()) {
                return Collections.emptyList();
            }

            DropTable dropTable = tableOpt.get();
            AbstractEntity entity = player != null ? BukkitAdapter.adapt(player) : null;
            var caster = entity != null ? MythicBukkit.inst().getSkillManager().getCaster(entity) : null;
            DropMetadata metadata = new DropMetadataImpl(caster, entity);

            LootBag lootBag = dropTable.generate(metadata);
            List<ItemStack> items = new ArrayList<>();

            for (Drop drop : lootBag.getDrops()) {
                if (drop instanceof IItemDrop itemDrop) {
                    AbstractItemStack dropStack = itemDrop.getDrop(metadata, 1.0);
                    if (dropStack != null) {
                        ItemStack bukkitStack = BukkitAdapter.adapt(dropStack);
                        if (bukkitStack != null && !bukkitStack.getType().isAir()) {
                            items.add(bukkitStack);
                        }
                    }
                }
            }
            return items;
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }
}
