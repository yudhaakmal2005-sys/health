package id.sehati.app.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class MeasurementWithDetails(
    @Embedded val header: HealthMeasurementEntity,
    @Relation(parentColumn = "id", entityColumn = "measurementId") val anthropometry: AnthropometryEntity?,
    @Relation(parentColumn = "id", entityColumn = "measurementId") val bloodPressure: BloodPressureEntity?,
    @Relation(parentColumn = "id", entityColumn = "measurementId") val glucose: BloodGlucoseEntity?,
    @Relation(parentColumn = "id", entityColumn = "measurementId") val lipid: LipidMeasurementEntity?,
)

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE sehatiId = :id") fun observe(id: String): Flow<UserEntity?>
    @Query("SELECT * FROM users WHERE sehatiId = :id") suspend fun get(id: String): UserEntity?
    @Query("SELECT * FROM users WHERE phone = :phone AND phone IS NOT NULL LIMIT 1") suspend fun getByPhone(phone: String): UserEntity?
    @Query("SELECT * FROM users WHERE role = 'WARGA' ORDER BY fullName") fun observeCitizens(): Flow<List<UserEntity>>
    @Query("SELECT * FROM users WHERE role = :role ORDER BY fullName") fun observeByRole(role: String): Flow<List<UserEntity>>
    @Query("SELECT COUNT(*) FROM users") suspend fun count(): Int
    @Query("SELECT MAX(CAST(SUBSTR(sehatiId, 4) AS INTEGER)) FROM users WHERE sehatiId LIKE 'HM-%'") suspend fun maxIdNumber(): Int?
    @Query("SELECT * FROM users WHERE role = 'WARGA' AND (fullName LIKE '%' || :q || '%' OR sehatiId LIKE '%' || :q || '%') ORDER BY fullName LIMIT 50")
    suspend fun searchCitizens(q: String): List<UserEntity>
    @Upsert suspend fun upsert(user: UserEntity)
    @Upsert suspend fun upsertAll(users: List<UserEntity>)
    @Query("DELETE FROM users WHERE sehatiId = :id") suspend fun delete(id: String)

    @Query("SELECT * FROM credentials WHERE sehatiId = :id") suspend fun credential(id: String): CredentialEntity?
    @Upsert suspend fun upsertCredential(c: CredentialEntity)

    @Query("SELECT * FROM households") fun observeHouseholds(): Flow<List<HouseholdEntity>>
    @Upsert suspend fun upsertHousehold(h: HouseholdEntity)
}

@Dao
interface HealthDao {
    @Query("SELECT * FROM health_assessments WHERE userId = :u ORDER BY takenAt DESC LIMIT 1")
    fun observeLatestAssessment(u: String): Flow<HealthAssessmentEntity?>
    @Query("SELECT * FROM health_assessments WHERE userId = :u ORDER BY takenAt DESC LIMIT 1")
    suspend fun latestAssessment(u: String): HealthAssessmentEntity?
    @Query("SELECT * FROM health_assessments ORDER BY takenAt DESC") fun observeAllAssessments(): Flow<List<HealthAssessmentEntity>>
    @Upsert suspend fun upsertAssessment(a: HealthAssessmentEntity)

    @Upsert suspend fun upsertHeader(h: HealthMeasurementEntity)
    @Upsert suspend fun upsertAnthropometry(a: AnthropometryEntity)
    @Upsert suspend fun upsertBloodPressure(b: BloodPressureEntity)
    @Upsert suspend fun upsertGlucose(g: BloodGlucoseEntity)
    @Upsert suspend fun upsertLipid(l: LipidMeasurementEntity)

    @Transaction @Query("SELECT * FROM health_measurements WHERE userId = :u ORDER BY measuredAt DESC")
    fun observeChecks(u: String): Flow<List<MeasurementWithDetails>>
    @Transaction @Query("SELECT * FROM health_measurements WHERE userId = :u ORDER BY measuredAt DESC")
    suspend fun checks(u: String): List<MeasurementWithDetails>
    @Transaction @Query("SELECT * FROM health_measurements ORDER BY measuredAt DESC")
    fun observeAllChecks(): Flow<List<MeasurementWithDetails>>
    @Transaction @Query("SELECT * FROM health_measurements WHERE id = :id")
    suspend fun check(id: String): MeasurementWithDetails?
    @Query("SELECT COUNT(*) FROM health_measurements WHERE id = :id") suspend fun headerExists(id: String): Int

    @Query("SELECT * FROM health_profiles WHERE userId = :u") suspend fun profile(u: String): HealthProfileEntity?
    @Query("SELECT * FROM health_profiles WHERE userId = :u") fun observeProfile(u: String): Flow<HealthProfileEntity?>
    @Query("SELECT * FROM health_profiles") fun observeAllProfiles(): Flow<List<HealthProfileEntity>>
    @Upsert suspend fun upsertProfile(p: HealthProfileEntity)
}

@Dao
interface DailyDao {
    @Upsert suspend fun upsertActivity(a: ActivitySessionEntity)
    @Query("SELECT * FROM activity_sessions WHERE userId = :u AND startAt >= :from AND startAt < :to ORDER BY startAt")
    fun observeActivity(u: String, from: Long, to: Long): Flow<List<ActivitySessionEntity>>
    @Query("SELECT * FROM activity_sessions WHERE userId = :u ORDER BY startAt DESC LIMIT :limit")
    fun observeRecentActivity(u: String, limit: Int): Flow<List<ActivitySessionEntity>>
    @Query("DELETE FROM activity_sessions WHERE id = :id") suspend fun deleteActivity(id: String)

    @Upsert suspend fun upsertSleep(s: SleepRecordEntity)
    @Query("SELECT * FROM sleep_records WHERE userId = :u AND dateIso = :d ORDER BY startAt DESC")
    fun observeSleep(u: String, d: String): Flow<List<SleepRecordEntity>>

    @Upsert suspend fun upsertFood(f: FoodEntryEntity)
    @Query("SELECT * FROM food_entries WHERE userId = :u AND dateIso = :d ORDER BY loggedAt")
    fun observeFood(u: String, d: String): Flow<List<FoodEntryEntity>>
    @Query("SELECT * FROM food_entries WHERE id = :id") suspend fun food(id: String): FoodEntryEntity?
    @Query("DELETE FROM food_entries WHERE id = :id") suspend fun deleteFood(id: String)

    @Query("SELECT * FROM habit_logs WHERE id = :id") suspend fun habit(id: String): HabitLogEntity?
    @Query("SELECT * FROM habit_logs WHERE userId = :u AND dateIso = :d") fun observeHabit(u: String, d: String): Flow<HabitLogEntity?>
    @Query("SELECT * FROM habit_logs WHERE userId = :u ORDER BY dateIso DESC LIMIT :limit") fun observeRecentHabits(u: String, limit: Int): Flow<List<HabitLogEntity>>
    @Upsert suspend fun upsertHabit(h: HabitLogEntity)

    @Query("SELECT * FROM smoking_records WHERE id = :id") suspend fun smoking(id: String): SmokingRecordEntity?
    @Query("SELECT * FROM smoking_records WHERE userId = :u AND dateIso = :d") fun observeSmoking(u: String, d: String): Flow<SmokingRecordEntity?>
    @Upsert suspend fun upsertSmoking(s: SmokingRecordEntity)

    @Query("SELECT * FROM education_progress WHERE userId = :u") fun observeEducation(u: String): Flow<List<EducationProgressEntity>>
    @Query("SELECT * FROM education_progress WHERE id = :id") suspend fun education(id: String): EducationProgressEntity?
    @Upsert suspend fun upsertEducation(e: EducationProgressEntity)
}

@Dao
interface PosyanduDao {
    @Upsert suspend fun upsertVisit(v: PosyanduVisitEntity)
    @Query("SELECT * FROM posyandu_visits WHERE id = :id") suspend fun visit(id: String): PosyanduVisitEntity?
    @Query("SELECT * FROM posyandu_visits WHERE id = :id") fun observeVisit(id: String): Flow<PosyanduVisitEntity?>
    @Query("SELECT * FROM posyandu_visits WHERE dateIso = :d ORDER BY registeredAt") fun observeVisitsOn(d: String): Flow<List<PosyanduVisitEntity>>
    @Query("SELECT * FROM posyandu_visits ORDER BY registeredAt DESC") fun observeAllVisits(): Flow<List<PosyanduVisitEntity>>
    @Query("SELECT * FROM posyandu_visits WHERE userId = :u ORDER BY registeredAt DESC") fun observeVisitsOf(u: String): Flow<List<PosyanduVisitEntity>>
    @Query("SELECT * FROM posyandu_visits WHERE userId = :u AND dateIso = :d AND status = 'IN_PROGRESS' LIMIT 1")
    suspend fun openVisit(u: String, d: String): PosyanduVisitEntity?

    @Upsert suspend fun upsertFollowUp(f: FollowUpEntity)
    @Query("SELECT * FROM follow_ups WHERE id = :id") suspend fun followUp(id: String): FollowUpEntity?
    @Query("SELECT * FROM follow_ups ORDER BY status = 'DONE', priority DESC, dueAt") fun observeFollowUps(): Flow<List<FollowUpEntity>>
    @Query("SELECT * FROM follow_ups WHERE userId = :u ORDER BY createdAt DESC") fun observeFollowUpsOf(u: String): Flow<List<FollowUpEntity>>
    @Query("SELECT * FROM follow_ups WHERE userId = :u AND reasonCode = :code AND status NOT IN ('DONE','CANCELLED') LIMIT 1")
    suspend fun openFollowUp(u: String, code: String): FollowUpEntity?

    @Upsert suspend fun upsertReferral(r: ReferralEntity)
    @Query("SELECT * FROM referrals ORDER BY createdAt DESC") fun observeReferrals(): Flow<List<ReferralEntity>>
    @Query("SELECT * FROM referrals WHERE userId = :u ORDER BY createdAt DESC") fun observeReferralsOf(u: String): Flow<List<ReferralEntity>>

    @Upsert suspend fun upsertHomeVisit(h: HomeVisitEntity)
    @Query("SELECT * FROM home_visits WHERE id = :id") suspend fun homeVisit(id: String): HomeVisitEntity?
    @Query("SELECT * FROM home_visits ORDER BY scheduledAt") fun observeHomeVisits(): Flow<List<HomeVisitEntity>>

    @Upsert suspend fun upsertCadre(c: CadreEntity)
    @Query("SELECT * FROM cadres") fun observeCadres(): Flow<List<CadreEntity>>
    @Query("SELECT * FROM cadres WHERE sehatiId = :id") suspend fun cadre(id: String): CadreEntity?
    @Upsert suspend fun upsertFacility(f: FacilityEntity)
    @Query("SELECT * FROM facilities") fun observeFacilities(): Flow<List<FacilityEntity>>

    @Upsert suspend fun upsertLogistics(l: LogisticsItemEntity)
    @Query("SELECT * FROM logistics_items ORDER BY name") fun observeLogistics(): Flow<List<LogisticsItemEntity>>
    @Query("SELECT * FROM logistics_items WHERE id = :id") suspend fun logistics(id: String): LogisticsItemEntity?
}

@Dao
interface SystemDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun enqueue(item: SyncQueueEntity): Long
    @Query("SELECT * FROM sync_queue WHERE status IN ('PENDING','FAILED') ORDER BY createdAt LIMIT :limit")
    suspend fun pending(limit: Int): List<SyncQueueEntity>
    @Query("UPDATE sync_queue SET status = :status, attempts = attempts + :inc, lastError = :err, updatedAt = :now WHERE id IN (:ids)")
    suspend fun mark(ids: List<String>, status: String, inc: Int, err: String?, now: Long)
    @Query("SELECT COUNT(*) FROM sync_queue WHERE status IN ('PENDING','FAILED','SYNCING')") fun observePendingCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'FAILED'") fun observeFailedCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'DONE'") fun observeDoneCount(): Flow<Int>
    @Query("SELECT MAX(updatedAt) FROM sync_queue WHERE status = 'DONE'") fun observeLastSync(): Flow<Long?>
    @Query("SELECT * FROM sync_queue ORDER BY createdAt DESC LIMIT :limit") fun observeRecent(limit: Int): Flow<List<SyncQueueEntity>>
    @Query("UPDATE sync_queue SET status = 'PENDING' WHERE status IN ('SYNCING','FAILED')") suspend fun requeueStuck()

    @Upsert suspend fun upsertNotification(n: NotificationEntity)
    @Query("SELECT * FROM notifications WHERE userId = :u ORDER BY createdAt DESC LIMIT 100") fun observeNotifications(u: String): Flow<List<NotificationEntity>>
    @Query("UPDATE notifications SET readAt = :now WHERE userId = :u AND readAt IS NULL") suspend fun markAllRead(u: String, now: Long)

    @Insert suspend fun audit(a: AuditLogEntity)
    @Query("SELECT * FROM audit_logs ORDER BY at DESC LIMIT :limit") fun observeAudit(limit: Int): Flow<List<AuditLogEntity>>
}
