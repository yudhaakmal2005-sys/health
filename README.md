# SEHATI — Sistem Edukasi & Pemantauan Kesehatan Komunitas

Platform pencegahan Penyakit Tidak Menular, khususnya **jantung koroner**, berbasis Posyandu ILP.
Menghubungkan **Warga → Kader Posyandu → Puskesmas** dalam satu alur data. SEHATI **bukan alat diagnosis**.

| Bagian | Folder | Teknologi |
|---|---|---|
| Aplikasi Android (warga, kader, admin) | `app/` | Kotlin, Jetpack Compose, Room + SQLCipher, Hilt, WorkManager, Health Connect, ML Kit |
| Server API (VPS) | `server/` | Node 22, TypeScript, Fastify 5, PostgreSQL 16, Claude API (Tanya SEHATI) |
| Dashboard web Puskesmas + situs publik | `web/` | React 19, Vite, TypeScript, Tailwind |
| Deploy VPS | `deploy/` | Docker Compose (PostgreSQL, API, Caddy HTTPS otomatis), backup harian |

Kontrak API: `docs/API.md`. Panduan pasang di VPS (Bahasa Indonesia, langkah demi langkah): `deploy/README.md`.

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
| Ambang klinis | Admin dapat menyesuaikan ambang (tervalidasi, tercatat di audit, semua profil dihitung ulang, dapat dikembalikan ke bawaan); dengan server, ambang terpusat dari dashboard web. |
| Tanya SEHATI AI | Jawaban mengalir (streaming) dari Claude lewat server; kunci AI hanya di server; persetujuan pengguna; gejala darurat diperiksa di HP dulu; cadangan offline bila tanpa internet. |
| Pengingat | Jam dapat diatur: obat, minum air, jalan sehat, cek tensi mingguan, Posyandu (H-1 & hari H), tantangan, fakta harian, tidur. Tombol langsung di notifikasi ("Tambah 1 gelas", "Sudah diminum", "Saya berhasil"). Tidak muncul bila target sudah tercapai. |
| Obat saya | Jadwal minum obat sesuai resep, centang dosis, kepatuhan 7 hari. Tidak memberi saran obat/dosis. |
| Darurat | Telepon 119/112, tanda serangan jantung & stroke (SeGeRa ke RS), langkah pertama, metronom pijat jantung (RJP) 110×/menit. |
| Latihan napas | Pola 4-4-6 beranimasi untuk mengelola stres. |
| Multi-perangkat | Hasil pemeriksaan kader muncul di HP warga (pull sync); aktivasi akun warga yang didaftarkan kader; jadwal Posyandu per RW dari Puskesmas. |

## Arsitektur
`ui (Compose, ViewModel) → domain (aturan murni Kotlin) → data (Room+SQLCipher, repository, sync, Health Connect) → infra (WorkManager, Keystore, lokasi)`.
* Room terenkripsi (SQLCipher), kunci acak disimpan terenkripsi Android Keystore.
* Satu sumber kebenaran: semua layar membaca Room lewat repository (`userId = SEHATI ID`).
* Sinkronisasi: Room → antrean → upload idempoten → konfirmasi → status `LOCAL_ONLY/SYNCING/SYNCED/SYNC_FAILED`.
* RBAC dari sesi terautentikasi; Admin tidak memiliki akses data kesehatan individu.
* Aturan klinis: konfigurasi internal (`ClinicalThresholds`) dengan sumber, penjelasan, keterbatasan, dan tindakan; hasil skrining tidak pernah ditulis sebagai diagnosis.

Server bersifat opsional: tanpa server, aplikasi tetap penuh secara offline. Dengan server: login di HP baru, hasil kader tersinkron
ke warga, dashboard web Puskesmas, jadwal Posyandu, ambang terpusat, dan Tanya SEHATI AI.

### Server & web (lokal)
```bash
cd server && npm ci && npm test          # 76 tes terhadap PostgreSQL sungguhan (cluster sementara dibuat otomatis)
DATABASE_URL=postgres://… npm run seed:demo && DATABASE_URL=postgres://… npm run dev   # http://localhost:8080/api/v1/health
cd web && npm ci && npm run dev:mock      # dashboard dengan data sintetis di peramban (tanpa server)
cd web && npm run dev                     # dashboard terhubung ke server lokal
```
Alamat server diatur di aplikasi: **Masuk → Pengaturan server** atau **Profil → Server SEHATI** (rilis wajib HTTPS).

## Status verifikasi
CI: Android (unit, integrasi Room, ViewModel, Compose, kontrak klien–server, UI end-to-end, build debug/staging/release),
emulator Android 14 (alur lengkap + tangkapan layar), Server (typecheck + 76 tes PostgreSQL + build image), Web (typecheck + build).
Detail temuan, perubahan, dan batasan: `docs/LAPORAN.md`.
