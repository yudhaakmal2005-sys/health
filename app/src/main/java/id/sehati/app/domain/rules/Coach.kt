package id.sehati.app.domain.rules

data class CoachContext(
    val name: String,
    val steps: Int, val stepTarget: Int,
    val waterGlasses: Int, val waterTarget: Int,
    val sleepHours: Float?,
    val cigarettesToday: Int,
    val latestBp: Pair<Int, Int>? = null,
    val profileLevelLabel: String? = null,
    val hasFollowUp: Boolean = false,
)

data class CoachReply(val text: String, val emergency: Boolean = false, val suggestions: List<String> = emptyList())

/**
 * Pelatih kesehatan berbasis aturan (berjalan offline). Tidak mendiagnosis, tidak meresepkan,
 * tidak mengubah obat, tidak memberi kepastian palsu.
 */
object HealthCoach {
    const val DISCLAIMER = "Saran umum gaya hidup, bukan diagnosis atau pengganti tenaga kesehatan."

    fun dailyTip(c: CoachContext): String {
        if (c.hasFollowUp) return "Ada tindak lanjut yang menunggu. Hubungi kader Posyandu atau datang ke Puskesmas untuk pemeriksaan ulang."
        val remaining = (c.stepTarget - c.steps).coerceAtLeast(0)
        return when {
            c.cigarettesToday > 0 -> "Hari ini tercatat ${c.cigarettesToday} batang rokok. Coba tunda satu batang berikutnya 30 menit; mengurangi rokok membantu menurunkan risiko penyakit jantung."
            remaining > 3000 -> "Langkahmu baru ${c.steps}. Kamu bisa mencoba berjalan santai 10–15 menit sore ini."
            remaining > 0 -> "Tinggal $remaining langkah lagi menuju targetmu. Sedikit jalan santai sudah cukup."
            c.waterGlasses < c.waterTarget -> "Target langkah tercapai. Lengkapi minum air putih (${c.waterGlasses}/${c.waterTarget} gelas)."
            else -> "Kerja bagus, target hari ini tercapai. Pertahankan kebiasaan ini."
        }
    }

    fun reply(question: String, c: CoachContext): CoachReply {
        if (RedFlag.detect(question)) return CoachReply(RedFlag.EMERGENCY_MESSAGE, emergency = true)
        val q = question.lowercase()
        val text = when {
            listOf("obat", "dosis").any(q::contains) ->
                "Aku tidak dapat memberi saran obat atau mengubah terapi. Tanyakan kepada dokter atau apoteker."
            listOf("langkah", "jalan", "olahraga", "aktivitas").any(q::contains) ->
                "Langkahmu hari ini ${c.steps} dari target ${c.stepTarget}. Mulai dengan jalan santai 10–15 menit lalu tambah bertahap sesuai kemampuan."
            listOf("garam", "asin", "natrium").any(q::contains) ->
                "Kurangi makanan tinggi garam, batasi mi instan dan makanan olahan, serta biasakan membaca label pangan."
            listOf("gula", "manis", "diabetes").any(q::contains) ->
                "Batasi minuman manis dan gula tambahan; pilih karbohidrat kompleks dan perbanyak sayur. Hasil gula darah perlu dinilai tenaga kesehatan."
            listOf("rokok", "merokok").any(q::contains) ->
                "Mengurangi dan berhenti merokok membantu menurunkan risiko penyakit kardiovaskular. Mulai dengan menunda rokok pertama di pagi hari."
            listOf("tensi", "tekanan darah", "hipertensi").any(q::contains) ->
                (c.latestBp?.let { "Pengukuran terakhirmu ${it.first}/${it.second} mmHg. " } ?: "") +
                    "Satu hasil pengukuran belum cukup untuk kesimpulan; ukur ulang dengan benar dan konsultasikan ke tenaga kesehatan bila tinggi."
            listOf("tidur", "begadang").any(q::contains) ->
                "Usahakan jam tidur teratur dan kurangi layar sebelum tidur. Tidurmu ${c.sleepHours?.let { "tercatat $it jam" } ?: "belum tercatat"}."
            listOf("minum", "air").any(q::contains) ->
                "Kamu sudah minum ${c.waterGlasses} dari ${c.waterTarget} gelas. Bawa botol air agar lebih mudah."
            else -> dailyTip(c)
        }
        return CoachReply("$text\n\n$DISCLAIMER", suggestions = listOf("Tips aktivitas", "Kurangi garam", "Cara berhenti merokok"))
    }
}
