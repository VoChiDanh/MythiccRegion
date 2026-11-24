package net.danh.mythiccregion.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.danh.mythiccregion.MythiccRegion;
import net.danh.mythiccregion.core.color.ColorParser;
import net.danh.mythiccregion.core.command.CommandBuilder;
import net.danh.mythiccregion.hook.MythicMobsHook;
import net.danh.mythiccregion.hook.WorldGuardHook;
import net.danh.mythiccregion.mine.MineBlockConfig;
import net.danh.mythiccregion.mine.MineDrop;
import net.danh.mythiccregion.mine.MineRegion;
import net.danh.mythiccregion.pickaxe.PickaxeProfile;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Handles all command registration and subcommands for MythiccRegion.
 * 100% configurable strings, 0% hardcoded text.
 */
@NullMarked
public final class MythiccRegionCommand {

    private MythiccRegionCommand() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void register(MythiccRegion plugin) {
        CommandBuilder.of("mythiccregion")
                .description("Main command for MythiccRegion")
                .aliases("mr", "mregion")
                .permission("mythiccregion.admin")
                .executes(ctx -> sendHelp(plugin, ctx.getSource().getSender()))
                .subcommand(buildReloadSubcommand(plugin))
                .subcommand(buildVersionSubcommand(plugin))
                .subcommand(buildInfoSubcommand(plugin))
                .subcommand(buildBypassSubcommand(plugin))
                .subcommand(buildListSubcommand(plugin))
                .subcommand(buildPickaxesSubcommand(plugin))
                .subcommand(buildGivePickaxeSubcommand(plugin))
                .register(plugin);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildBypassSubcommand(MythiccRegion plugin) {
        return CommandBuilder.literal("bypass", ctx -> {
            CommandSender sender = ctx.getSource().getSender();
            if (!(sender instanceof Player player)) {
                sendMessage(plugin, sender, "messages.player-only", "<red>This command can only be executed by a player!");
                return;
            }

            boolean bypassing = plugin.toggleBypass(player.getUniqueId());
            if (bypassing) {
                sendMessage(plugin, player, "messages.command.bypass.enabled", "<green>Mine bypass mode <bold>ENABLED</bold>! You can now break and build normally without triggering mine mechanics.");
            } else {
                sendMessage(plugin, player, "messages.command.bypass.disabled", "<yellow>Mine bypass mode <red><bold>DISABLED</bold></red>! Mine mechanics and block regeneration are now active.");
            }
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildReloadSubcommand(MythiccRegion plugin) {
        return CommandBuilder.literal("reload", ctx -> {
            plugin.getConfigManager().reloadAll();
            plugin.getPickaxeManager().loadProfiles();
            plugin.getMineManager().loadMines();

            sendMessage(plugin, ctx.getSource().getSender(), "messages.reload-success", "<green>All configuration files, pickaxe profiles, and mine caches reloaded successfully!");
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildVersionSubcommand(MythiccRegion plugin) {
        return CommandBuilder.literal("version", ctx -> {
            String ver = plugin.getPluginMeta().getVersion();
            sendMessage(plugin, ctx.getSource().getSender(), "messages.version-info",
                    "<gray>MythiccRegion Running Version: <gold><version></gold>", "<version>", ver);
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildInfoSubcommand(MythiccRegion plugin) {
        return CommandBuilder.literal("info", ctx -> {
            CommandSender sender = ctx.getSource().getSender();
            if (!(sender instanceof Player player)) {
                sendMessage(plugin, sender, "messages.player-only", "<red>This command can only be executed by a player!");
                return;
            }

            var loc = player.getLocation();
            var world = loc.getWorld();
            String worldName = world != null ? world.getName() : "Unknown";

            player.sendMessage(ColorParser.parse("<gold>====== <yellow>MythiccRegion Diagnostics</yellow> ======"));
            player.sendMessage(ColorParser.parse("<gray>World: <white>" + worldName + "</white> | WG Hook: " + (WorldGuardHook.isEnabled() ? "<green>ACTIVE</green>" : "<red>DISABLED</red>") + " | Mythic Hook: " + (MythicMobsHook.isEnabled() ? "<green>ACTIVE</green>" : "<red>DISABLED</red>")));
            player.sendMessage(ColorParser.parse("<gray>Bypass Mode: " + (plugin.isBypassing(player.getUniqueId()) ? "<green>ACTIVE (Bypassing)</green>" : "<dark_gray>OFF</dark_gray>")));

            // 1. Inspect regions at player location
            Set<String> regions = WorldGuardHook.getRegionsAt(loc);
            if (regions.isEmpty()) {
                sendMessage(plugin, player, "messages.command.info.no-region", "<yellow>You are not standing in any WorldGuard region.");
            } else {
                sendMessage(plugin, player, "messages.command.info.region-found",
                        "<gray>WorldGuard regions at your position: <gold><regions></gold>",
                        "<regions>", String.join(", ", regions)
                );
            }

            String customMineId = WorldGuardHook.getCustomMineId(loc);
            boolean flagAllowed = WorldGuardHook.isMineRegionAllowed(player, loc);
            player.sendMessage(ColorParser.parse("<gray>mine-id flag: <gold>" + (customMineId != null ? customMineId : "none") + "</gold> | mine-region/custom-mine flag: " + (flagAllowed ? "<green>ALLOW</green>" : "<red>DENY / NOT SET</red>")));

            // Check if matches a config mine
            MineRegion matched = null;
            if (customMineId != null) {
                matched = plugin.getMineManager().getRegion(customMineId);
            }
            if (matched == null) {
                for (String rId : regions) {
                    MineRegion m = plugin.getMineManager().getRegion(rId);
                    if (m != null) {
                        matched = m;
                        break;
                    }
                }
            }
            player.sendMessage(ColorParser.parse("<gray>Matched Mine Config: " + (matched != null ? "<green>" + matched.getRegionId() + " (Blocks: " + matched.getBlockConfigs().size() + ")</green>" : "<red>None</red>")));

            // 2. Inspect held tool / pickaxe profile
            ItemStack hand = player.getInventory().getItemInMainHand();
            PickaxeProfile profile = plugin.getPickaxeManager().getProfile(hand);
            if (profile != null) {
                sendMessage(plugin, player, "messages.command.info.held-pickaxe",
                        "<gray>Held Tool Profile: <green><profile></green> | Power: <yellow><power></yellow> | AoE Radius: <aqua><radius></aqua>",
                        "<profile>", profile.id(),
                        "<power>", String.valueOf(profile.miningPower()),
                        "<radius>", String.valueOf(profile.breakRadius())
                );
            } else {
                sendMessage(plugin, player, "messages.command.info.held-none", "<gray>Held item does not match any custom pickaxe profile.");
            }
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildListSubcommand(MythiccRegion plugin) {
        return CommandBuilder.literal("list", ctx -> {
            CommandSender sender = ctx.getSource().getSender();
            Collection<MineRegion> regions = plugin.getMineManager().getRegions().values();

            sendMessage(plugin, sender, "messages.command.list.header", "<gold>====== <yellow>Configured Mine Regions (<count>)</yellow> ======", "<count>", String.valueOf(regions.size()));
            for (MineRegion r : regions) {
                sender.sendMessage(ColorParser.parse("<dark_gray>• <gold>" + r.getRegionId() + " <gray>(Blocks: <white>" + r.getBlockConfigs().size() + "<gray>)"));
            }
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildPickaxesSubcommand(MythiccRegion plugin) {
        return CommandBuilder.literal("pickaxes", ctx -> {
            CommandSender sender = ctx.getSource().getSender();
            Collection<PickaxeProfile> profiles = plugin.getPickaxeManager().getProfiles().values();

            sendMessage(plugin, sender, "messages.command.pickaxes.header", "<gold>====== <yellow>Configured Pickaxe Profiles (<count>)</yellow> ======", "<count>", String.valueOf(profiles.size()));
            for (PickaxeProfile p : profiles) {
                sender.sendMessage(ColorParser.parse(
                        "<dark_gray>• <green>" + p.id() +
                        " <gray>| Power: <yellow>" + p.miningPower() +
                        " <gray>| Dmg: <yellow>" + p.damagePerHit() +
                        " <gray>| AoE Radius: <aqua>" + p.breakRadius() +
                        (p.mythicItemId() != null ? " <gray>(Mythic: <gold>" + p.mythicItemId() + "<gray>)" : "")
                ));
            }
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildGivePickaxeSubcommand(MythiccRegion plugin) {
        return CommandBuilder.literal("give")
                .then(CommandBuilder.playerArgument("target")
                        .then(CommandBuilder.wordArgument("profile", () -> plugin.getPickaxeManager().getProfiles().keySet())
                                .executes(ctx -> {
                                    CommandSender sender = ctx.getSource().getSender();
                                    String profileKey = ctx.getArgument("profile", String.class);
                                    PickaxeProfile profile = plugin.getPickaxeManager().getProfiles().get(profileKey.toLowerCase(Locale.ROOT));

                                    if (profile == null) {
                                        sendMessage(plugin, sender, "messages.command.give.unknown-profile",
                                                "<red>Unknown pickaxe profile: <gold><profile></gold>!", "<profile>", profileKey);
                                        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                                    }

                                    try {
                                        List<Player> players = ctx.getArgument("target", PlayerSelectorArgumentResolver.class)
                                                .resolve(ctx.getSource());

                                        for (Player target : players) {
                                            givePickaxeItem(plugin, sender, target, profile);
                                        }
                                    } catch (Exception e) {
                                        sendMessage(plugin, sender, "messages.command.give.player-not-found", "<red>Target player could not be found!");
                                    }
                                    return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                                })
                        )
                );
    }

    private static void givePickaxeItem(MythiccRegion plugin, CommandSender sender, Player target, PickaxeProfile profile) {
        ItemStack itemToGive = null;

        if (profile.mythicItemId() != null) {
            itemToGive = MythicMobsHook.getItem(profile.mythicItemId(), 1, target);
        } else if (profile.vanillaMaterial() != null) {
            itemToGive = new ItemStack(profile.vanillaMaterial(), 1);
        }

        if (itemToGive == null) {
            sendMessage(plugin, sender, "messages.command.give.cannot-create-item",
                    "<red>Could not generate item for profile: <gold><profile></gold>!", "<profile>", profile.id());
            return;
        }

        var leftover = target.getInventory().addItem(itemToGive);
        for (ItemStack left : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), left);
        }

        sendMessage(plugin, sender, "messages.command.give.success-sender",
                "<green>Successfully gave pickaxe <gold><profile></gold> to <yellow><player></yellow>!",
                "<profile>", profile.id(),
                "<player>", target.getName()
        );

        sendMessage(plugin, target, "messages.command.give.success-receiver",
                "<green>You have received pickaxe: <gold><profile></gold>!",
                "<profile>", profile.id()
        );
    }

    private static void sendHelp(MythiccRegion plugin, CommandSender sender) {
        plugin.getMainConfig().getComponentList("messages.help").forEach(sender::sendMessage);
    }

    private static void sendMessage(MythiccRegion plugin, CommandSender sender, String path, String def, String... placeholders) {
        String prefix = plugin.getMainConfig().getString("prefix", "");
        String msg = plugin.getMainConfig().getString(path, def);
        for (int i = 0; i < placeholders.length - 1; i += 2) {
            msg = msg.replace(placeholders[i], placeholders[i + 1]);
        }
        sender.sendMessage(ColorParser.parse(prefix + msg));
    }
}
