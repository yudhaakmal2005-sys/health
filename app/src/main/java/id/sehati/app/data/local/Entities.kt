package id.sehati.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/*
 * Semua data kesehatan memakai userId = SEHATI ID (mis. "HM-000127") sebagai kunci.
 * Entitas yang disinkronkan membawa: createdAt, updatedAt, syncStatus, serverId, version.
 */

@Serializable
@Entity(tableName = "users", indices = [Index("role"), Index("rw")])
data class UserEntity(
    @PrimaryKey val sehatiId: String,
    val fullName: String,
    val birthDate: String, // ISO yyyy-MM-dd
    val sex: String,
    val village: String,
    val rw: String,
    val rt: String = "",
    val phone: String? = null,
    val role: String = "WARGA",
    val qrToken: String,
    val goals: String = "", // csv
    val consentLocal: Boolean = false,
    val consentServerSync: Boolean = false,
    val consentHealthConnect: Boolean = false,
    val consentAt: Long? = null,
    val onboardingDone: Boolean = false,
    val assessmentDone: Boolean = false,
    val hasAccount: Boolean = true,
    val registeredBy: String? = null,
    val householdId: String? = null,
    val isDemo: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String = "LOCAL_ONLY",
    val serverId: String? = null,
    val version: Int = 1,
)

@Entity(tableName = "credentials")
data class CredentialEntity(
    @PrimaryKey val sehatiId: String,
    val salt: String,
    val hash: String,
    val iterations: Int,
    val failedAttempts: Int = 0,
    val lockedUntil: Long = 0,
)

@Serializable
@Entity(tableName = "households")
data class HouseholdEntity(
    @PrimaryKey val id: String,
    val headName: String,
    val rw: String,
    val rt: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String = "LOCAL_ONLY",
    val serverId: String? = null,
    val version: Int = 1,
)

@Serializable
@Entity(tableName = "facilities")
data class FacilityEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String, // POSYANDU / PUSKESMAS
    val village: String,
    val rw: String = "",
)

@Serializable
@Entity(tableName = "cadres")
data class CadreEntity(
    @PrimaryKey val sehatiId: String,
    val facilityId: String,
    val assignedRw: String,
    val active: Boolean = true,
    val updatedAt: Long,
    val syncStatus: String = "LOCAL_ONLY",
    val serverId: String? = null,
    val version: Int = 1,
)

@Serializable
@Entity(tableName = "health_profiles")
data class HealthProfileEntity(
    @PrimaryKey val userId: String,
    val level: String,
    val findingsJson: String,
    val planJson: String,
    val rulesetVersion: String,
    val computedAt: Long,
    val syncStatus: String = "LOCAL_ONLY",
    val serverId: String? = null,
    val version: Int = 1,
)

@Serializable
@Entity(
    tableName = "health_assessments",
    foreignKeys = [ForeignKey(UserEntity::class, ["sehatiId"], ["userId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("userId")],
)
data class HealthAssessmentEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val takenAt: Long,
    val heightCm: Float,
    val weightKg: Float,
    val waistCm: Float,
    val knownHypertension: Boolean,
    val knownDiabetes: Boolean,
    val knownDyslipidemia: Boolean,
    val knownHeartDisease: Boolean,
    val knownKidneyDisease: Boolean,
    val otherConditions: String,
    val familyHypertension: Boolean,
    val familyDiabetes: Boolean,
    val familyCardio: Boolean,
    val smokingStatus: String, // NEVER FORMER CURRENT
    val smokingProduct: String,
    val cigarettesPerDay: Int,
    val vegetableDays: Int,
    val fruitDays: Int,
    val saltyFrequent: Boolean,
    val sugaryFrequent: Boolean,
    val fattyFrequent: Boolean,
    val activeDays: Int,
    val activeMinutes: Int,
    val activityIntensity: String,
    val sedentaryHours: Int,
    val sleepHours: Float,
    val sleepQuality: String,
    val stressLevel: Int,
    val bpSystolic: Int? = null,
    val bpDiastolic: Int? = null,
    val heartRate: Int? = null,
    val glucose: Float? = null,
    val cholesterol: Float? = null,
    val redFlagSymptom: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String = "LOCAL_ONLY",
    val serverId: String? = null,
    val version: Int = 1,
)

/** Header sebuah pemeriksaan; nilai pengukuran ada pada tabel anak (antropometri, TD, gula, lipid). */
@Serializable
@Entity(
    tableName = "health_measurements",
    foreignKeys = [ForeignKey(UserEntity::class, ["sehatiId"], ["userId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("userId"), Index("measuredAt"), Index("visitId")],
)
data class HealthMeasurementEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val measuredAt: Long,
    val source: String, // DataSource
    val operatorId: String? = null,
    val facilityId: String? = null,
    val visitId: String? = null,
    val notes: String = "",
    val verification: String = "UNVERIFIED",
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String = "LOCAL_ONLY",
    val serverId: String? = null,
    val version: Int = 1,
)

@Serializable
@Entity(
    tableName = "anthropometry",
    foreignKeys = [ForeignKey(HealthMeasurementEntity::class, ["id"], ["measurementId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("measurementId"), Index("userId")],
)
data class AnthropometryEntity(
    @PrimaryKey val id: String,
    val measurementId: String,
    val userId: String,
    val weightKg: Float?,
    val heightCm: Float?,
    val waistCm: Float?,
    val bmi: Float?,
)

@Serializable
@Entity(
    tableName = "blood_pressure",
    foreignKeys = [ForeignKey(HealthMeasurementEntity::class, ["id"], ["measurementId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("measurementId"), Index("userId")],
)
data class BloodPressureEntity(
    @PrimaryKey val id: String,
    val measurementId: String,
    val userId: String,
    val systolic: Int,
    val diastolic: Int,
    val heartRate: Int?,
)

@Serializable
@Entity(
    tableName = "blood_glucose",
    foreignKeys = [ForeignKey(HealthMeasurementEntity::class, ["id"], ["measurementId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("measurementId"), Index("userId")],
)
data class BloodGlucoseEntity(
    @PrimaryKey val id: String,
    val measurementId: String,
    val userId: String,
    val mgDl: Float,
    val fasting: Boolean,
)

@Serializable
@Entity(
    tableName = "lipid_measurements",
    foreignKeys = [ForeignKey(HealthMeasurementEntity::class, ["id"], ["measurementId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("measurementId"), Index("userId")],
)
data class LipidMeasurementEntity(
    @PrimaryKey val id: String,
    val measurementId: String,
    val userId: String,
    val totalCholesterol: Float,
)

@Serializable
@Entity(tableName = "activity_sessions", indices = [Index("userId"), Index("startAt")])
data class ActivitySessionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val kind: String, // MovementKind
    val startAt: Long,
    val endAt: Long,
    val distanceMeters: Float = 0f,
    val steps: Int = 0,
    val kcal: Int = 0,
    val source: String = "SELF",
    val usedLocation: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String = "LOCAL_ONLY",
    val serverId: String? = null,
    val version: Int = 1,
)

@Serializable
@Entity(tableName = "sleep_records", indices = [Index("userId"), Index("dateIso")])
data class SleepRecordEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val dateIso: String,
    val startAt: Long,
    val endAt: Long,
    val minutes: Int,
    val quality: Int? = null,
    val source: String = "SELF",
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String = "LOCAL_ONLY",
    val serverId: String? = null,
    val version: Int = 1,
)

@Serializable
@Entity(tableName = "food_entries", indices = [Index("userId"), Index("dateIso")])
data class FoodEntryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val dateIso: String,
    val loggedAt: Long,
    val meal: String,
    val foodId: String?,
    val name: String,
    val portions: Float,
    val portionLabel: String,
    val kcal: Int,
    val carbs: Float,
    val protein: Float,
    val fat: Float,
    val sugar: Float,
    val fiber: Float,
    val sodiumMg: Float,
    val heartFriendly: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String = "LOCAL_ONLY",
    val serverId: String? = null,
    val version: Int = 1,
)

/** Satu baris per pengguna per hari: air, langkah (mis. dari Health Connect), tugas rencana selesai. */
@Serializable
@Entity(tableName = "habit_logs", indices = [Index("userId"), Index("dateIso")])
data class HabitLogEntity(
    @PrimaryKey val id: String, // "$userId|$dateIso"
    val userId: String,
    val dateIso: String,
    val waterGlasses: Int = 0,
    val steps: Int = 0,
    val stepsSource: String = "SELF",
    val completedPlanItems: String = "", // csv id
    val updatedAt: Long,
    val syncStatus: String = "LOCAL_ONLY",
    val serverId: String? = null,
    val version: Int = 1,
)

@Serializable
@Entity(tableName = "smoking_records", indices = [Index("userId"), Index("dateIso")])
data class SmokingRecordEntity(
    @PrimaryKey val id: String, // "$userId|$dateIso"
    val userId: String,
    val dateIso: String,
    val cigarettes: Int,
    val updatedAt: Long,
    val syncStatus: String = "LOCAL_ONLY",
    val serverId: String? = null,
    val version: Int = 1,
)

@Serializable
@Entity(tableName = "education_progress", indices = [Index("userId")])
data class EducationProgressEntity(
    @PrimaryKey val id: String, // "$userId|$moduleId"
    val userId: String,
    val moduleId: String,
    val readAt: Long?,
    val quizScore: Int?,
    val quizTotal: Int?,
    val completedAt: Long?,
    val updatedAt: Long,
    val syncStatus: String = "LOCAL_ONLY",
    val serverId: String? = null,
    val version: Int = 1,
)

@Serializable
@Entity(tableName = "posyandu_visits", indices = [Index("userId"), Index("cadreId"), Index("dateIso")])
data class PosyanduVisitEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val cadreId: String,
    val facilityId: String?,
    val dateIso: String,
    val registeredAt: Long,
    val step: String, // VisitStep
    val status: String, // IN_PROGRESS / COMPLETED
    val measurementId: String? = null,
    val educationModules: String = "",
    val notes: String = "",
    val validatedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String = "LOCAL_ONLY",
    val serverId: String? = null,
    val version: Int = 1,
)

@Serializable
@Entity(tableName = "follow_ups", indices = [Index("userId"), Index("status"), Index("assignedCadreId")])
data class FollowUpEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val visitId: String? = null,
    val type: String,
    val reasonCode: String,
    val reason: String,
    val priority: Int,
    val status: String,
    val assignedCadreId: String? = null,
    val dueAt: Long,
    val createdBy: String,
    val closedAt: Long? = null,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String = "LOCAL_ONLY",
    val serverId: String? = null,
    val version: Int = 1,
)

@Serializable
@Entity(tableName = "referrals", indices = [Index("userId"), Index("followUpId")])
data class ReferralEntity(
    @PrimaryKey val id: String,
    val followUpId: String,
    val userId: String,
    val facilityId: String?,
    val reason: String,
    val status: String, // REQUESTED / ACCEPTED / COMPLETED
    val verifiedBy: String?,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String = "LOCAL_ONLY",
    val serverId: String? = null,
    val version: Int = 1,
)

@Serializable
@Entity(tableName = "home_visits", indices = [Index("userId"), Index("cadreId"), Index("followUpId")])
data class HomeVisitEntity(
    @PrimaryKey val id: String,
    val followUpId: String,
    val userId: String,
    val cadreId: String,
    val scheduledAt: Long,
    val arrivedAt: Long? = null,
    val status: String, // HomeVisitStatus
    val citizenVerified: Boolean = false,
    val assessmentNote: String = "",
    val educationNote: String = "",
    val actionNote: String = "",
    val closedAt: Long? = null,
    val measurementId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String = "LOCAL_ONLY",
    val serverId: String? = null,
    val version: Int = 1,
)

@Serializable
@Entity(tableName = "logistics_items")
data class LogisticsItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val unit: String,
    val stock: Int,
    val minStock: Int,
    val facilityId: String?,
    val updatedAt: Long,
    val syncStatus: String = "LOCAL_ONLY",
    val serverId: String? = null,
    val version: Int = 1,
)

@Entity(tableName = "sync_queue", indices = [Index(value = ["entityType", "entityId", "version"], unique = true), Index("status")])
data class SyncQueueEntity(
    @PrimaryKey val id: String, // idempotency key
    val entityType: String,
    val entityId: String,
    val subjectId: String?, // SEHATI ID pemilik data (untuk pengecekan consent)
    val operation: String, // UPSERT / DELETE
    val payload: String,
    val version: Int,
    val status: String, // PENDING / SYNCING / DONE / FAILED
    val attempts: Int = 0,
    val lastError: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "notifications", indices = [Index("userId")])
data class NotificationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val kind: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val readAt: Long? = null,
)

@Entity(tableName = "audit_logs", indices = [Index("at")])
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val actorId: String,
    val actorRole: String,
    val action: String,
    val subjectId: String?,
    val detail: String,
    val at: Long,
)
