# MossRegen

1.20+ (Paper) için yüksek performanslı, asenkron-dostu **blok yenilenme (regen)** eklentisi.
Yapımcı: **wulnrydev**

## Özellikler

- ⚡ **Sıfır lag hedefi:** Blok yenilenmeleri tick başına bölünerek (`blocks-per-tick`) yapılır; ana thread asla bloklanmaz. Yüklü olmayan chunk'lar Paper'ın asenkron chunk yükleme API'si ile yüklenir.
- 🧠 **RAM cache:** Tüm regen verileri bellekte tutulur; diske yazma yalnızca periyodik yedek ve kapanış anındadır.
- 🪶 **Hafif event:** Kırılan blok önce O(1) `EnumSet` ön-filtresinden geçer; WorldGuard bölge sorgusu yalnızca gerçekten ilgili bloklarda yapılır.
- 🛡️ **WorldGuard:** Eklenti yalnızca config'de tanımlı WorldGuard bölgelerinde aktiftir. (Soft-depend — WorldGuard yoksa eklenti çökmez, bölge kontrolü pasifleşir.)
- 🎨 **Hex + gradient renk:** `&#RRGGBB` solid ve `<gradient:#A:#B>metin</gradient>` desteği. Tüm metinler `messages.yml` üzerinden yapılandırılır.
- 🎁 **Şanslı drop:** `ITEM` (CustomModelData, özel isim, lore, custom NBT, direkt envantere/yere) ve `COMMAND` (oyuncu adına komut) hediyeleri, yüzde şansla. ActionBar/chat bildirimi.
- 🌾 **Global/toplu yenilenme (Tarım/Wheat):** Bireysel cooldown yoktur. İlk bitki kırılınca grup geri sayımı başlar; süre sonunda biriken tüm bloklar toplu yenilenir. Kimse kırmazsa hiçbir timer çalışmaz.
- 📊 **PlaceholderAPI:** Global geri sayım için placeholder desteği.

## Derleme

```bash
mvn clean package
```

Çıktı: `target/MossRegen-1.0.0.jar` → sunucunun `plugins/` klasörüne atın.

Gereksinim: JDK 17+ (Paper 1.20.4 API ile derlenir).

## Komutlar

| Komut | Açıklama |
|-------|----------|
| `/mossregen reload` | Config + mesajları yeniden yükler |
| `/mossregen info` | Sürüm bilgisi |
| `/mossregen forceregen` | Bekleyen tüm blokları anında yeniler |

İzin: `mossregen.admin` (varsayılan: op). Alias: `/mr`, `/moss`.

## Yapılandırma

- `config.yml` — bölgeler, bloklar, global gruplar, ödüller ve performans ayarları.
- `messages.yml` — tüm metinler, prefix, renk paleti, placeholder formatları.

### PlaceholderAPI

Her global grup için (örn. `bugday_tarlasi`):

| Placeholder | Çıktı |
|-------------|-------|
| `%mossregen_bugday_tarlasi_time_left%` | Kalan saniye (geri sayım yoksa `-1`) |
| `%mossregen_bugday_tarlasi_time_left_formatted%` | `Xdk Ysn` veya "Hazır" |
| `%mossregen_bugday_tarlasi_pending%` | O an bekleyen blok sayısı |

## Mimari (özet)

```
MossRegen (ana sınıf)
├── config/ConfigManager      → config.yml → immutable model (bir kez parse)
├── config/MessageManager     → messages.yml, hex/gradient renklendirme
├── hook/WorldGuardHook        → soft-depend bölge sorgusu
├── listener/BlockBreakListener→ ucuzdan pahalıya sıralı hot-path
├── manager/
│   ├── BulkRestoreService     → tick başına bölünmüş geri yükleme
│   ├── RegenManager           → bireysel cooldown + disk yedeği
│   ├── GlobalRegenManager     → toplu/global yenilenme + PAPI verisi
│   └── RewardManager          → ITEM/COMMAND hediye + bildirim
└── placeholder/MossRegenExpansion
```
