package dev.wulnry.mossregen.manager;

import dev.wulnry.mossregen.MossRegen;
import dev.wulnry.mossregen.config.MessageManager;
import dev.wulnry.mossregen.util.ColorUtil;
import dev.wulnry.mossregen.model.Reward;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hediye (reward) verme mantigini yonetir: esya olusturma (NBT, CMD, isim,
 * lore), komut calistirma ve bildirim (chat / ActionBar) gonderme.
 *
 * <p>Olusturulan ItemStack'ler onbellege alinir (NBT/isim/lore hesaplamasi
 * bir kez yapilir); her drop'ta yalnizca {@code clone()} edilir. Bu, sik
 * dropelarda gereksiz meta isleme yukunu ortadan kaldirir.</p>
 */
public final class RewardManager {

    private final MossRegen plugin;
    private final MessageManager messages;

    // Reward id -> onceden olusturulmus sablon ItemStack (clone'lanarak verilir)
    private final Map<String, ItemStack> itemCache = new HashMap<>();

    public RewardManager(MossRegen plugin, MessageManager messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    /** Reload sirasinda onbellek temizlenir (yeni config'e gore yeniden uretilir). */
    public void clearCache() {
        itemCache.clear();
    }

    /**
     * Bir hediye listesini, her biri icin sansi deneyerek oyuncuya uygular.
     * Birden fazla hediye ayni anda dusebilir (her biri bagimsiz sans).
     *
     * @param player hediyeyi alacak oyuncu
     * @param rewards denenecek hediyeler
     * @param dropLocation esya yere dusecekse kullanilacak konum (null ise oyuncu konumu)
     */
    public void tryGiveAll(Player player, List<Reward> rewards, Location dropLocation) {
        if (rewards.isEmpty()) {
            return;
        }
        for (Reward reward : rewards) {
            if (rollChance(reward.getChance())) {
                give(player, reward, dropLocation);
            }
        }
    }

    /** Sansi gecen tek bir hediyeyi kosulsuz uygular. */
    public void give(Player player, Reward reward, Location dropLocation) {
        switch (reward.getType()) {
            case ITEM -> giveItem(player, reward, dropLocation);
            case COMMAND -> runCommands(player, reward);
            default -> { /* olamaz */ }
        }
        notifyPlayer(player, reward);
    }

    // ----------------------------------------------------------------
    //  ITEM
    // ----------------------------------------------------------------

    private void giveItem(Player player, Reward reward, Location dropLocation) {
        ItemStack item = buildItem(reward).clone();

        if (reward.isGiveDirectly()) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            // Envanter doluysa kalan kismi yere birak ve oyuncuyu bilgilendir.
            if (!overflow.isEmpty()) {
                Location loc = dropLocation != null ? dropLocation : player.getLocation();
                for (ItemStack leftover : overflow.values()) {
                    loc.getWorld().dropItemNaturally(loc, leftover);
                }
                messages.send(player, "reward.inventory-full");
            }
        } else {
            Location loc = dropLocation != null ? dropLocation : player.getLocation();
            loc.getWorld().dropItemNaturally(loc, item);
        }
    }

    /**
     * Reward icin sablon ItemStack'i (onbellekten) doner; yoksa olusturur.
     * NBT/CMD/isim/lore yalnizca ilk seferde uygulanir.
     */
    private ItemStack buildItem(Reward reward) {
        ItemStack cached = itemCache.get(reward.getId());
        if (cached != null) {
            return cached;
        }

        ItemStack item = new ItemStack(reward.getMaterial(), reward.getAmount());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Ozel isim
            if (reward.getName() != null) {
                meta.setDisplayName(ColorUtil.colorize(reward.getName()));
            }
            // Lore
            if (!reward.getLore().isEmpty()) {
                List<String> lore = new ArrayList<>(reward.getLore().size());
                for (String line : reward.getLore()) {
                    lore.add(ColorUtil.colorize(line));
                }
                meta.setLore(lore);
            }
            // CustomModelData
            if (reward.hasCustomModelData()) {
                meta.setCustomModelData(reward.getCustomModelData());
            }
            // Custom NBT -> PersistentDataContainer (modern, guvenli yontem)
            if (!reward.getNbt().isEmpty()) {
                var pdc = meta.getPersistentDataContainer();
                for (Map.Entry<String, String> entry : reward.getNbt().entrySet()) {
                    NamespacedKey key = new NamespacedKey(plugin, entry.getKey().toLowerCase());
                    pdc.set(key, PersistentDataType.STRING, entry.getValue());
                }
            }
            item.setItemMeta(meta);
        }

        itemCache.put(reward.getId(), item);
        return item;
    }

    // ----------------------------------------------------------------
    //  COMMAND
    // ----------------------------------------------------------------

    private void runCommands(Player player, Reward reward) {
        for (String command : reward.getCommands()) {
            // %player% yer tutucusunu oyuncu adiyla degistir.
            String parsed = command.replace("%player%", player.getName());
            // Komutlar ana thread'de, konsol yetkisiyle calistirilir.
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    // ----------------------------------------------------------------
    //  BILDIRIM
    // ----------------------------------------------------------------

    private void notifyPlayer(Player player, Reward reward) {
        String dropMessage = reward.getDropMessage();
        if (dropMessage == null || dropMessage.isEmpty()) {
            return; // bildirim istenmemis
        }
        if (plugin.getConfigManager().isUseActionBar()) {
            messages.sendActionBarRaw(player, dropMessage);
        } else {
            messages.sendRaw(player, dropMessage);
        }
    }

    // ----------------------------------------------------------------
    //  Sans hesaplama
    // ----------------------------------------------------------------

    /**
     * Yuzde cinsinden sansi dener. ThreadLocalRandom kullanildigi icin
     * kilitsiz ve hizlidir.
     */
    private boolean rollChance(double chancePercent) {
        if (chancePercent >= 100.0) {
            return true;
        }
        if (chancePercent <= 0.0) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble(100.0) < chancePercent;
    }
}
