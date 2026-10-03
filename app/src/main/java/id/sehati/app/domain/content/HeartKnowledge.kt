package id.sehati.app.domain.content

data class FaqEntry(val id: String, val question: String, val answer: String, val keywords: List<String>, val moduleId: String? = null, val emergency: Boolean = false)

data class FaqAnswer(val entry: FaqEntry?, val related: List<FaqEntry>)

/** Tanya SEHATI: basis pengetahuan jantung koroner yang berjalan offline. Edukasi umum, bukan diagnosis. */
object HeartKnowledge {
    const val EMERGENCY_NUMBERS = "119 atau 112"
    const val DISCLAIMER = "Informasi umum untuk edukasi, bukan diagnosis atau pengganti tenaga kesehatan."

    val faq: List<FaqEntry> = listOf(
        FaqEntry("pjk", "Apa itu penyakit jantung koroner?", "Penyakit jantung koroner (PJK) terjadi saat pembuluh darah yang memberi makan otot jantung menyempit karena penumpukan lemak (plak). Aliran darah ke jantung berkurang, dan bila tersumbat dapat terjadi serangan jantung.", listOf("jantung koroner", "pjk", "apa itu jantung", "koroner", "penyempitan"), "faktor_pjk"),
        FaqEntry("tanda", "Apa tanda bahaya serangan jantung?", "Nyeri atau rasa tertekan/berat di dada lebih dari beberapa menit, bisa menjalar ke lengan kiri, rahang, leher, atau punggung; sesak, keringat dingin, mual, lemas mendadak. Segera hubungi $EMERGENCY_NUMBERS atau ke IGD terdekat. Jangan mengendarai sendiri.", listOf("tanda serangan", "gejala serangan", "gejala jantung", "tanda jantung", "serangan jantung"), "serangan", true),
        FaqEntry("langkah", "Apa yang harus dilakukan saat ada yang diduga serangan jantung?", "1) Hubungi $EMERGENCY_NUMBERS. 2) Dudukkan dan tenangkan, longgarkan pakaian. 3) Bila tidak alergi dan tidak diarahkan lain oleh dokter, aspirin dapat dikunyah sesuai arahan petugas. 4) Jangan membiarkan orang itu berjalan jauh. 5) Bila pingsan dan tidak bernapas, mulai pijat jantung bila kamu terlatih.", listOf("pertolongan pertama", "harus dilakukan", "serangan jantung", "tolong"), "serangan", true),
        FaqEntry("risiko", "Apa faktor risiko jantung koroner?", "Yang dapat diubah: merokok, tekanan darah tinggi, gula darah tinggi, kolesterol tinggi, obesitas, kurang bergerak, pola makan tinggi garam dan lemak jenuh, stres. Yang tidak dapat diubah: usia dan riwayat keluarga.", listOf("faktor risiko", "penyebab", "risiko jantung"), "faktor_pjk"),
        FaqEntry("tensi", "Berapa tekanan darah yang dianggap tinggi?", "Di SEHATI, tekanan darah di atas ambang tertentu ditandai untuk diperiksa ulang. Nilai pasti dan diagnosis ditentukan tenaga kesehatan dari beberapa kali pengukuran. Ukur saat duduk tenang 5 menit.", listOf("tensi", "tekanan darah", "hipertensi", "darah tinggi"), "hipertensi"),
        FaqEntry("garam", "Berapa batas garam per hari?", "Anjuran umum sekitar 1 sendok teh garam (5 gram, setara ±2.000 mg natrium) per hari termasuk dari makanan olahan, mi instan, kecap, dan ikan asin. Batas yang dipakai di aplikasi dapat diatur Puskesmas.", listOf("garam", "natrium", "asin", "kecap"), "garam"),
        FaqEntry("kolesterol", "Apa itu kolesterol dan kapan perlu diperiksa?", "Kolesterol adalah lemak darah. Kadar tinggi tidak terasa gejalanya tetapi menambah risiko plak di pembuluh darah. Periksa bila tersedia di Posyandu/Puskesmas, terutama bila usia di atas 40, merokok, atau ada riwayat keluarga.", listOf("kolesterol", "lemak darah", "ldl"), "kolesterol"),
        FaqEntry("rokok", "Bagaimana cara mulai berhenti merokok?", "Tentukan tanggal berhenti, buang rokok dan korek, tunda rokok pertama 30 menit lalu perpanjang, ganti kebiasaan dengan jalan atau mengunyah permen tanpa gula, minta dukungan keluarga. Tanyakan layanan berhenti merokok di Puskesmas.", listOf("berhenti merokok", "rokok", "merokok", "stop rokok"), "rokok"),
        FaqEntry("olahraga", "Olahraga apa yang aman untuk jantung?", "Jalan cepat, bersepeda santai, senam ringan 150 menit per minggu (misalnya 30 menit, 5 hari). Mulai pelan, boleh sambil bercakap. Berhenti dan periksa bila nyeri dada, sesak berat, atau pusing.", listOf("olahraga", "latihan", "senam", "jalan kaki", "aktivitas"), "aktivitas"),
        FaqEntry("makan", "Makanan apa yang baik untuk jantung?", "Sayur, buah, ikan, tempe, tahu, kacang, biji-bijian utuh. Batasi gorengan, santan kental, jeroan, makanan asin, dan minuman manis.", listOf("makanan sehat", "diet", "makan", "menu"), "makanan"),
        FaqEntry("gorengan", "Apakah gorengan dilarang total?", "Tidak harus dilarang total, tetapi batasi. Pilih dipanggang, dikukus, atau direbus. Hindari minyak yang dipakai berulang-ulang.", listOf("gorengan", "goreng", "minyak"), "makanan"),
        FaqEntry("kopi", "Bolehkah minum kopi?", "Umumnya boleh dalam jumlah wajar. Hindari banyak gula dan krimer. Bila tekanan darahmu tinggi, kurangi kopi dan tanyakan ke tenaga kesehatan.", listOf("kopi", "kafein"), null),
        FaqEntry("diabetes", "Apa hubungan diabetes dengan jantung?", "Gula darah tinggi jangka panjang merusak dinding pembuluh darah sehingga risiko jantung koroner meningkat. Menjaga gula darah juga menjaga jantung.", listOf("diabetes", "gula darah", "kencing manis"), "diabetes"),
        FaqEntry("obat", "Bolehkah saya berhenti minum obat jika sudah merasa sehat?", "Jangan berhenti atau mengubah dosis obat tanpa arahan dokter. Bila ada efek samping atau obat habis, hubungi Puskesmas atau dokter.", listOf("obat", "dosis", "berhenti obat", "minum obat")),
        FaqEntry("stres", "Bagaimana mengelola stres untuk jantung?", "Napas dalam 4-4-6, jalan santai, tidur teratur, berbagi cerita, dan membatasi kafein. Bila stres berat berlangsung lama, bicarakan dengan tenaga kesehatan.", listOf("stres", "cemas", "kecemasan", "tegang"), "stres"),
        FaqEntry("tidur", "Berapa jam tidur yang ideal?", "Sekitar 7–8 jam untuk kebanyakan dewasa, dengan jam tidur teratur. Kurang tidur kronis berkaitan dengan tekanan darah tinggi.", listOf("tidur", "begadang", "insomnia"), "tidur"),
        FaqEntry("cek", "Seberapa sering sebaiknya cek tekanan darah?", "Setiap 1–3 bulan bila sehat dan usia dewasa; lebih sering bila tinggi atau ada obat. Datanglah ke Posyandu atau Puskesmas.", listOf("seberapa sering", "cek rutin", "kontrol", "pemeriksaan")),
        FaqEntry("posyandu", "Kenapa Posyandu penting untuk jantung?", "Posyandu dewasa/lansia memeriksa tekanan darah, berat, gula darah, dan lingkar perut secara rutin sehingga faktor risiko ketahuan lebih dini dan bisa ditindaklanjuti ke Puskesmas.", listOf("posyandu", "kader", "puskesmas"), null),
        FaqEntry("muda", "Apakah anak muda bisa terkena serangan jantung?", "Bisa, terutama pada perokok, penderita tekanan darah tinggi, diabetes, atau riwayat keluarga. Jangan abaikan nyeri dada walau masih muda.", listOf("anak muda", "usia muda", "masih muda")),
        FaqEntry("perempuan", "Apakah gejala serangan jantung pada perempuan berbeda?", "Kadang tidak khas: sesak, lemas, mual, nyeri punggung atau rahang tanpa nyeri dada yang jelas. Tetap waspada dan cari pertolongan bila mendadak.", listOf("perempuan", "wanita", "ibu"), "serangan"),
        FaqEntry("mitos", "Apakah sakit jantung bisa disembuhkan dengan herbal saja?", "Tidak ada bukti bahwa herbal saja dapat menggantikan terapi jantung. Bicarakan semua jamu/suplemen dengan dokter karena sebagian berinteraksi dengan obat.", listOf("herbal", "jamu", "suplemen", "tradisional"), "mitos"),
        FaqEntry("lemak", "Apa bedanya lemak baik dan lemak jahat?", "Lemak jenuh dan trans (gorengan, santan kental, jeroan, makanan olahan) menaikkan kolesterol jahat. Lemak tak jenuh (ikan, kacang, alpukat, minyak zaitun) lebih ramah jantung.", listOf("lemak baik", "lemak jahat", "lemak jenuh", "ldl hdl"), "kolesterol"),
        FaqEntry("berat", "Berapa berat badan ideal?", "Cek IMT dan lingkar perutmu di SEHATI. Penurunan 5% berat badan sudah memberi manfaat. Hindari diet ekstrem.", listOf("berat badan", "ideal", "imt", "lingkar perut", "obesitas", "gemuk"), "obesitas"),
        FaqEntry("sesak", "Saya sering sesak saat naik tangga, apakah berbahaya?", "Sesak saat aktivitas ringan yang baru muncul atau memburuk perlu diperiksa tenaga kesehatan. Segera ke IGD bila sesak berat atau disertai nyeri dada.", listOf("sesak", "napas pendek", "cepat lelah"), "serangan"),
        FaqEntry("bengkak", "Kaki bengkak, apakah tanda jantung?", "Bengkak kaki punya banyak penyebab, termasuk jantung, ginjal, atau pembuluh darah. Periksakan ke Puskesmas, terutama bila disertai sesak.", listOf("bengkak", "kaki bengkak")),
        FaqEntry("anak", "Bagaimana mengajak keluarga hidup sehat jantung?", "Masak bersama dengan garam lebih sedikit, jalan kaki bersama tiap sore, jadikan rumah bebas rokok, dan ajak keluarga cek tensi di Posyandu.", listOf("keluarga", "rumah tangga"), "aktivitas"),
        FaqEntry("aspirin", "Perlukah minum aspirin untuk mencegah serangan jantung?", "Jangan minum aspirin rutin tanpa anjuran dokter karena dapat menimbulkan perdarahan. Tanyakan dokter.", listOf("aspirin", "pengencer darah")),
    )

    private val symptomWords = listOf("nyeri dada", "dada sakit", "dada sesak", "dada tertekan", "dada berat", "sesak napas", "keringat dingin", "pingsan", "nyeri lengan", "menjalar")

    fun isEmergencyText(text: String): Boolean {
        val q = text.lowercase()
        return symptomWords.count { q.contains(it) } >= 1 && (q.contains("sekarang") || q.contains("mendadak") || q.contains("sedang") || q.contains("barusan") || q.contains("keringat") || q.contains("pingsan") || q.contains("menjalar") || q.contains("nyeri dada") || q.contains("dada sakit"))
    }

    private val personal = Regex("\\b(saya|aku|sedang|sekarang|barusan|mendadak|ayah|ibu|bapak|suami|istri|kakek|nenek)\\b")

    /** Pertanyaan edukasi umum ("apa tanda...") berbeda dari laporan gejala yang sedang dialami. */
    fun isGeneralQuestion(text: String): Boolean = !personal.containsMatchIn(text.lowercase())

    fun answer(question: String): FaqAnswer {
        val q = question.lowercase().trim()
        if (q.isBlank()) return FaqAnswer(null, faq.take(5))
        val scored = faq.map { e ->
            var s = 0
            e.keywords.forEach { k -> if (q.contains(k)) s += 3 + k.length / 6 }
            q.split(Regex("\\W+")).filter { it.length > 3 }.forEach { w -> if (e.question.lowercase().contains(w)) s += 1 }
            e to s
        }.filter { it.second > 0 }.sortedByDescending { it.second }
        return FaqAnswer(scored.firstOrNull()?.first, scored.drop(1).take(3).map { it.first })
    }

    val suggestedQuestions = listOf("Apa tanda bahaya serangan jantung?", "Berapa batas garam per hari?", "Cara berhenti merokok", "Olahraga apa yang aman?", "Faktor risiko jantung koroner")
}
