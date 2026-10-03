package id.sehati.app.domain.rules

enum class FactorStatus { PRESENT, ABSENT, UNKNOWN }

data class HeartFactor(
    val id: String,
    val title: String,
    val status: FactorStatus,
    val detail: String,
    val action: String,
    val moduleId: String?,
)

enum class HeartBand(val label: String, val explanation: String) {
    INSUFFICIENT("Data belum cukup", "Lengkapi asesmen dan pemeriksaan agar faktor risikomu dapat dikenali."),
    FEW("Faktor risiko sedikit", "Hanya sedikit faktor risiko yang terdeteksi. Pertahankan kebiasaan baikmu."),
    SOME("Beberapa faktor risiko", "Ada beberapa faktor yang masih bisa kamu perbaiki. Mulai dari yang paling mudah."),
    MANY("Banyak faktor risiko", "Ada banyak faktor risiko. Bicarakan dengan kader atau tenaga kesehatan, dan mulai perbaikan bertahap."),
}

data class HeartRiskReport(
    val factors: List<HeartFactor>,
    val ageFactor: Boolean,
) {
    val present: Int get() = factors.count { it.status == FactorStatus.PRESENT }
    val known: Int get() = factors.count { it.status != FactorStatus.UNKNOWN }
    val total: Int get() = factors.size
    val band: HeartBand get() = when {
        known < 5 -> HeartBand.INSUFFICIENT
        present <= 1 -> HeartBand.FEW
        present <= 3 -> HeartBand.SOME
        else -> HeartBand.MANY
    }
}

/**
 * Daftar faktor risiko jantung koroner yang DAPAT DIUBAH. Ini hitungan faktor, BUKAN skor kemungkinan serangan jantung
 * dan bukan diagnosis. Skor risiko terkalibrasi (mis. grafik WHO/ISH) memerlukan tenaga kesehatan.
 */
object HeartRisk {
    const val DISCLAIMER =
        "Ini jumlah faktor risiko yang dikenali dari data yang kamu isi, bukan perkiraan kemungkinan serangan jantung dan bukan diagnosis."

    fun evaluate(s: HealthSnapshot, t: ClinicalThresholds = ClinicalConfig.current): HeartRiskReport {
        fun f(id: String, title: String, st: FactorStatus, detail: String, action: String, module: String?) =
            HeartFactor(id, title, st, detail, action, module)
        val out = mutableListOf<HeartFactor>()

        // 1. Tekanan darah
        val bp = s.bpReadings.firstOrNull()
        out += when {
            s.knownHypertension -> f("bp", "Tekanan darah", FactorStatus.PRESENT, "Kamu melaporkan riwayat tekanan darah tinggi.", "Kontrol rutin dan kurangi garam.", "hipertensi")
            bp == null -> f("bp", "Tekanan darah", FactorStatus.UNKNOWN, "Belum ada pengukuran.", "Ukur di Posyandu atau catat sendiri.", "hipertensi")
            BloodPressureRules.isElevated(bp.first, bp.second, t) -> f("bp", "Tekanan darah", FactorStatus.PRESENT, "Pengukuran terakhir ${bp.first}/${bp.second} mmHg berada pada rentang tinggi.", "Ukur ulang dan konsultasikan ke tenaga kesehatan.", "hipertensi")
            else -> f("bp", "Tekanan darah", FactorStatus.ABSENT, "Pengukuran terakhir ${bp.first}/${bp.second} mmHg.", "Pertahankan dan cek berkala.", "hipertensi")
        }
        // 2. Merokok
        out += when {
            !s.assessed -> f("smoking", "Merokok", FactorStatus.UNKNOWN, "Asesmen belum diisi.", "Isi asesmen awal.", "rokok")
            s.smokingStatus == "CURRENT" -> f("smoking", "Merokok", FactorStatus.PRESENT, "Perokok aktif (${s.cigarettesPerDay} batang/hari).", "Kurangi bertahap, mulai dengan menunda rokok pertama.", "rokok")
            s.smokingStatus == "FORMER" -> f("smoking", "Merokok", FactorStatus.ABSENT, "Mantan perokok. Terus pertahankan.", "Tetap bebas rokok.", "rokok")
            else -> f("smoking", "Merokok", FactorStatus.ABSENT, "Tidak merokok.", "Pertahankan; hindari asap rokok orang lain.", "rokok")
        }
        // 3. Gula darah
        val gl = s.glucose
        out += when {
            s.knownDiabetes -> f("glucose", "Gula darah", FactorStatus.PRESENT, "Kamu melaporkan riwayat diabetes.", "Kontrol rutin sesuai arahan dokter.", "diabetes")
            gl == null -> f("glucose", "Gula darah", FactorStatus.UNKNOWN, "Belum ada pemeriksaan gula darah.", "Periksa di Posyandu.", "diabetes")
            GlucoseRules.interpret(gl, s.glucoseFasting, t).severity.rank >= Severity.WATCH.rank -> f("glucose", "Gula darah", FactorStatus.PRESENT, "Hasil terakhir ${gl.toInt()} mg/dL di atas rentang normal.", "Batasi minuman manis dan periksa ulang.", "diabetes")
            else -> f("glucose", "Gula darah", FactorStatus.ABSENT, "Hasil terakhir ${gl.toInt()} mg/dL.", "Pertahankan pola makan seimbang.", "diabetes")
        }
        // 4. Kolesterol
        val ch = s.cholesterol
        out += when {
            s.knownDyslipidemia -> f("cholesterol", "Kolesterol", FactorStatus.PRESENT, "Kamu melaporkan kolesterol/lemak darah tinggi.", "Kurangi gorengan dan lemak jenuh.", "jantung")
            ch == null -> f("cholesterol", "Kolesterol", FactorStatus.UNKNOWN, "Belum ada pemeriksaan kolesterol.", "Periksa bila tersedia di Posyandu/Puskesmas.", "jantung")
            ch >= t.cholBorderline -> f("cholesterol", "Kolesterol", FactorStatus.PRESENT, "Kolesterol total ${ch.toInt()} mg/dL berada di batas atas atau lebih.", "Kurangi lemak jenuh, tambah serat.", "jantung")
            else -> f("cholesterol", "Kolesterol", FactorStatus.ABSENT, "Kolesterol total ${ch.toInt()} mg/dL.", "Pertahankan.", "jantung")
        }
        // 5. Berat badan / lingkar perut
        out += if (s.heightCm <= 0f || s.weightKg <= 0f) {
            f("weight", "Berat badan & lingkar perut", FactorStatus.UNKNOWN, "Berat atau tinggi badan belum diisi.", "Isi di asesmen.", "obesitas")
        } else {
            val bmi = AnthropometryRules.bmi(s.heightCm, s.weightKg)
            val central = AnthropometryRules.centralObesity(s.waistCm, s.male, t)
            if (bmi >= t.bmiObese1 || central)
                f("weight", "Berat badan & lingkar perut", FactorStatus.PRESENT, "IMT ${bmi.fmt1()}" + if (central) " dan lingkar perut di atas batas." else ".", "Turunkan perlahan lewat porsi dan aktivitas.", "obesitas")
            else f("weight", "Berat badan & lingkar perut", FactorStatus.ABSENT, "IMT ${bmi.fmt1()}.", "Pertahankan.", "obesitas")
        }
        // 6. Aktivitas
        val weekly = s.activeDaysPerWeek * s.activeMinutesPerSession
        out += when {
            !s.assessed -> f("activity", "Aktivitas fisik", FactorStatus.UNKNOWN, "Asesmen belum diisi.", "Isi asesmen awal.", "aktivitas")
            weekly < t.activeMinutesPerWeekGoal -> f("activity", "Aktivitas fisik", FactorStatus.PRESENT, "Sekitar $weekly menit/minggu; anjuran umum ${t.activeMinutesPerWeekGoal} menit.", "Tambah jalan kaki 10–15 menit per hari.", "aktivitas")
            else -> f("activity", "Aktivitas fisik", FactorStatus.ABSENT, "Sekitar $weekly menit/minggu. Bagus.", "Pertahankan.", "aktivitas")
        }
        // 7. Pola makan
        out += when {
            !s.assessed -> f("diet", "Pola makan", FactorStatus.UNKNOWN, "Asesmen belum diisi.", "Isi asesmen awal.", "makanan")
            s.saltyFoodFrequent || s.fattyFrequent || (s.vegetableDaysPerWeek < 5 && s.fruitDaysPerWeek < 5) ->
                f("diet", "Pola makan", FactorStatus.PRESENT, "Sering makanan asin/berlemak atau kurang sayur dan buah.", "Kurangi garam dan gorengan, tambah sayur-buah.", "makanan")
            else -> f("diet", "Pola makan", FactorStatus.ABSENT, "Pola makan cukup baik.", "Pertahankan.", "makanan")
        }
        // 8. Riwayat keluarga
        out += when {
            !s.assessed -> f("family", "Riwayat keluarga", FactorStatus.UNKNOWN, "Asesmen belum diisi.", "Isi asesmen awal.", "jantung")
            s.familyCardio || s.familyHypertension || s.familyDiabetes -> f("family", "Riwayat keluarga", FactorStatus.PRESENT, "Ada riwayat jantung, tekanan darah tinggi, atau diabetes pada keluarga.", "Pemeriksaan rutin menjadi lebih penting.", "jantung")
            else -> f("family", "Riwayat keluarga", FactorStatus.ABSENT, "Tidak ada riwayat yang dilaporkan.", "Tetap cek berkala.", "jantung")
        }
        // 9. Stres & tidur
        out += when {
            !s.assessed -> f("stress", "Stres & tidur", FactorStatus.UNKNOWN, "Asesmen belum diisi.", "Isi asesmen awal.", "stres")
            s.stressLevel >= 4 || s.sleepHours < 6f -> f("stress", "Stres & tidur", FactorStatus.PRESENT, "Stres tinggi atau tidur kurang dari 6 jam.", "Atur jam tidur dan latih napas dalam.", "stres")
            else -> f("stress", "Stres & tidur", FactorStatus.ABSENT, "Stres dan tidur cukup terjaga.", "Pertahankan.", "tidur")
        }
        val ageFactor = s.age > 0 && ((s.male && s.age >= 45) || (!s.male && s.age >= 55))
        return HeartRiskReport(out, ageFactor)
    }
}

/** Lima pilar harian jantung sehat (untuk motivasi harian; bukan indikator medis). */
data class Pillar(val id: String, val title: String, val done: Boolean, val hint: String)

object HeartPillars {
    fun evaluate(
        steps: Int, stepTarget: Int, cigarettesToday: Int, foodCount: Int, sodiumMg: Float, sodiumLimit: Int,
        sleepHours: Float?, sleepTarget: Float, daysSinceBpCheck: Int?,
    ): List<Pillar> = listOf(
        Pillar("active", "Aktif bergerak", steps >= stepTarget, "Capai $stepTarget langkah atau jalan 30 menit."),
        Pillar("smoke", "Bebas rokok hari ini", cigarettesToday == 0, "Tunda rokok berikutnya 30 menit."),
        Pillar("salt", "Garam terkendali", foodCount > 0 && sodiumMg <= sodiumLimit, "Catat makananmu dan pilih yang rendah garam."),
        Pillar("sleep", "Tidur cukup", sleepHours != null && sleepHours >= sleepTarget - 1f, "Targetkan tidur teratur."),
        Pillar("check", "Kontrol tensi", daysSinceBpCheck != null && daysSinceBpCheck <= 90, "Ukur tekanan darah di Posyandu tiap 1–3 bulan."),
    )
}
