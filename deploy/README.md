# Panduan Deploy Server SEHATI (VPS)

Panduan ini memasang **server SEHATI** (API + basis data + dashboard web + HTTPS otomatis) pada satu VPS
Ubuntu 22.04/24.04 memakai Docker. Semua perintah dijalankan di VPS sebagai user biasa yang punya akses `sudo`.

Komponen (lihat `docker-compose.yml`):

| Service | Isi | Keterangan |
|---|---|---|
| `db` | PostgreSQL 16 | Data tersimpan di volume Docker `db-data`, tidak dibuka ke internet |
| `api` | Backend Node.js (`server/`) | Port 8080 hanya di jaringan internal Docker |
| `caddy` | Caddy 2 | Port 80/443, sertifikat HTTPS otomatis, melayani dashboard (`web/dist`), `/api/*`, dan `/download/` (APK) |

> SEHATI menyimpan data kesehatan. Jaga kerahasiaan file `.env`, batasi akses SSH, dan lakukan cadangan harian.

---

## 1. Siapkan domain (DNS)

Di panel DNS domain Anda, buat **A record** yang mengarah ke IP publik VPS, contoh:

| Tipe | Nama | Nilai |
|---|---|---|
| A | `sehati` | `203.0.113.10` |

Sehingga `sehati.desaanda.id` → IP VPS. Cek dari komputer Anda: `ping sehati.desaanda.id` (tunggu propagasi DNS, bisa beberapa menit sampai beberapa jam).
Sertifikat HTTPS baru bisa terbit setelah DNS benar.

## 2. Amankan VPS dan buka firewall

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y ufw git curl
sudo ufw allow 22/tcp      # SSH
sudo ufw allow 80/tcp      # HTTP (diperlukan untuk penerbitan sertifikat)
sudo ufw allow 443/tcp     # HTTPS
sudo ufw allow 443/udp     # HTTP/3 (opsional)
sudo ufw enable
sudo ufw status
```

Disarankan: login SSH dengan kunci (bukan kata sandi) dan nonaktifkan login root di `/etc/ssh/sshd_config`.

## 3. Pasang Docker

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
# keluar lalu login SSH lagi agar grup docker berlaku
docker version
docker compose version
```

## 4. Ambil kode

```bash
sudo mkdir -p /opt/sehati && sudo chown $USER:$USER /opt/sehati
git clone https://github.com/<akun-anda>/<repo-sehati>.git /opt/sehati
cd /opt/sehati
```

## 5. Isi konfigurasi `.env`

```bash
cd /opt/sehati/deploy
cp .env.example .env
chmod 600 .env
openssl rand -base64 32 | tr -d '/+='   # pakai hasilnya sebagai POSTGRES_PASSWORD
nano .env
```

Isi minimal:

| Variabel | Contoh | Keterangan |
|---|---|---|
| `DOMAIN` | `sehati.desaanda.id` | Domain dari langkah 1 |
| `POSTGRES_PASSWORD` | (acak) | Kata sandi basis data |
| `DATABASE_URL` | `postgres://sehati:<POSTGRES_PASSWORD>@db:5432/sehati` | Kata sandi **harus sama** dengan `POSTGRES_PASSWORD` |
| `ADMIN_ID` | `AD-000001` | ID admin Puskesmas pertama |
| `ADMIN_PASSWORD` | (min. 10 karakter) | **Wajib.** Server menolak start tanpa ini |
| `ANTHROPIC_API_KEY` | `sk-ant-...` | Kunci Tanya SEHATI (AI). Kosongkan bila AI belum dipakai |
| `AI_MODEL` | `claude-opus-5-5` | Model AI (dapat diganti, lihat §13) |
| `AI_EFFORT` | `low` | `low` cocok untuk jawaban chat singkat |
| `CORS_ORIGIN` | `https://sehati.desaanda.id` | Origin dashboard web |
| `TZ` | `Asia/Jakarta` | Zona waktu (batas harian AI, jadwal Posyandu "hari ini") |

> Kata sandi admin hanya dipakai saat admin pertama dibuat. Setelah itu mengubah `ADMIN_PASSWORD` di `.env` tidak mengubah akun.

## 6. Build dashboard web

Dashboard ada di folder `web/` dan disajikan Caddy dari `web/dist`. Build dengan Docker (tanpa memasang Node di VPS):

```bash
cd /opt/sehati/web
docker run --rm -v "$PWD":/app -w /app node:22-alpine sh -c "npm ci && npm run build"
ls dist/index.html
```

Bila dashboard memerlukan alamat API saat build, gunakan alamat relatif `/api/v1` (domain yang sama, tidak perlu CORS).

## 7. Jalankan

```bash
cd /opt/sehati/deploy
docker compose up -d --build
docker compose ps
```

Saat start pertama, API otomatis menjalankan migrasi basis data dan membuat akun admin dari `ADMIN_ID`/`ADMIN_PASSWORD`.

## 8. Verifikasi

```bash
curl -s https://sehati.desaanda.id/api/v1/health
# {"status":"ok","version":"2.0.0","time":...,"ai":true}
docker compose logs --tail=50 api
```

`"ai": false` berarti `ANTHROPIC_API_KEY` belum diisi (Tanya SEHATI membalas `503 AI_DISABLED`; fitur lain tetap berjalan).
Buka `https://sehati.desaanda.id` di peramban, masuk sebagai `AD-000001`.

Opsional — isi **data demo sintetis** (3 kader, 60 warga RW 01–05, kata sandi `demo1234`) untuk latihan/presentasi.
**Jangan** lakukan di server yang dipakai warga sungguhan:

```bash
docker compose exec -e SEED_DEMO_CONFIRM=yes api node dist/cli/seed-demo.js
```

## 9. Buat akun kader dari dashboard

1. Masuk dashboard sebagai admin → menu **Kader** → **Tambah kader**.
2. Isi nama, RW tugas (contoh `02`, atau beberapa RW `01,02`, atau rentang `04-06`) dan kata sandi awal (min. 6 karakter).
3. Server membuat ID `KD-00000x`. Berikan ID dan kata sandi ke kader secara langsung (jangan lewat grup chat).
4. Kader yang berhenti: **Nonaktifkan** (sesi langsung dicabut). Lupa kata sandi: admin dapat mengatur ulang.

## 10. Hubungkan aplikasi Android

Di aplikasi SEHATI (build release/staging), atur **alamat server** ke:

```
https://sehati.desaanda.id/api/v1/
```

(Build debug pada emulator memakai `http://10.0.2.2:8080/api/v1/` ke server lokal.)
APK dapat dibagikan lewat server: salin file ke `deploy/download/`, lalu unduh dari
`https://sehati.desaanda.id/download/sehati-2.0.0.apk`.

```bash
cp ~/sehati-2.0.0.apk /opt/sehati/deploy/download/
```

## 11. Cadangan (backup) harian

`backup.sh` membuat `pg_dump` terkompresi di `deploy/backups/` dan menghapus cadangan lebih dari 14 hari.

```bash
chmod +x /opt/sehati/deploy/backup.sh
/opt/sehati/deploy/backup.sh          # uji sekali
crontab -e
# tambahkan baris berikut (setiap hari pukul 02.30):
30 2 * * * /opt/sehati/deploy/backup.sh >> /home/$USER/sehati-backup.log 2>&1
```

Salin cadangan secara berkala ke tempat lain (mis. laptop Puskesmas) karena cadangan di VPS yang sama tidak melindungi
dari kerusakan VPS:

```bash
scp user@sehati.desaanda.id:/opt/sehati/deploy/backups/sehati-*.sql.gz ./
```

## 12. Pulihkan (restore) dari cadangan

> Restore **menimpa** data saat ini. Buat cadangan terbaru dulu.

```bash
cd /opt/sehati/deploy
docker compose stop api
gunzip -c backups/sehati-20261003-023000.sql.gz | docker compose exec -T db psql -U sehati -d sehati -v ON_ERROR_STOP=1
docker compose start api
curl -s https://sehati.desaanda.id/api/v1/health
```

## 13. Tanya SEHATI (AI): kunci, model, dan perkiraan biaya

* Kunci AI **hanya** ada di `deploy/.env` (tidak pernah di aplikasi atau repositori). Server tidak menyimpan isi percakapan,
  hanya hitungan pemakaian per hari (dashboard → Pemakaian AI).
* Batas: 40 pertanyaan/hari/akun dan 10/menit/akun. Gejala darurat (mis. "saya nyeri dada sekarang") dijawab dengan pesan
  baku 119/112 **tanpa** memanggil model (gratis).

**Mengganti/merotasi `ANTHROPIC_API_KEY`** (mis. kunci bocor atau pergantian penanggung jawab):

1. Buat kunci baru di console Anthropic.
2. `nano /opt/sehati/deploy/.env` → ganti `ANTHROPIC_API_KEY`.
3. `docker compose up -d api` (container dibuat ulang dengan env baru).
4. Cek `curl -s https://<DOMAIN>/api/v1/health` → `"ai": true`, coba satu pertanyaan di aplikasi.
5. Hapus/nonaktifkan kunci lama di console.

**Perkiraan biaya** (harga dapat berubah; cek halaman harga Anthropic): model bawaan `claude-opus-5-5`
(sekitar US$4 per juta token masukan, US$20 per juta token keluaran). Satu pertanyaan ≈ 2–4 ribu token masukan
(prompt sistem sebagian besar ter-cache sehingga lebih murah) dan ≈ 300–800 token keluaran pada `AI_EFFORT=low`,
yaitu kira-kira **US$0,02–0,03 per pertanyaan**. Contoh: 100 warga × 10 pertanyaan/bulan ≈ 1.000 pertanyaan ≈ **US$20–30/bulan**.
Model dapat diganti lewat `AI_MODEL` (mis. `claude-sonnet-5-5` lebih murah, sekitar setengahnya) lalu `docker compose up -d api`.
Pasang batas pengeluaran bulanan di console Anthropic.

## 14. Pembaruan (update) aplikasi server

```bash
cd /opt/sehati
/opt/sehati/deploy/backup.sh
git pull
cd web && docker run --rm -v "$PWD":/app -w /app node:22-alpine sh -c "npm ci && npm run build" && cd ..
cd deploy && docker compose up -d --build
docker compose ps
curl -s https://<DOMAIN>/api/v1/health
```

Migrasi basis data berjalan otomatis saat API start. Hapus image lama sesekali: `docker image prune -f`.

## 15. Log dan pemecahan masalah

```bash
cd /opt/sehati/deploy
docker compose ps                      # status & health
docker compose logs -f api             # log API (tanpa isi data kesehatan/token/kata sandi)
docker compose logs -f caddy           # sertifikat HTTPS, akses
docker compose logs --tail=100 db
docker compose restart api
```

| Gejala | Penyebab umum |
|---|---|
| HTTPS gagal / sertifikat tidak terbit | DNS belum mengarah ke VPS, port 80/443 tertutup (cek `ufw status` dan firewall penyedia VPS) |
| API terus restart | `.env` salah (lihat `docker compose logs api`): `ADMIN_PASSWORD` kosong, `DATABASE_URL` tidak cocok dengan `POSTGRES_PASSWORD` |
| Dashboard kosong / 404 | `web/dist` belum di-build (langkah 6) |
| `"ai": false` | `ANTHROPIC_API_KEY` kosong |
| Login dikunci | 5 kali salah → tunggu 15 menit |

## 16. Pengembangan lokal (untuk pengembang)

```bash
cd server
npm ci
npm test                  # menjalankan Postgres 16 sementara (port 54329) bila DATABASE_URL tidak diatur
npm run typecheck
DATABASE_URL=postgres://postgres@127.0.0.1:5432/sehati npm run dev     # http://localhost:8080/api/v1/health
DATABASE_URL=... npm run seed:demo
```

Tanpa `NODE_ENV=production` dan tanpa `ADMIN_PASSWORD`, admin pengembangan `AD-000001` dibuat dengan kata sandi `admin12345`.
Kontrak API lengkap: `docs/API.md`.
