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
        NotificationEntity::class, AuditLogEntity::class, ChallengeEntity::class, MedicationEntity::class, MedicationLogEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class SehatiDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun healthDao(): HealthDao
    abstract fun dailyDao(): DailyDao
    abstract fun posyanduDao(): PosyanduDao
    abstract fun systemDao(): SystemDao

    companion object {
        const val NAME = "sehati.db"

        /** v2 → v3: obat & catatan minum obat. */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `medications` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT NOT NULL, `instructions` TEXT NOT NULL, `times` TEXT NOT NULL, `active` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, `serverId` TEXT, `version` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_medications_userId` ON `medications` (`userId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `medication_logs` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `medicationId` TEXT NOT NULL, `dateIso` TEXT NOT NULL, `time` TEXT NOT NULL, `takenAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, `serverId` TEXT, `version` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_medication_logs_userId` ON `medication_logs` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_medication_logs_medicationId` ON `medication_logs` (`medicationId`)")
            }
        }

        /** v1 → v2: tabel tantangan kebiasaan. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `challenges` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `challengeId` TEXT NOT NULL, `startDate` TEXT NOT NULL, `checkIns` TEXT NOT NULL, `completedAt` INTEGER, `updatedAt` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, `serverId` TEXT, `version` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_challenges_userId` ON `challenges` (`userId`)")
            }
        }
    }
}
