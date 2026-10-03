package id.sehati.app.domain.content

data class QuizQuestion(val question: String, val options: List<String>, val correctIndex: Int, val explanation: String)

data class EducationModule(
    val id: String,
    val category: String,
    val title: String,
    val minutes: Int,
    val summary: String,
    val paragraphs: List<String>,
    val quiz: List<QuizQuestion>,
    val challenge: String,
)

object Academy {
    val categories = listOf("Jantung", "Hipertensi", "Diabetes", "Rokok", "Aktivitas", "Makanan", "Tidur", "Stres", "Obesitas")

    val modules: List<EducationModule> = listOf(
        EducationModule(
            "hipertensi", "Hipertensi", "Mengenal tekanan darah tinggi", 2,
            "Tekanan darah tinggi sering tanpa gejala, sehingga pemeriksaan rutin penting.",
            listOf(
                "Tekanan darah ditulis dua angka, misalnya 120/80 mmHg. Angka atas (sistolik) adalah tekanan saat jantung memompa, angka bawah (diastolik) saat jantung beristirahat.",
                "Tekanan darah tinggi sering tidak menimbulkan keluhan. Karena itu ia disebut 'silent killer' dan baru terasa setelah menyebabkan masalah pada jantung, otak, atau ginjal.",
                "Satu kali hasil tinggi belum berarti Anda menderita hipertensi. Tekanan darah dipengaruhi stres, kopi, rokok, dan cara mengukur. Tenaga kesehatan akan mengukur ulang pada waktu berbeda.",
                "Yang dapat membantu: kurangi garam dan makanan olahan, perbanyak sayur dan buah, tetap aktif, jaga berat badan, hindari rokok, dan kontrol rutin di Posyandu atau Puskesmas.",
            ),
            listOf(
                QuizQuestion("Apa arti angka pertama (atas) pada tekanan darah?", listOf("Tekanan saat jantung memompa", "Tekanan saat jantung istirahat", "Denyut nadi"), 0, "Angka atas disebut sistolik: tekanan saat jantung memompa darah."),
                QuizQuestion("Apakah satu hasil tinggi pasti berarti hipertensi?", listOf("Ya, pasti", "Tidak, perlu diukur ulang dan dinilai tenaga kesehatan"), 1, "Pengukuran perlu dikonfirmasi pada waktu yang berbeda."),
                QuizQuestion("Mana kebiasaan yang membantu menjaga tekanan darah?", listOf("Banyak makanan asin", "Mengurangi garam dan aktif bergerak", "Begadang rutin"), 1, "Kurangi garam dan aktif bergerak membantu menjaga tekanan darah."),
            ),
            "Hari ini, pilih satu makanan asin yang akan kamu kurangi.",
        ),
        EducationModule(
            "serangan", "Jantung", "Tanda bahaya serangan jantung", 3,
            "Mengenali tanda bahaya dan tahu langkah pertama dapat menyelamatkan nyawa.",
            listOf(
                "Tanda yang paling umum: nyeri, rasa tertekan, atau berat di dada selama beberapa menit; dapat menjalar ke lengan kiri, rahang, leher, atau punggung.",
                "Tanda lain: sesak napas, keringat dingin, mual, pusing, atau lemas mendadak. Pada perempuan dan lansia, tanda bisa tidak khas.",
                "Langkah pertama: hentikan aktivitas, dudukkan, dan segera hubungi 119 atau 112 atau bawa ke IGD terdekat. Jangan menunggu hilang sendiri, jangan berkendara sendiri.",
                "Bila ada alat pijat jantung/AED di dekatmu dan kamu terlatih, mulai bantuan hidup dasar saat orang tidak sadar dan tidak bernapas.",
            ),
            listOf(
                QuizQuestion("Apa yang dilakukan pertama saat dicurigai serangan jantung?", listOf("Menunggu sampai hilang", "Segera hubungi 119/112 atau ke IGD", "Tidur dulu"), 1, "Waktu sangat berharga; semakin cepat ditangani, semakin baik."),
                QuizQuestion("Apakah nyeri dada selalu terasa seperti ditusuk?", listOf("Ya", "Tidak, bisa berupa rasa tertekan atau berat"), 1, "Rasa tertekan/berat juga merupakan tanda yang umum."),
            ),
            "Simpan nomor 119 dan 112, dan beri tahu keluargamu tanda bahaya ini.",
        ),
        EducationModule(
            "faktor_pjk", "Jantung", "Faktor risiko jantung koroner", 3,
            "Sebagian besar faktor risiko jantung koroner dapat kita ubah.",
            listOf(
                "Jantung koroner terjadi saat pembuluh yang memberi makan jantung menyempit karena plak lemak. Prosesnya berlangsung pelan selama bertahun-tahun.",
                "Faktor yang dapat diubah: merokok, tekanan darah tinggi, gula darah tinggi, kolesterol tinggi, berat badan berlebih, kurang bergerak, pola makan tidak sehat, dan stres.",
                "Faktor yang tidak dapat diubah: usia dan riwayat keluarga. Bila ada, pemeriksaan rutin menjadi lebih penting.",
                "SEHATI menghitung berapa faktor yang terdeteksi dan menunjukkan langkah paling mudah untuk memperbaikinya. Ini bukan diagnosis.",
            ),
            listOf(QuizQuestion("Mana faktor risiko yang dapat diubah?", listOf("Usia", "Merokok dan tekanan darah", "Riwayat keluarga"), 1, "Merokok dan tekanan darah dapat dikendalikan.")),
            "Buka kartu Faktor Risiko Jantung di beranda dan pilih satu faktor untuk diperbaiki.",
        ),
        EducationModule(
            "kolesterol", "Jantung", "Kolesterol dan lemak darah", 2,
            "Kolesterol tinggi tidak terasa, tetapi menambah risiko plak di pembuluh darah.",
            listOf(
                "Kolesterol adalah lemak darah. Kolesterol LDL ('jahat') berlebih menumpuk di dinding pembuluh darah, sedangkan HDL ('baik') membantu membersihkannya.",
                "Kolesterol tinggi biasanya tanpa gejala sehingga hanya diketahui lewat pemeriksaan darah.",
                "Yang membantu: kurangi gorengan, santan kental, jeroan, dan lemak jenuh; tambah sayur, buah, ikan, tempe, kacang; tetap aktif; berhenti merokok.",
            ),
            listOf(QuizQuestion("Bagaimana mengetahui kolesterol tinggi?", listOf("Dari rasa pusing", "Dari pemeriksaan darah", "Dari bentuk badan"), 1, "Kolesterol hanya terdeteksi lewat pemeriksaan darah.")),
            "Ganti satu gorengan hari ini dengan versi rebus atau panggang.",
        ),
        EducationModule(
            "garam", "Makanan", "Garam tersembunyi dan cara menguranginya", 2,
            "Natrium berlebih membuat tekanan darah naik. Banyak garam ada di makanan olahan.",
            listOf(
                "Anjuran umum sekitar 1 sendok teh garam per hari termasuk dari makanan olahan. Satu bungkus mi instan dengan bumbu bisa menyumbang hampir seluruh batas harian.",
                "Garam tersembunyi: kecap, saus, kerupuk, ikan asin, sosis, bumbu penyedap, dan makanan kemasan. Baca label dan pilih natrium lebih rendah.",
                "Tips: kurangi garam pelan-pelan agar lidah terbiasa, pakai bawang, jahe, jeruk nipis, dan rempah, jangan taruh garam di meja makan.",
            ),
            listOf(QuizQuestion("Mana sumber garam tersembunyi?", listOf("Air putih", "Mi instan dan kecap", "Apel"), 1, "Makanan olahan dan penyedap banyak mengandung natrium.")),
            "Hari ini, jangan tambah garam atau kecap pada makananmu.",
        ),
        EducationModule(
            "mitos", "Jantung", "Mitos dan fakta tentang jantung", 2,
            "Meluruskan anggapan yang sering salah.",
            listOf(
                "Mitos: serangan jantung hanya pada orang tua. Fakta: dapat terjadi lebih muda, terutama pada perokok dan penderita tekanan darah tinggi atau diabetes.",
                "Mitos: tidak ada keluhan berarti sehat. Fakta: tekanan darah, gula darah, dan kolesterol tinggi sering tanpa gejala.",
                "Mitos: obat boleh dihentikan bila sudah merasa baik. Fakta: jangan berhenti tanpa arahan dokter.",
                "Mitos: herbal saja cukup. Fakta: jamu/suplemen sebaiknya didiskusikan dengan dokter, tidak menggantikan terapi.",
            ),
            listOf(QuizQuestion("Tekanan darah tinggi biasanya…", listOf("Selalu terasa", "Sering tanpa gejala"), 1, "Karena itu perlu diukur berkala.")),
            "Ceritakan satu fakta jantung hari ini kepada keluarga atau tetangga.",
        ),
        EducationModule(
            "diabetes", "Diabetes", "Gula darah dan cara menjaganya", 2,
            "Gula darah yang terlalu tinggi dalam jangka panjang dapat merusak organ tubuh.",
            listOf(
                "Tubuh memakai gula (glukosa) sebagai energi. Hormon insulin membantu gula masuk ke sel. Bila insulin kurang atau tidak bekerja baik, gula menumpuk di darah.",
                "Pemeriksaan gula darah di Posyandu adalah skrining. Hasil di atas rentang normal berarti perlu pemeriksaan lanjutan oleh tenaga kesehatan, bukan langsung berarti diabetes.",
                "Gejala yang perlu diwaspadai: sering haus, sering buang air kecil, mudah lapar, lemas, luka sulit sembuh. Bila ada, periksakan ke Puskesmas.",
                "Kebiasaan yang membantu: batasi minuman manis, pilih nasi porsi wajar dengan banyak sayur, jalan kaki rutin, dan jaga berat badan.",
            ),
            listOf(
                QuizQuestion("Hasil gula darah tinggi saat skrining berarti…", listOf("Pasti diabetes", "Perlu pemeriksaan lanjutan", "Tidak perlu diapa-apakan"), 1, "Skrining bukan diagnosis; perlu pemeriksaan lanjutan."),
                QuizQuestion("Minuman mana yang sebaiknya dibatasi?", listOf("Air putih", "Teh manis dan minuman kemasan manis", "Teh tawar"), 1, "Gula tambahan pada minuman cepat menaikkan gula darah."),
            ),
            "Ganti satu minuman manis hari ini dengan air putih.",
        ),
        EducationModule(
            "jantung", "Jantung", "Menjaga jantung sejak dini", 2,
            "Risiko penyakit jantung dipengaruhi banyak hal, sebagian besar dapat kita ubah.",
            listOf(
                "Faktor yang dapat diubah: merokok, kurang aktivitas, pola makan tinggi garam dan lemak jenuh, berat badan berlebih, tekanan darah dan gula darah yang tidak terkontrol.",
                "Faktor yang tidak dapat diubah: usia dan riwayat keluarga. Bila ada riwayat keluarga, pemantauan rutin menjadi lebih penting.",
                "Tanda bahaya: nyeri atau rasa tertekan di dada, sesak berat, nyeri menjalar ke lengan atau rahang, keringat dingin, pingsan. Segera cari pertolongan medis, jangan menunggu.",
            ),
            listOf(
                QuizQuestion("Mana yang termasuk faktor risiko yang dapat diubah?", listOf("Usia", "Merokok", "Riwayat keluarga"), 1, "Merokok dapat dihentikan sehingga risiko turun."),
                QuizQuestion("Apa yang dilakukan bila nyeri dada hebat dan sesak?", listOf("Menunggu besok", "Segera cari pertolongan medis", "Minum kopi"), 1, "Gejala akut perlu penanganan medis segera."),
            ),
            "Catat satu kebiasaan sehat jantung yang ingin kamu mulai minggu ini.",
        ),
        EducationModule(
            "rokok", "Rokok", "Mengurangi dan berhenti merokok", 2,
            "Berhenti merokok adalah salah satu langkah terbaik untuk jantung dan paru.",
            listOf(
                "Mengurangi dan berhenti merokok membantu menurunkan risiko penyakit kardiovaskular. Manfaat mulai dirasakan segera setelah berhenti dan bertambah seiring waktu.",
                "Mulailah dengan mengenali pemicu: kopi pagi, setelah makan, saat berkumpul. Siapkan pengganti: minum air, mengunyah permen tanpa gula, berjalan sebentar.",
                "Tentukan tanggal berhenti, beri tahu keluarga atau teman, dan kurangi jumlah batang secara bertahap. Tanyakan layanan berhenti merokok di Puskesmas.",
            ),
            listOf(
                QuizQuestion("Langkah awal yang baik untuk mengurangi rokok adalah…", listOf("Mengenali pemicu merokok", "Menambah jumlah batang", "Merokok sambil olahraga"), 0, "Mengenali pemicu membantu menyiapkan pengganti."),
            ),
            "Tunda rokok pertamamu hari ini 30 menit lebih lama dari biasanya.",
        ),
        EducationModule(
            "aktivitas", "Aktivitas", "Bergerak sedikit demi sedikit", 2,
            "Aktivitas fisik teratur menurunkan risiko banyak penyakit tidak menular.",
            listOf(
                "Secara umum orang dewasa dianjurkan aktif sekitar 150 menit per minggu dengan intensitas sedang, misalnya jalan cepat. Mulai dari kecil lebih baik daripada tidak sama sekali.",
                "Berjalan santai 10–15 menit setelah makan atau sore hari sudah bermanfaat. Tambahkan durasi perlahan sesuai kemampuan.",
                "Berkendara bukan aktivitas fisik. Pilih berjalan untuk jarak dekat bila aman. Selingi duduk lama dengan berdiri atau peregangan.",
                "Bila memiliki penyakit jantung atau keluhan saat bergerak, konsultasikan dahulu dengan tenaga kesehatan.",
            ),
            listOf(
                QuizQuestion("Apakah naik motor dihitung sebagai aktivitas fisik?", listOf("Ya", "Tidak, itu transportasi pasif"), 1, "Aktivitas fisik membutuhkan gerak tubuh Anda sendiri."),
                QuizQuestion("Cara memulai yang aman adalah…", listOf("Langsung lari jauh", "Mulai ringan dan tambah bertahap", "Tidak usah bergerak"), 1, "Tambah bertahap agar aman dan konsisten."),
            ),
            "Jalan santai 10–15 menit hari ini.",
        ),
        EducationModule(
            "makanan", "Makanan", "Pola makan seimbang dan rendah garam", 2,
            "Sayur, buah, dan pengurangan garam membantu tekanan darah dan berat badan.",
            listOf(
                "Isi separuh piring dengan sayur dan buah, seperempat dengan sumber protein (tempe, tahu, ikan, telur), seperempat dengan karbohidrat.",
                "Garam tersembunyi ada pada mi instan, kerupuk, ikan asin, saus, kecap, dan makanan kemasan. Biasakan membaca label pangan dan kurangi garam meja.",
                "Batasi gorengan dan minuman manis. Pilih merebus, mengukus, atau memanggang.",
            ),
            listOf(
                QuizQuestion("Mana sumber garam tersembunyi?", listOf("Mi instan dan kerupuk", "Air putih", "Pepaya"), 0, "Makanan olahan sering tinggi natrium."),
                QuizQuestion("Cara masak yang lebih baik untuk jantung?", listOf("Digoreng", "Direbus atau dikukus", "Dibakar gosong"), 1, "Merebus/mengukus mengurangi lemak tambahan."),
            ),
            "Tambahkan satu porsi sayur pada makan siang hari ini.",
        ),
        EducationModule(
            "tidur", "Tidur", "Tidur cukup untuk tubuh sehat", 2,
            "Tidur yang kurang berkaitan dengan tekanan darah, berat badan, dan suasana hati.",
            listOf(
                "Kebanyakan orang dewasa membutuhkan sekitar 7–9 jam tidur per malam, meski kebutuhan tiap orang berbeda.",
                "Jam tidur teratur, kamar gelap dan tenang, serta menjauhi layar dan kopi menjelang tidur membantu kualitas tidur.",
                "Bila sering mendengkur keras, berhenti napas saat tidur, atau sangat mengantuk di siang hari, konsultasikan ke tenaga kesehatan.",
            ),
            listOf(QuizQuestion("Kebiasaan yang membantu tidur adalah…", listOf("Jam tidur teratur", "Kopi sebelum tidur", "Main ponsel di tempat tidur"), 0, "Keteraturan jadwal membantu ritme tubuh.")),
            "Tentukan jam tidur malam ini dan patuhi.",
        ),
        EducationModule(
            "stres", "Stres", "Mengelola stres sehari-hari", 2,
            "Stres berkepanjangan dapat memengaruhi tidur, makan, dan tekanan darah.",
            listOf(
                "Stres adalah respons normal, namun yang berlangsung lama perlu dikelola. Kenali tandanya: sulit tidur, mudah marah, sakit kepala, nafsu makan berubah.",
                "Teknik sederhana: napas dalam 4-4-6 selama 2 menit, jalan kaki, berbincang dengan orang tepercaya, dan membatasi kafein.",
                "Bila perasaan tertekan berat atau berkepanjangan, mintalah bantuan tenaga kesehatan atau layanan kesehatan jiwa di Puskesmas.",
            ),
            listOf(QuizQuestion("Teknik cepat meredakan stres:", listOf("Napas dalam perlahan", "Menahan perasaan sendiri terus-menerus", "Menambah kopi"), 0, "Napas dalam perlahan menenangkan sistem saraf.")),
            "Lakukan napas dalam 2 menit sebelum tidur.",
        ),
        EducationModule(
            "obesitas", "Obesitas", "Berat badan dan lingkar perut", 2,
            "Berat badan berlebih, terutama lemak perut, meningkatkan risiko tekanan darah dan gula darah tinggi.",
            listOf(
                "IMT dihitung dari berat dan tinggi badan. IMT adalah indikator kasar; lingkar perut membantu menilai lemak perut.",
                "Penurunan berat badan sedikit demi sedikit (misalnya 5% dari berat badan) sudah memberi manfaat kesehatan.",
                "Fokus pada kebiasaan: porsi wajar, kurangi minuman manis dan gorengan, aktif bergerak, tidur cukup. Hindari diet ekstrem.",
            ),
            listOf(QuizQuestion("Penurunan berat badan yang aman adalah…", listOf("Bertahap dan berkelanjutan", "Puasa ekstrem", "Minum obat pelangsing sembarangan"), 0, "Perubahan bertahap lebih aman dan bertahan lama.")),
            "Ukur lingkar perutmu dan catat di SEHATI.",
        ),
    )

    fun byId(id: String) = modules.firstOrNull { it.id == id }
    fun byCategory(c: String) = modules.filter { it.category == c }

    /** Pemetaan temuan → materi edukasi yang relevan. */
    fun recommendFor(findingIds: Collection<String>): List<EducationModule> {
        val ids = LinkedHashSet<String>()
        findingIds.forEach { f ->
            when {
                f.startsWith("bp") -> ids += "hipertensi"
                f.startsWith("glucose") || f == "sugar" -> ids += "diabetes"
                f == "lipid" -> ids += "kolesterol"
                f == "history" || f == "family" -> ids += "faktor_pjk"
                f == "smoking" -> ids += "rokok"
                f == "inactive" || f == "sedentary" -> ids += "aktivitas"
                f == "salt" -> ids += "garam"
                f == "fat" || f == "veg_fruit" -> ids += "makanan"
                f == "sleep" -> ids += "tidur"
                f == "stress" -> ids += "stres"
                f == "bmi" || f == "waist" -> ids += "obesitas"
            }
        }
        if (ids.isEmpty()) ids += "aktivitas"
        return ids.mapNotNull(::byId)
    }
}
