package id.sehati.app.data.sync

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import id.sehati.app.data.local.*
import id.sehati.app.data.remote.PullItem
import id.sehati.app.data.repository.DetailDto
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Menerapkan data dari server (pull) ke Room. Hanya versi yang lebih baru yang diterapkan, sehingga perubahan
 * lokal yang belum terkirim tidak tertimpa. Data yang ditarik ditandai SYNCED dan tidak dikirim ulang.
 */
class SyncApplier(private val db: SehatiDatabase, private val json: Json) {
    private val users get() = db.userDao()
    private val health get() = db.healthDao()
    private val daily get() = db.dailyDao()
    private val pos get() = db.posyanduDao()

    data class Result(val applied: Int, val skipped: Int, val subjects: Set<String>)

    suspend fun apply(items: List<PullItem>): Result {
        var applied = 0
        var skipped = 0
        val subjects = mutableSetOf<String>()
        // Akun dulu agar baris anak dengan foreign key ke users tidak gagal.
        val ordered = items.sortedWith(compareBy({ if (it.type == "user") 0 else 1 }, { it.seq }))
        for (item in ordered) {
            val ok = runCatching { db.withTransaction { applyOne(item) } }.getOrDefault(false)
            if (ok) { applied++; item.subjectId?.let(subjects::add) } else skipped++
        }
        return Result(applied, skipped, subjects)
    }

    private suspend fun applyOne(item: PullItem): Boolean {
        if (item.type == "measurement_detail") return applyDetail(item)
        val t = SyncTables.map[item.type] ?: return false
        val local = localVersion(t, item.entityId)
        if (item.deleted) {
            if (local == null) return false
            db.openHelper.writableDatabase.execSQL("DELETE FROM ${t.table} WHERE ${t.pk} = ?", arrayOf(item.entityId))
            return true
        }
        if (local != null && local >= item.version) return false
        val p = item.payload ?: return false
        when (item.type) {
            "user" -> users.upsert(decode(UserEntity.serializer(), p))
            "household" -> users.upsertHousehold(decode(HouseholdEntity.serializer(), p))
            "cadre" -> pos.upsertCadre(decode(CadreEntity.serializer(), p))
            "profile" -> health.upsertProfile(decode(HealthProfileEntity.serializer(), p))
            "assessment" -> health.upsertAssessment(decode(HealthAssessmentEntity.serializer(), p))
            "measurement" -> health.upsertHeader(decode(HealthMeasurementEntity.serializer(), p))
            "activity" -> daily.upsertActivity(decode(ActivitySessionEntity.serializer(), p))
            "sleep" -> daily.upsertSleep(decode(SleepRecordEntity.serializer(), p))
            "food" -> daily.upsertFood(decode(FoodEntryEntity.serializer(), p))
            "habit" -> daily.upsertHabit(decode(HabitLogEntity.serializer(), p))
            "smoking" -> daily.upsertSmoking(decode(SmokingRecordEntity.serializer(), p))
            "education" -> daily.upsertEducation(decode(EducationProgressEntity.serializer(), p))
            "challenge" -> daily.upsertChallenge(decode(ChallengeEntity.serializer(), p))
            "medication" -> daily.upsertMedication(decode(MedicationEntity.serializer(), p))
            "medlog" -> daily.upsertMedLog(decode(MedicationLogEntity.serializer(), p))
            "visit" -> pos.upsertVisit(decode(PosyanduVisitEntity.serializer(), p))
            "followup" -> pos.upsertFollowUp(decode(FollowUpEntity.serializer(), p))
            "referral" -> pos.upsertReferral(decode(ReferralEntity.serializer(), p))
            "homevisit" -> pos.upsertHomeVisit(decode(HomeVisitEntity.serializer(), p))
            "logistics" -> pos.upsertLogistics(decode(LogisticsItemEntity.serializer(), p))
            else -> return false
        }
        db.openHelper.writableDatabase.execSQL(
            "UPDATE ${t.table} SET syncStatus = 'SYNCED' WHERE ${t.pk} = ?", arrayOf(item.entityId),
        )
        return true
    }

    private suspend fun applyDetail(item: PullItem): Boolean {
        val d = decode(DetailDto.serializer(), item.payload ?: return false)
        if (health.check(d.measurementId) == null) return false
        d.anthropometry?.let { health.upsertAnthropometry(it) }
        d.bloodPressure?.let { health.upsertBloodPressure(it) }
        d.glucose?.let { health.upsertGlucose(it) }
        d.lipid?.let { health.upsertLipid(it) }
        return true
    }

    private fun localVersion(t: SyncTables.T, id: String): Int? =
        db.query(SimpleSQLiteQuery("SELECT version FROM ${t.table} WHERE ${t.pk} = ?", arrayOf(id))).use { c ->
            if (c.moveToFirst()) c.getInt(0) else null
        }

    private fun <T> decode(s: KSerializer<T>, p: String): T = json.decodeFromString(s, p)
}
