# Setbaşı Fırını

Fırının günlük ekmek üretimini, satışlarını ve kalan stoğunu takip eden Android uygulaması.
Veriler telefonda saklanır; internet bağlantısı gerekmez.

## Özellikler

- **Bugün:** Her ekmek için günün üretimi girilir, satışlar eklenir, kalan stok anlık görünür.
  Yanlış girilen satış "Satışları düzelt" ile geri alınabilir. Gün sonunda gün tamamlanır.
- **Ekmekler:** Ekmek türleri eklenir, düzenlenir, arşivlenir. Haftanın her günü için ayrı
  üretim planı tanımlanabilir.
- **Raporlar:** Günlük, haftalık, aylık ve özel tarih aralığı raporları; en çok satan ve en çok
  kalan ekmekler; ekmek bazında gün gün döküm. Rapor WhatsApp ile paylaşılabilir.
- **Öneriler:** Son dört haftanın tamamlanmış günlerine bakarak satışı düşük kalan günler için
  üretimi kademeli azaltma önerisi verir ve plan değişikliğinin sonucunu izler.

Uygulama Türkçe çalışır ve günleri Türkiye saatine (UTC+3) göre hesaplar.

## Teknik

- Java, Material 3, yerel SQLite
- minSdk 24 (Android 7.0), targetSdk 35
- Debug derlemesi `com.example.bakeryapp.qa` kimliğiyle ayrı kurulur ve örnek verilerle açılır.

## Derleme ve test

JDK 17 ve Android SDK 35 gerekir.

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

## Lisanslar

Başlık yazı tipi DM Serif Display, SIL Open Font License ile dağıtılır
(`licenses/DMSerifDisplay-OFL.txt`).
