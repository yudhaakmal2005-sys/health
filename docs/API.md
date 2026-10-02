# Kontrak API SEHATI (backend opsional)

SEHATI **offline-first**: aplikasi berfungsi penuh tanpa server. Server hanya menerima sinkronisasi.
`BASE_URL` dikonfigurasi per build type (`sehati.baseUrl.debug|staging|release` di `gradle.properties`
atau `-P`), tidak pernah di-hardcode. Seluruh trafik wajib HTTPS pada staging/release.

## Autentikasi
`POST /auth/login` → `{ token, expiresAt, user }` (Bearer token, kedaluwarsa; refresh via `/auth/refresh`).
Role (`WARGA|KADER|ADMIN`) ditentukan server dari akun, bukan klien (RBAC).

## Sinkronisasi (diimplementasikan di aplikasi)

`POST /sync/push`

```json
{
  "deviceId": "uuid",
  "items": [
    { "id": "idempotency-key", "type": "measurement", "entityId": "uuid",
      "operation": "UPSERT", "version": 3, "payload": "{...json entitas...}" }
  ]
}
```

Respons:

```json
{ "results": [ { "id": "idempotency-key", "status": "OK|DUPLICATE|CONFLICT|REJECTED", "serverId": "…", "message": "…" } ] }
```

Aturan server:
* **Idempoten** pada `id`: kiriman ulang dibalas `DUPLICATE` (dianggap sukses oleh klien).
* Data pengukuran bersifat *append-only*; entitas mutable memakai `version` (optimistic concurrency); konflik → `CONFLICT`.
* `operation = DELETE` untuk `user` berarti penghapusan data warga (hak penghapusan).
* Server **tidak boleh** menerima data warga yang tidak disetujui sinkronisasinya (klien juga menyaring berdasarkan consent).
* Jenis `type`: `user, household, cadre, profile, assessment, measurement, measurement_detail, activity, sleep, food, habit, smoking, education, visit, followup, referral, homevisit, logistics`.

## Endpoint yang direncanakan (belum dipanggil klien v1.0)
`/users`, `/assessments`, `/measurements`, `/activities`, `/foods`, `/posyandu-visits`, `/follow-ups`,
`/home-visits`, `/cadres`, `/analytics`, `/reports` — bentuk REST per sumber daya. Klien v1.0 hanya memakai
`/sync/push`; **pull sync** (server → perangkat) belum diimplementasikan.

## Keamanan
HTTPS, token kedaluwarsa, RBAC di server, audit log, least privilege, tanpa data sensitif di log, tanpa kunci API
di sumber kode. Admin hanya menerima agregat dan registri tindak lanjut.
