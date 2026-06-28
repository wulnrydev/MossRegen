package dev.wulnry.mossregen.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import dev.wulnry.mossregen.MossRegen;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * WorldGuard ile guvenli (soft-depend) entegrasyon.
 *
 * <p>WorldGuard sunucuda yoksa {@link #isEnabled()} false doner ve bolge
 * sorgulari bos kume dondurur; eklenti yine de cokmeden calisir.</p>
 *
 * <p>Performans: WorldGuard sorgusu nispeten pahali oldugu icin, bu sorgu
 * <b>yalnizca</b> kirilan blok config'de "ilgili" oldugunda (on-filtreyi
 * gectikten sonra) yapilir. Bu sayede sicak yol cogu zaman WorldGuard'a
 * hic dokunmaz.</p>
 */
public final class WorldGuardHook {

    private final MossRegen plugin;
    private boolean enabled;
    private RegionContainer container;

    public WorldGuardHook(MossRegen plugin) {
        this.plugin = plugin;
    }

    /**
     * WorldGuard'i tespit edip baglar. Sunucu acilirken bir kez cagrilir.
     */
    public void initialize() {
        if (plugin.getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            this.enabled = false;
            plugin.getLogger().info("WorldGuard bulunamadi; bolge kontrolu devre disi.");
            return;
        }
        try {
            this.container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            this.enabled = true;
            plugin.getLogger().info("WorldGuard basariyla baglandi.");
        } catch (Throwable t) {
            // API uyumsuzlugu vb. durumlarda eklenti yine de calismaya devam etsin.
            this.enabled = false;
            plugin.getLogger().warning("WorldGuard baglanamadi: " + t.getMessage());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Verilen konumdaki tum WorldGuard bolge ID'lerini (kucuk harfli) doner.
     * WorldGuard yoksa veya bolge yoksa bos kume doner.
     *
     * <p>NOT: Bu metot ana thread'de cagrilmalidir (WorldGuard region sorgusu
     * thread-safe garanti etmez). Sicak yolda yalnizca on-filtre gecildiginde
     * cagrilir.</p>
     */
    public Set<String> getRegionIdsAt(Location location) {
        if (!enabled || location == null) {
            return Collections.emptySet();
        }
        World world = location.getWorld();
        if (world == null) {
            return Collections.emptySet();
        }
        RegionManager regionManager = container.get(BukkitAdapter.adapt(world));
        if (regionManager == null) {
            return Collections.emptySet();
        }
        BlockVector3 vector = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        ApplicableRegionSet set = regionManager.getApplicableRegions(vector);
        if (set.size() == 0) {
            return Collections.emptySet();
        }
        Set<String> ids = new HashSet<>(set.size());
        for (ProtectedRegion region : set) {
            ids.add(region.getId().toLowerCase(java.util.Locale.ROOT));
        }
        return ids;
    }
}
