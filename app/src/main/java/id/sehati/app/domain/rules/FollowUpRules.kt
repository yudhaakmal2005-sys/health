package id.sehati.app.domain.rules

import id.sehati.app.domain.model.FollowUpType

data class FollowUpAdvice(
    val type: FollowUpType,
    val reasonCode: String,
    val reason: String,
    val priority: Int, // 3 = tertinggi
    val dueInDays: Int,
)

/**
 * Menurunkan rekomendasi tindak lanjut dari hasil skrining. Hasil adalah
 * "perlu pemantauan / ukur ulang / ditinjau tenaga kesehatan", bukan diagnosis.
 */
object FollowUpRules {
    fun advise(
        sys: Int?, dia: Int?, glucose: Float?, glucoseFasting: Boolean = false, cholesterol: Float?,
        previousBp: List<Pair<Int, Int>> = emptyList(),
        t: ClinicalThresholds = ClinicalThresholds(),
    ): List<FollowUpAdvice> {
        val out = mutableListOf<FollowUpAdvice>()
        if (sys != null && dia != null) {
            val r = BloodPressureRules.interpret(sys, dia, t)
            val elevated = BloodPressureRules.isElevated(sys, dia, t)
            val repeated = elevated && previousBp.any { BloodPressureRules.isElevated(it.first, it.second, t) }
            when {
                r.needsUrgentCare -> out += FollowUpAdvice(FollowUpType.PUSKESMAS_EVALUATION, "bp_urgent",
                    "Tekanan darah $sys/$dia sangat tinggi", 3, 1)
                repeated -> out += FollowUpAdvice(FollowUpType.HOME_VISIT, "bp_repeated",
                    "Tekanan darah tinggi berulang", 3, 7)
                elevated -> out += FollowUpAdvice(FollowUpType.REPEAT_MEASUREMENT, "bp_elevated",
                    "Tekanan darah $sys/$dia perlu diukur ulang", 2, 14)
                r.severity == Severity.WATCH && r.category == "Rendah" -> out += FollowUpAdvice(
                    FollowUpType.REPEAT_MEASUREMENT, "bp_low", "Tekanan darah $sys/$dia rendah, ukur ulang", 1, 14)
            }
        }
        if (glucose != null) {
            val r = GlucoseRules.interpret(glucose, glucoseFasting, t)
            when (r.severity) {
                Severity.URGENT -> out += FollowUpAdvice(FollowUpType.PUSKESMAS_EVALUATION, "glucose_urgent",
                    "Gula darah ${glucose.fmt1()} mg/dL di luar rentang aman", 3, 1)
                Severity.ATTENTION -> out += FollowUpAdvice(FollowUpType.PUSKESMAS_EVALUATION, "glucose_high",
                    "Gula darah ${glucose.fmt1()} mg/dL tinggi, perlu ditinjau tenaga kesehatan", 2, 7)
                Severity.WATCH -> out += FollowUpAdvice(FollowUpType.REPEAT_MEASUREMENT, "glucose_watch",
                    "Gula darah ${glucose.fmt1()} mg/dL perlu diukur ulang", 1, 14)
                else -> Unit
            }
        }
        if (cholesterol != null && LipidRules.interpret(cholesterol, t).severity.rank >= Severity.ATTENTION.rank)
            out += FollowUpAdvice(FollowUpType.EDUCATION, "chol_high", "Kolesterol total ${cholesterol.fmt1()} mg/dL tinggi", 1, 30)
        return out.sortedByDescending { it.priority }
    }
}
