package id.sehati.app.domain.rules

data class MeasurementInput(
    val systolic: Int? = null, val diastolic: Int? = null, val heartRate: Int? = null,
    val weightKg: Float? = null, val heightCm: Float? = null, val waistCm: Float? = null,
    val glucose: Float? = null, val cholesterol: Float? = null,
)

data class ValidationIssue(val field: String, val message: String, val blocking: Boolean)

object MeasurementValidator {
    fun validate(m: MeasurementInput): List<ValidationIssue> {
        val out = mutableListOf<ValidationIssue>()
        fun err(f: String, msg: String) { out += ValidationIssue(f, msg, true) }
        fun warn(f: String, msg: String) { out += ValidationIssue(f, msg, false) }

        if ((m.systolic == null) != (m.diastolic == null)) err("bp", "Isi sistolik dan diastolik sekaligus.")
        if (m.systolic != null && m.diastolic != null) {
            if (m.systolic !in 50..300) err("systolic", "Sistolik di luar rentang wajar (50–300).")
            if (m.diastolic !in 30..200) err("diastolic", "Diastolik di luar rentang wajar (30–200).")
            if (m.systolic <= m.diastolic) err("bp", "Sistolik harus lebih besar dari diastolik.")
            if (m.systolic - m.diastolic < 20 || m.systolic - m.diastolic > 100) warn("bp", "Selisih sistolik–diastolik tidak lazim; ulangi pengukuran.")
        }
        m.heartRate?.let { if (it !in 30..250) err("heartRate", "Denyut jantung di luar rentang wajar (30–250).") }
        m.weightKg?.let { if (it !in 2f..300f) err("weight", "Berat badan di luar rentang wajar (2–300 kg).") }
        m.heightCm?.let { if (it !in 50f..250f) err("height", "Tinggi badan di luar rentang wajar (50–250 cm).") }
        m.waistCm?.let { if (it !in 30f..200f) err("waist", "Lingkar perut di luar rentang wajar (30–200 cm).") }
        m.glucose?.let { if (it !in 20f..800f) err("glucose", "Gula darah di luar rentang wajar (20–800 mg/dL).") }
        m.cholesterol?.let { if (it !in 50f..600f) err("cholesterol", "Kolesterol di luar rentang wajar (50–600 mg/dL).") }
        if (m.systolic == null && m.weightKg == null && m.glucose == null && m.cholesterol == null &&
            m.waistCm == null && m.heartRate == null) err("all", "Isi minimal satu pengukuran.")
        return out
    }

    fun hasBlocking(issues: List<ValidationIssue>) = issues.any { it.blocking }
}

/** Pemeriksaan kualitas pengukuran (SOP tensi) yang dicatat kader. */
data class QualityCheck(val cuffPositioned: Boolean, val restedFiveMinutes: Boolean, val repeatNeeded: Boolean) {
    val passed: Boolean get() = cuffPositioned && restedFiveMinutes && !repeatNeeded
    fun toNote(): String = buildString {
        append("Cek kualitas: manset ${if (cuffPositioned) "benar" else "belum benar"}, ")
        append("istirahat 5 menit ${if (restedFiveMinutes) "ya" else "belum"}")
        if (repeatNeeded) append(", perlu ulang")
    }
}
