package dev.wulnry.mossregen.model;

import org.bukkit.Material;

import java.util.Collections;
import java.util.List;

/**
 * Bireysel yenilenen bir blok tanimini temsil eder (orn. DIAMOND_ORE).
 *
 * <p>Her blogun kendi cooldown'u ({@link #getRegenTimeSeconds()}) vardir.
 * Degismez (immutable) bir tanim nesnesidir.</p>
 */
public final class RegenBlock {

    private final Material original;       // Orijinal blok (kirilan)
    private final Material replaceBlock;   // Cooldown boyunca gorunecek gecici blok
    private final long regenTimeSeconds;   // Orijinaline donme suresi (saniye)
    private final List<Reward> rewards;    // Bu blok kirilinca denenecek hediyeler

    public RegenBlock(Material original, Material replaceBlock, long regenTimeSeconds, List<Reward> rewards) {
        this.original = original;
        this.replaceBlock = replaceBlock;
        this.regenTimeSeconds = regenTimeSeconds;
        this.rewards = rewards == null ? Collections.emptyList() : List.copyOf(rewards);
    }

    public Material getOriginal() {
        return original;
    }

    public Material getReplaceBlock() {
        return replaceBlock;
    }

    public long getRegenTimeSeconds() {
        return regenTimeSeconds;
    }

    public List<Reward> getRewards() {
        return rewards;
    }
}
