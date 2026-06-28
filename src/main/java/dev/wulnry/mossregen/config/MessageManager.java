package dev.wulnry.mossregen.config;

import dev.wulnry.mossregen.MossRegen;
import dev.wulnry.mossregen.util.ColorUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;

/**
 * messages.yml dosyasini yonetir; mesajlari prefix + renk ile hazirlar.
 *
 * <p>Tum metinler buradan okunur, hicbir mesaj kod icinde sabit (hardcoded)
 * degildir. Renk donusumu {@link ColorUtil} ile yapilir (hex + gradient).</p>
 */
public final class MessageManager {

    private final MossRegen plugin;
    private FileConfiguration messages;
    private String prefix;

    public MessageManager(MossRegen plugin) {
        this.plugin = plugin;
    }

    /**
     * messages.yml dosyasini (yoksa kaynaktan kopyalayarak) yeniden yukler.
     */
    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.messages = YamlConfiguration.loadConfiguration(file);
        this.prefix = messages.getString("prefix", "");
    }

    /**
     * Verilen yoldaki ham mesaji okur; "prefix" kelimesini gercek prefix ile
     * degistirir ve renklendirir.
     *
     * @param path messages.yml icindeki anahtar (orn. "command.reload-success")
     * @return renklendirilmis, kullanima hazir metin (yoksa eksik anahtar uyarisi)
     */
    public String get(String path) {
        String raw = messages.getString(path);
        if (raw == null) {
            return ColorUtil.colorize("&cmessages.yml icinde eksik anahtar: " + path);
        }
        return format(raw);
    }

    /**
     * Ham bir metni (config'den gelen drop_message gibi) prefix ve renklerle
     * isler. "prefix" ile baslayan metinlerde prefix otomatik eklenir.
     */
    public String format(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        // "prefix" anahtar kelimesini gercek prefix ile degistir.
        String withPrefix = raw.replace("prefix", prefix);
        return ColorUtil.colorize(withPrefix);
    }

    /** Bir mesaji (varsa {placeholder} degisimleriyle) bir alicilara gonderir. */
    public void send(CommandSender target, String path, String... replacements) {
        String message = get(path);
        message = applyReplacements(message, replacements);
        if (!message.isEmpty()) {
            target.sendMessage(message);
        }
    }

    /** Ham bir mesaji (config drop_message gibi) dogrudan gonderir. */
    public void sendRaw(CommandSender target, String raw, String... replacements) {
        String message = applyReplacements(format(raw), replacements);
        if (!message.isEmpty()) {
            target.sendMessage(message);
        }
    }

    /** Bir oyuncuya ActionBar bildirimi gonderir (ham metin). */
    public void sendActionBarRaw(Player player, String raw, String... replacements) {
        String message = applyReplacements(format(raw), replacements);
        if (!message.isEmpty()) {
            player.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(message));
        }
    }

    /**
     * {anahtar} -> deger seklinde basit yer tutucu degisimi yapar.
     * replacements dizisi [anahtar1, deger1, anahtar2, deger2, ...] formatindadir.
     */
    private String applyReplacements(String message, String... replacements) {
        if (replacements == null || replacements.length == 0) {
            return message;
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return message;
    }

    public String getPrefix() {
        return prefix;
    }

    /**
     * Verilen anahtardaki ham (renklendirilmemis, prefix uygulanmamis) metni doner.
     * Ozellikle PlaceholderAPI gibi kendi formatlamasini yapan yerlerde kullanilir.
     */
    public String getRaw(String path, String def) {
        return messages.getString(path, def);
    }
}
