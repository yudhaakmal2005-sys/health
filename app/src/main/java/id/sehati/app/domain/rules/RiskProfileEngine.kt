package id.sehati.app.domain.rules

import id.sehati.app.domain.model.RiskLevel

enum class FindingDomain { BLOOD_PRESSURE, GLUCOSE, LIPID, WEIGHT, SMOKING, ACTIVITY, DIET, HISTORY, SLEEP_STRESS, SYMPTOM }

data class Finding(
    val id: String,
    val domain: FindingDomain,
    val title: String,
    val detail: String,
    val severity: Severity,
)

data class PlanItem(val id: String, val title: String, val module: String? = null)

data class HealthProfile(
    val level: RiskLevel,
    val findings: List<Finding>,
    val plan: List<PlanItem>,
    val rulesetVersion: String = ClinicalConfig.rulesetVersion,
)

/** Data masukan profil; semua opsional agar profil dapat dihitung dari data parsial. */
data class HealthSnapshot(
    val age: Int = 0,
    val male: Boolean = true,
    val heightCm: Float = 0f,
    val weightKg: Float = 0f,
    val waistCm: Float = 0f,
    // Pengukuran terbaru (terbaru dulu)
    val bpReadings: List<Pair<Int, Int>> = emptyList(),
    val glucose: Float? = null,
    val glucoseFasting: Boolean = false,
    val cholesterol: Float? = null,
    // Riwayat
    val knownHypertension: Boolean = false,
    val knownDiabetes: Boolean = false,
    val knownHeartOrKidneyDisease: Boolean = false,
    val familyHypertension: Boolean = false,
    val familyDiabetes: Boolean = false,
    val familyCardio: Boolean = false,
    // Gaya hidup
    val smokingStatus: String = "NEVER", // NEVER, FORMER, CURRENT
    val cigarettesPerDay: Int = 0,
    val activeDaysPerWeek: Int = 0,
    val activeMinutesPerSession: Int = 0,
    val sedentaryHoursPerDay: Int = 0,
    val vegetableDaysPerWeek: Int = 7,
    val fruitDaysPerWeek: Int = 7,
    val saltyFoodFrequent: Boolean = false,
    val sugaryFrequent: Boolean = false,
    val fattyFrequent: Boolean = false,
    val sleepHours: Float = 7f,
    val stressLevel: Int = 1, // 1..5
    val redFlagSymptom: Boolean = false,
    val knownDyslipidemia: Boolean = false,
    /** false bila asesmen belum diisi: faktor gaya hidup/riwayat dianggap belum diketahui. */
    val assessed: Boolean = true,
)

object RiskProfileEngine {

    fun evaluate(s: HealthSnapshot, t: ClinicalThresholds = ClinicalConfig.current): HealthProfile {
        val f = mutableListOf<Finding>()
        val plan = mutableListOf<PlanItem>()

        if (s.redFlagSymptom) f += Finding("symptom_red_flag", FindingDomain.SYMPTOM,
            "Gejala yang perlu perhatian segera", "Gejala serius dilaporkan. Segera cari pertolongan medis.", Severity.URGENT)

        // --- Tekanan darah ---
        val latest = s.bpReadings.firstOrNull()
        if (latest != null) {
            val r = BloodPressureRules.interpret(latest.first, latest.second, t)
            if (r.severity != Severity.INFO) f += Finding("bp_latest", FindingDomain.BLOOD_PRESSURE,
                "Tekanan darah ${latest.first}/${latest.second} mmHg: ${r.category}", r.interpretation, r.severity)
            val elevatedCount = s.bpReadings.count { BloodPressureRules.isElevated(it.first, it.second, t) }
            if (elevatedCount >= 2) f += Finding("bp_repeated", FindingDomain.BLOOD_PRESSURE,
                "Tekanan darah tinggi berulang", "Ada $elevatedCount pengukuran pada rentang tinggi. Perlu evaluasi tenaga kesehatan.", Severity.URGENT)
            if (r.severity.rank >= Severity.WATCH.rank) plan += PlanItem("recheck_bp", "Ukur ulang tekanan darah di Posyandu", "hipertensi")
        }
        // --- Glukosa ---
        s.glucose?.let { g ->
            val r = GlucoseRules.interpret(g, s.glucoseFasting, t)
            if (r.severity != Severity.INFO) f += Finding("glucose", FindingDomain.GLUCOSE,
                "Gula darah ${g.fmt1()} mg/dL: ${r.category}", r.interpretation, r.severity)
            if (r.severity.rank >= Severity.WATCH.rank) plan += PlanItem("limit_sugar", "Batasi minuman manis hari ini", "diabetes")
        }
        s.cholesterol?.let { c ->
            val r = LipidRules.interpret(c, t)
            if (r.severity != Severity.INFO) f += Finding("lipid", FindingDomain.LIPID,
                "Kolesterol total ${c.fmt1()} mg/dL: ${r.category}", r.interpretation, r.severity)
        }
        // --- Berat badan ---
        if (s.heightCm > 0 && s.weightKg > 0) {
            val b = AnthropometryRules.classify(s.heightCm, s.weightKg, t)
            if (b.severity.rank >= Severity.WATCH.rank && b.category != "Berat badan kurang")
                f += Finding("bmi", FindingDomain.WEIGHT, "IMT ${b.bmi.fmt1()}: ${b.category}",
                    "Perhatikan keseimbangan asupan dan aktivitas.", b.severity)
            if (AnthropometryRules.centralObesity(s.waistCm, s.male, t))
                f += Finding("waist", FindingDomain.WEIGHT, "Lingkar perut ${s.waistCm.fmt1()} cm di atas batas",
                    "Lemak perut berkaitan dengan risiko metabolik.", Severity.WATCH)
        }
        // --- Merokok ---
        if (s.smokingStatus == "CURRENT") {
            f += Finding("smoking", FindingDomain.SMOKING, "Perokok aktif (${s.cigarettesPerDay} batang/hari)",
                "Mengurangi dan berhenti merokok membantu menurunkan risiko penyakit kardiovaskular.", Severity.ATTENTION)
            plan += PlanItem("cut_smoking", "Kurangi rokok secara bertahap", "rokok")
        }
        // --- Aktivitas ---
        val weeklyMin = s.activeDaysPerWeek * s.activeMinutesPerSession
        if (weeklyMin < t.activeMinutesPerWeekGoal) {
            f += Finding("inactive", FindingDomain.ACTIVITY, "Aktivitas fisik di bawah anjuran",
                "Aktivitas saat ini sekitar $weeklyMin menit/minggu; anjuran umum ${t.activeMinutesPerWeekGoal} menit.", Severity.WATCH)
            plan += PlanItem("walk_10", "Jalan santai 10–15 menit hari ini, tambah bertahap", "aktivitas")
        }
        if (s.sedentaryHoursPerDay >= t.sedentaryHoursHigh)
            f += Finding("sedentary", FindingDomain.ACTIVITY, "Banyak duduk (${s.sedentaryHoursPerDay} jam/hari)",
                "Selingi duduk lama dengan bergerak ringan.", Severity.WATCH)
        // --- Pola makan ---
        if (s.saltyFoodFrequent) { f += Finding("salt", FindingDomain.DIET, "Sering makanan asin/tinggi garam",
            "Kurangi makanan tinggi garam dan biasakan membaca label pangan.", Severity.WATCH); plan += PlanItem("less_salt", "Kurangi makanan asin hari ini", "makanan") }
        if (s.sugaryFrequent) f += Finding("sugar", FindingDomain.DIET, "Sering makanan/minuman manis", "Batasi gula tambahan.", Severity.WATCH)
        if (s.fattyFrequent) f += Finding("fat", FindingDomain.DIET, "Sering makanan tinggi lemak/gorengan", "Pilih cara masak rebus/kukus/panggang.", Severity.WATCH)
        if (s.vegetableDaysPerWeek < 5 || s.fruitDaysPerWeek < 5)
            { f += Finding("veg_fruit", FindingDomain.DIET, "Sayur/buah belum cukup sering", "Tambah sayur dan buah setiap hari.", Severity.WATCH); plan += PlanItem("veg", "Tambahkan sayur di setiap makan", "makanan") }
        // --- Riwayat ---
        if (s.knownHypertension || s.knownDiabetes || s.knownHeartOrKidneyDisease)
            f += Finding("history", FindingDomain.HISTORY, "Memiliki riwayat penyakit kronis (dilaporkan sendiri)",
                "Teruskan kontrol rutin sesuai arahan tenaga kesehatan.", Severity.ATTENTION)
        if (s.familyHypertension || s.familyDiabetes || s.familyCardio)
            f += Finding("family", FindingDomain.HISTORY, "Riwayat keluarga hipertensi/diabetes/jantung",
                "Pemantauan rutin lebih penting.", Severity.WATCH)
        // --- Tidur & stres ---
        if (s.sleepHours < 6f) f += Finding("sleep", FindingDomain.SLEEP_STRESS, "Tidur kurang dari 6 jam", "Upayakan tidur lebih teratur.", Severity.WATCH)
        if (s.stressLevel >= 4) f += Finding("stress", FindingDomain.SLEEP_STRESS, "Stres tinggi (dilaporkan)", "Cari dukungan dan teknik relaksasi.", Severity.WATCH)

        plan += PlanItem("water", "Minum air putih sesuai target harian", "aktivitas")
        plan += PlanItem("learn", "Baca 1 materi di Health Academy", "akademi")

        return HealthProfile(level(f), f.sortedByDescending { it.severity.rank }, plan.distinctBy { it.id })
    }

    private fun level(f: List<Finding>): RiskLevel {
        val attention = f.count { it.severity == Severity.ATTENTION }
        val watch = f.count { it.severity == Severity.WATCH }
        val measuredAttention = f.any {
            it.severity.rank >= Severity.ATTENTION.rank &&
                it.domain in setOf(FindingDomain.BLOOD_PRESSURE, FindingDomain.GLUCOSE, FindingDomain.LIPID)
        }
        return when {
            f.any { it.severity == Severity.URGENT } -> RiskLevel.MEDICAL_FOLLOW_UP
            measuredAttention || attention >= 2 || (attention >= 1 && watch >= 2) -> RiskLevel.HIGHER_MONITORING
            attention >= 1 || watch >= 1 -> RiskLevel.RISK_AWARENESS
            else -> RiskLevel.HEALTHY_HABIT
        }
    }
}
