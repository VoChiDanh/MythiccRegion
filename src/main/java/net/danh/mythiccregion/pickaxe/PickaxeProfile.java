package net.danh.mythiccregion.pickaxe;

import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * Immutable profile data class representing custom mining tools and pickaxes.
 * Everything is read from pickaxes.yml without any hardcoded values.
 */
@NullMarked
public record PickaxeProfile(
        String id,
        @Nullable String mythicItemId,
        @Nullable Material vanillaMaterial,
        int miningPower,
        int damagePerHit,
        int breakRadius,
        double multiplierBonus,
        boolean directToInventory,
        @Nullable Key breakSoundKey,
        float soundVolume,
        float soundPitch,
        @Nullable String breakParticle,
        Set<Material> allowedMaterials,
        Set<Material> deniedMaterials
) {
    public PickaxeProfile {
        allowedMaterials = allowedMaterials != null ? Set.copyOf(allowedMaterials) : Collections.emptySet();
        deniedMaterials = deniedMaterials != null ? Set.copyOf(deniedMaterials) : Collections.emptySet();
    }

    /**
     * Checks if this pickaxe profile can mine a given block material.
     */
    public boolean canMineMaterial(Material material) {
        if (!deniedMaterials.isEmpty() && deniedMaterials.contains(material)) {
            return false;
        }
        if (!allowedMaterials.isEmpty() && !allowedMaterials.contains(material)) {
            return false;
        }
        return true;
    }
}
