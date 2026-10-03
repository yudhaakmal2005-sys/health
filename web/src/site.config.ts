/**
 * Teks yang dapat diubah tanpa menyentuh komponen. Sunting nilai di bawah ini
 * (nama desa, kontak, nama tim KKN), lalu build ulang.
 */
export const site = {
  appName: 'SEHATI',
  tagline: 'Sistem Edukasi & Pemantauan Kesehatan Komunitas',
  village: 'Desa Mirigambar',
  district: '[Kecamatan], [Kabupaten]',
  puskesmas: 'Puskesmas setempat',
  contact: {
    email: 'sehati.mirigambar@example.id',
    phone: '0812-0000-0000',
    whatsapp: '6281200000000',
    address: 'Balai Desa Mirigambar',
  },
  kkn: {
    team: 'Tim KKN [Nama Kelompok]',
    university: '[Nama Universitas]',
    year: '2026',
    credit: 'Dikembangkan bersama warga dan kader Posyandu oleh',
  },
  apk: {
    url: '/download/sehati.apk',
    version: '2.0.0',
    minAndroid: 'Android 8.0 ke atas',
  },
  emergencyNumbers: ['119', '112'],
  privacyUpdated: '3 Oktober 2026',
} as const
