# Kontrak API SEHATI v2

SEHATI tetap **offline-first**: aplikasi Android berfungsi penuh tanpa server. Server (VPS) menambahkan:
multi-perangkat (data dari kader muncul di HP warga), dashboard web Puskesmas, Tanya SEHATI berbasis AI,
jadwal Posyandu, dan ambang klinis terpusat.

* Basis URL: `https://<domain>/api/v1`. Server sendiri memasang semua rute di bawah prefiks `/api/v1`; Caddy meneruskan `/api/*` apa adanya. Debug Android: `http://10.0.2.2:8080/api/v1/`. Semua JSON UTF-8.
* Waktu: epoch milidetik (`Long`) kecuali disebut lain; tanggal kalender `YYYY-MM-DD`.
* Galat: `{ "error": { "code": "STRING_KODE", "message": "Pesan bahasa Indonesia untuk pengguna" } }` dengan status HTTP yang sesuai
  (400 validasi, 401 belum masuk/token tidak valid, 403 RBAC, 404, 409 konflik, 429 batas, 503 fitur nonaktif).
* Autentikasi: `Authorization: Bearer <token>`. Token **opaque** (32 byte acak, base64url); server menyimpan **hash SHA-256**-nya saja.
  Masa berlaku: WARGA 30 hari, KADER/ADMIN 12 jam (sama dengan aplikasi).
* Role ditentukan server dari akun (RBAC), bukan dari klien. ADMIN **tidak pernah** menerima data kesehatan individu.
* Server **tidak boleh** mencatat isi data kesehatan, kata sandi, token, atau isi pesan AI ke log.

## 1. Kesehatan & konfigurasi

`GET /health` (tanpa auth) → `{ "status": "ok", "version": "2.0.0", "time": 1790000000000, "ai": true }`
(`ai` = kunci AI terkonfigurasi).

`GET /config` (auth role apa pun) →
```json
{
  "thresholds": { "...": "objek ClinicalThresholds atau null bila memakai bawaan aplikasi" },
  "thresholdsVersion": 3,
  "emergencyNumbers": "119 atau 112",
  "posyandu": [ { "id": "uuid", "rw": "02", "date": "2026-10-12", "startTime": "08:00", "endTime": "11:00",
                 "location": "Balai RW 02", "notes": "Bawa QR SEHATI" } ]
}
```
`posyandu` hanya jadwal mendatang (hari ini dan sesudahnya), urut tanggal. WARGA menerima jadwal RW-nya saja; KADER RW tugasnya; ADMIN semua.

## 2. Autentikasi

Format hash kata sandi **sama dengan aplikasi** agar keduanya dapat memverifikasi:
PBKDF2-HMAC-SHA256, kunci 256 bit, `iterations` (bawaan 120000), `salt` 16 byte base64, `hash` base64.

`POST /auth/login` `{ "sehatiId": "HM-000127", "password": "…", "deviceId": "uuid" }` →
```json
{ "token": "…", "expiresAt": 1790000000000,
  "user": { "sehatiId": "HM-000127", "fullName": "Tariska", "role": "WARGA", "rw": "02", "village": "Desa Mirigambar" } }
```
* Kunci akun 15 menit setelah 5 gagal berturut-turut (`429 ACCOUNT_LOCKED`). Pesan gagal generik: `401 INVALID_CREDENTIALS`.
* Akun WARGA dibuat dari sinkronisasi (`user` + `credential`, lihat §4) atau `POST /auth/register`.

`POST /auth/register` (tanpa auth, rate limit per IP 10/jam) — pendaftaran mandiri warga ketika online:
```json
{ "sehatiId": "HM-000231", "fullName": "…", "birthDate": "1990-01-01", "sex": "FEMALE", "village": "…", "rw": "02", "rt": "01",
  "phone": null, "password": "…", "consentServerSync": true, "deviceId": "uuid" }
```
`sehatiId` wajib berasal dari reservasi perangkat itu (§3) atau belum dipakai. Tanpa `consentServerSync: true` → `400 CONSENT_REQUIRED`.
Respons sama dengan login (status 201). ID terpakai → `409 ID_TAKEN`.

`POST /auth/activate` (tanpa auth, rate limit 5/jam per IP dan per ID) — aktivasi akun warga yang didaftarkan kader:
`{ "sehatiId": "HM-000127", "birthDate": "1992-03-01", "password": "…", "deviceId": "uuid" }`. Server memeriksa entitas `user` WARGA
dengan `consentServerSync=true`, `birthDate` cocok, dan belum ada kata sandi; lalu membuat kredensial dan menjawab seperti login.
Data tidak cocok/tidak ada → `404 NOT_FOUND` (pesan generik yang sama), sudah aktif → `409 ALREADY_ACTIVE`.

`POST /auth/logout` (auth) → `204`. Mencabut token.
`GET /auth/me` (auth) → objek `user` seperti di atas.

## 3. Reservasi SEHATI ID

Mencegah bentrok ID antar-perangkat. Aplikasi menyimpan "kolam" ID cadangan saat online dan memakainya saat mendaftar;
bila kolam kosong dan offline, aplikasi memakai penghitung lokal (risiko bentrok ditangani sebagai `CONFLICT`).

`POST /ids/reserve` `{ "deviceId": "uuid", "count": 20, "prefix": "HM" }` →
`{ "ids": ["HM-000231", "HM-000232"], "expiresAt": 1792592000000 }`
* Tanpa auth: `count` maks 1 (untuk pendaftaran mandiri), rate limit per IP.
* KADER: maks 50, prefix `HM`. ADMIN: prefix `HM` atau `KD`.
* Reservasi berlaku 30 hari, terikat `deviceId`. Nomor tidak pernah dipakai ulang.

## 4. Sinkronisasi

### Push — `POST /sync/push` (auth)
```json
{
  "deviceId": "uuid",
  "items": [
    { "id": "idempotency-key", "type": "measurement", "entityId": "uuid", "subjectId": "HM-000127",
      "operation": "UPSERT", "version": 3, "payload": "{...JSON entitas Room...}" }
  ]
}
```
Respons `{ "results": [ { "id": "idempotency-key", "status": "OK|DUPLICATE|CONFLICT|REJECTED", "serverId": "…", "message": "…" } ] }`
(urut sama dengan permintaan, maks 500 item per permintaan).

* `payload` = string JSON hasil kotlinx.serialization entitas Room (nama field = nama properti Kotlin, camelCase;
  lihat `app/src/main/java/id/sehati/app/data/local/Entities.kt`). Server menyimpannya sebagai JSONB apa adanya.
* **Idempoten** pada `id`: kiriman ulang → `DUPLICATE` (sukses bagi klien).
* `version`: bila server sudah punya versi **lebih tinggi** untuk `(type, entityId)` → `CONFLICT` (server menang; klien menerima versi server lewat pull).
  Versi sama atau lebih rendah dari yang diterima tetapi dengan id idempoten baru → `DUPLICATE`.
* `operation = "DELETE"` untuk `type = "user"`: hapus semua entitas warga tersebut (hak penghapusan) dan akunnya.
* **Consent**: item dengan `subjectId` warga ditolak (`REJECTED`, pesan "Warga belum menyetujui sinkronisasi") kecuali server sudah menyimpan
  `user` warga itu dengan `consentServerSync = true`, atau item `user` tersebut ada lebih awal di batch yang sama.
* `type`: `user, credential, household, cadre, profile, assessment, measurement, measurement_detail, activity, sleep, food, habit, smoking,
  education, challenge, medication, medlog, visit, followup, referral, homevisit, logistics`.
* `credential` payload: `{ "sehatiId", "salt", "hash", "iterations" }` — hanya hash PBKDF2, tidak pernah kata sandi. Membuat/memperbarui
  akun login warga. Tidak pernah dikirim balik lewat pull.
* RBAC push (server memeriksa; pelanggaran → `REJECTED`):
  * WARGA: `subjectId` harus dirinya; tipe `user` (diri sendiri), `credential` (diri sendiri), `profile, assessment, measurement,
    measurement_detail, activity, sleep, food, habit, smoking, education, challenge, medication, medlog`.
  * KADER: tipe warga di atas (kecuali `medication`, `medlog`) + `household, visit, followup, referral, homevisit` untuk subjek ber-role WARGA; serta `user`/`credential` warga
    yang ia daftarkan. Tidak boleh mengubah akun staf.
  * ADMIN: `cadre, logistics, followup` (penugasan/penjadwalan). Tidak boleh mengirim data kesehatan warga.

### Pull — `GET /sync/pull?cursor=0&limit=500` (auth)
```json
{ "items": [ { "seq": 101, "type": "measurement", "entityId": "uuid", "subjectId": "HM-000127", "version": 3,
               "deleted": false, "payload": "{...}" } ],
  "nextCursor": 101, "hasMore": false }
```
* `seq` naik monoton setiap entitas berubah di server; klien menyimpan `nextCursor` dan memanggil ulang selama `hasMore`.
* Visibilitas: WARGA → entitas dengan `subjectId` = dirinya (+ `user` miliknya). KADER → entitas warga di RW tugasnya + `cadre`/`logistics`.
  ADMIN → hanya `cadre`, `logistics`, `followup`. Tipe `credential` tidak pernah dikirim.
* Item dari perangkat yang sama tetap dikirim (klien mengabaikan bila versinya tidak lebih tinggi).

## 5. Tanya SEHATI (AI) — `POST /ai/chat` (auth, respons Server-Sent Events)

Permintaan:
```json
{ "messages": [ { "role": "user", "content": "Apa tanda serangan jantung?" },
                { "role": "assistant", "content": "…" },
                { "role": "user", "content": "Kalau pada perempuan?" } ],
  "context": { "ageBand": "40-49", "sex": "FEMALE", "factors": ["bp", "smoking"], "steps": 4200 } }
```
* `messages` maks 20, tiap `content` maks 1000 karakter, pesan terakhir harus `user`. `context` opsional dan **tanpa identitas**
  (aplikasi hanya mengirim bila pengguna mengizinkan "Gunakan data saya untuk jawaban yang lebih sesuai").
* Batas: 40 pesan/hari/akun (`429 AI_DAILY_LIMIT`), 10/menit/akun. Kunci tidak terkonfigurasi → `503 AI_DISABLED`.
* Server memeriksa kata gejala darurat (Bahasa Indonesia) **sebelum** memanggil model; bila darurat, model tidak dipanggil.

Respons `Content-Type: text/event-stream`, event berurutan:
```
event: meta
data: {"emergency":false}

event: delta
data: {"text":"Tanda yang perlu diwaspadai "}

event: done
data: {"stopReason":"end_turn"}
```
* Darurat: `meta {"emergency":true}`, satu `delta` berisi pesan darurat baku (hubungi 119/112), lalu `done {"stopReason":"emergency"}`.
* Galat di tengah aliran: `event: error` `data: {"code":"AI_UNAVAILABLE","message":"…"}` lalu koneksi ditutup.
* Server tidak menyimpan isi percakapan; hanya hitungan pemakaian per hari.

## 6. Admin Puskesmas (role ADMIN; dipakai dashboard web)

Semua agregat menyembunyikan sel kecil: hitungan 1–4 dikirim sebagai `null` dengan `"suppressed": true` (MIN_CELL = 5).

* `GET /admin/overview` → `{ "registered", "screened30d", "followUpOpen", "followUpOverdue", "levels": {"HEALTHY_HABIT":n,...},
  "bp": {"normal","elevated","high"}, "smokers", "byMonth": [ {"month":"2026-09","screened":n} ], "updatedAt" }`
* `GET /admin/rw` → `[ { "rw": "02", "registered", "screened", "followUpOpen", "elevatedBpPct": 0.31 | null, "suppressed": false } ]`
* `GET /admin/followups?status=OPEN|SCHEDULED|DONE|ALL&rw=02` → `[ { "id", "sehatiId", "rw", "type", "reason", "priority", "status",
  "dueAt", "assignedCadreId", "assignedCadreName", "createdAt" } ]` — tanpa nama warga dan tanpa nilai pemeriksaan.
* `POST /admin/followups/:id/assign` `{ "cadreId": "KD-000001" | null }`; `POST /admin/followups/:id/schedule` `{ "dueAt": 1790000000000 }`
  → mengubah payload `followup` (versi +1, `updatedAt`) sehingga ikut ter-pull ke perangkat kader.
* `GET /admin/cadres` → `[ { "sehatiId", "fullName", "rw", "active", "lastSeenAt" } ]`;
  `POST /admin/cadres` `{ "fullName", "rw", "password" }` → 201 `{ "sehatiId": "KD-000004" }`; `PATCH /admin/cadres/:id` `{ "active": false }`.
* `GET /admin/posyandu` / `POST /admin/posyandu` `{ "rw", "date", "startTime", "endTime", "location", "notes" }` /
  `PATCH /admin/posyandu/:id` / `DELETE /admin/posyandu/:id`.
* `GET /admin/thresholds` → `{ "thresholds": {…} | null, "version": 3 }`; `PUT /admin/thresholds` `{ "thresholds": {…} | null }`
  (null = kembali ke bawaan). Validasi rentang wajar dan urutan naik; dicatat di audit.
* `GET /admin/audit?limit=100` → `[ { "at", "actorId", "actorRole", "action", "subjectId", "detail" } ]`.
* `GET /admin/ai-usage?days=30` → `[ { "day": "2026-10-01", "messages", "users", "emergencies" } ]`.
* `GET /admin/reports/summary.csv` → CSV agregat (RW, terdaftar, terskrining, tindak lanjut terbuka, % TD tinggi).

## 7. Keamanan & operasional
HTTPS (Caddy, sertifikat otomatis), header keamanan, CORS hanya origin dashboard, rate limit, ukuran body maks 2 MB,
audit log untuk login, perubahan akun, penugasan, ambang, dan penghapusan data. Cadangan harian `pg_dump`.
Kunci AI (`ANTHROPIC_API_KEY`) hanya di server (`.env`), tidak pernah di aplikasi atau repositori.
