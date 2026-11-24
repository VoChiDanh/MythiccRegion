package net.danh.mythiccregion.core.color;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modern Paper-native color and text formatting utility.
 * Supports Adventure MiniMessage (<red>, <gradient>, <#rrggbb>, etc.)
 * as well as legacy '&' and '&#rrggbb' formats without any Spigot API (Bungee ChatColor).
 */
@NullMarked
public final class ColorParser {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer AMPERSAND_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final PlainTextComponentSerializer PLAIN_SERIALIZER = PlainTextComponentSerializer.plainText();

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private ColorParser() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Parses a string containing MiniMessage tags or legacy color codes into an Adventure {@link Component}.
     *
     * @param text The input string.
     * @return The formatted Adventure Component.
     */
    public static Component parse(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        // If string contains modern MiniMessage formatting tags like <red>, <#rrggbb>, <bold>
        if (hasMiniMessageTags(text)) {
            return MINI_MESSAGE.deserialize(text);
        }

        // Handle legacy &#rrggbb -> &x&r&r&g&g&b&b before serializing
        String normalized = convertHexToLegacy(text);

        if (normalized.indexOf('&') != -1) {
            return AMPERSAND_SERIALIZER.deserialize(normalized);
        }

        if (normalized.indexOf(LegacyComponentSerializer.SECTION_CHAR) != -1) {
            return SECTION_SERIALIZER.deserialize(normalized);
        }

        return Component.text(normalized);
    }

    /**
     * Parses a list of strings into a list of Adventure {@link Component}s.
     *
     * @param lines The list of strings to parse.
     * @return Formatted list of Components.
     */
    public static List<Component> parseList(@Nullable List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }
        return lines.stream().map(ColorParser::parse).toList();
    }

    /**
     * Strips all formatting, colors, and tags, returning plain text.
     *
     * @param text The input string.
     * @return Plain text without formatting.
     */
    public static String stripColor(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return PLAIN_SERIALIZER.serialize(parse(text));
    }

    /**
     * Serializes an Adventure Component to plain text.
     */
    public static String toPlainText(Component component) {
        return PLAIN_SERIALIZER.serialize(component);
    }

    /**
     * Serializes an Adventure Component back into MiniMessage tag syntax.
     */
    public static String toMiniMessage(Component component) {
        return MINI_MESSAGE.serialize(component);
    }

    /**
     * Serializes an Adventure Component into legacy string with '&' formatting codes.
     */
    public static String toLegacyAmpersand(Component component) {
        return AMPERSAND_SERIALIZER.serialize(component);
    }

    /**
     * Converts '&#rrggbb' hex codes into legacy '&x&r&r&g&g&b&b' format compatible with Paper legacy serializer.
     */
    private static String convertHexToLegacy(String text) {
        Matcher matcher = HEX_COLOR_PATTERN.matcher(text);
        if (!matcher.find()) {
            return text;
        }

        StringBuilder buffer = new StringBuilder(text.length() + 32);
        do {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("&x");
            for (char c : hex.toCharArray()) {
                replacement.append('&').append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        } while (matcher.find());
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static boolean hasMiniMessageTags(String text) {
        int openIdx = text.indexOf('<');
        return openIdx != -1 && text.indexOf('>', openIdx) != -1;
    }
}
