package dev.wulnry.mossregen.model;

import org.bukkit.Material;

import java.util.Collections;
import java.util.List;

/**
 * Global/toplu yenilenen bir grup tanimini temsil eder (orn. bugday_tarlasi).
 *
 * <p>Bireysel cooldown YOKTUR. Ilk bitki kirildiginda
 * {@link #getGlobalTimerSeconds()} kadar bir geri sayim baslar; sure sonunda
 * o ana kadar kirilan tum bloklar toplu olarak yenilenir.</p>
 *
 * <p>Bu sinif yalnizca <b>tanimi</b> tutar; calisma zamani durumu (aktif
 * geri sayim, bekleyen bloklar) {@code GlobalRegenManager} icinde tutulur.</p>
 */
public final class GlobalRegenGroup {

    private final String id;
    private final String regionId;
    private final Material triggerBlock;
    private final Material replaceBlock;
    private final long globalTimerSeconds;
    private final RewardTrigger rewardTrigger;
    private final List<Reward> rewards;

    public GlobalRegenGroup(String id,
                            String regionId,
                            Material triggerBlock,
                            Material replaceBlock,
                            long globalTimerSeconds,
                            RewardTrigger rewardTrigger,
                            List<Reward> rewards) {
        this.id = id;
        this.regionId = regionId;
        this.triggerBlock = triggerBlock;
        this.replaceBlock = replaceBlock;
        this.globalTimerSeconds = globalTimerSeconds;
        this.rewardTrigger = rewardTrigger;
        this.rewards = rewards == null ? Collections.emptyList() : List.copyOf(rewards);
    }

    public String getId() {
        return id;
    }

    public String getRegionId() {
        return regionId;
    }

    public Material getTriggerBlock() {
        return triggerBlock;
    }

    public Material getReplaceBlock() {
        return replaceBlock;
    }

    public long getGlobalTimerSeconds() {
        return globalTimerSeconds;
    }

    public RewardTrigger getRewardTrigger() {
        return rewardTrigger;
    }

    public List<Reward> getRewards() {
        return rewards;
    }
}
