package dev.wulnry.mossregen.model;

import org.bukkit.Material;

import java.util.Collections;
import java.util.Map;

/**
 * Tek bir WorldGuard bolgesine ait tum yapilandirmayi tutar.
 *
 * <p>Hizli arama icin {@link Material} anahtarli haritalar kullanilir:
 * event icinde sadece {@code map.get(material)} cagrisi yapilir (O(1)).</p>
 */
public final class RegionConfig {

    private final String regionId;
    private final Map<Material, RegenBlock> individualBlocks;
    private final Map<Material, GlobalRegenGroup> globalGroupsByTrigger;
    private final Map<String, GlobalRegenGroup> globalGroupsById;
    private final boolean denyOtherBlocks;

    public RegionConfig(String regionId,
                        Map<Material, RegenBlock> individualBlocks,
                        Map<Material, GlobalRegenGroup> globalGroupsByTrigger,
                        Map<String, GlobalRegenGroup> globalGroupsById,
                        boolean denyOtherBlocks) {
        this.regionId = regionId;
        this.individualBlocks = individualBlocks == null
                ? Collections.emptyMap() : Map.copyOf(individualBlocks);
        this.globalGroupsByTrigger = globalGroupsByTrigger == null
                ? Collections.emptyMap() : Map.copyOf(globalGroupsByTrigger);
        this.globalGroupsById = globalGroupsById == null
                ? Collections.emptyMap() : Map.copyOf(globalGroupsById);
        this.denyOtherBlocks = denyOtherBlocks;
    }

    public String getRegionId() {
        return regionId;
    }

    /**
     * Bu bolgede config'de tanimli olmayan bloklarin kirilmasi engellensin mi?
     *
     * <p>true ise: bolgede yalnizca {@code blocks} ve {@code global_regen_groups}
     * altinda tanimli materyaller kirilabilir; diger her sey iptal edilir.</p>
     */
    public boolean isDenyOtherBlocks() {
        return denyOtherBlocks;
    }

    /** Kirilan materyal icin bireysel blok tanimi (yoksa null). */
    public RegenBlock getIndividualBlock(Material material) {
        return individualBlocks.get(material);
    }

    /** Kirilan materyal bir global grubun tetikleyicisi mi? (yoksa null). */
    public GlobalRegenGroup getGlobalGroupByTrigger(Material material) {
        return globalGroupsByTrigger.get(material);
    }

    public Map<Material, RegenBlock> getIndividualBlocks() {
        return individualBlocks;
    }

    public Map<String, GlobalRegenGroup> getGlobalGroupsById() {
        return globalGroupsById;
    }
}
