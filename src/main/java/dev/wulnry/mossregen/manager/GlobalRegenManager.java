package dev.wulnry.mossregen.manager;

import dev.wulnry.mossregen.MossRegen;
import dev.wulnry.mossregen.manager.BulkRestoreService.PendingRestore;
import dev.wulnry.mossregen.model.GlobalRegenGroup;
import dev.wulnry.mossregen.model.RewardTrigger;
import dev.wulnry.mossregen.util.BlockKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Set;

/**
 * Global/toplu yenilenmeyi yonetir (Tarim / Wheat sistemi).
 *
 * <p>Onemli ozellik: Kimse kirmadigi surece <b>hicbir timer calismaz</b>.
 * Ilk bitki kirildiginda o gruba ait geri sayim baslar; sure sonunda o ana
 * kadar biriken tum bloklar {@link BulkRestoreService} ile (tick basina
 * bolunerek) yenilenir. Tek bir hafif kontrol gorevi tum gruplarin sayaclarini
 * izler.</p>
 */
public final class GlobalRegenManager {

    private final MossRegen plugin;
    private final BulkRestoreService restoreService;
    private final RewardManager rewardManager;

    /** groupId -> calisma zamani durumu. */
    private final ConcurrentHashMap<String, GroupState> states = new ConcurrentHashMap<>();

    /** Su an bir grupta yenilenmeyi bekleyen blok konumlari (cift-break korumasi). */
    private final Set<BlockKey> pendingKeys = ConcurrentHashMap.newKeySet();

    private BukkitTask tickTask;

    public GlobalRegenManager(MossRegen plugin, BulkRestoreService restoreService, RewardManager rewardManager) {
        this.plugin = plugin;
        this.restoreService = restoreService;
        this.rewardManager = rewardManager;
    }

    public void start() {
        // Sayaclari her saniye kontrol et (saniye hassasiyeti yeterli, cok hafif).
        this.tickTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::checkTimers, 20L, 20L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    /**
     * Reload sirasinda calisma durumunu temizler (config yeniden okunacak).
     * Bekleyen bloklar varsa once senkron yenilenir ki dunyada placeholder kalmasin.
     */
    public void reset() {
        flushAllSync();
        states.clear();
        pendingKeys.clear();
    }

    /**
     * Gruba ait bir bitki kirildiginda cagrilir.
     */
    public void handleBreak(Block block, GlobalRegenGroup group, Player breaker) {
        BlockKey key = BlockKey.of(block);
        if (!pendingKeys.add(key)) {
            return; // bu konum zaten bekliyor
        }

        // Orijinal blok verisini sakla, sonra placeholder'a cevir.
        BlockData originalData = block.getBlockData();
        block.setType(group.getReplaceBlock(), false);

        GroupState state = states.computeIfAbsent(group.getId(), id -> new GroupState());

        // Ilk kirilma -> geri sayimi baslat.
        if (state.endMillis == 0L) {
            state.endMillis = System.currentTimeMillis() + group.getGlobalTimerSeconds() * 1000L;
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("Global geri sayim basladi: " + group.getId()
                        + " (" + group.getGlobalTimerSeconds() + "sn)");
            }
        }

        // Blogu havuza ekle.
        state.queue.add(new PendingRestore(block.getLocation(), originalData,
                () -> pendingKeys.remove(key)));

        // ON_GROUP_REGEN icin kiran oyuncuyu kaydet.
        state.breakers.add(breaker.getUniqueId());

        // ON_BREAK ise hediyeyi hemen dene.
        if (group.getRewardTrigger() == RewardTrigger.ON_BREAK) {
            rewardManager.tryGiveAll(breaker, group.getRewards(), block.getLocation());
        }
    }

    /** Suresi dolan gruplarin havuzunu bosaltir. */
    private void checkTimers() {
        long now = System.currentTimeMillis();
        for (var entry : states.entrySet()) {
            GroupState state = entry.getValue();
            if (state.endMillis != 0L && now >= state.endMillis) {
                flushGroup(entry.getKey(), state);
            }
        }
    }

    /** Bir grubun havuzundaki tum bloklari yenilenmeye gonderir ve sayaci sifirlar. */
    private void flushGroup(String groupId, GroupState state) {
        GlobalRegenGroup group = findGroupById(groupId);

        // Havuzdaki bloklari geri yukleme servisine aktar.
        int count = 0;
        PendingRestore pr;
        while ((pr = state.queue.poll()) != null) {
            restoreService.enqueue(pr);
            count++;
        }

        // ON_GROUP_REGEN ise kiran (cevrimici) herkese hediye dene.
        if (group != null && group.getRewardTrigger() == RewardTrigger.ON_GROUP_REGEN) {
            for (UUID uuid : state.breakers) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    rewardManager.tryGiveAll(player, group.getRewards(), player.getLocation());
                }
            }
        }

        // Sayaci sifirla: bir sonraki kirilmaya kadar timer tekrar calismaz.
        state.endMillis = 0L;
        state.breakers.clear();

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("Global yenilenme tamamlandi: " + groupId + " (" + count + " blok)");
        }
    }

    /** Kapanis/reload: tum gruplardaki bekleyen bloklari senkron olarak yeniler. */
    public void flushAllSync() {
        for (GroupState state : states.values()) {
            PendingRestore pr;
            while ((pr = state.queue.poll()) != null) {
                Location loc = pr.location();
                if (loc.getWorld() != null) {
                    loc.getBlock().setBlockData(pr.data(), false);
                }
                pr.runCallback();
            }
            state.endMillis = 0L;
            state.breakers.clear();
        }
    }

    private GlobalRegenGroup findGroupById(String groupId) {
        for (var region : plugin.getConfigManager().getRegions().values()) {
            GlobalRegenGroup group = region.getGlobalGroupsById().get(groupId);
            if (group != null) {
                return group;
            }
        }
        return null;
    }

    // ----------------------------------------------------------------
    //  PlaceholderAPI destegi icin yardimcilar
    // ----------------------------------------------------------------

    /**
     * Belirtilen grup icin kalan sureyi saniye cinsinden doner.
     *
     * @return kalan saniye; geri sayim aktif degilse -1
     */
    public long getTimeLeftSeconds(String groupId) {
        GroupState state = states.get(groupId);
        if (state == null || state.endMillis == 0L) {
            return -1L;
        }
        long remaining = state.endMillis - System.currentTimeMillis();
        return remaining <= 0 ? 0L : (remaining + 999L) / 1000L; // yukari yuvarla
    }

    /** Grupta su an bekleyen (kirilmis) blok sayisi. */
    public int getPendingCount(String groupId) {
        GroupState state = states.get(groupId);
        return state == null ? 0 : state.queue.size();
    }

    /**
     * Tek bir global grubun calisma zamani durumu.
     *
     * <p>{@code endMillis} alani hem ana thread (kontrol gorevi) hem de PAPI
     * (bazen asenkron) tarafindan okunabilecegi icin {@code volatile}'dir.</p>
     */
    private static final class GroupState {
        private volatile long endMillis = 0L;
        private final ConcurrentLinkedQueue<PendingRestore> queue = new ConcurrentLinkedQueue<>();
        private final Set<UUID> breakers = ConcurrentHashMap.newKeySet();
    }
}
