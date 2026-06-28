package dev.wulnry.mossregen.manager;

import dev.wulnry.mossregen.MossRegen;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Bloklari ana thread'i dondurmadan, <b>tick basina bolerek</b> orijinal
 * haline geri yukleyen merkezi servis.
 *
 * <p>Hem bireysel regen (suresi dolan bloklar) hem de global toplu yenilenme
 * (havuzun bosaltilmasi) buraya is birakir. Tek bir tekrarli gorev tum
 * kuyrugu isler; bu sayede yuzlerce blok ayni anda yenilense bile sunucu
 * tick basina yalnizca {@code blocks-per-tick} kadar islem yapar.</p>
 *
 * <p>Yuklu olmayan chunk'lar Paper'in asenkron chunk yukleme API'si ile
 * (ana thread bloklanmadan) yuklenir, sonra blok set edilir.</p>
 */
public final class BulkRestoreService {

    private final MossRegen plugin;

    // Thread-safe kuyruk: gorevler farkli yerlerden eklenebilir.
    private final ConcurrentLinkedQueue<PendingRestore> queue = new ConcurrentLinkedQueue<>();

    private BukkitTask task;

    public BulkRestoreService(MossRegen plugin) {
        this.plugin = plugin;
    }

    /** Her tick calisan isleyici gorevini baslatir. */
    public void start() {
        if (task != null) {
            return;
        }
        // 1 tick periyotla calis; her tick'te budget kadar blok isle.
        this.task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::processTick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Sunucu kapanirken kuyrukta kalan tum bloklari hemen (senkron) geri yukler.
     * Boylece placeholder bloklar diske yanlis halleriyle kaydedilmez.
     */
    public void flushAllSync() {
        PendingRestore pending;
        while ((pending = queue.poll()) != null) {
            restoreNow(pending);
        }
    }

    /** Kuyruga bir geri yukleme gorevi ekler. */
    public void enqueue(PendingRestore pending) {
        queue.add(pending);
    }

    public int getPendingCount() {
        return queue.size();
    }

    /** Bir tick boyunca budget kadar blogu isler. */
    private void processTick() {
        int budget = plugin.getConfigManager().getBlocksPerTick();
        int processed = 0;
        PendingRestore pending;
        while (processed < budget && (pending = queue.poll()) != null) {
            restoreNow(pending);
            processed++;
        }
    }

    /**
     * Tek bir blogu geri yukler. Chunk yuklu degilse Paper'in asenkron chunk
     * yukleme mekanizmasi kullanilir (ana thread bloklanmaz).
     */
    private void restoreNow(PendingRestore pending) {
        Location loc = pending.location();
        World world = loc.getWorld();
        if (world == null) {
            pending.runCallback();
            return;
        }
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;

        if (world.isChunkLoaded(cx, cz)) {
            applyData(pending);
        } else {
            // Asenkron chunk yukle, ardindan ana thread'de blogu set et.
            world.getChunkAtAsync(cx, cz, true).thenAccept(chunk -> applyData(pending));
        }
    }

    private void applyData(PendingRestore pending) {
        Block block = pending.location().getBlock();
        // physics=false: cevre bloklara fizik tetiklemez -> daha hafif ve daha guvenli.
        block.setBlockData(pending.data(), false);
        pending.runCallback();
    }

    /**
     * Tek bir geri yukleme gorevi: konum, set edilecek blok verisi ve
     * tamamlandiktan sonra calistirilacak (izleme haritasindan silme gibi)
     * istege bagli geri cagri.
     */
    public record PendingRestore(Location location, BlockData data, Runnable callback) {

        public void runCallback() {
            if (callback != null) {
                callback.run();
            }
        }
    }
}
