# Pasang Server SEHATI tanpa akses root

Paket ini berisi **satu aplikasi Node.js** yang sudah jadi:
API untuk aplikasi Android + dashboard web Puskesmas (folder `public`).
Tidak perlu Docker, tidak perlu root, tidak perlu `npm install` (folder `node_modules` sudah disertakan).

Kebutuhan:
1. **Node.js versi 22** (di cPanel: menu *Setup Node.js App*).
2. **Basis data PostgreSQL** (di cPanel: menu *PostgreSQL Databases*). Bila hosting tidak menyediakan PostgreSQL,
   pakai layanan gratis seperti **Neon** (neon.tech) atau **Supabase** dan salin *connection string*-nya.

---

## A. Hosting cPanel (paling umum)

1. **Buat subdomain** `sehati.akademiforex.com` (menu *Domains* / *Subdomains*).
   Arahkan *document root*-nya ke folder baru, misalnya `sehati-web` (folder ini tidak dipakai, aplikasi Node yang melayani).
2. **Buat basis data**: menu *PostgreSQL Databases* → buat database `sehati`, buat user + kata sandi,
   lalu *Add User to Database* (centang semua hak). Catat nama database, user, dan kata sandi lengkap
   (cPanel biasanya menambah awalan nama akun, mis. `akademif_sehati`).
3. **Unggah paket**: menu *File Manager* → masuk ke folder home (mis. `/home/NAMAAKUN`, BUKAN public_html)
   → *Upload* `sehati-server.zip` → klik kanan → *Extract* ke `/home/NAMAAKUN`.
   Hasilnya folder `/home/NAMAAKUN/sehati` berisi `app.cjs`, `dist`, `public`, `node_modules`, dst.
4. **Isi konfigurasi**: di File Manager, aktifkan *Show Hidden Files* (Settings).
   Ganti nama `.env.contoh` menjadi `.env`, klik *Edit*, isi `DATABASE_URL`, `ADMIN_PASSWORD`, dan bagian AI. Simpan.
5. **Buat aplikasi Node**: menu *Setup Node.js App* → *Create Application*:
   - Node.js version: **22.x**
   - Application mode: **Production**
   - Application root: `sehati`
   - Application URL: `sehati.akademiforex.com`
   - Application startup file: `app.cjs`
   Klik *Create*, lalu *Start/Restart*.
   (Tidak perlu klik *Run NPM Install* — modul sudah ada. Bila diminta, boleh diklik, tidak masalah.)
6. **HTTPS**: menu *SSL/TLS Status* → jalankan *AutoSSL* untuk subdomain tersebut.
7. **Cek**: buka `https://sehati.akademiforex.com/api/v1/health` → harus tampil `{"status":"ok",...}`.
   Lalu buka `https://sehati.akademiforex.com` → dashboard; masuk dengan `AD-000001` + `ADMIN_PASSWORD`.

Bila gagal: di *Setup Node.js App* klik aplikasi → lihat log (atau file `stderr.log` di folder `sehati`).
Pesan yang umum:
- `DATABASE_URL wajib diisi` / `password authentication failed` → periksa `.env` langkah 4.
- `ADMIN_PASSWORD minimal 10 karakter` → perpanjang kata sandi admin.

## B. VPS dengan user biasa (tanpa root/sudo)

Butuh Node 22 (pasang tanpa root lewat `nvm`) dan PostgreSQL (minta penyedia memasangnya, atau pakai Neon/Supabase):

```bash
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.3/install.sh | bash
source ~/.bashrc && nvm install 22
unzip sehati-server.zip -d ~/sehati && cd ~/sehati
cp .env.contoh .env && nano .env        # isi seperti di atas, tambahkan PORT=8080
npx --yes pm2 start app.cjs --name sehati && npx pm2 save
```
Lalu mintalah penyedia VPS / panel (mis. aaPanel, CyberPanel) membuat *reverse proxy* dari
`sehati.akademiforex.com` ke `http://127.0.0.1:8080` dengan SSL.

---

## Setelah server jalan

- **Aplikasi Android**: di layar masuk → *Pengaturan server* → isi `https://sehati.akademiforex.com/api/v1/`.
- **Bagikan APK**: unggah file APK ke folder `sehati/public/download/`, lalu warga mengunduh dari
  `https://sehati.akademiforex.com/download/NAMA-FILE.apk`.
- **Data demo** (hanya untuk latihan, jangan di server warga sungguhan): di terminal folder `sehati`:
  `SEED_DEMO_CONFIRM=yes node dist/cli/seed-demo.js`
- **Cadangan**: unduh cadangan basis data rutin (cPanel → *Backup* → PostgreSQL database), simpan di tempat aman.
- **Pembaruan**: ganti folder `dist`, `public`, `migrations`, `node_modules` dengan versi baru (jangan hapus `.env`), lalu *Restart*.
  Migrasi basis data berjalan otomatis saat start.

Catatan keamanan: SEHATI menyimpan data kesehatan. Jangan bagikan `.env`, gunakan kata sandi kuat,
dan aktifkan HTTPS sebelum dipakai warga.
