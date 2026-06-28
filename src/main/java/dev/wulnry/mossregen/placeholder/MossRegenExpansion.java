package dev.wulnry.mossregen.placeholder;

import dev.wulnry.mossregen.MossRegen;
import dev.wulnry.mossregen.config.MessageManager;
import dev.wulnry.mossregen.manager.GlobalRegenManager;
import dev.wulnry.mossregen.util.ColorUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

/**
 * MossRegen icin PlaceholderAPI genislemesi.
 *
 * <p>Desteklenen placeholder'lar (her global grup icin):</p>
 * <ul>
 *   <li>{@code %mossregen_<grup>_time_left%} &rarr; kalan saniye (sayi).
 *       Geri sayim yoksa -1.</li>
 *   <li>{@code %mossregen_<grup>_time_left_formatted%} &rarr; "Xdk Ysn" bicimi.
 *       Geri sayim yoksa messages.yml'deki "placeholder.ready" metni.</li>
 *   <li>{@code %mossregen_<grup>_pending%} &rarr; o an bekleyen blok sayisi.</li>
 * </ul>
 *
 * <p>Ornek: {@code %mossregen_bugday_tarlasi_time_left_formatted%}</p>
 */
public final class MossRegenExpansion extends PlaceholderExpansion {

    private final MossRegen plugin;
    private final GlobalRegenManager globalManager;
    private final MessageManager messages;

    public MossRegenExpansion(MossRegen plugin) {
        this.plugin = plugin;
        this.globalManager = plugin.getGlobalRegenManager();
        this.messages = plugin.getMessageManager();
    }

    @Override
    public String getIdentifier() {
        return "mossregen";
    }

    @Override
    public String getAuthor() {
        return "wulnrydev";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** PAPI reload'lari arasinda kayitli kalmasi icin. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        return handle(params);
    }

    /**
     * "&lt;grup&gt;_&lt;tur&gt;" formatindaki istegi cozumler.
     * Grup adlarinda alt cizgi olabilecegi icin, bilinen son ekler sondan ayristirilir.
     */
    private String handle(String params) {
        if (params == null) {
            return "";
        }
        String lower = params.toLowerCase(Locale.ROOT);

        if (lower.endsWith("_time_left_formatted")) {
            String group = strip(params, "_time_left_formatted");
            return formatTime(globalManager.getTimeLeftSeconds(group));
        }
        if (lower.endsWith("_time_left")) {
            String group = strip(params, "_time_left");
            return String.valueOf(globalManager.getTimeLeftSeconds(group));
        }
        if (lower.endsWith("_pending")) {
            String group = strip(params, "_pending");
            return String.valueOf(globalManager.getPendingCount(group));
        }
        return null; // PAPI bilinmeyen placeholder'i oldugu gibi birakir
    }

    private String strip(String params, String suffix) {
        return params.substring(0, params.length() - suffix.length());
    }

    /**
     * Saniyeyi messages.yml'deki bicime ("placeholder.time-format") gore
     * "Xdk Ysn" seklinde formatlar. Geri sayim yoksa "placeholder.ready" doner.
     */
    private String formatTime(long totalSeconds) {
        if (totalSeconds < 0) {
            return ColorUtil.colorize(messages.getRaw("placeholder.ready", "&aHazir"));
        }
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        String format = messages.getRaw("placeholder.time-format", "{minutes}dk {seconds}sn");
        String out = format
                .replace("{minutes}", String.valueOf(minutes))
                .replace("{seconds}", String.valueOf(seconds));
        return ColorUtil.colorize(out);
    }
}
