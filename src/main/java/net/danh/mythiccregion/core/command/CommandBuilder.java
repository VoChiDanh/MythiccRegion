package net.danh.mythiccregion.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.danh.mythiccregion.core.color.ColorParser;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 100% Pure Paper Brigadier Command API Wrapper.
 * Provides a fluid builder for modern paper-brigadier commands with permission checks,
 * sender validation, subcommands, arguments, suggestions, and aliases.
 */
@NullMarked
public class CommandBuilder {

    private final String name;
    @Nullable
    private String description;
    private Collection<String> aliases = Collections.emptyList();
    @Nullable
    private String permission;
    private boolean playerOnly = false;
    private final LiteralArgumentBuilder<CommandSourceStack> root;

    public CommandBuilder(String name) {
        this.name = name;
        this.root = Commands.literal(name);
    }

    public static CommandBuilder of(String name) {
        return new CommandBuilder(name);
    }

    public CommandBuilder description(String description) {
        this.description = description;
        return this;
    }

    public CommandBuilder aliases(String... aliases) {
        this.aliases = List.of(aliases);
        return this;
    }

    public CommandBuilder permission(String permission) {
        this.permission = permission;
        this.root.requires(source -> source.getSender().hasPermission(permission));
        return this;
    }

    public CommandBuilder playerOnly() {
        this.playerOnly = true;
        return this;
    }

    /**
     * Sets the root execution handler with a Consumer lambda.
     */
    public CommandBuilder executes(Consumer<CommandContext<CommandSourceStack>> executor) {
        this.root.executes(ctx -> {
            if (playerOnly && !(ctx.getSource().getSender() instanceof Player)) {
                ctx.getSource().getSender().sendMessage(ColorParser.parse("<red>This command is player-only!"));
                return Command.SINGLE_SUCCESS;
            }
            executor.accept(ctx);
            return Command.SINGLE_SUCCESS;
        });
        return this;
    }

    /**
     * Adds a literal subcommand.
     */
    public CommandBuilder subcommand(LiteralArgumentBuilder<CommandSourceStack> sub) {
        this.root.then(sub);
        return this;
    }

    /**
     * Adds an argument node.
     */
    public CommandBuilder argument(RequiredArgumentBuilder<CommandSourceStack, ?> arg) {
        this.root.then(arg);
        return this;
    }

    /**
     * Registers this command to Paper's LifecycleEventManager.
     */
    public void register(Plugin plugin) {
        LifecycleEventManager<Plugin> manager = plugin.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            registrar.register(
                    root.build(),
                    description != null ? description : "",
                    aliases
            );
        });
    }

    // --- Helper builder methods for subcommands & arguments ---

    public static LiteralArgumentBuilder<CommandSourceStack> literal(String name, Consumer<CommandContext<CommandSourceStack>> executor) {
        return Commands.literal(name).executes(ctx -> {
            executor.accept(ctx);
            return Command.SINGLE_SUCCESS;
        });
    }

    public static LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
        return Commands.literal(name);
    }

    public static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(String name, ArgumentType<T> type) {
        return Commands.argument(name, type);
    }

    public static RequiredArgumentBuilder<CommandSourceStack, String> wordArgument(String name) {
        return Commands.argument(name, StringArgumentType.word());
    }

    public static RequiredArgumentBuilder<CommandSourceStack, String> wordArgument(String name, Supplier<Collection<String>> suggestions) {
        return Commands.argument(name, StringArgumentType.word()).suggests((ctx, builder) -> {
            for (String s : suggestions.get()) {
                if (s.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                    builder.suggest(s);
                }
            }
            return builder.buildFuture();
        });
    }

    public static RequiredArgumentBuilder<CommandSourceStack, String> greedyStringArgument(String name) {
        return Commands.argument(name, StringArgumentType.greedyString());
    }

    public static RequiredArgumentBuilder<CommandSourceStack, Integer> integerArgument(String name, int min, int max) {
        return Commands.argument(name, IntegerArgumentType.integer(min, max));
    }

    public static RequiredArgumentBuilder<CommandSourceStack, Double> doubleArgument(String name, double min, double max) {
        return Commands.argument(name, DoubleArgumentType.doubleArg(min, max));
    }

    public static RequiredArgumentBuilder<CommandSourceStack, PlayerSelectorArgumentResolver> playerArgument(String name) {
        return Commands.argument(name, ArgumentTypes.player());
    }

    public static void reply(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().getSender().sendMessage(ColorParser.parse(message));
    }

    public static void reply(CommandContext<CommandSourceStack> ctx, Component component) {
        ctx.getSource().getSender().sendMessage(component);
    }
}
