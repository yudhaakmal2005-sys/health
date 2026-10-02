package id.sehati.app.data.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord
import id.sehati.app.core.util.Clock
import id.sehati.app.core.util.TimeUtils
import id.sehati.app.data.prefs.SettingsStore
import id.sehati.app.data.local.ActivitySessionEntity
import id.sehati.app.data.repository.DailyRepository
import id.sehati.app.data.repository.HealthRepository
import id.sehati.app.data.repository.NewMeasurement
import id.sehati.app.domain.model.DataSource
import id.sehati.app.domain.model.MovementKind
import id.sehati.app.domain.rules.MeasurementInput
import id.sehati.app.domain.rules.ActivityAggregator

data class HcImportSummary(
    val steps: Int, val sleep: Int, val bp: Int, val glucose: Int, val weight: Int, val exercise: Int, val avgHeartRate: Int?,
)

sealed interface HcSyncResult {
    data class Success(val summary: HcImportSummary) : HcSyncResult
    data object NotAvailable : HcSyncResult
    data object NeedsUpdate : HcSyncResult
    data class PermissionMissing(val missing: Set<String>) : HcSyncResult
    data object NoConsent : HcSyncResult
    data class Error(val message: String) : HcSyncResult
}

/**
 * Health Connect → Room. Semua id impor stabil ("hc-…") sehingga sinkron berulang tidak menggandakan data.
 * Data hanya diproses lokal; unggah ke server mengikuti persetujuan pengguna.
 */
class HealthConnectImporter(
    private val gateway: HealthConnectGateway,
    private val health: HealthRepository,
    private val daily: DailyRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    suspend fun sync(userId: String, consent: Boolean, weightKgForKcal: Float): HcSyncResult {
        if (!consent) return HcSyncResult.NoConsent
        when (gateway.availability()) {
            HcAvailability.Unavailable -> return HcSyncResult.NotAvailable
            HcAvailability.NeedsUpdate -> return HcSyncResult.NeedsUpdate
            HcAvailability.Available -> Unit
        }
        val missing = gateway.missingPermissions()
        if (missing.isNotEmpty()) return HcSyncResult.PermissionMissing(missing)
        return try {
            val now = clock.now()
            val dayStart = TimeUtils.startOfDay(now)
            val week = now - 7 * 86_400_000L
            val month = now - 30 * 86_400_000L

            // Langkah 7 hari terakhir per hari
            var todaySteps = 0
            for (d in 0..6) {
                val from = dayStart - d * 86_400_000L
                val steps = gateway.steps(from, minOf(from + 86_400_000L, now))
                if (steps > 0) daily.setSteps(userId, TimeUtils.dateIso(from), steps, DataSource.HEALTH_CONNECT.name)
                if (d == 0) todaySteps = steps
            }
            val glasses = gateway.hydrationGlasses(dayStart, now)
            if (glasses > 0) {
                val iso = TimeUtils.dateIso(now)
                // tidak menimpa catatan manual yang lebih besar
                val cur = daily.observeHabitOnce(userId, iso)?.waterGlasses ?: 0
                if (glasses > cur) daily.addWater(userId, iso, glasses - cur)
            }
            val sleeps = gateway.sleep(week, now)
            sleeps.forEach { daily.saveSleep(userId, it.start, it.end, null, DataSource.HEALTH_CONNECT.name, "hc-${it.id}") }
            val bps = gateway.bloodPressure(month, now)
            bps.forEach {
                health.saveMeasurement(NewMeasurement(userId, DataSource.HEALTH_CONNECT, MeasurementInput(systolic = it.systolic, diastolic = it.diastolic),
                    measuredAt = it.at, fixedId = "hc-bp-${it.id}", notes = "Diimpor dari Health Connect"))
            }
            val gl = gateway.glucose(month, now)
            gl.forEach {
                health.saveMeasurement(NewMeasurement(userId, DataSource.HEALTH_CONNECT, MeasurementInput(glucose = it.mgDl),
                    measuredAt = it.at, fixedId = "hc-gl-${it.id}", notes = "Diimpor dari Health Connect"))
            }
            val ws = gateway.weight(month, now)
            ws.forEach {
                health.saveMeasurement(NewMeasurement(userId, DataSource.HEALTH_CONNECT, MeasurementInput(weightKg = it.kg),
                    measuredAt = it.at, fixedId = "hc-wt-${it.id}", notes = "Diimpor dari Health Connect"))
            }
            val ex = gateway.exercise(week, now)
            ex.forEach { e ->
                val kind = when (e.type) {
                    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> MovementKind.WALKING
                    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> MovementKind.RUNNING
                    ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> MovementKind.CYCLING
                    else -> MovementKind.EXERCISE
                }
                val minutes = ((e.end - e.start) / 60000L).toInt()
                daily.importActivity(
                    ActivitySessionEntity(
                        id = "hc-ex-${e.id}", userId = userId, kind = kind.name, startAt = e.start, endAt = e.end,
                        distanceMeters = e.distanceMeters, steps = (e.distanceMeters / 0.75f).toInt(),
                        kcal = ActivityAggregator.estimateKcal(kind, minutes, weightKgForKcal), source = DataSource.HEALTH_CONNECT.name,
                        usedLocation = false, createdAt = now, updatedAt = now,
                    ),
                )
            }
            settings.setHealthConnectSynced(now)
            HcSyncResult.Success(HcImportSummary(todaySteps, sleeps.size, bps.size, gl.size, ws.size, ex.size, gateway.averageHeartRate(dayStart, now)))
        } catch (e: SecurityException) {
            HcSyncResult.PermissionMissing(gateway.missingPermissions())
        } catch (e: Exception) {
            HcSyncResult.Error("Gagal membaca Health Connect. Coba lagi nanti.")
        }
    }
}
