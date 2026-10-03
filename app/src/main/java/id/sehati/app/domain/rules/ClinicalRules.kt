package id.sehati.app.domain.rules

import id.sehati.app.domain.model.RiskLevel
import java.util.Locale
import kotlin.math.round

/**
 * Setiap aturan klinis memiliki: Rule, Source, Explanation, Limitations, Action.
 * Semua ambang adalah KONFIGURASI INTERNAL aplikasi yang merujuk pedoman umum;
 * bukan klaim "standar WHO/Kemenkes". Ubah lewat [ClinicalThresholds].
 */
data class RuleInfo(
    val rule: String,
    val source: String,
    val explanation: String,
    val limitations: String,
    val action: String,
)

@kotlinx.serialization.Serializable
data class ClinicalThresholds(
    // Tekanan darah (mmHg)
    val bpLowSys: Int = 90, val bpLowDia: Int = 60,
    val bpNormalSys: Int = 120, val bpNormalDia: Int = 80,
    val bpHighSys: Int = 140, val bpHighDia: Int = 90,
    val bpStage2Sys: Int = 160, val bpStage2Dia: Int = 100,
    val bpUrgentSys: Int = 180, val bpUrgentDia: Int = 110,
    // Gula darah (mg/dL)
    val glucoseLow: Float = 70f,
    val gdsElevated: Float = 140f, val gdsHigh: Float = 200f,
    val gdpElevated: Float = 100f, val gdpHigh: Float = 126f,
    // Kolesterol total (mg/dL)
    val cholBorderline: Float = 200f, val cholHigh: Float = 240f,
    // IMT (kg/m²) - klasifikasi Asia-Pasifik
    val bmiUnder: Float = 18.5f, val bmiOver: Float = 23f, val bmiObese1: Float = 25f, val bmiObese2: Float = 30f,
    // Lingkar perut (cm)
    val waistMale: Float = 90f, val waistFemale: Float = 80f,
    // Aktivitas
    val activeMinutesPerWeekGoal: Int = 150,
    val sedentaryHoursHigh: Int = 8,
) {
    /** Pesan galat bila konfigurasi tidak masuk akal (urutan ambang harus naik, rentang wajar). */
    fun validate(): String? = when {
        bpHighSys !in 120..200 || bpHighDia !in 70..130 -> "Ambang tekanan darah tinggi di luar rentang wajar."
        !(bpNormalSys < bpHighSys && bpHighSys < bpStage2Sys && bpStage2Sys < bpUrgentSys) -> "Ambang sistolik harus berurutan: normal < tinggi < tingkat lanjut < sangat tinggi."
        !(bpNormalDia < bpHighDia && bpHighDia < bpStage2Dia && bpStage2Dia < bpUrgentDia) -> "Ambang diastolik harus berurutan: normal < tinggi < tingkat lanjut < sangat tinggi."
        !(glucoseLow < gdsElevated && gdsElevated < gdsHigh) -> "Ambang gula darah sewaktu harus berurutan: rendah < di atas normal < tinggi."
        !(gdpElevated < gdpHigh) -> "Ambang gula darah puasa harus berurutan."
        !(cholBorderline < cholHigh) -> "Ambang kolesterol harus berurutan: batas < tinggi."
        !(bmiUnder < bmiOver && bmiOver < bmiObese1 && bmiObese1 < bmiObese2) -> "Ambang IMT harus berurutan."
        waistMale !in 70f..130f || waistFemale !in 60f..120f -> "Ambang lingkar perut di luar rentang wajar."
        activeMinutesPerWeekGoal !in 30..600 -> "Target aktivitas mingguan di luar rentang wajar."
        else -> null
    }

    companion object { const val VERSION = "sehati-rules-1.0" }
}

/** Konfigurasi aktif yang dipakai semua aturan. Admin dapat mengubahnya; nilai dimuat saat aplikasi dibuka. */
object ClinicalConfig {
    @Volatile var current: ClinicalThresholds = ClinicalThresholds()
    val isCustom: Boolean get() = current != ClinicalThresholds()
    val rulesetVersion: String get() = if (isCustom) "${ClinicalThresholds.VERSION}+custom" else ClinicalThresholds.VERSION
}

enum class Severity(val rank: Int) { INFO(0), WATCH(1), ATTENTION(2), URGENT(3) }

data class Interpretation(
    val category: String,
    val severity: Severity,
    val interpretation: String,
    val education: String,
    val nextStep: String,
    val rule: RuleInfo,
) {
    val needsUrgentCare: Boolean get() = severity == Severity.URGENT
}

object BloodPressureRules {
    private val info = RuleInfo(
        rule = "Klasifikasi tekanan darah satu kali pengukuran (sistolik/diastolik, mmHg)",
        source = "Konfigurasi internal SEHATI mengacu pada klasifikasi pedoman hipertensi umum (ambang dapat disesuaikan)",
        explanation = "Satu hasil pengukuran adalah data skrining, bukan diagnosis hipertensi.",
        limitations = "Dipengaruhi posisi manset, istirahat, kafein, rokok, dan stres. Perlu pengukuran ulang.",
        action = "Ukur ulang dengan SOP yang benar; bila tetap tinggi, evaluasi oleh tenaga kesehatan.",
    )

    fun interpret(sys: Int, dia: Int, t: ClinicalThresholds = ClinicalConfig.current): Interpretation = when {
        sys >= t.bpUrgentSys || dia >= t.bpUrgentDia -> Interpretation(
            "Sangat tinggi", Severity.URGENT,
            "Hasil pengukuran berada pada rentang sangat tinggi dan perlu dikonfirmasi serta dievaluasi tenaga kesehatan sesegera mungkin.",
            "Hindari aktivitas berat dan rokok sampai dievaluasi.",
            "Segera cari pertolongan medis bila disertai nyeri dada, sesak, lemah satu sisi, atau sakit kepala hebat.", info)
        sys >= t.bpStage2Sys || dia >= t.bpStage2Dia -> Interpretation(
            "Tinggi (tingkat lebih lanjut)", Severity.ATTENTION,
            "Hasil pengukuran berada pada rentang tekanan darah tinggi dan perlu dikonfirmasi melalui pengukuran yang sesuai serta evaluasi tenaga kesehatan.",
            "Kurangi garam, hindari rokok, tetap aktif sesuai kemampuan.",
            "Ukur ulang dalam waktu dekat dan rujuk ke Puskesmas untuk evaluasi.", info)
        sys >= t.bpHighSys || dia >= t.bpHighDia -> Interpretation(
            "Tinggi", Severity.ATTENTION,
            "Hasil pengukuran berada pada rentang tekanan darah tinggi dan perlu dikonfirmasi melalui pengukuran yang sesuai serta evaluasi tenaga kesehatan.",
            "Kurangi makanan asin dan olahan, perbanyak sayur-buah, aktif bergerak.",
            "Ukur ulang dalam 1–2 minggu; bila tetap tinggi, evaluasi tenaga kesehatan.", info)
        sys < t.bpLowSys || dia < t.bpLowDia -> Interpretation(
            "Rendah", Severity.WATCH,
            "Hasil pengukuran berada di bawah rentang yang umum. Perhatikan apakah ada keluhan seperti pusing atau lemas.",
            "Cukupi cairan dan bangun perlahan dari posisi duduk/berbaring.",
            "Ukur ulang; bila ada keluhan, konsultasikan ke tenaga kesehatan.", info)
        sys >= t.bpNormalSys || dia >= t.bpNormalDia -> Interpretation(
            "Normal-tinggi", Severity.WATCH,
            "Hasil pengukuran sedikit di atas rentang optimal. Perlu dipantau secara berkala.",
            "Jaga pola makan rendah garam dan aktivitas teratur.",
            "Pantau ulang pada pemeriksaan Posyandu berikutnya.", info)
        else -> Interpretation(
            "Normal", Severity.INFO,
            "Hasil pengukuran berada pada rentang optimal.",
            "Pertahankan kebiasaan sehat.",
            "Lanjutkan pemantauan rutin.", info)
    }

    fun isElevated(sys: Int, dia: Int, t: ClinicalThresholds = ClinicalConfig.current) =
        sys >= t.bpHighSys || dia >= t.bpHighDia
}

object GlucoseRules {
    private val info = RuleInfo(
        rule = "Interpretasi gula darah skrining (mg/dL), sewaktu (GDS) atau puasa (GDP)",
        source = "Konfigurasi internal SEHATI mengacu pada ambang skrining umum (ambang dapat disesuaikan)",
        explanation = "Hasil skrining/pemantauan; bukan diagnosis diabetes.",
        limitations = "Dipengaruhi waktu makan terakhir dan alat ukur. Diagnosis memerlukan pemeriksaan laboratorium.",
        action = "Pemeriksaan lanjutan oleh tenaga kesehatan bila di atas rentang umum.",
    )

    fun interpret(mgDl: Float, fasting: Boolean = false, t: ClinicalThresholds = ClinicalConfig.current): Interpretation {
        val elevated = if (fasting) t.gdpElevated else t.gdsElevated
        val high = if (fasting) t.gdpHigh else t.gdsHigh
        val kind = if (fasting) "puasa" else "sewaktu"
        return when {
            mgDl < t.glucoseLow -> Interpretation("Rendah", Severity.URGENT,
                "Gula darah $kind berada di bawah rentang umum. Bila ada gemetar, keringat dingin, atau lemas, ini perlu penanganan segera.",
                "Konsumsi sumber gula cepat serap bila bergejala, lalu makan teratur.",
                "Segera cari pertolongan medis bila gejala berat atau tidak membaik.", info)
            mgDl >= high -> Interpretation("Tinggi", Severity.ATTENTION,
                "Gula darah $kind berada pada rentang tinggi dan perlu pemeriksaan lanjutan oleh tenaga kesehatan.",
                "Batasi minuman manis dan makanan tinggi gula; perbanyak aktivitas.",
                "Rujuk ke Puskesmas untuk pemeriksaan lanjutan.", info)
            mgDl >= elevated -> Interpretation("Di atas rentang normal", Severity.WATCH,
                "Gula darah $kind sedikit di atas rentang normal. Perlu dipantau dan dikonfirmasi.",
                "Kurangi gula tambahan, pilih karbohidrat kompleks.",
                "Ulangi pemeriksaan dan konsultasikan bila tetap tinggi.", info)
            else -> Interpretation("Dalam rentang normal", Severity.INFO,
                "Gula darah $kind berada pada rentang normal.", "Pertahankan pola makan seimbang.",
                "Lanjutkan pemantauan rutin.", info)
        }
    }
}

object LipidRules {
    private val info = RuleInfo(
        rule = "Interpretasi kolesterol total (mg/dL)",
        source = "Konfigurasi internal SEHATI mengacu pada ambang umum (ambang dapat disesuaikan)",
        explanation = "Kolesterol total saja tidak cukup untuk menilai risiko jantung.",
        limitations = "Tidak memisahkan LDL/HDL/trigliserida; alat cepat kurang presisi.",
        action = "Evaluasi lengkap oleh tenaga kesehatan bila tinggi.",
    )

    fun interpret(mgDl: Float, t: ClinicalThresholds = ClinicalConfig.current): Interpretation = when {
        mgDl >= t.cholHigh -> Interpretation("Tinggi", Severity.ATTENTION,
            "Kolesterol total berada pada rentang tinggi dan perlu evaluasi tenaga kesehatan.",
            "Kurangi gorengan dan lemak jenuh, tambah serat.", "Rujuk ke Puskesmas untuk pemeriksaan profil lemak.", info)
        mgDl >= t.cholBorderline -> Interpretation("Batas tinggi", Severity.WATCH,
            "Kolesterol total berada pada batas atas.", "Atur pola makan rendah lemak jenuh.",
            "Pantau ulang pada pemeriksaan berikutnya.", info)
        else -> Interpretation("Dalam rentang yang diinginkan", Severity.INFO,
            "Kolesterol total berada pada rentang yang diinginkan.", "Pertahankan pola makan sehat.", "Lanjutkan pemantauan rutin.", info)
    }
}

object AnthropometryRules {
    data class BmiResult(val bmi: Float, val category: String, val severity: Severity, val rule: RuleInfo)

    private val info = RuleInfo(
        rule = "IMT = berat (kg) / tinggi (m)²; klasifikasi Asia-Pasifik",
        source = "Konfigurasi internal SEHATI mengacu pada klasifikasi IMT Asia-Pasifik (ambang dapat disesuaikan)",
        explanation = "IMT adalah indikator kasar komposisi tubuh.",
        limitations = "Tidak membedakan massa otot dan lemak; kurang akurat pada lansia, atlet, ibu hamil.",
        action = "Gunakan bersama lingkar perut dan penilaian tenaga kesehatan.",
    )

    fun bmi(heightCm: Float, weightKg: Float): Float {
        if (heightCm <= 0f || weightKg <= 0f) return 0f
        val m = heightCm / 100f
        return round(weightKg / (m * m) * 10f) / 10f
    }

    fun classify(heightCm: Float, weightKg: Float, t: ClinicalThresholds = ClinicalConfig.current): BmiResult {
        val v = bmi(heightCm, weightKg)
        val (cat, sev) = when {
            v <= 0f -> "Tidak valid" to Severity.INFO
            v < t.bmiUnder -> "Berat badan kurang" to Severity.WATCH
            v < t.bmiOver -> "Normal" to Severity.INFO
            v < t.bmiObese1 -> "Berat badan lebih" to Severity.WATCH
            v < t.bmiObese2 -> "Obesitas tingkat I" to Severity.ATTENTION
            else -> "Obesitas tingkat II" to Severity.ATTENTION
        }
        return BmiResult(v, cat, sev, info)
    }

    fun centralObesity(waistCm: Float, male: Boolean, t: ClinicalThresholds = ClinicalConfig.current): Boolean =
        waistCm > 0f && waistCm >= (if (male) t.waistMale else t.waistFemale)
}

fun Float.fmt1(): String = String.format(Locale.US, "%.1f", this)
