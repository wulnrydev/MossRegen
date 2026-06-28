package dev.wulnry.mossregen.config;

import dev.wulnry.mossregen.MossRegen;
import dev.wulnry.mossregen.model.GlobalRegenGroup;
import dev.wulnry.mossregen.model.RegenBlock;
import dev.wulnry.mossregen.model.RegionConfig;
import dev.wulnry.mossregen.model.Reward;
import dev.wulnry.mossregen.model.RewardTrigger;
import dev.wulnry.mossregen.model.RewardType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * config.yml dosyasini okuyup degismez (immutable) model nesnelerine cevirir.
 *
 * <p>Tum agir ayristirma (parse) islemi yalnizca yuklenme/reload sirasinda
 * <b>bir kez</b> yapilir. Calisma sirasinda event'ler bu hazirlanmis
 * haritalardan O(1) okuma yapar; bu da "sifir lag" hedefinin temelidir.</p>
 */
public final class ConfigManager {

    private final MossRegen plugin;

    // regionId (kucuk harf) -> RegionConfig
    private Map<String, RegionConfig> regions = Collections.emptyMap();

    // Hizli on-filtre: event'te kirilan materyal bu kumede yoksa hicbir is yapilmaz.
    private Set<Material> interestingMaterials = EnumSet.noneOf(Material.class);

    // Genel ayarlar
    private int blocksPerTick = 200;
    private int cacheSaveIntervalSeconds = 300;
    private boolean useActionBar = true;
    private boolean debug = false;

    public ConfigManager(MossRegen plugin) {
        this.plugin = plugin;
    }

    /**
     * config.yml'i yeniden yukler ve tum modelleri yeniden olusturur.
     */
    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        var config = plugin.getConfig();

        // --- Genel ayarlar ---
        this.blocksPerTick = Math.max(1, config.getInt("settings.blocks-per-tick", 200));
        this.cacheSaveIntervalSeconds = Math.max(30, config.getInt("settings.cache-save-interval-seconds", 300));
        this.useActionBar = config.getBoolean("settings.use-actionbar-for-rewards", true);
        this.debug = config.getBoolean("settings.debug", false);

        Map<String, RegionConfig> loadedRegions = new HashMap<>();
        Set<Material> loadedMaterials = EnumSet.noneOf(Material.class);

        ConfigurationSection regionsSection = config.getConfigurationSection("regions");
        if (regionsSection != null) {
            for (String regionId : regionsSection.getKeys(false)) {
                ConfigurationSection regionSec = regionsSection.getConfigurationSection(regionId);
                if (regionSec == null) {
                    continue;
                }
                RegionConfig regionConfig = parseRegion(regionId, regionSec, loadedMaterials);
                // WorldGuard bolge ID'leri kucuk harfli tutulur -> hizli karsilastirma.
                loadedRegions.put(regionId.toLowerCase(Locale.ROOT), regionConfig);
            }
        }

        this.regions = Map.copyOf(loadedRegions);
        this.interestingMaterials = loadedMaterials.isEmpty()
                ? EnumSet.noneOf(Material.class) : EnumSet.copyOf(loadedMaterials);

        plugin.getLogger().info("Yuklendi: " + regions.size() + " bolge, "
                + interestingMaterials.size() + " ilgili materyal turu.");
    }

    /** Tek bir bolgeyi ayristirir. */
    private RegionConfig parseRegion(String regionId, ConfigurationSection sec, Set<Material> materialSink) {
        Map<Material, RegenBlock> individualBlocks = new HashMap<>();
        Map<Material, GlobalRegenGroup> groupsByTrigger = new HashMap<>();
        Map<String, GlobalRegenGroup> groupsById = new LinkedHashMap<>();

        // --- Bireysel bloklar ---
        ConfigurationSection blocksSec = sec.getConfigurationSection("blocks");
        if (blocksSec != null) {
            for (String matName : blocksSec.getKeys(false)) {
                Material original = parseMaterial(matName, regionId + ".blocks");
                if (original == null) {
                    continue;
                }
                ConfigurationSection blockSec = blocksSec.getConfigurationSection(matName);
                if (blockSec == null) {
                    continue;
                }
                Material replace = parseMaterial(blockSec.getString("replace_block"), regionId + "." + matName);
                if (replace == null) {
                    replace = Material.BEDROCK; // guvenli varsayilan
                }
                long regenTime = blockSec.getLong("regen_time_seconds", 60L);
                List<Reward> rewards = parseRewards(blockSec.getConfigurationSection("rewards"));

                individualBlocks.put(original, new RegenBlock(original, replace, regenTime, rewards));
                materialSink.add(original);
            }
        }

        // --- Global gruplar ---
        ConfigurationSection groupsSec = sec.getConfigurationSection("global_regen_groups");
        if (groupsSec != null) {
            for (String groupId : groupsSec.getKeys(false)) {
                ConfigurationSection groupSec = groupsSec.getConfigurationSection(groupId);
                if (groupSec == null) {
                    continue;
                }
                Material trigger = parseMaterial(groupSec.getString("trigger_block"), regionId + "." + groupId);
                if (trigger == null) {
                    continue;
                }
                Material replace = parseMaterial(groupSec.getString("replace_block"), regionId + "." + groupId);
                if (replace == null) {
                    replace = Material.AIR;
                }
                long timer = groupSec.getLong("global_timer_seconds", 180L);
                RewardTrigger rewardTrigger = parseRewardTrigger(groupSec.getString("reward-trigger"));
                List<Reward> rewards = parseRewards(groupSec.getConfigurationSection("rewards"));

                GlobalRegenGroup group = new GlobalRegenGroup(
                        groupId, regionId.toLowerCase(Locale.ROOT), trigger, replace, timer, rewardTrigger, rewards);
                groupsById.put(groupId, group);
                groupsByTrigger.put(trigger, group);
                materialSink.add(trigger);
            }
        }

        return new RegionConfig(regionId.toLowerCase(Locale.ROOT), individualBlocks, groupsByTrigger, groupsById);
    }

    /** Bir reward bolumunu (rewards: {...}) listeye cevirir. */
    private List<Reward> parseRewards(ConfigurationSection rewardsSec) {
        if (rewardsSec == null) {
            return Collections.emptyList();
        }
        List<Reward> rewards = new ArrayList<>();
        for (String rewardId : rewardsSec.getKeys(false)) {
            ConfigurationSection r = rewardsSec.getConfigurationSection(rewardId);
            if (r == null) {
                continue;
            }
            RewardType type = parseRewardType(r.getString("type"));
            if (type == null) {
                plugin.getLogger().warning("Gecersiz reward type: " + rewardId + " (atlandi)");
                continue;
            }
            Reward.Builder builder = Reward.builder(rewardId, type)
                    .chance(r.getDouble("chance", 100.0))
                    .dropMessage(r.getString("drop_message"));

            if (type == RewardType.ITEM) {
                Material material = parseMaterial(r.getString("material"), "reward." + rewardId);
                if (material == null) {
                    plugin.getLogger().warning("Reward '" + rewardId + "' icin gecersiz material (atlandi).");
                    continue;
                }
                builder.material(material)
                        .amount(r.getInt("amount", 1))
                        .customModelData(r.getInt("custom_model_data", 0))
                        .name(r.getString("name"))
                        .lore(r.getStringList("lore"))
                        .giveDirectly(r.getBoolean("give-directly", true))
                        .nbt(readStringMap(r.getConfigurationSection("nbt")));
            } else { // COMMAND
                builder.commands(r.getStringList("commands"));
            }
            rewards.add(builder.build());
        }
        return rewards;
    }

    /** Bir ConfigurationSection'i String->String haritasina cevirir. */
    private Map<String, String> readStringMap(ConfigurationSection sec) {
        if (sec == null) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new HashMap<>();
        for (String key : sec.getKeys(false)) {
            map.put(key, sec.getString(key, ""));
        }
        return map;
    }

    // ----------------------------------------------------------------
    //  Yardimci ayristiricilar
    // ----------------------------------------------------------------

    private Material parseMaterial(String name, String context) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        if (material == null) {
            plugin.getLogger().log(Level.WARNING,
                    "Gecersiz materyal '" + name + "' (konum: " + context + ")");
        }
        return material;
    }

    private RewardType parseRewardType(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return RewardType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private RewardTrigger parseRewardTrigger(String raw) {
        if (raw == null) {
            return RewardTrigger.ON_BREAK;
        }
        try {
            return RewardTrigger.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return RewardTrigger.ON_BREAK;
        }
    }

    // ----------------------------------------------------------------
    //  Erisimciler (getters)
    // ----------------------------------------------------------------

    /** Verilen WorldGuard bolge ID'si icin yapilandirma (yoksa null). */
    public RegionConfig getRegion(String regionId) {
        if (regionId == null) {
            return null;
        }
        return regions.get(regionId.toLowerCase(Locale.ROOT));
    }

    public Map<String, RegionConfig> getRegions() {
        return regions;
    }

    /**
     * Sicak yol on-filtresi: bu materyal herhangi bir bolgede ilgili mi?
     * Event icinde ilk yapilan, en ucuz kontroldur.
     */
    public boolean isInteresting(Material material) {
        return interestingMaterials.contains(material);
    }

    public int getBlocksPerTick() {
        return blocksPerTick;
    }

    public int getCacheSaveIntervalSeconds() {
        return cacheSaveIntervalSeconds;
    }

    public boolean isUseActionBar() {
        return useActionBar;
    }

    public boolean isDebug() {
        return debug;
    }
}
