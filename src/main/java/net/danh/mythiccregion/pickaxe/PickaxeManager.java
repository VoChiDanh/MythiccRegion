package net.danh.mythiccregion.pickaxe;

import net.danh.mythiccregion.core.config.ConfigFile;
import net.danh.mythiccregion.hook.MythicMobsHook;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all pickaxe profiles loaded dynamically from pickaxes.yml.
 * Thread-safe maps and sets prevent concurrency issues across Folia worker threads.
 */
@NullMarked
public class PickaxeManager {

    private final Plugin plugin;
    private final ConfigFile pickaxesConfig;

    // Fast O(1) lookups by Mythic Item ID and Vanilla Material
    private final Map<String, PickaxeProfile> profilesById = new ConcurrentHashMap<>();
    private final Map<String, PickaxeProfile> profilesByMythicId = new ConcurrentHashMap<>();
    private final Map<Material, PickaxeProfile> profilesByMaterial = new ConcurrentHashMap<>();

    @Nullable
    private PickaxeProfile defaultProfile;

    public PickaxeManager(Plugin plugin, ConfigFile pickaxesConfig) {
        this.plugin = plugin;
        this.pickaxesConfig = pickaxesConfig;
        loadProfiles();
    }

    /**
     * Reloads all pickaxe definitions from pickaxes.yml.
     */
    public synchronized void loadProfiles() {
        profilesById.clear();
        profilesByMythicId.clear();
        profilesByMaterial.clear();
        defaultProfile = null;

        pickaxesConfig.reload();

        ConfigurationSection defaultSec = pickaxesConfig.getConfigurationSection("default-profile");
        if (defaultSec != null) {
            defaultProfile = parseProfile("default-profile", defaultSec);
        }

        ConfigurationSection pickaxesSec = pickaxesConfig.getConfigurationSection("pickaxes");
        if (pickaxesSec != null) {
            for (String key : pickaxesSec.getKeys(false)) {
                ConfigurationSection sec = pickaxesSec.getConfigurationSection(key);
                if (sec == null) continue;

                PickaxeProfile profile = parseProfile(key, sec);
                profilesById.put(key.toLowerCase(Locale.ROOT), profile);

                if (profile.mythicItemId() != null) {
                    profilesByMythicId.put(profile.mythicItemId().toLowerCase(Locale.ROOT), profile);
                }
                if (profile.vanillaMaterial() != null) {
                    profilesByMaterial.put(profile.vanillaMaterial(), profile);
                }
            }
        }

        plugin.getLogger().info("Loaded " + profilesById.size() + " pickaxe profiles.");
    }

    private PickaxeProfile parseProfile(String key, ConfigurationSection sec) {
        String mythicItemId = sec.getString("mythic-item");
        String matName = sec.getString("vanilla-material");
        Material vanillaMaterial = matName != null ? Material.matchMaterial(matName) : null;

        int miningPower = sec.getInt("mining-power", 1);
        int damagePerHit = sec.getInt("damage-per-hit", 1);
        int breakRadius = Math.max(0, sec.getInt("break-radius", 0));
        double multiplierBonus = sec.getDouble("multiplier-bonus", 0.0);
        boolean directToInventory = sec.getBoolean("direct-to-inventory", false);

        String soundName = sec.getString("break-sound.name");
        Key breakSoundKey = null;
        if (soundName != null && !soundName.isEmpty()) {
            String formatted = soundName.toLowerCase(Locale.ROOT).replace('_', '.');
            if (formatted.contains(":")) {
                breakSoundKey = Key.key(formatted);
            } else {
                breakSoundKey = Key.key("minecraft", formatted);
            }
        }
        float soundVolume = (float) sec.getDouble("break-sound.volume", 1.0);
        float soundPitch = (float) sec.getDouble("break-sound.pitch", 1.0);

        String breakParticle = sec.getString("break-particle");

        Set<Material> allowed = ConcurrentHashMap.newKeySet();
        for (String m : sec.getStringList("allowed-materials")) {
            Material mat = Material.matchMaterial(m);
            if (mat != null) allowed.add(mat);
        }

        Set<Material> denied = ConcurrentHashMap.newKeySet();
        for (String m : sec.getStringList("denied-materials")) {
            Material mat = Material.matchMaterial(m);
            if (mat != null) denied.add(mat);
        }

        return new PickaxeProfile(
                key,
                mythicItemId,
                vanillaMaterial,
                miningPower,
                damagePerHit,
                breakRadius,
                multiplierBonus,
                directToInventory,
                breakSoundKey,
                soundVolume,
                soundPitch,
                breakParticle,
                allowed,
                denied
        );
    }

    /**
     * Resolves the corresponding PickaxeProfile for a held ItemStack.
     *
     * @param item The held item in player's hand.
     * @return Resolved profile, or fallback default profile, or null.
     */
    @Nullable
    public PickaxeProfile getProfile(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return defaultProfile;
        }

        // 1. Check if it's a registered MythicMobs item
        String mythicType = MythicMobsHook.getMythicType(item);
        if (mythicType != null) {
            PickaxeProfile profile = profilesByMythicId.get(mythicType.toLowerCase(Locale.ROOT));
            if (profile != null) {
                return profile;
            }
        }

        // 2. Check by vanilla Material
        PickaxeProfile profile = profilesByMaterial.get(item.getType());
        if (profile != null) {
            return profile;
        }

        // 3. Fallback to default configured profile
        return defaultProfile;
    }

    public Map<String, PickaxeProfile> getProfiles() {
        return profilesById;
    }
}
