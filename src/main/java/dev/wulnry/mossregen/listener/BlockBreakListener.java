package dev.wulnry.mossregen.listener;

import dev.wulnry.mossregen.MossRegen;
import dev.wulnry.mossregen.config.ConfigManager;
import dev.wulnry.mossregen.hook.WorldGuardHook;
import dev.wulnry.mossregen.manager.GlobalRegenManager;
import dev.wulnry.mossregen.manager.RegenManager;
import dev.wulnry.mossregen.manager.RewardManager;
import dev.wulnry.mossregen.model.GlobalRegenGroup;
import dev.wulnry.mossregen.model.RegenBlock;
import dev.wulnry.mossregen.model.RegionConfig;
import dev.wulnry.mossregen.util.BlockKey;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Set;

/**
 * Blok kirilma event'ini dinler. Tum eklenti mantiginin giris noktasidir.
 *
 * <p><b>Performans tasarimi (sicak yol):</b> Event her blok kirildiginda
 * tetiklenir, bu yuzden olabildigince ucuz olmalidir. Kontrol sirasi en
 * ucuzdan en pahaliya dogru duzenlenmistir:</p>
 * <ol>
 *   <li>Bu konum zaten yenilenmeyi bekleyen bir placeholder mi? &rarr; sadece
 *       drop'lari iptal et ve cik.</li>
 *   <li>Materyal config'de "ilgili" mi? &rarr; O(1) {@code EnumSet} kontrolu.
 *       Cogu blok burada elenir ve hicbir ek is yapilmaz.</li>
 *   <li>Yalnizca buraya kadar gelinirse WorldGuard bolge sorgusu yapilir
 *       (en pahali adim).</li>
 *   <li>Bolge config'de tanimli mi ve blok o bolgede mi? &rarr; isle.</li>
 * </ol>
 */
public final class BlockBreakListener implements Listener {

    private final ConfigManager config;
    private final WorldGuardHook worldGuard;
    private final RegenManager regenManager;
    private final GlobalRegenManager globalManager;
    private final RewardManager rewardManager;

    public BlockBreakListener(MossRegen plugin) {
        this.config = plugin.getConfigManager();
        this.worldGuard = plugin.getWorldGuardHook();
        this.regenManager = plugin.getRegenManager();
        this.globalManager = plugin.getGlobalRegenManager();
        this.rewardManager = plugin.getRewardManager();
    }

    // ignoreCancelled = true: baska bir eklenti event'i iptal ettiyse hic ugrasmayiz.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        BlockKey key = BlockKey.of(block);

        // (1) Konum zaten bir placeholder mi? (orn. bedrock'u tekrar kirma)
        //     Vanilya droplari iptal edilir; baska is yapilmaz.
        if (regenManager.isPending(key)) {
            cancelDrops(event);
            return;
        }

        // (2) EN UCUZ KONTROL: Bu materyal hicbir bolgede ilgili degilse cik.
        //     Sicak yolun buyuk cogunlugu burada sonlanir.
        if (!config.isInteresting(type)) {
            return;
        }

        // (3) PAHALI ADIM: WorldGuard bolgelerini sorgula (yalnizca ilgili blokta).
        Set<String> regionIds = worldGuard.getRegionIdsAt(block.getLocation());
        if (regionIds.isEmpty()) {
            // WorldGuard yoksa veya blok hicbir bolgede degilse eklenti devre disi:
            // "sadece tanimli bolgelerde aktif" sarti boyle saglanir.
            return;
        }

        // (4) Blogun icinde bulundugu bolgelerden config'de tanimli olani bul.
        for (String regionId : regionIds) {
            RegionConfig regionConfig = config.getRegion(regionId);
            if (regionConfig == null) {
                continue;
            }

            // 4a) Global grup tetikleyicisi mi? (bireysel cooldown yok, toplu yenilenme)
            GlobalRegenGroup group = regionConfig.getGlobalGroupByTrigger(type);
            if (group != null) {
                cancelDrops(event);
                globalManager.handleBreak(block, group, event.getPlayer());
                return;
            }

            // 4b) Bireysel yenilenen blok mu?
            RegenBlock regenBlock = regionConfig.getIndividualBlock(type);
            if (regenBlock != null) {
                Player player = event.getPlayer();
                cancelDrops(event);
                regenManager.handleBreak(block, regenBlock);
                // Bireysel bloklar icin her kirilmada hediye sansi denenir.
                rewardManager.tryGiveAll(player, regenBlock.getRewards(), block.getLocation());
                return;
            }
        }
        // Blok ilgili bir bolgede degil veya o bolgede tanimli degil: dokunma.
    }

    /** Vanilya esya ve tecrube (XP) droplarini iptal eder. */
    private void cancelDrops(BlockBreakEvent event) {
        event.setDropItems(false);
        event.setExpToDrop(0);
    }
}
