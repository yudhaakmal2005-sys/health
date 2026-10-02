package id.sehati.app.data.repository

import id.sehati.app.data.local.*
import id.sehati.app.domain.model.FollowUpStatus
import id.sehati.app.domain.model.RiskLevel
import id.sehati.app.domain.rules.*

/** Satu pemetaan Room → analitik, dipakai Dashboard Admin dan tes integrasi (sumber data yang sama). */
object AnalyticsMapper {
    fun stats(
        users: List<UserEntity>, checks: List<HealthCheck>, assessments: List<HealthAssessmentEntity>,
        follow: List<FollowUpEntity>, profiles: List<HealthProfileEntity>,
    ): CommunityStats = CommunityAnalytics.calculate(
        citizens = users.map { CitizenRec(it.sehatiId, it.rw, it.assessmentDone) },
        checks = checks.map { CheckRec(it.userId, it.measuredAt, it.systolic, it.diastolic, it.glucose, it.bmi) },
        assessments = assessments.map { AssessmentRec(it.userId, it.smokingStatus == "CURRENT", it.activeDays * it.activeMinutes) },
        followUps = follow.map { FollowUpRec(it.id, it.userId, FollowUpStatus.parse(it.status), it.priority, it.reason, it.dueAt) },
        profiles = profiles.map { ProfileRec(it.userId, RiskLevel.parse(it.level)) },
    )
}
