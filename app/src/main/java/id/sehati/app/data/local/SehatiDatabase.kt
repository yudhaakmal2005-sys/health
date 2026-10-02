package id.sehati.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class, CredentialEntity::class, HouseholdEntity::class, FacilityEntity::class, CadreEntity::class,
        HealthProfileEntity::class, HealthAssessmentEntity::class, HealthMeasurementEntity::class,
        AnthropometryEntity::class, BloodPressureEntity::class, BloodGlucoseEntity::class, LipidMeasurementEntity::class,
        ActivitySessionEntity::class, SleepRecordEntity::class, FoodEntryEntity::class, HabitLogEntity::class,
        SmokingRecordEntity::class, EducationProgressEntity::class, PosyanduVisitEntity::class, FollowUpEntity::class,
        ReferralEntity::class, HomeVisitEntity::class, LogisticsItemEntity::class, SyncQueueEntity::class,
        NotificationEntity::class, AuditLogEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class SehatiDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun healthDao(): HealthDao
    abstract fun dailyDao(): DailyDao
    abstract fun posyanduDao(): PosyanduDao
    abstract fun systemDao(): SystemDao

    companion object { const val NAME = "sehati.db" }
}
