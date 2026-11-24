package net.danh.mythiccregion.mine;

import net.danh.mythiccregion.MythiccRegion;
import net.danh.mythiccregion.core.color.ColorParser;
import net.danh.mythiccregion.core.config.ConfigFile;
import net.danh.mythiccregion.hook.MythicMobsHook;
import net.danh.mythiccregion.hook.WorldGuardHook;
import net.danh.mythiccregion.pickaxe.PickaxeManager;
import net.danh.mythiccregion.pickaxe.PickaxeProfile;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Event listener handling custom mining logic in protected WorldGuard regions.
 * Integrates:
 * 1. BlockDamageEvent & PlayerInteractEvent for instant left-click progressive mining
 * 2. Visual block crack packet animation (Player#sendBlockDamage)
 * 3. Fallback BlockBreakEvent for standard breakage
 * 4. AoE radius mining (3x3, 5x5) and MythicMobs integration
 */
@NullMarked
public class MineBlockListener implements Listener {

    private final MythiccRegion plugin;
    private final MineManager mineManager;
    private final PickaxeManager pickaxeManager;
    private final BlockHardnessAndRegenHandler handler;
    private final ConfigFile mainConfig;

    // Cooldown per player to prevent left-click spamming (1 hit every 250ms)
    private final Map<UUID, Long> clickCooldown = new ConcurrentHashMap<>();

    // Prevent recursive block break triggers during AoE mining
    private final Set<Location> recursiveProtection = Collections.newSetFromMap(new WeakHashMap<>());

    public MineBlockListener(
            MythiccRegion plugin,
            MineManager mineManager,
            PickaxeManager pickaxeManager,
            BlockHardnessAndRegenHandler handler,
            ConfigFile mainConfig
    ) {
        this.plugin = plugin;
        this.mineManager = mineManager;
        this.pickaxeManager = pickaxeManager;
        this.handler = handler;
        this.mainConfig = mainConfig;
    }

    /**
     * Intercepts left click block damage to allow progressive mining in protected regions
     * where WorldGuard cancels standard BlockBreakEvent.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Location loc = block.getLocation();

        // If block is currently awaiting respawn (temporary bedrock), strictly protect it
        if (handler.isPendingRespawn(loc)) {
            event.setCancelled(true);
            return;
        }

        MineRegion mineRegion = resolveMineRegion(player, loc);
        if (mineRegion == null) {
            return;
        }

        Material blockType = block.getType();
        MineBlockConfig blockConfig = mineRegion.getBlockConfig(blockType);
        if (blockConfig == null) {
            return;
        }

        // Cancel vanilla break attempt so bedrock or regional protection stays intact
        event.setCancelled(true);

        if (player.getGameMode() == GameMode.CREATIVE && !mainConfig.getBoolean("mine.allow-creative-mining", true)) {
            return;
        }

        // Process mining hit
        handleMiningHit(player, block, blockConfig, mineRegion);
    }

    /**
     * Fallback for left-click block interaction in case BlockDamageEvent was cancelled by other plugins.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        Location loc = block.getLocation();
        if (handler.isPendingRespawn(loc)) {
            event.setCancelled(true);
            return;
        }

        MineRegion mineRegion = resolveMineRegion(player, loc);
        if (mineRegion == null) {
            return;
        }

        Material blockType = block.getType();
        MineBlockConfig blockConfig = mineRegion.getBlockConfig(blockType);
        if (blockConfig == null) {
            return;
        }

        if (player.getGameMode() == GameMode.CREATIVE && !mainConfig.getBoolean("mine.allow-creative-mining", true)) {
            return;
        }

        // Rate limit hits to prevent macro spam (e.g., max 4 hits per second)
        long now = System.currentTimeMillis();
        long last = clickCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 250L) {
            event.setCancelled(true);
            return;
        }
        clickCooldown.put(player.getUniqueId(), now);

        event.setCancelled(true);
        handleMiningHit(player, block, blockConfig, mineRegion);
    }

    /**
     * Catches block break events (either vanilla mining completion or instant mining).
     * Prevents the block from permanently disappearing like vanilla and handles regeneration and drops.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        Block centerBlock = event.getBlock();
        Location loc = centerBlock.getLocation();
        Player player = event.getPlayer();

        if (recursiveProtection.contains(loc) || handler.isPendingRespawn(loc)) {
            event.setCancelled(true);
            event.setDropItems(false);
            event.setExpToDrop(0);
            return;
        }

        MineRegion mineRegion = resolveMineRegion(player, loc);
        if (mineRegion == null) {
            return;
        }

        Material blockType = centerBlock.getType();
        MineBlockConfig blockConfig = mineRegion.getBlockConfig(blockType);
        if (blockConfig == null) {
            return;
        }

        // Cancel vanilla break completely: block must NOT disappear into air or drop vanilla items
        event.setCancelled(true);
        event.setDropItems(false);
        event.setExpToDrop(0);

        ItemStack handItem = player.getInventory().getItemInMainHand();
        PickaxeProfile profile = pickaxeManager.getProfile(handItem);

        // Validate tool power and profile requirements
        if (!validatePickaxe(player, profile, blockConfig, blockType)) {
            return;
        }

        // When standard BlockBreakEvent fires, the player completed the mining process
        int radius = profile != null ? profile.breakRadius() : 0;
        if (radius <= 0) {
            completeBlockBreak(player, centerBlock, blockConfig, profile);
        } else {
            processAoeMining(player, centerBlock, mineRegion, profile, radius);
        }
    }

    private void handleMiningHit(Player player, Block block, MineBlockConfig blockConfig, MineRegion mineRegion) {
        ItemStack handItem = player.getInventory().getItemInMainHand();
        PickaxeProfile profile = pickaxeManager.getProfile(handItem);

        // Validate tool power and profile requirements
        if (!validatePickaxe(player, profile, blockConfig, block.getType())) {
            return;
        }

        int radius = profile != null ? profile.breakRadius() : 0;
        if (radius <= 0) {
            processProgressiveHit(player, block, blockConfig, profile);
        } else {
            processAoeMining(player, block, mineRegion, profile, radius);
        }
    }

    private void processProgressiveHit(
            Player player,
            Block block,
            MineBlockConfig blockConfig,
            @Nullable PickaxeProfile profile
    ) {
        Location loc = block.getLocation();
        int damage = profile != null ? profile.damagePerHit() : 1;
        int requiredHits = blockConfig.getHardnessHits();

        boolean broken = handler.applyDamage(loc, damage, requiredHits);
        if (!broken) {
            int currentHits = handler.getCurrentHits(loc);
            float progress = Math.min(1.0f, (float) currentHits / (float) requiredHits);
            player.sendBlockDamage(loc, progress);

            sendActionBar(player, "messages.mine.hardness-progress",
                    "<yellow>Block Integrity: <green><current></green><gray>/<white><max></white>",
                    "<current>", String.valueOf(currentHits),
                    "<max>", String.valueOf(requiredHits)
            );
            return;
        }

        completeBlockBreak(player, block, blockConfig, profile);
    }

    /**
     * Executes the full breaking cycle:
     * 1. Resets crack visual animation & hit counter.
     * 2. Plays break sound & particles.
     * 3. Drops custom items (Mythic / Vanilla) with multiplier calculations.
     * 4. Executes configured console commands.
     * 5. Instantly turns block into replaceBlock (e.g. BEDROCK) and schedules sequenced regeneration.
     */
    private void completeBlockBreak(
            Player player,
            Block block,
            MineBlockConfig blockConfig,
            @Nullable PickaxeProfile profile
    ) {
        Location loc = block.getLocation();
        Material originalType = block.getType();

        // Reset visual break crack animation and damage counter
        handler.resetHits(loc);
        player.sendBlockDamage(loc, 0.0f);

        // Break sound & particles
        playBreakEffects(player, loc, profile, originalType);

        // Distribute drops
        double bonusMultiplierChance = profile != null ? profile.multiplierBonus() : 0.0;
        boolean directToInventory = profile != null ? profile.directToInventory() : mainConfig.getBoolean("mine.drop-direct-to-inventory", false);
        processDrops(player, loc, blockConfig.getDrops(), bonusMultiplierChance, directToInventory);

        // Execute commands
        executeCommands(player, blockConfig.getCommands());

        // Sequenced regeneration: immediately sets to replace-block and schedules respawn
        handler.scheduleRegeneration(
                block,
                originalType,
                blockConfig.getReplaceBlock(),
                blockConfig.getRegenPool(),
                blockConfig.getRespawnTimeSeconds()
        );
    }

    @Nullable
    private MineRegion resolveMineRegion(Player player, Location loc) {
        if (!isWorldWhitelisted(loc)) {
            return null;
        }

        // Players who toggled bypass mode can build/break naturally
        if (plugin.isBypassing(player.getUniqueId())) {
            return null;
        }

        // 1. Direct link via WorldGuard 'mine-id' custom StringFlag
        String customMineId = WorldGuardHook.getCustomMineId(loc);
        if (customMineId != null) {
            MineRegion directMatch = mineManager.getRegion(customMineId);
            if (directMatch != null) {
                return directMatch;
            }
        }

        // 2. Check WorldGuard 'mine-region' or 'custom-mine' StateFlag
        boolean isMineRegionFlagAllowed = WorldGuardHook.isMineRegionAllowed(player, loc);
        boolean requireFlag = mainConfig.getBoolean("mine.require-worldguard-custom-flag", true);

        Set<String> regionIds = WorldGuardHook.getRegionsAt(loc);
        if (regionIds.isEmpty()) {
            return null;
        }

        if (requireFlag && !isMineRegionFlagAllowed) {
            return null;
        }

        for (String id : regionIds) {
            MineRegion r = mineManager.getRegion(id);
            if (r != null) {
                return r;
            }
        }

        return null;
    }

    private boolean isWorldWhitelisted(Location loc) {
        if (loc.getWorld() == null) return false;
        List<String> whitelistWorlds = mainConfig.getStringList("whitelist-worlds");
        if (whitelistWorlds.isEmpty()) {
            return true;
        }

        String worldName = loc.getWorld().getName();
        String worldKey = loc.getWorld().key().asString();
        String normalizedName = worldName.replace(":", "_");
        String normalizedKey = worldKey.replace(":", "_");

        for (String allowed : whitelistWorlds) {
            String normAllowed = allowed.replace(":", "_");
            if (worldName.equalsIgnoreCase(allowed)
                    || worldKey.equalsIgnoreCase(allowed)
                    || normalizedName.equalsIgnoreCase(normAllowed)
                    || normalizedKey.equalsIgnoreCase(normAllowed)) {
                return true;
            }
            if (worldKey.startsWith("minecraft:") && allowed.equalsIgnoreCase(worldKey.substring(10))) {
                return true;
            }
        }
        return false;
    }

    private boolean validatePickaxe(
            Player player,
            @Nullable PickaxeProfile profile,
            MineBlockConfig blockConfig,
            Material blockType
    ) {
        if (profile != null && !profile.canMineMaterial(blockType)) {
            sendConfigMessage(player, "messages.mine.cannot-mine-material", "<red>This pickaxe cannot mine this block!");
            return false;
        }

        int heldPower = profile != null ? profile.miningPower() : 0;
        if (heldPower < blockConfig.getRequiredMiningPower()) {
            sendConfigMessage(player, "messages.mine.insufficient-mining-power",
                    "<red>Mining Power too low! Required: <gold><required></gold> (Your tool: <yellow><current></yellow>)",
                    "<required>", String.valueOf(blockConfig.getRequiredMiningPower()),
                    "<current>", String.valueOf(heldPower)
            );
            return false;
        }

        if (blockConfig.getRequiredPickaxeProfile() != null && !blockConfig.getRequiredPickaxeProfile().isEmpty()) {
            if (profile == null || !profile.id().equalsIgnoreCase(blockConfig.getRequiredPickaxeProfile())) {
                sendConfigMessage(player, "messages.mine.required-profile",
                        "<red>You must use pickaxe profile: <gold><profile></gold>!",
                        "<profile>", blockConfig.getRequiredPickaxeProfile()
                );
                return false;
            }
        }

        if (!blockConfig.getAllowedPickaxeProfiles().isEmpty()) {
            if (profile == null || !blockConfig.getAllowedPickaxeProfiles().contains(profile.id().toLowerCase(Locale.ROOT))) {
                sendConfigMessage(player, "messages.mine.not-allowed-profile", "<red>This tool is not permitted for mining this ore!");
                return false;
            }
        }

        return true;
    }

    private void processAoeMining(
            Player player,
            Block centerBlock,
            MineRegion mineRegion,
            @Nullable PickaxeProfile profile,
            int radius
    ) {
        Location centerLoc = centerBlock.getLocation();
        int cx = centerLoc.getBlockX();
        int cy = centerLoc.getBlockY();
        int cz = centerLoc.getBlockZ();

        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int y = cy - radius; y <= cy + radius; y++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    Block targetBlock = centerBlock.getWorld().getBlockAt(x, y, z);
                    Location tLoc = targetBlock.getLocation();

                    if (handler.isPendingRespawn(tLoc)) continue;

                    MineBlockConfig cfg = mineRegion.getBlockConfig(targetBlock.getType());
                    if (cfg == null) continue;

                    if (!validatePickaxe(player, profile, cfg, targetBlock.getType())) continue;

                    recursiveProtection.add(tLoc);
                    try {
                        completeBlockBreak(player, targetBlock, cfg, profile);
                    } finally {
                        recursiveProtection.remove(tLoc);
                    }
                }
            }
        }
    }

    private void processDrops(
            Player player,
            Location loc,
            List<MineDrop> drops,
            double bonusMultiplierChance,
            boolean directToInventory
    ) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (MineDrop drop : drops) {
            double roll = random.nextDouble(100.0);
            if (roll > drop.chance()) {
                continue;
            }

            int finalMultiplier = 1;
            for (MineDrop.MultiplierChance mc : drop.multipliers()) {
                double chanceWithBonus = mc.chance() + (bonusMultiplierChance * 100.0);
                if (random.nextDouble(100.0) <= chanceWithBonus) {
                    finalMultiplier = mc.multiplier();
                    sendActionBar(player, "messages.mine.multiplier-notify",
                            "<gold><bold>LUCKY HIT!</bold> <yellow>Received x<multiplier> multiplier!",
                            "<multiplier>", String.valueOf(finalMultiplier)
                    );
                    break;
                }
            }

            if (drop.mythicDropTable() != null && !drop.mythicDropTable().isEmpty()) {
                List<ItemStack> dtItems = MythicMobsHook.getDropTableItems(drop.mythicDropTable(), player);
                for (ItemStack dtItem : dtItems) {
                    dtItem.setAmount(Math.max(1, dtItem.getAmount() * finalMultiplier));
                    deliverItem(player, loc, dtItem, directToInventory);
                }
                continue;
            }

            int amount = drop.minAmount();
            if (drop.maxAmount() > drop.minAmount()) {
                amount += random.nextInt(drop.maxAmount() - drop.minAmount() + 1);
            }
            int totalAmount = amount * finalMultiplier;

            ItemStack itemToDrop = null;
            if (drop.mythicItem() != null && !drop.mythicItem().isEmpty()) {
                itemToDrop = MythicMobsHook.getItem(drop.mythicItem(), totalAmount, player);
            } else if (drop.vanillaMaterial() != null) {
                Material mat = Material.matchMaterial(drop.vanillaMaterial());
                if (mat != null) {
                    itemToDrop = new ItemStack(mat, totalAmount);
                }
            }

            if (itemToDrop != null) {
                deliverItem(player, loc, itemToDrop, directToInventory);
            }
        }
    }

    private void executeCommands(Player player, List<String> commands) {
        if (commands.isEmpty()) return;
        for (String cmd : commands) {
            String formatted = cmd.replace("%player_name%", player.getName())
                    .replace("%player_uuid%", player.getUniqueId().toString());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formatted);
        }
    }

    private void deliverItem(Player player, Location loc, ItemStack item, boolean directToInventory) {
        Location dropLoc = loc.clone().add(0.5, 0.5, 0.5);
        if (directToInventory) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack left : leftover.values()) {
                dropLoc.getWorld().dropItemNaturally(dropLoc, left);
            }
        } else {
            dropLoc.getWorld().dropItemNaturally(dropLoc, item);
        }
    }

    private void playBreakEffects(Player player, Location loc, @Nullable PickaxeProfile profile, Material originalMat) {
        Location centerLoc = loc.clone().add(0.5, 0.5, 0.5);
        if (profile != null && profile.breakSoundKey() != null) {
            Sound sound = Sound.sound(profile.breakSoundKey(), Sound.Source.BLOCK, profile.soundVolume(), profile.soundPitch());
            loc.getWorld().playSound(sound, centerLoc.getX(), centerLoc.getY(), centerLoc.getZ());
        } else {
            try {
                loc.getWorld().playSound(centerLoc, originalMat.createBlockData().getSoundGroup().getBreakSound(), 1.0f, 1.0f);
            } catch (Throwable ignored) {}
        }

        if (profile != null && profile.breakParticle() != null && !profile.breakParticle().isEmpty()) {
            try {
                Particle p = Particle.valueOf(profile.breakParticle().toUpperCase(Locale.ROOT));
                loc.getWorld().spawnParticle(p, centerLoc, 15, 0.3, 0.3, 0.3, 0.05);
            } catch (IllegalArgumentException ignored) {}
        } else {
            try {
                loc.getWorld().spawnParticle(Particle.BLOCK, centerLoc, 20, 0.25, 0.25, 0.25, 0.05, originalMat.createBlockData());
            } catch (Throwable ignored) {}
        }
    }

    private void sendConfigMessage(Player player, String path, String def, String... placeholders) {
        String prefix = mainConfig.getString("prefix", "");
        String msg = mainConfig.getString(path, def);
        for (int i = 0; i < placeholders.length - 1; i += 2) {
            msg = msg.replace(placeholders[i], placeholders[i + 1]);
        }
        player.sendMessage(ColorParser.parse(prefix + msg));
    }

    private void sendActionBar(Player player, String path, String def, String... placeholders) {
        String msg = mainConfig.getString(path, def);
        for (int i = 0; i < placeholders.length - 1; i += 2) {
            msg = msg.replace(placeholders[i], placeholders[i + 1]);
        }
        player.sendActionBar(ColorParser.parse(msg));
    }
}
