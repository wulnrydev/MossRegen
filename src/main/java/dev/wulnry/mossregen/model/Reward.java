package dev.wulnry.mossregen.model;

import org.bukkit.Material;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Tek bir hediye tanimini (config'deki bir reward girdisini) temsil eder.
 *
 * <p>Bu sinif <b>degismez (immutable)</b> tasarlanmistir: config yuklenirken
 * bir kez olusturulur ve sonrasinda yalnizca okunur. Bu sayede asenkron/cok
 * thread'li okumalarda guvenlidir ve sicak yolda (event icinde) ekstra nesne
 * uretimi olmaz.</p>
 */
public final class Reward {

    private final String id;
    private final RewardType type;
    private final double chance;
    private final String dropMessage;

    // --- ITEM tipi alanlari (COMMAND tipinde null/0 olabilir) ---
    private final Material material;
    private final int amount;
    private final int customModelData;     // 0 = ayarlanmamis
    private final String name;             // null = ayarlanmamis
    private final List<String> lore;
    private final boolean giveDirectly;
    private final Map<String, String> nbt; // PersistentDataContainer'a yazilacak etiketler

    // --- COMMAND tipi alanlari ---
    private final List<String> commands;

    private Reward(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.chance = builder.chance;
        this.dropMessage = builder.dropMessage;
        this.material = builder.material;
        this.amount = builder.amount;
        this.customModelData = builder.customModelData;
        this.name = builder.name;
        this.lore = builder.lore == null ? Collections.emptyList() : List.copyOf(builder.lore);
        this.giveDirectly = builder.giveDirectly;
        this.nbt = builder.nbt == null ? Collections.emptyMap() : Map.copyOf(builder.nbt);
        this.commands = builder.commands == null ? Collections.emptyList() : List.copyOf(builder.commands);
    }

    public String getId() {
        return id;
    }

    public RewardType getType() {
        return type;
    }

    /** Yuzde cinsinden sans (orn. 5.0 = %5). */
    public double getChance() {
        return chance;
    }

    public String getDropMessage() {
        return dropMessage;
    }

    public Material getMaterial() {
        return material;
    }

    public int getAmount() {
        return amount;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public boolean hasCustomModelData() {
        return customModelData > 0;
    }

    public String getName() {
        return name;
    }

    public List<String> getLore() {
        return lore;
    }

    public boolean isGiveDirectly() {
        return giveDirectly;
    }

    public Map<String, String> getNbt() {
        return nbt;
    }

    public List<String> getCommands() {
        return commands;
    }

    public static Builder builder(String id, RewardType type) {
        return new Builder(id, type);
    }

    /**
     * Reward nesnesini adim adim olusturmak icin builder.
     * Config okuma katmaninin kullanim kolayligi icindir.
     */
    public static final class Builder {
        private final String id;
        private final RewardType type;
        private double chance = 100.0;
        private String dropMessage;
        private Material material;
        private int amount = 1;
        private int customModelData = 0;
        private String name;
        private List<String> lore;
        private boolean giveDirectly = true;
        private Map<String, String> nbt;
        private List<String> commands;

        private Builder(String id, RewardType type) {
            this.id = id;
            this.type = type;
        }

        public Builder chance(double chance) {
            this.chance = chance;
            return this;
        }

        public Builder dropMessage(String dropMessage) {
            this.dropMessage = dropMessage;
            return this;
        }

        public Builder material(Material material) {
            this.material = material;
            return this;
        }

        public Builder amount(int amount) {
            this.amount = Math.max(1, amount);
            return this;
        }

        public Builder customModelData(int customModelData) {
            this.customModelData = customModelData;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder lore(List<String> lore) {
            this.lore = lore;
            return this;
        }

        public Builder giveDirectly(boolean giveDirectly) {
            this.giveDirectly = giveDirectly;
            return this;
        }

        public Builder nbt(Map<String, String> nbt) {
            this.nbt = nbt;
            return this;
        }

        public Builder commands(List<String> commands) {
            this.commands = commands;
            return this;
        }

        public Reward build() {
            return new Reward(this);
        }
    }
}
