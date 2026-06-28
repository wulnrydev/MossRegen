package dev.wulnry.mossregen.util;

import net.md_5.bungee.api.ChatColor;

import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renk donusturme yardimcisi.
 *
 * <p>Desteklenen formatlar:</p>
 * <ul>
 *   <li>Solid hex: {@code &#RRGGBB} (orn: {@code &#00D420})</li>
 *   <li>Gradient: {@code <gradient:#RRGGBB:#RRGGBB>metin</gradient>}</li>
 *   <li>Legacy: {@code &a &7 &l &n} vb. tum klasik kodlar</li>
 * </ul>
 *
 * <p>Performans notu: Pattern nesneleri statik olarak bir kez derlenir;
 * bu sayede her cagrida yeniden regex derlemesi yapilmaz. Renk donusumu
 * yalnizca mesaj gonderilirken (nadiren) yapilir, sicak yol (hot path)
 * uzerinde calismaz.</p>
 */
public final class ColorUtil {

    /** &#RRGGBB formatindaki solid hex kodlarini yakalar. */
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    /** <gradient:#RRGGBB:#RRGGBB>...</gradient> bloklarini yakalar. */
    private static final Pattern GRADIENT_PATTERN = Pattern.compile(
            "<gradient:#([A-Fa-f0-9]{6}):#([A-Fa-f0-9]{6})>(.*?)</gradient>");

    private ColorUtil() {
        // Yardimci sinif: ornek olusturulamaz.
    }

    /**
     * Bir metni renklendirilmis hale getirir.
     *
     * @param input ham metin (null ise bos string doner)
     * @return Minecraft tarafindan gosterilebilir renkli metin
     */
    public static String colorize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        // 1) Once gradient bloklarini cozumle (icindeki metni harf harf renklendirir)
        input = applyGradients(input);

        // 2) Sonra solid hex kodlarini cozumle
        input = applyHex(input);

        // 3) En son klasik & renk kodlarini cozumle
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    /**
     * {@code &#RRGGBB} kodlarini ChatColor.of(...) ciktisina cevirir.
     */
    private static String applyHex(String input) {
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(buffer, ChatColor.of("#" + hex).toString());
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    /**
     * {@code <gradient:#A:#B>metin</gradient>} bloklarini, baslangic ve bitis
     * rengi arasinda yumusak gecisli renkli metne donusturur.
     */
    private static String applyGradients(String input) {
        Matcher matcher = GRADIENT_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            Color start = hexToColor(matcher.group(1));
            Color end = hexToColor(matcher.group(2));
            String text = matcher.group(3);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(buildGradient(text, start, end)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    /**
     * Verilen metni, baslangic ve bitis rengi arasinda lineer enterpolasyonla
     * harf harf renklendirir.
     */
    private static String buildGradient(String text, Color start, Color end) {
        // & ile baslayan formatlama kodlarini (orn &l) renk gecisinden ayri tutariz.
        StringBuilder result = new StringBuilder();
        int length = text.length();
        int visibleCount = Math.max(1, countVisibleChars(text));
        int step = 0;

        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            // Formatlama kodlarini oldugu gibi gecir (renk pozisyonunu degistirmez)
            if (c == '&' && i + 1 < length) {
                result.append(c).append(text.charAt(i + 1));
                i++;
                continue;
            }
            float ratio = visibleCount == 1 ? 0f : (float) step / (visibleCount - 1);
            Color color = blend(start, end, ratio);
            result.append(ChatColor.of(color)).append(c);
            step++;
        }
        return result.toString();
    }

    /** & formatlama kodlarini saymadan gorunur karakter sayisini bulur. */
    private static int countVisibleChars(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&' && i + 1 < text.length()) {
                i++;
                continue;
            }
            count++;
        }
        return count;
    }

    /** Iki rengi verilen orana gore karistirir (0.0 = start, 1.0 = end). */
    private static Color blend(Color start, Color end, float ratio) {
        int r = Math.round(start.getRed() + ratio * (end.getRed() - start.getRed()));
        int g = Math.round(start.getGreen() + ratio * (end.getGreen() - start.getGreen()));
        int b = Math.round(start.getBlue() + ratio * (end.getBlue() - start.getBlue()));
        return new Color(clamp(r), clamp(g), clamp(b));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static Color hexToColor(String hex) {
        return new Color(
                Integer.parseInt(hex.substring(0, 2), 16),
                Integer.parseInt(hex.substring(2, 4), 16),
                Integer.parseInt(hex.substring(4, 6), 16));
    }

    /**
     * Renk kodlarini tamamen temizler (orn. log/konsol ciktilari icin).
     */
    public static String stripColor(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.stripColor(colorize(input));
    }
}
