package id.sehati.app.domain.content

import java.time.LocalDate

data class DailyFact(val statement: String, val isTrue: Boolean, val explanation: String)

/** Fakta atau mitos harian tentang jantung: satu pertanyaan per hari, pendek, dengan penjelasan. */
object DailyFacts {
    val all: List<DailyFact> = listOf(
        DailyFact("Tekanan darah tinggi biasanya selalu menimbulkan keluhan.", false, "Sering tanpa gejala. Satu-satunya cara tahu adalah mengukurnya."),
        DailyFact("Berjalan kaki rutin baik untuk jantung.", true, "Aktivitas sedang seperti jalan cepat membantu jantung dan pembuluh darah."),
        DailyFact("Serangan jantung hanya terjadi pada orang tua.", false, "Risikonya naik seiring usia, tetapi dapat terjadi lebih muda terutama pada perokok dan penderita tekanan darah tinggi."),
        DailyFact("Berhenti merokok menurunkan risiko penyakit jantung, kapan pun dimulai.", true, "Manfaatnya mulai terasa dalam hitungan minggu hingga bulan."),
        DailyFact("Nyeri dada yang berlangsung lebih dari beberapa menit perlu segera dicek.", true, "Jangan menunggu hilang sendiri; cari pertolongan medis."),
        DailyFact("Kolesterol tinggi pasti terasa di badan.", false, "Biasanya tanpa gejala; hanya terlihat lewat pemeriksaan darah."),
        DailyFact("Mengurangi garam membantu menjaga tekanan darah.", true, "Natrium berlebih membuat tekanan darah cenderung naik."),
        DailyFact("Makanan gurih tanpa garam meja berarti pasti rendah garam.", false, "Mi instan, kecap, saus, ikan asin dan makanan olahan banyak mengandung natrium tersembunyi."),
        DailyFact("Gorengan sesekali dan banyak sayur tetap bermanfaat untuk jantung.", true, "Yang penting pola keseluruhan: batasi gorengan, perbanyak sayur, buah, dan biji-bijian."),
        DailyFact("Kurang tidur terus-menerus dapat meningkatkan risiko tekanan darah tinggi.", true, "Tidur 7–8 jam yang teratur membantu tubuh memulihkan diri."),
        DailyFact("Rokok elektrik sepenuhnya aman bagi jantung.", false, "Rokok elektrik tetap mengandung zat yang dapat membebani jantung dan pembuluh darah."),
        DailyFact("Stres berkepanjangan dapat memengaruhi tekanan darah dan kebiasaan hidup.", true, "Latihan napas, bergerak, dan bercerita dengan orang terdekat membantu."),
        DailyFact("Jika tensi sudah normal, obat boleh dihentikan sendiri.", false, "Tensi normal bisa jadi karena obat bekerja. Jangan berhenti tanpa arahan dokter."),
        DailyFact("Lingkar perut besar berkaitan dengan risiko jantung.", true, "Lemak di perut berhubungan dengan tekanan darah dan gula darah yang lebih tinggi."),
        DailyFact("Diabetes tidak ada hubungannya dengan jantung.", false, "Gula darah tinggi jangka panjang dapat merusak pembuluh darah dan meningkatkan risiko jantung."),
        DailyFact("Kamu perlu olahraga berat agar jantung sehat.", false, "Aktivitas sedang seperti jalan cepat 150 menit per minggu sudah sangat membantu."),
        DailyFact("Riwayat keluarga jantung berarti kamu pasti terkena.", false, "Risiko lebih tinggi, tetapi gaya hidup sehat dan kontrol rutin tetap menurunkannya."),
        DailyFact("Buah dan sayur membantu menjaga tekanan darah dan kolesterol.", true, "Serat dan kalium membantu; usahakan sayur di setiap makan."),
        DailyFact("Keringat dingin dan sesak mendadak bersama nyeri dada bisa jadi tanda bahaya.", true, "Segera hubungi 119/112 atau ke IGD terdekat."),
        DailyFact("Minuman manis kemasan tidak berpengaruh pada berat badan dan gula darah.", false, "Gula tambahan menaikkan kalori dan gula darah dengan cepat."),
        DailyFact("Mengukur tekanan darah secara berkala itu penting walau merasa sehat.", true, "Cek setiap 1–3 bulan, terutama bila usia di atas 40 atau ada riwayat keluarga."),
        DailyFact("Duduk berjam-jam tanpa bergerak tidak berpengaruh pada jantung.", false, "Berdiri dan bergerak ringan tiap satu jam membantu."),
        DailyFact("Ikan, tempe, dan tahu adalah pilihan protein yang ramah jantung.", true, "Pilih dipanggang, dikukus, atau direbus, bukan digoreng."),
        DailyFact("Perempuan tidak perlu khawatir soal serangan jantung.", false, "Penyakit jantung juga penyebab kematian penting pada perempuan; gejalanya kadang tidak khas."),
        DailyFact("Asap rokok orang lain (perokok pasif) juga merugikan jantung.", true, "Hindari ruangan berasap dan jadikan rumah bebas rokok."),
    )

    fun forDay(day: LocalDate): DailyFact = all[Math.floorMod(day.toEpochDay(), all.size.toLong()).toInt()]
}
