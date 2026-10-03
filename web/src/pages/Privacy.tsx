import type { ReactNode } from 'react'
import { PublicShell } from '../components/PublicShell'
import { Icon, type IconName } from '../components/Icon'
import { Callout } from '../components/ui'
import { site } from '../site.config'

const SECTIONS: { id: string; title: string; icon: IconName }[] = [
  { id: 'ringkas', title: 'Ringkasnya', icon: 'info' },
  { id: 'data', title: 'Data yang dicatat', icon: 'database' },
  { id: 'perangkat', title: 'Penyimpanan di HP', icon: 'phoneDevice' },
  { id: 'sinkron', title: 'Sinkronisasi ke server', icon: 'refresh' },
  { id: 'akses', title: 'Siapa dapat melihat', icon: 'users' },
  { id: 'ai', title: 'Tanya SEHATI (AI)', icon: 'sparkles' },
  { id: 'hak', title: 'Hak Anda', icon: 'shield' },
  { id: 'keamanan', title: 'Keamanan', icon: 'lock' },
  { id: 'kontak', title: 'Kontak', icon: 'message' },
]

function Section({ id, title, icon, children }: { id: string; title: string; icon: IconName; children: ReactNode }) {
  return (
    <section id={id} aria-labelledby={`${id}-h`} className="scroll-mt-24 border-t border-line pt-8 first:border-t-0 first:pt-0">
      <h2 id={`${id}-h`} className="flex items-center gap-3 text-xl font-extrabold tracking-tight text-fg">
        <span className="inline-flex h-9 w-9 items-center justify-center rounded-xl bg-primary-soft text-accent"><Icon name={icon} size={18} /></span>
        {title}
      </h2>
      <div className="mt-4 space-y-3 text-[0.95rem] leading-relaxed text-fg-2 [&_li]:pl-1 [&_strong]:font-semibold [&_strong]:text-fg [&_ul]:list-disc [&_ul]:space-y-1.5 [&_ul]:pl-5 marker:text-muted">
        {children}
      </div>
    </section>
  )
}

export function PrivacyPage() {
  return (
    <PublicShell>
      <div className="mx-auto max-w-6xl px-4 py-12 sm:px-6 md:py-16">
        <header className="max-w-3xl animate-fade-up">
          <p className="text-xs font-bold tracking-[0.14em] text-accent uppercase">Kebijakan privasi</p>
          <h1 className="mt-2 text-3xl font-extrabold tracking-tight text-fg sm:text-4xl">Data Anda, kendali Anda</h1>
          <p className="mt-3 text-base leading-relaxed text-muted">
            Kebijakan ini menjelaskan data apa yang dicatat SEHATI, di mana disimpan, siapa yang dapat melihatnya, dan bagaimana Anda dapat
            menghapusnya. Berlaku untuk aplikasi Android SEHATI dan dashboard web Puskesmas di {site.village}.
          </p>
          <p className="mt-2 text-xs text-muted">Terakhir diperbarui: {site.privacyUpdated}</p>
        </header>

        <div className="mt-10 grid gap-10 lg:grid-cols-[230px_1fr]">
          <nav aria-label="Daftar isi" className="hidden lg:block">
            <ul className="sticky top-24 space-y-0.5 border-l border-line">
              {SECTIONS.map((s) => (
                <li key={s.id}>
                  <a href={`#${s.id}`} className="-ml-px block border-l-2 border-transparent py-1.5 pl-4 text-sm text-muted transition-colors hover:border-primary hover:text-fg">
                    {s.title}
                  </a>
                </li>
              ))}
            </ul>
          </nav>

          <article className="max-w-3xl space-y-8">
            <Section id="ringkas" title="Ringkasnya" icon="info">
              <ul>
                <li>Data Anda <strong>disimpan terenkripsi di HP</strong> dan aplikasi tetap bekerja tanpa internet.</li>
                <li>Data dikirim ke server <strong>hanya bila Anda menyetujui sinkronisasi</strong>, dan persetujuan itu dapat dicabut kapan saja.</li>
                <li>Puskesmas hanya melihat <strong>angka gabungan</strong>; jumlah 1–4 orang disembunyikan agar tidak ada yang bisa dikenali.</li>
                <li>Pertanyaan ke Tanya SEHATI <strong>tidak disimpan</strong> di server.</li>
                <li>Anda berhak <strong>menghapus seluruh data</strong> Anda.</li>
              </ul>
              <Callout tone="primary" icon="heartPulse" className="mt-4">
                SEHATI adalah alat bantu edukasi dan pemantauan, <strong>bukan alat diagnosis</strong>. Hasil di aplikasi adalah kategori pemantauan
                berdasarkan data yang dimasukkan; keputusan medis tetap oleh tenaga kesehatan.
              </Callout>
            </Section>

            <Section id="data" title="Data yang dicatat" icon="database">
              <p><strong>Identitas dasar:</strong> SEHATI ID, nama, tanggal lahir, jenis kelamin, desa, RW/RT, dan nomor HP (opsional).</p>
              <p><strong>Data kesehatan dan gaya hidup:</strong> tekanan darah, gula darah, kolesterol total, berat dan tinggi badan, lingkar perut,
                jawaban asesmen faktor risiko, kebiasaan merokok, aktivitas dan langkah, tidur, catatan makan, serta progres tantangan.</p>
              <p><strong>Catatan pelayanan oleh kader:</strong> kunjungan Posyandu, tindak lanjut, rujukan, dan kunjungan rumah.</p>
              <p><strong>Akun:</strong> kata sandi tidak pernah disimpan dalam bentuk aslinya — hanya hash (PBKDF2) yang tidak dapat dikembalikan.</p>
              <p><strong>Izin perangkat (opsional, dapat ditolak):</strong></p>
              <ul>
                <li><strong>Kamera</strong> — memindai kartu QR dan foto makanan. Foto makanan diproses di HP dan tidak disimpan atau dikirim.</li>
                <li><strong>Lokasi (GPS)</strong> — hanya saat Anda memulai pelacakan jalan/lari, untuk menghitung jarak. Yang disimpan hanya jarak, bukan titik lokasi.</li>
                <li><strong>Health Connect</strong> — membaca langkah, tidur, tekanan darah, dan data serupa bila Anda izinkan. Diproses di perangkat.</li>
              </ul>
            </Section>

            <Section id="perangkat" title="Penyimpanan di HP" icon="phoneDevice">
              <p>
                Semua data disimpan di basis data terenkripsi (SQLCipher) di HP Anda. Kunci enkripsinya dibuat acak dan dilindungi Android Keystore,
                sehingga berkas data tidak dapat dibaca aplikasi lain. Menghapus aplikasi akan menghapus data yang ada di HP tersebut.
              </p>
            </Section>

            <Section id="sinkron" title="Sinkronisasi ke server" icon="refresh">
              <p>
                Sinkronisasi memungkinkan data dari kader muncul di HP Anda, dan memungkinkan Puskesmas menindaklanjuti hasil skrining.
                Saat mendaftar — sendiri atau dibantu kader — Anda akan ditanya apakah menyetujui sinkronisasi ke server.
              </p>
              <ul>
                <li>Tanpa persetujuan, data <strong>tetap hanya di perangkat</strong> dan server menolak data kesehatan atas nama Anda.</li>
                <li>Persetujuan dapat diubah kapan saja di menu <strong>Profil → Sinkronisasi ke server</strong>.</li>
                <li>Server dikelola untuk layanan kesehatan desa dan tidak digunakan untuk iklan atau dijual kepada pihak lain.</li>
              </ul>
            </Section>

            <Section id="akses" title="Siapa dapat melihat data" icon="users">
              <ul>
                <li><strong>Anda</strong> — seluruh data Anda sendiri.</li>
                <li><strong>Kader Posyandu</strong> — data warga di RW tugasnya, untuk pelayanan dan tindak lanjut.</li>
                <li><strong>Admin Puskesmas</strong> — <strong>tidak</strong> menerima data kesehatan perorangan. Dashboard hanya menampilkan angka
                  gabungan per desa/RW, dan daftar tindak lanjut berisi SEHATI ID, alasan umum, dan jadwal — tanpa nama dan tanpa nilai pemeriksaan.</li>
                <li>Angka gabungan yang berjumlah 1–4 orang ditampilkan sebagai <strong>“&lt; 5”</strong> agar tidak ada warga yang dapat dikenali.</li>
              </ul>
            </Section>

            <Section id="ai" title="Tanya SEHATI (AI)" icon="sparkles">
              <p>
                Tanya SEHATI memiliki kumpulan tanya-jawab yang bekerja tanpa internet. Bila Anda memakai jawaban berbasis AI saat online,
                pertanyaan Anda dikirim melalui server SEHATI ke penyedia layanan AI untuk dijawab.
              </p>
              <ul>
                <li>Isi percakapan <strong>tidak disimpan</strong> di server dan tidak dicatat di log; server hanya menghitung jumlah pemakaian per hari.</li>
                <li>Data Anda (mis. kelompok umur atau faktor risiko) hanya disertakan bila Anda mengaktifkan
                  “Gunakan data saya untuk jawaban yang lebih sesuai”, dan <strong>tanpa identitas</strong>.</li>
                <li>Bila pertanyaan menyebut gejala darurat, server tidak meneruskannya ke AI dan langsung menampilkan anjuran menghubungi
                  {' '}<strong>{site.emergencyNumbers.join(' atau ')}</strong>.</li>
                <li>Jawaban AI bersifat edukasi umum, bukan nasihat medis pribadi.</li>
              </ul>
            </Section>

            <Section id="hak" title="Hak Anda" icon="shield">
              <ul>
                <li><strong>Melihat dan memperbaiki</strong> data Anda di aplikasi, atau meminta bantuan kader.</li>
                <li><strong>Mencabut persetujuan</strong> sinkronisasi kapan saja; data berikutnya tidak lagi dikirim.</li>
                <li><strong>Menghapus data</strong>: pilih <strong>Profil → Hapus data saya</strong>. Bila tersinkron, server menghapus seluruh data
                  kesehatan dan akun Anda. Penghapusan dicatat di log audit tanpa isi data.</li>
                <li><strong>Bertanya atau mengajukan keberatan</strong> melalui kontak di bawah.</li>
              </ul>
            </Section>

            <Section id="keamanan" title="Keamanan" icon="lock">
              <ul>
                <li>Koneksi ke server selalu terenkripsi (HTTPS).</li>
                <li>Token masuk bersifat acak; server hanya menyimpan sidiknya (hash). Sesi staf berakhir otomatis setelah 12 jam.</li>
                <li>Akun dikunci sementara setelah beberapa kali gagal masuk.</li>
                <li>Server tidak mencatat isi data kesehatan, kata sandi, token, atau pesan AI ke log.</li>
                <li>Perubahan penting (akun, penugasan, ambang klinis, penghapusan data) dicatat di log audit, dan data dicadangkan setiap hari.</li>
              </ul>
            </Section>

            <Section id="kontak" title="Kontak" icon="message">
              <p>Pertanyaan tentang privasi atau permintaan penghapusan data dapat disampaikan kepada:</p>
              <div className="mt-2 grid gap-3 rounded-2xl border border-line bg-surface p-5 sm:grid-cols-3">
                <div><p className="text-xs font-semibold text-muted">Alamat</p><p className="mt-0.5 text-sm text-fg">{site.contact.address}, {site.village}</p></div>
                <div><p className="text-xs font-semibold text-muted">Surel</p><a className="mt-0.5 block text-sm font-medium text-accent hover:underline" href={`mailto:${site.contact.email}`}>{site.contact.email}</a></div>
                <div><p className="text-xs font-semibold text-muted">Telepon</p><a className="mt-0.5 block text-sm font-medium text-accent hover:underline" href={`tel:${site.contact.phone.replace(/\D/g, '')}`}>{site.contact.phone}</a></div>
              </div>
              <p className="text-sm text-muted">Kebijakan ini dapat diperbarui. Perubahan penting akan diberitahukan melalui aplikasi atau kader Posyandu.</p>
            </Section>
          </article>
        </div>
      </div>
    </PublicShell>
  )
}
