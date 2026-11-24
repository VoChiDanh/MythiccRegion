# ⛏️ MythiccRegion

<div align="center">

![Java](https://img.shields.io/badge/Java-25-orange.svg?style=for-the-badge&logo=openjdk)
![Paper](https://img.shields.io/badge/Paper-1.20%2B-blue.svg?style=for-the-badge&logo=minecraft)
![WorldGuard](https://img.shields.io/badge/WorldGuard-7.0.21%2B-green.svg?style=for-the-badge)
![MythicMobs](https://img.shields.io/badge/MythicMobs-5.14%2B-purple.svg?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-brightgreen.svg?style=for-the-badge)[

**Advanced RPG Mining & Sequenced Block Regeneration Plugin for modern Paper servers, featuring native WorldGuard Custom Flags and deep MythicMobs integration.**
]()
</div>

---

## 🌟 Key Features

- **🔄 Sequenced Block Regeneration (`regen-pool`):**
  - Mined blocks **never permanently disappear like vanilla**.
  - Automatically replaced with a temporary placeholder block (`replace-block`, e.g., Bedrock, Obsidian) while waiting to respawn.
  - Weighted regeneration pool support: a single stone vein can regenerate into Coal, Copper, Iron, Gold, Diamond, or rare minerals dynamically.

- **🛡️ Native WorldGuard Custom Flags Integration:**
  - `mine-region: allow` or `custom-mine: allow` to enable mining in protected regions.
  - `mine-id: <mine_id>` to directly link any WorldGuard region to a configured mine profile in `mines.yml`.
  - Fully compatible with `build: deny` and `block-break: deny` flags.

- **⚔️ Deep MythicMobs Integration:**
  - Drops MythicMobs custom items directly or via DropTables (`mythic-item`, `mythic-droptable`).
  - Tiered RPG pickaxes with Mining Power (`mining-power`) and block hardness hits (`hardness-hits`).
  - Supports AoE radius mining: 3x3 (`break-radius: 1`) and 5x5 (`break-radius: 2`).

- **🎲 Resource Multipliers & Lucky Drops:**
  - Chance-based resource multiplication (x2, x3, x5) on lucky hits (`LUCKY HIT`).
  - Displays progress and notifications via MiniMessage Action Bar.

- **🛠️ Admin Bypass Mode:**
  - `/mr bypass` command allows administrators to freely place and break blocks without triggering mine mechanics.

---

## 📋 Commands & Permissions

| Command | Alias | Permission | Description |
| :--- | :--- | :--- | :--- |
| `/mythiccregion help` | `/mr` | `mythiccregion.admin` | Display plugin help menu |
| `/mythiccregion info` | `/mr info` | `mythiccregion.admin` | Inspect location, world, WorldGuard flags, mine config & held tool |
| `/mythiccregion bypass` | `/mr bypass` | `mythiccregion.admin` | Toggle admin bypass mode (build/break naturally) |
| `/mythiccregion reload` | `/mr reload` | `mythiccregion.admin` | Reload all configuration files, mines, and pickaxes |
| `/mythiccregion list` | `/mr list` | `mythiccregion.admin` | List all configured mine regions |
| `/mythiccregion pickaxes` | `/mr pickaxes` | `mythiccregion.admin` | List all configured pickaxe profiles |
| `/mythiccregion give <player> <profile>` | `/mr give` | `mythiccregion.admin` | Give a configured pickaxe to a player |
| `/mythiccregion version` | `/mr version` | `mythiccregion.admin` | Display plugin version and status |

---

## 🏔️ Default 6 Mine Tiers (8 Materials x 3 Tiers)

MythiccRegion includes 6 pre-configured mine regions covering all **24 RPG materials**:

```
[Tier 1: Refined] -> [Tier 2: Pure] -> [Tier 3: Astral]
```

1. **`mine_tier1` (Prospector's Valley - Tool T1-T2):**
   - Blocks: `STONE`, `COAL_ORE`, `COPPER_ORE`, `OAK_LOG`
   - Drops: `Special_Wood`, `Special_Stone`, `Special_Coal`, `Special_Copper_Ingot`
2. **`mine_tier2` (Golden Cavern - Tool T3-T4):**
   - Blocks: `DEEPSLATE`, `DEEPSLATE_IRON_ORE`, `DEEPSLATE_GOLD_ORE`, `DEEPSLATE_DIAMOND_ORE`
   - Drops: `Special_Iron_Ingot`, `Special_Gold_Ingot`, `Special_Diamond` + `Ancient_Geode`
3. **`mine_tier3` (Ancient Abyss - Tool T5):**
   - Blocks: `BLACKSTONE`, `ANCIENT_DEBRIS`, `CRYING_OBSIDIAN`
   - Drops: `Special_Netherite_Ingot`, `Special_Diamond` + `Ancient_Geode`
4. **`mine_tier4` (Pure Vein - Tool T6-T7):**
   - Blocks: `TUFF`, `STRIPPED_OAK_LOG`, `COBBLED_DEEPSLATE`, `RAW_COPPER_BLOCK`, `RAW_IRON_BLOCK`
   - Drops: `Pure_Wood`, `Pure_Stone`, `Pure_Coal`, `Pure_Copper_Ingot`, `Pure_Iron_Ingot`
5. **`mine_tier5` (Supreme Pure Sanctuary - Tool T8):**
   - Blocks: `RAW_GOLD_BLOCK`, `PRISMARINE_BRICKS`, `GILDED_BLACKSTONE`
   - Drops: `Pure_Gold_Ingot`, `Pure_Diamond`, `Pure_Netherite_Ingot` + `Void_Drill_Core`
6. **`mine_tier6` (Astral Void Realm - Tool T9-T10):**
   - Blocks: `DARK_OAK_WOOD`, `LODESTONE`, `COAL_BLOCK`, `COPPER_BLOCK`, `IRON_BLOCK`, `GOLD_BLOCK`, `DIAMOND_BLOCK`, `NETHERITE_BLOCK`
   - Drops: All 8 **Astral Materials (`Astral_`)** + `Void_Drill_Core`

---

## ⚙️ WorldGuard Setup Guide

1. Select and define a WorldGuard region for your mine:
   ```bash
   //wand
   # Select two corner points
   /rg define mine_tier1
   ```
2. Enable the custom mine flag:
   ```bash
   /rg flag mine_tier1 mine-region allow
   ```
   *(Optional) If your WorldGuard region ID differs from `mines.yml`:*
   ```bash
   /rg flag <region_id> mine-id mine_tier1
   ```
3. Stand inside the mine and verify:
   ```bash
   /mr info
   ```

---

## 🔨 Building from Source

Requirement: **JDK 25**

```bash
# Clone the repository
git clone https://github.com/<username>/MythiccRegion.git
cd MythiccRegion

# Build on Linux / macOS
./gradlew build

# Build on Windows
.\gradlew.bat build
```

The output jar will be located in `build/libs/`.

---

## 📄 License

Developed by **VoChiDanh**. All rights reserved.
