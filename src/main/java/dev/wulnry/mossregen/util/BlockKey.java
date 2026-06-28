package dev.wulnry.mossregen.util;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.UUID;

/**
 * Bir blogun konumunu temsil eden hafif, degismez anahtar.
 *
 * <p>Bukkit'in {@link Location} sinifi hashCode'unda pitch/yaw degerlerini de
 * hesaba katar ve mutable'dir; bu nedenle harita anahtari olarak guvensizdir.
 * {@code BlockKey} yalnizca dunya + tam sayi koordinatlari kullanir; hizli ve
 * cakismasiz bir anahtar saglar.</p>
 *
 * <p>Java {@code record} oldugu icin equals/hashCode otomatik ve dogrudur.</p>
 */
public record BlockKey(UUID worldId, int x, int y, int z) {

    public static BlockKey of(Block block) {
        return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public static BlockKey of(Location location) {
        return new BlockKey(
                location.getWorld().getUID(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }
}
