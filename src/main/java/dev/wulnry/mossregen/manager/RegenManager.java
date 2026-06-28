package dev.wulnry.mossregen.manager;

import dev.wulnry.mossregen.MossRegen;
import dev.wulnry.mossregen.manager.BulkRestoreService.PendingRestore;
import dev.wulnry.mossregen.model.RegenBlock;
import dev.wulnry.mossregen.util.BlockKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bireysel blok yenilenmesini yonetir.
 *
 * <p>Her kirilan blok, kendi cooldown suresi dolunca orijinaline doner. Veriler
 * RAM'de tutulur (cache); diske yazma yalnizca periyodik yedek ve kapanis
 * aninda yapilir. Suresi gelen bloklar {@link BulkRestoreService} araciligiyla
 * tick basina bolunerek yenilenir, boylece ana thread asla bloklanmaz.</p>
 */
public final class RegenManager {

    private final MossRegen plugin;
    private final BulkRestoreService restoreService;

    /** Su an placeholder durumdaki (yenilenmeyi bekleyen) bloklar. */
    private final ConcurrentHashMap<BlockKey, PendingIndividual> pending = new ConcurrentHashMap<>();

    /** Bitis zamanina gore siralanmis kuyruk (yalnizca ana thread'de erisilir). */
    private final PriorityQueue<PendingIndividual> dueQueue =
            new PriorityQueue<>(Comparator.comparingLong(p -> p.dueMillis));

    private BukkitTask tickTask;
    private BukkitTask saveTask;

    public RegenManager(MossRegen plugin, BulkRestoreService restoreService) {
        this.plugin = plugin;
        this.restoreService = restoreService;
    }

    public void start() {
        // Suresi dolan bloklari her saniye kontrol et (cooldown saniye bazli oldugu icin yeterli).
        this.tickTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::checkDueBlocks, 20L, 20L);

        // Periyodik disk yedegi.
        long interval = plugin.getConfigManager().getCacheSaveIntervalSeconds() * 20L;
        this.saveTask = plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, this::saveData, interval, interval);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
    }

    /**
     * Bir blok kirildiginda cagrilir: blok aninda placeholder'a donusturulur ve
     * cooldown sonunda yenilenmek uzere kuyruga alinir.
     */
    public void handleBreak(Block block, RegenBlock def) {
        BlockKey key = BlockKey.of(block);
        if (pending.containsKey(key)) {
            return; // zaten bekliyor (cift islemden korunma)
        }

        // Orijinal blok verisini (durumuyla birlikte) anlik olarak kaydet.
        BlockData originalData = block.getBlockData();

        // Blogu aninda gecici (placeholder) bloga cevir; fizik tetikleme.
        block.setType(def.getReplaceBlock(), false);

        long dueMillis = System.currentTimeMillis() + def.getRegenTimeSeconds() * 1000L;
        PendingIndividual entry = new PendingIndividual(key, block.getLocation(), originalData, dueMillis);

        pending.put(key, entry);
        dueQueue.add(entry);
    }

    /** Bu konum su an yenilenmeyi bekleyen bir placeholder mu? */
    public boolean isPending(BlockKey key) {
        return pending.containsKey(key);
    }

    /** Suresi gelen bloklari geri yukleme servisine aktarir. */
    private void checkDueBlocks() {
        long now = System.currentTimeMillis();
        PendingIndividual head;
        while ((head = dueQueue.peek()) != null && head.dueMillis <= now) {
            dueQueue.poll();
            // Hala gecerli mi? (forceregen vb. ile silinmis olabilir)
            PendingIndividual current = pending.get(head.key);
            if (current != head) {
                continue;
            }
            scheduleRestore(head);
        }
    }

    private void scheduleRestore(PendingIndividual entry) {
        restoreService.enqueue(new PendingRestore(
                entry.location,
                entry.originalData,
                () -> pending.remove(entry.key)));
    }

    /**
     * Tum bekleyen bloklari aninda yenilenmek uzere kuyruga aktarir
     * (orn. /mossregen forceregen).
     *
     * @return yenilenmek uzere aktarilan blok sayisi
     */
    public int forceRegenAll() {
        int count = pending.size();
        dueQueue.clear();
        for (PendingIndividual entry : pending.values()) {
            scheduleRestore(entry);
        }
        return count;
    }

    // ----------------------------------------------------------------
    //  Disk kalicilik (cache yedegi)
    // ----------------------------------------------------------------

    /** Sunucu kapanirken: kuyruktaki her seyi diske yaz. */
    public void saveData() {
        File file = new File(plugin.getDataFolder(), "regen-data.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        int i = 0;
        for (PendingIndividual entry : pending.values()) {
            String path = "pending." + i++;
            yaml.set(path + ".world", entry.key.worldId().toString());
            yaml.set(path + ".x", entry.key.x());
            yaml.set(path + ".y", entry.key.y());
            yaml.set(path + ".z", entry.key.z());
            yaml.set(path + ".data", entry.originalData.getAsString());
            yaml.set(path + ".due", entry.dueMillis);
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("regen-data.yml kaydedilemedi: " + ex.getMessage());
        }
    }

    /**
     * Acilista kayitli bekleyen bloklari yukler. Placeholder bloklar dunyada
     * zaten kayitlidir; burada sadece yenilenme zamanlarini geri yukluyoruz.
     */
    public void loadData() {
        File file = new File(plugin.getDataFolder(), "regen-data.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("pending");
        if (section == null) {
            return;
        }
        int loaded = 0;
        for (String idx : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(idx);
            if (s == null) {
                continue;
            }
            try {
                UUID worldId = UUID.fromString(s.getString("world"));
                World world = Bukkit.getWorld(worldId);
                if (world == null) {
                    continue; // dunya yuklu degil; atla
                }
                int x = s.getInt("x");
                int y = s.getInt("y");
                int z = s.getInt("z");
                BlockData data = Bukkit.createBlockData(s.getString("data"));
                long due = s.getLong("due");

                BlockKey key = new BlockKey(worldId, x, y, z);
                Location loc = new Location(world, x, y, z);
                PendingIndividual entry = new PendingIndividual(key, loc, data, due);
                pending.put(key, entry);
                dueQueue.add(entry);
                loaded++;
            } catch (Exception ex) {
                plugin.getLogger().warning("Bekleyen blok yuklenemedi (" + idx + "): " + ex.getMessage());
            }
        }
        if (loaded > 0) {
            plugin.getLogger().info(loaded + " bekleyen blok onbellekten geri yuklendi.");
        }
        // Yuklenen veriyi temizle (tekrar yuklemeyi onlemek icin).
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }

    /**
     * Tek bir bekleyen blogun calisma zamani durumu.
     */
    private static final class PendingIndividual {
        private final BlockKey key;
        private final Location location;
        private final BlockData originalData;
        private final long dueMillis;

        private PendingIndividual(BlockKey key, Location location, BlockData originalData, long dueMillis) {
            this.key = key;
            this.location = location;
            this.originalData = originalData;
            this.dueMillis = dueMillis;
        }
    }
}
