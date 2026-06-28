package dev.wulnry.mossregen;

import dev.wulnry.mossregen.command.MossRegenCommand;
import dev.wulnry.mossregen.config.ConfigManager;
import dev.wulnry.mossregen.config.MessageManager;
import dev.wulnry.mossregen.hook.WorldGuardHook;
import dev.wulnry.mossregen.listener.BlockBreakListener;
import dev.wulnry.mossregen.manager.BulkRestoreService;
import dev.wulnry.mossregen.manager.GlobalRegenManager;
import dev.wulnry.mossregen.manager.RegenManager;
import dev.wulnry.mossregen.manager.RewardManager;
import dev.wulnry.mossregen.placeholder.MossRegenExpansion;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MossRegen ana sinifi.
 *
 * <p>1.20+ icin yuksek performansli, asenkron-dostu blok yenilenme eklentisi.
 * Tum bilesenler burada olusturulup birbirine baglanir (dependency wiring).</p>
 *
 * <p>Tasarim ilkeleri:</p>
 * <ul>
 *   <li>Agir is (config ayristirma) yalnizca acilis/reload aninda yapilir.</li>
 *   <li>Blok yenilenmeleri tick basina bolunerek ana thread'i bloklamaz.</li>
 *   <li>Veriler RAM'de (cache) tutulur; diske yazma seyrek ve yedek amaclidir.</li>
 *   <li>Event'ler ucuz on-filtrelerden gecer; WorldGuard yalnizca gerektiginde
 *       sorgulanir.</li>
 * </ul>
 *
 * @author wulnrydev
 */
public final class MossRegen extends JavaPlugin {

    private static MossRegen instance;

    private ConfigManager configManager;
    private MessageManager messageManager;
    private WorldGuardHook worldGuardHook;
    private RewardManager rewardManager;
    private BulkRestoreService restoreService;
    private RegenManager regenManager;
    private GlobalRegenManager globalRegenManager;

    @Override
    public void onEnable() {
        instance = this;

        // --- 1) Yapilandirma ve mesajlar ---
        this.configManager = new ConfigManager(this);
        this.configManager.load();

        this.messageManager = new MessageManager(this);
        this.messageManager.load();

        // --- 2) WorldGuard entegrasyonu (soft-depend) ---
        this.worldGuardHook = new WorldGuardHook(this);
        this.worldGuardHook.initialize();

        // --- 3) Hediye sistemi ---
        this.rewardManager = new RewardManager(this, messageManager);

        // --- 4) Geri yukleme servisi (tick basina bolunmus) ---
        this.restoreService = new BulkRestoreService(this);
        this.restoreService.start();

        // --- 5) Bireysel yenilenme yoneticisi ---
        this.regenManager = new RegenManager(this, restoreService);
        this.regenManager.loadData();   // onceki oturumdan kalan placeholder'lari yukle
        this.regenManager.start();

        // --- 6) Global/toplu yenilenme yoneticisi ---
        this.globalRegenManager = new GlobalRegenManager(this, restoreService, rewardManager);
        this.globalRegenManager.start();

        // --- 7) Event dinleyicisi ---
        getServer().getPluginManager().registerEvents(new BlockBreakListener(this), this);

        // --- 8) Komut ---
        var command = getCommand("mossregen");
        if (command != null) {
            command.setExecutor(new MossRegenCommand(this));
        }

        // --- 9) PlaceholderAPI genislemesi (varsa) ---
        registerPlaceholders();

        getLogger().info("MossRegen etkinlestirildi. (Yapimci: wulnrydev)");
    }

    @Override
    public void onDisable() {
        // Once gorevleri durdur, sonra verileri guvenli sekilde kalici hale getir.
        if (regenManager != null) {
            regenManager.saveData();   // bekleyen bloklari diske yedekle
            regenManager.stop();
        }
        if (globalRegenManager != null) {
            // Global bloklar diske yazilmadigindan kapanista hemen orijinaline donmeli.
            globalRegenManager.flushAllSync();
            globalRegenManager.stop();
        }
        if (restoreService != null) {
            restoreService.flushAllSync(); // kuyrukta kalanlari da hemen uygula
            restoreService.stop();
        }
        getLogger().info("MossRegen devre disi birakildi.");
    }

    /**
     * Tum yapilandirmayi ve mesajlari yeniden yukler (/mossregen reload).
     * Bekleyen global bloklar once senkron yenilenir; bireysel bloklar korunur.
     */
    public void reloadEverything() {
        // Calisma durumu config'e bagli oldugu icin global durumu sifirla.
        globalRegenManager.reset();

        configManager.load();
        messageManager.load();
        rewardManager.clearCache();

        // PAPI genislemesi persist() = true oldugundan yeniden kayit gerekmez.
        getLogger().info("MossRegen yeniden yuklendi.");
    }

    /** PlaceholderAPI yukluyse genislemeyi kaydeder. */
    private void registerPlaceholders() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new MossRegenExpansion(this).register();
            getLogger().info("PlaceholderAPI genislemesi kaydedildi.");
        } else {
            getLogger().info("PlaceholderAPI bulunamadi; placeholder destegi devre disi.");
        }
    }

    // ----------------------------------------------------------------
    //  Erisimciler
    // ----------------------------------------------------------------

    public static MossRegen getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public WorldGuardHook getWorldGuardHook() {
        return worldGuardHook;
    }

    public RewardManager getRewardManager() {
        return rewardManager;
    }

    public BulkRestoreService getRestoreService() {
        return restoreService;
    }

    public RegenManager getRegenManager() {
        return regenManager;
    }

    public GlobalRegenManager getGlobalRegenManager() {
        return globalRegenManager;
    }
}
