package id.sehati.app.data.repository

import androidx.room.withTransaction
import id.sehati.app.core.util.Clock
import id.sehati.app.core.util.Ids
import id.sehati.app.data.local.*
import id.sehati.app.data.sync.SyncRecorder
import id.sehati.app.domain.model.DataSource
import id.sehati.app.domain.model.RiskLevel
import id.sehati.app.domain.model.VerificationStatus
import id.sehati.app.domain.rules.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Satu hasil pemeriksaan lengkap dengan data asal (provenance). */
data class HealthCheck(
    val id: String,
    val userId: String,
    val measuredAt: Long,
    val source: DataSource,
    val operatorId: String?,
    val facilityId: String?,
    val visitId: String?,
    val notes: String,
    val verification: VerificationStatus,
    val syncStatus: String,
    val weightKg: Float?, val heightCm: Float?, val waistCm: Float?, val bmi: Float?,
    val systolic: Int?, val diastolic: Int?, val heartRate: Int?,
    val glucose: Float?, val glucoseFasting: Boolean,
    val cholesterol: Float?,
) {
    val bloodPressure: Pair<Int, Int>? get() = if (systolic != null && diastolic != null) systolic to diastolic else null
}

fun MeasurementWithDetails.toCheck() = HealthCheck(
    id = header.id, userId = header.userId, measuredAt = header.measuredAt, source = DataSource.parse(header.source),
    operatorId = header.operatorId, facilityId = header.facilityId, visitId = header.visitId, notes = header.notes,
    verification = VerificationStatus.parse(header.verification), syncStatus = header.syncStatus,
    weightKg = anthropometry?.weightKg, heightCm = anthropometry?.heightCm, waistCm = anthropometry?.waistCm, bmi = anthropometry?.bmi,
    systolic = bloodPressure?.systolic, diastolic = bloodPressure?.diastolic, heartRate = bloodPressure?.heartRate,
    glucose = glucose?.mgDl, glucoseFasting = glucose?.fasting ?: false, cholesterol = lipid?.totalCholesterol,
)

data class NewMeasurement(
    val userId: String,
    val source: DataSource,
    val input: MeasurementInput,
    val glucoseFasting: Boolean = false,
    val operatorId: String? = null,
    val facilityId: String? = null,
    val visitId: String? = null,
    val notes: String = "",
    val verified: Boolean = false,
    val measuredAt: Long? = null,
    /** id tetap (mis. dari Health Connect) agar impor ulang tidak menggandakan data. */
    val fixedId: String? = null,
)

@Serializable
data class FindingDto(val id: String, val domain: String, val title: String, val detail: String, val severity: String)
@Serializable
data class PlanDto(val id: String, val title: String, val module: String? = null)

class HealthRepository(
    private val db: SehatiDatabase,
    private val sync: SyncRecorder,
    private val clock: Clock,
    private val json: Json,
) {
    private val dao get() = db.healthDao()
    private val users get() = db.userDao()

    fun observeChecks(userId: String): Flow<List<HealthCheck>> = dao.observeChecks(userId).map { l -> l.map { it.toCheck() } }
    fun observeAllChecks(): Flow<List<HealthCheck>> = dao.observeAllChecks().map { l -> l.map { it.toCheck() } }
    fun observeLatestAssessment(userId: String) = dao.observeLatestAssessment(userId)
    fun observeAllAssessments() = dao.observeAllAssessments()
    fun observeProfile(userId: String) = dao.observeProfile(userId)
    fun observeAllProfiles() = dao.observeAllProfiles()

    suspend fun observeLatestAssessmentOnce(userId: String) = dao.latestAssessment(userId)

    suspend fun checks(userId: String) = dao.checks(userId).map { it.toCheck() }

    /** Menyimpan pemeriksaan (header + tabel anak) lalu menghitung ulang profil, semuanya satu transaksi. */
    suspend fun saveMeasurement(m: NewMeasurement): HealthCheck {
        val issues = MeasurementValidator.validate(m.input)
        require(!MeasurementValidator.hasBlocking(issues)) { issues.first { it.blocking }.message }
        val now = clock.now()
        val id = m.fixedId ?: Ids.uuid()
        return db.withTransaction {
            val existing = dao.check(id)
            if (existing != null && m.fixedId != null) return@withTransaction existing.toCheck()
            val header = HealthMeasurementEntity(
                id = id, userId = m.userId, measuredAt = m.measuredAt ?: now, source = m.source.name,
                operatorId = m.operatorId, facilityId = m.facilityId, visitId = m.visitId, notes = m.notes,
                verification = if (m.verified) VerificationStatus.VERIFIED.name else VerificationStatus.UNVERIFIED.name,
                createdAt = now, updatedAt = now,
            )
            dao.upsertHeader(header)
            val i = m.input
            if (i.weightKg != null || i.heightCm != null || i.waistCm != null) {
                val h = i.heightCm ?: dao.checks(m.userId).firstNotNullOfOrNull { it.anthropometry?.heightCm }
                    ?: dao.latestAssessment(m.userId)?.heightCm
                val bmi = if (i.weightKg != null && h != null) AnthropometryRules.bmi(h, i.weightKg) else null
                dao.upsertAnthropometry(AnthropometryEntity(Ids.uuid(), id, m.userId, i.weightKg, i.heightCm ?: h, i.waistCm, bmi))
            }
            if (i.systolic != null && i.diastolic != null)
                dao.upsertBloodPressure(BloodPressureEntity(Ids.uuid(), id, m.userId, i.systolic, i.diastolic, i.heartRate))
            if (i.glucose != null) dao.upsertGlucose(BloodGlucoseEntity(Ids.uuid(), id, m.userId, i.glucose, m.glucoseFasting))
            if (i.cholesterol != null) dao.upsertLipid(LipidMeasurementEntity(Ids.uuid(), id, m.userId, i.cholesterol))
            sync.record("measurement", id, m.userId, header.version, HealthMeasurementEntity.serializer(), header)
            val saved = dao.check(id)!!
            // Data anak ikut dikirim sebagai satu paket JSON agar server menerima pemeriksaan utuh.
            sync.record("measurement_detail", id, m.userId, header.version, DetailDto.serializer(), DetailDto.from(saved))
            recomputeProfile(m.userId)
            saved.toCheck()
        }
    }

    suspend fun saveAssessment(a: HealthAssessmentEntity) = db.withTransaction {
        dao.upsertAssessment(a)
        sync.record("assessment", a.id, a.userId, a.version, HealthAssessmentEntity.serializer(), a)
        users.get(a.userId)?.let { u ->
            val updated = u.copy(assessmentDone = true, updatedAt = clock.now(), version = u.version + 1, syncStatus = "LOCAL_ONLY")
            users.upsert(updated)
            sync.record("user", u.sehatiId, u.sehatiId, updated.version, UserEntity.serializer(), updated)
        }
        // pengukuran opsional pada asesmen juga masuk riwayat pemeriksaan (sumber: mandiri)
        if (a.bpSystolic != null || a.glucose != null || a.cholesterol != null) {
            val input = MeasurementInput(a.bpSystolic, a.bpDiastolic, a.heartRate, a.weightKg, a.heightCm, a.waistCm, a.glucose, a.cholesterol)
            if (!MeasurementValidator.hasBlocking(MeasurementValidator.validate(input))) {
                saveMeasurementInTx(NewMeasurement(a.userId, DataSource.SELF, input, notes = "Dari asesmen awal", fixedId = "asm-${a.id}"))
            }
        }
        recomputeProfile(a.userId)
    }

    private suspend fun saveMeasurementInTx(m: NewMeasurement) = saveMeasurement(m)

    /** Snapshot data terbaru satu warga (asesmen + pemeriksaan) untuk mesin aturan. */
    suspend fun buildSnapshot(userId: String, today: LocalDate = LocalDate.now()): HealthSnapshot {
        val user = users.get(userId)
        val a = dao.latestAssessment(userId)
        val checks = dao.checks(userId).map { it.toCheck() }
        val bps = checks.mapNotNull { it.bloodPressure }.take(5)
        val latestGlucose = checks.firstOrNull { it.glucose != null }
        return HealthSnapshot(
            age = user?.let { AgeCalc.age(it.birthDate, today) } ?: 0,
            male = user?.sex == "MALE",
            heightCm = checks.firstNotNullOfOrNull { it.heightCm } ?: a?.heightCm ?: 0f,
            weightKg = checks.firstNotNullOfOrNull { it.weightKg } ?: a?.weightKg ?: 0f,
            waistCm = checks.firstNotNullOfOrNull { it.waistCm } ?: a?.waistCm ?: 0f,
            bpReadings = bps, glucose = latestGlucose?.glucose, glucoseFasting = latestGlucose?.glucoseFasting ?: false,
            cholesterol = checks.firstNotNullOfOrNull { it.cholesterol },
            knownHypertension = a?.knownHypertension ?: false, knownDiabetes = a?.knownDiabetes ?: false,
            knownHeartOrKidneyDisease = (a?.knownHeartDisease ?: false) || (a?.knownKidneyDisease ?: false),
            familyHypertension = a?.familyHypertension ?: false, familyDiabetes = a?.familyDiabetes ?: false, familyCardio = a?.familyCardio ?: false,
            smokingStatus = a?.smokingStatus ?: "NEVER", cigarettesPerDay = a?.cigarettesPerDay ?: 0,
            activeDaysPerWeek = a?.activeDays ?: 0, activeMinutesPerSession = a?.activeMinutes ?: 0,
            sedentaryHoursPerDay = a?.sedentaryHours ?: 0,
            vegetableDaysPerWeek = a?.vegetableDays ?: 7, fruitDaysPerWeek = a?.fruitDays ?: 7,
            saltyFoodFrequent = a?.saltyFrequent ?: false, sugaryFrequent = a?.sugaryFrequent ?: false, fattyFrequent = a?.fattyFrequent ?: false,
            sleepHours = a?.sleepHours ?: 7f, stressLevel = a?.stressLevel ?: 1, redFlagSymptom = a?.redFlagSymptom ?: false,
            knownDyslipidemia = a?.knownDyslipidemia ?: false, assessed = a != null,
        )
    }

    /** Faktor risiko jantung koroner dari data terbaru (bukan diagnosis). */
    suspend fun heartRisk(userId: String, today: LocalDate = LocalDate.now()): HeartRiskReport = HeartRisk.evaluate(buildSnapshot(userId, today))

    /** Profil dihitung ulang dari asesmen + pemeriksaan terbaru; disimpan agar dapat dibaca konsisten oleh semua layar. */
    suspend fun recomputeProfile(userId: String, today: LocalDate = LocalDate.now()): HealthProfile {
        val snap = buildSnapshot(userId, today)
        val profile = RiskProfileEngine.evaluate(snap)
        val prev = dao.profile(userId)
        val now = clock.now()
        val entity = HealthProfileEntity(
            userId = userId, level = profile.level.name,
            findingsJson = json.encodeToString(ListSerializer(FindingDto.serializer()),
                profile.findings.map { FindingDto(it.id, it.domain.name, it.title, it.detail, it.severity.name) }),
            planJson = json.encodeToString(ListSerializer(PlanDto.serializer()), profile.plan.map { PlanDto(it.id, it.title, it.module) }),
            rulesetVersion = profile.rulesetVersion, computedAt = now,
            version = (prev?.version ?: 0) + 1,
        )
        dao.upsertProfile(entity)
        sync.record("profile", userId, userId, entity.version, HealthProfileEntity.serializer(), entity)
        return profile
    }

    fun decodeFindings(p: HealthProfileEntity?): List<FindingDto> =
        p?.let { runCatching { json.decodeFromString(ListSerializer(FindingDto.serializer()), it.findingsJson) }.getOrNull() }.orEmpty()

    fun decodePlan(p: HealthProfileEntity?): List<PlanDto> =
        p?.let { runCatching { json.decodeFromString(ListSerializer(PlanDto.serializer()), it.planJson) }.getOrNull() }.orEmpty()

    fun levelOf(p: HealthProfileEntity?): RiskLevel = RiskLevel.parse(p?.level)
}

@Serializable
data class DetailDto(
    val measurementId: String,
    val anthropometry: AnthropometryEntity? = null,
    val bloodPressure: BloodPressureEntity? = null,
    val glucose: BloodGlucoseEntity? = null,
    val lipid: LipidMeasurementEntity? = null,
) {
    companion object {
        fun from(m: MeasurementWithDetails) = DetailDto(m.header.id, m.anthropometry, m.bloodPressure, m.glucose, m.lipid)
    }
}
