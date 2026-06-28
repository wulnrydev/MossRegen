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

    public RegionConfig(String regionId,
                        Map<Material, RegenBlock> individualBlocks,
                        Map<Material, GlobalRegenGroup> globalGroupsByTrigger,
                        Map<String, GlobalRegenGroup> globalGroupsById) {
        this.regionId = regionId;
        this.individualBlocks = individualBlocks == null
                ? Collections.emptyMap() : Map.copyOf(individualBlocks);
        this.globalGroupsByTrigger = globalGroupsByTrigger == null
                ? Collections.emptyMap() : Map.copyOf(globalGroupsByTrigger);
        this.globalGroupsById = globalGroupsById == null
                ? Collections.emptyMap() : Map.copyOf(globalGroupsById);
    }

    public String getRegionId() {
        return regionId;
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
