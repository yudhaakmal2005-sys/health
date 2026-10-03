# SEHATI — Sistem Edukasi & Pemantauan Kesehatan Komunitas

Aplikasi Android (Kotlin + Jetpack Compose) untuk promotif-preventif Penyakit Tidak Menular berbasis Posyandu ILP.
Menghubungkan **Warga → Kader Posyandu → Puskesmas/Admin** dalam satu alur data. SEHATI **bukan alat diagnosis**.

## Menjalankan
```bash
./gradlew testDebugUnitTest      # unit + integrasi (Robolectric)
./gradlew assembleDebug          # app/build/outputs/apk/debug/*.apk
```
Prasyarat: JDK 17+, Android SDK 36. Build type: `debug` (DEMO_MODE), `staging` (DEMO_MODE, minify), `release` (tanpa demo).
CI GitHub Actions membangun APK debug dan mengunggahnya sebagai artifact.

### Mode demo (debug/staging)
Dataset **sintetis** (100 warga HM-000100…199, 3 kader, 1 admin). Kata sandi demo: `demo1234`.
Golden path: Warga `HM-000127` → asesmen → profil → QR → keluar → Kader `KD-000001` → pindai/cari `HM-000127` → ukur → simpan
→ validasi & sinkron → keluar → Admin `AD-000001` → dashboard/registri tindak lanjut.
Sinkronisasi demo memakai **server simulasi di perangkat** (dilabeli jelas di UI). Release tidak pernah mengisi data demo.

## Fokus: pencegahan penyakit jantung koroner

Aplikasi berpusat pada pencegahan PTM, khususnya jantung koroner. Posyandu dewasa/lansia tetap dipakai sebagai kanal skrining (tekanan darah, gula, kolesterol, lingkar perut).

| Fitur | Keterangan |
|---|---|
| Sambutan | Logo hati berdenyut dalam cincin berputar saat membuka aplikasi dan setelah masuk (ketuk untuk melewati; mati otomatis bila "kurangi animasi" aktif). |
| Lima pilar jantung sehat | Aktif, bebas rokok, garam terkendali, tidur cukup, kontrol tensi. |
| Faktor risiko jantung koroner | Hitungan 9 faktor (bukan skor kemungkinan, bukan diagnosis) + langkah perbaikan + materi terkait. |
| Tantangan & lencana | 6 tantangan 7 hari dalam 14 hari (langkah dan air terhitung otomatis), fakta/mitos harian. |
| Tanya SEHATI | 28 tanya-jawab jantung koroner offline, pembeda pertanyaan umum vs gejala yang sedang dialami, banner darurat 119/112. |
| Pindai foto makanan | ML Kit di perangkat (foto tidak disimpan/dikirim) memberi saran makanan; pengguna memilih sendiri. |
| Ambang klinis | Admin dapat menyesuaikan ambang (tervalidasi, tercatat di audit, semua profil dihitung ulang, dapat dikembalikan ke bawaan). |

## Arsitektur
`ui (Compose, ViewModel) → domain (aturan murni Kotlin) → data (Room+SQLCipher, repository, sync, Health Connect) → infra (WorkManager, Keystore, lokasi)`.
* Room terenkripsi (SQLCipher), kunci acak disimpan terenkripsi Android Keystore.
* Satu sumber kebenaran: semua layar membaca Room lewat repository (`userId = SEHATI ID`).
* Sinkronisasi: Room → antrean → upload idempoten → konfirmasi → status `LOCAL_ONLY/SYNCING/SYNCED/SYNC_FAILED`.
* RBAC dari sesi terautentikasi; Admin tidak memiliki akses data kesehatan individu.
* Aturan klinis: konfigurasi internal (`ClinicalThresholds`) dengan sumber, penjelasan, keterbatasan, dan tindakan; hasil skrining tidak pernah ditulis sebagai diagnosis.

Lihat `docs/API.md` untuk kontrak backend opsional.

## Status verifikasi
CI: 118 tes lulus (unit, integrasi Room, ViewModel, Compose, dan UI end-to-end), build debug/staging/release sukses.
Detail temuan, perubahan, dan batasan: `docs/LAPORAN.md`.
