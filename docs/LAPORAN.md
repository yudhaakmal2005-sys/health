# Laporan pengerjaan SEHATI

## 1. Temuan audit proyek awal (heartwise.zip)
* Satu `HeartViewModel` raksasa (775 baris), tanpa DI, tanpa lapisan domain; data berasal dari seed palsu ("Ahmad Fauzi") dan nilai default.
* ID tidak konsisten (`SH-`/`HM-`/`KD-`), tidak ada konsep consent, QR, kunjungan Posyandu, tindak lanjut, rujukan, kunjungan rumah, sinkronisasi.
* **Pengalih peran Admin/Kader selalu tampil** tanpa autentikasi; login hanya dengan email tanpa kata sandi.
* Health Connect hanya dialog tiruan; "AI" bergantung Firebase/Gemini; klaim "Standar WHO/Kemenkes" untuk aturan internal.
* Ambang klinis tertanam di kode tanpa sumber/keterbatasan; hasil skrining berbunyi mirip diagnosis.
* Konfigurasi build: applicationId AI Studio, keystore debug tidak ada di repo, plugin Firebase/secrets tidak diperlukan.

## 2. Yang dilakukan
Penulisan ulang terarah (arsitektur lalu data, baru kosmetik): paket `id.sehati.app`, Clean Architecture + MVVM, Hilt, Room terenkripsi SQLCipher,
WorkManager, Health Connect, CameraX+zxing (pemindai QR offline), kotlinx.serialization, Retrofit.

## 3. Database (Room v1, 26 entitas)
User, Credential, Household, Facility, Cadre, HealthProfile, HealthAssessment, HealthMeasurement + Anthropometry/BloodPressure/BloodGlucose/LipidMeasurement,
ActivitySession, SleepRecord, FoodEntry, HabitLog, SmokingRecord, EducationProgress, PosyanduVisit, FollowUp, Referral, HomeVisit, LogisticsItem,
SyncQueue, Notification, AuditLog. Semua data kesehatan berkunci `userId = SEHATI ID`.

## 4. API
Klien memakai `POST /sync/push` (idempoten). Kontrak lengkap: `docs/API.md`. Pull-sync belum ada.

## 5. Layar
Warga: Welcome, Onboarding (6 halaman + consent), Asesmen (8 langkah) → Profil → Rencana, Beranda, Aktivitas (GPS + Health Connect), Makanan, Kesehatan (riwayat, tren, QR),
Profil/Privasi/Target/Pengingat, Health Academy + kuis, Pelatih (aturan, offline), Notifikasi.
Kader: Hari Ini (register + filter), Warga (QR/ID/daftar baru), Pemeriksaan (langkah 2–5), Follow-Up, Kunjungan Rumah, Sinkronisasi.
Admin: Overview, Community (Heart Map), Follow-Up registry, Reports (teks/CSV), Cadres (tambah/aktifkan), Logistics, Settings (aturan + audit).

## 6. Pengujian (CI GitHub Actions)
118 tes, 0 gagal: unit domain (aturan klinis, profil, tindak lanjut, validasi, identitas/QR, kata sandi, analitik, gamifikasi, RBAC),
integrasi Room (golden path kader → riwayat warga → profil → follow-up → analitik admin; sinkron offline/online/duplikat/consent; hak hapus; auth & kunci akun; provisioning kader),
ViewModel (alur Posyandu 5 langkah), komponen Compose, dan **uji UI end-to-end Hilt+Robolectric** (login warga → asesmen → profil → QR → keluar → kader mengukur → validasi → sinkron → keluar → admin melihat tindak lanjut).
Build: `assembleDebug`, `assembleStaging` (R8), `assembleRelease` sukses.

## 7. Belum / batasan (jujur)
* **Tidak diverifikasi di perangkat fisik/emulator**: kamera pemindai QR, GPS, Health Connect, SQLCipher+Keystore pada perangkat nyata, tampilan visual/animasi. Semua dikompilasi dan logikanya diuji, tetapi uji manual di perangkat tetap diperlukan.
* Pemindai foto makanan (AI) **tidak dibuat**; Pelatih berbasis aturan (bukan LLM) agar aman dan offline.
* Backend hanya kontrak (`docs/API.md`); pull-sync dan resolusi konflik penuh belum ada. Build `release` tidak memiliki akun kader/admin sampai disediakan backend `/auth`
  (admin dapat menambah kader secara lokal; admin pertama harus disediakan lewat backend/MDM).
* Ambang klinis dapat dikonfigurasi di kode (`ClinicalThresholds`), belum ada antarmuka pengubah/persistensi.
* Migrasi basis data: baru versi 1 (skema belum diekspor), jadi uji migrasi belum ada.
* Akses baca repositori belum dibatasi per peran di lapisan data (pembatasan ada pada aksi tulis dan UI; admin hanya diberi agregat).

---

## 8. Pembaruan v2 (fokus jantung koroner, server VPS, AI, pengingat)

### Ditambahkan
* **Server** (`server/`): Fastify 5 + PostgreSQL 16. Login/daftar/aktivasi akun warga, reservasi SEHATI ID (mencegah bentrok antar-HP),
  sinkron **push + pull** (idempoten, versi, consent, RBAC per peran, tombstone hapus), dashboard admin hanya agregat (sel < 5 disembunyikan),
  jadwal Posyandu per RW, ambang klinis terpusat, audit log, dan **Tanya SEHATI AI** (Claude, streaming SSE, pemeriksaan darurat sebelum model,
  batas 40 pertanyaan/hari/akun, isi percakapan tidak disimpan). 76 tes terhadap PostgreSQL sungguhan.
* **Dashboard web + situs publik** (`web/`): beranda publik, kebijakan privasi, login staf, ringkasan, peta RW, registri tindak lanjut (tugaskan
  kader, atur jadwal), kader, jadwal Posyandu, ambang klinis, laporan cetak/CSV, audit, pemakaian AI. Mode terang/gelap, responsif hingga 360 px.
  Diuji langsung terhadap server sungguhan (Playwright) tanpa galat.
* **Deploy** (`deploy/`): Docker Compose (db, api, Caddy dengan HTTPS otomatis), backup harian `pg_dump` 14 hari, panduan VPS langkah demi langkah.
* **Android**: koneksi server (alamat dapat diatur tanpa build ulang; rilis wajib HTTPS), login di HP baru + tarik data, aktivasi akun warga dari
  kartu kader, kolam SEHATI ID, jadwal Posyandu di beranda; Tanya SEHATI AI dengan persetujuan & cadangan offline; pengingat berbasis jam dengan
  aksi di notifikasi; Obat saya (DB v3, migrasi 2→3); Darurat + metronom RJP; latihan napas; beranda baru (header gradien, aksi cepat, konfeti).

### Batasan yang masih ada
* Image Docker baru diuji di CI (tidak ada Docker daemon di lingkungan pengembangan); pasang pertama di VPS tetap perlu diperiksa.
* Tanya SEHATI AI memerlukan `ANTHROPIC_API_KEY` di server (berbayar, perkiraan di `deploy/README.md` §13); tanpa kunci aplikasi memakai pustaka offline.
* Batas laju per-menit dan percobaan aktivasi disimpan di memori server (cukup untuk satu instance API).
* Aktivasi akun warga memakai verifikasi tanggal lahir + batas percobaan; untuk keamanan lebih tinggi dapat ditambah kode dari kader.
* Pemindai foto makanan, kamera, GPS, Health Connect, pengingat, dan getar RJP tetap perlu dicoba di HP fisik.
