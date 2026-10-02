package id.sehati.app.domain.rules

import id.sehati.app.domain.model.FollowUpStatus
import id.sehati.app.domain.model.RiskLevel

data class CitizenRec(val id: String, val rw: String, val hasAssessment: Boolean)
data class CheckRec(val userId: String, val at: Long, val systolic: Int?, val diastolic: Int?, val glucose: Float?, val bmi: Float?)
data class AssessmentRec(val userId: String, val smoker: Boolean, val activeMinutesPerWeek: Int)
data class FollowUpRec(val id: String, val userId: String, val status: FollowUpStatus, val priority: Int, val reason: String, val dueAt: Long)
data class ProfileRec(val userId: String, val level: RiskLevel)

data class Ratio(val numerator: Int, val denominator: Int) {
    val percent: Int get() = if (denominator == 0) 0 else Math.round(numerator * 100f / denominator)
    val label: String get() = "$numerator dari $denominator"
}

enum class MapState(val label: String) {
    INSUFFICIENT("Data belum cukup"),
    LOWER("Kebutuhan pemantauan lebih rendah"),
    HIGHER("Kebutuhan pemantauan lebih tinggi"),
    FOLLOW_UP_CONCENTRATION("Konsentrasi tindak lanjut"),
}

/** Hanya agregat: tidak ada nama, NIK, nomor HP, alamat, atau hasil individu. */
data class MapCell(val rw: String, val registered: Int, val screened: Int, val higherNeed: Int, val openFollowUps: Int, val state: MapState)

data class CommunityStats(
    val totalRegistered: Int,
    val screeningCoverage: Ratio,
    val followUpCoverage: Ratio,
    val openFollowUps: Int,
    val elevatedBp: Ratio,
    val elevatedGlucose: Ratio,
    val smoking: Ratio,
    val obesityIndicator: Ratio,
    val physicalInactivity: Ratio,
    val riskDistribution: Map<RiskLevel, Int>,
    val map: List<MapCell>,
)

object CommunityAnalytics {
    const val MIN_CELL = 5
    const val TERMINOLOGY = "Distribusi hasil skrining peserta (bukan prevalensi populasi)."

    fun calculate(
        citizens: List<CitizenRec>, checks: List<CheckRec>, assessments: List<AssessmentRec>,
        followUps: List<FollowUpRec>, profiles: List<ProfileRec>, t: ClinicalThresholds = ClinicalThresholds(),
    ): CommunityStats {
        val byUserChecks = checks.groupBy { it.userId }
        val screenedIds = byUserChecks.keys.intersect(citizens.map { it.id }.toSet())

        fun latest(u: String, pick: (CheckRec) -> Boolean) = byUserChecks[u]?.filter(pick)?.maxByOrNull { it.at }

        val bpMeasured = screenedIds.mapNotNull { u -> latest(u) { it.systolic != null && it.diastolic != null } }
        val glMeasured = screenedIds.mapNotNull { u -> latest(u) { it.glucose != null } }
        val bmiMeasured = screenedIds.mapNotNull { u -> latest(u) { it.bmi != null } }
        val latestAssessments = assessments.groupBy { it.userId }.mapValues { it.value.last() }.filterKeys { k -> citizens.any { it.id == k } }

        val fuStatuses = followUps.filter { it.status != FollowUpStatus.CANCELLED }
        val handled = fuStatuses.count { it.status != FollowUpStatus.OPEN }
        val open = followUps.count { it.status == FollowUpStatus.OPEN || it.status == FollowUpStatus.SCHEDULED || it.status == FollowUpStatus.IN_PROGRESS }

        val levelByUser = profiles.associate { it.userId to it.level }
        val dist = RiskLevel.entries.associateWith { lvl -> citizens.count { levelByUser[it.id] == lvl } }
        val openByUser = followUps.filter { it.status != FollowUpStatus.DONE && it.status != FollowUpStatus.CANCELLED }.groupBy { it.userId }

        val cells = citizens.groupBy { it.rw }.toSortedMap().map { (rw, members) ->
            val ids = members.map { it.id }.toSet()
            val screened = ids.count { it in screenedIds }
            val higher = ids.count { (levelByUser[it]?.rank ?: 0) >= RiskLevel.HIGHER_MONITORING.rank && it in screenedIds }
            val fu = ids.sumOf { openByUser[it]?.size ?: 0 }
            val state = when {
                screened < MIN_CELL -> MapState.INSUFFICIENT
                fu >= 3 && fu * 100 >= screened * 30 -> MapState.FOLLOW_UP_CONCENTRATION
                higher * 100 >= screened * 25 -> MapState.HIGHER
                else -> MapState.LOWER
            }
            MapCell(rw, members.size, screened, higher, fu, state)
        }

        return CommunityStats(
            totalRegistered = citizens.size,
            screeningCoverage = Ratio(screenedIds.size, citizens.size),
            followUpCoverage = Ratio(handled, fuStatuses.size),
            openFollowUps = open,
            elevatedBp = Ratio(bpMeasured.count { BloodPressureRules.isElevated(it.systolic!!, it.diastolic!!, t) }, bpMeasured.size),
            elevatedGlucose = Ratio(glMeasured.count { GlucoseRules.interpret(it.glucose!!, false, t).severity.rank >= Severity.WATCH.rank }, glMeasured.size),
            smoking = Ratio(latestAssessments.values.count { it.smoker }, latestAssessments.size),
            obesityIndicator = Ratio(bmiMeasured.count { it.bmi!! >= t.bmiObese1 }, bmiMeasured.size),
            physicalInactivity = Ratio(latestAssessments.values.count { it.activeMinutesPerWeek < t.activeMinutesPerWeekGoal }, latestAssessments.size),
            riskDistribution = dist,
            map = cells,
        )
    }
}
