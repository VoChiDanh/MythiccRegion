package net.danh.mythiccregion.mine;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Drop item definition supporting Mythic item IDs, Mythic DropTables, and Vanilla Materials.
 * Loaded 100% from mines.yml without hardcoded values.
 */
@NullMarked
public record MineDrop(
        @Nullable String mythicItem,
        @Nullable String mythicDropTable,
        @Nullable String vanillaMaterial,
        int minAmount,
        int maxAmount,
        double chance,
        List<MultiplierChance> multipliers
) {
    public MineDrop {
        multipliers = multipliers != null ? List.copyOf(multipliers) : Collections.emptyList();
    }

    public record MultiplierChance(int multiplier, double chance) {}
}
