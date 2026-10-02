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
