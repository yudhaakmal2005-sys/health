package id.sehati.app.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

sealed interface HcAvailability {
    data object Available : HcAvailability
    data object NeedsUpdate : HcAvailability
    data object Unavailable : HcAvailability
}

data class HcBp(val id: String, val at: Long, val systolic: Int, val diastolic: Int)
data class HcGlucose(val id: String, val at: Long, val mgDl: Float)
data class HcWeight(val id: String, val at: Long, val kg: Float)
data class HcSleep(val id: String, val start: Long, val end: Long)
data class HcExercise(val id: String, val start: Long, val end: Long, val type: Int, val distanceMeters: Float)

/** Pembungkus tipis Health Connect. Izin selalu dicek ulang karena pengguna dapat mencabutnya kapan saja. */
class HealthConnectGateway(private val context: Context) {

    companion object {
        val readPermissions: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(BloodPressureRecord::class),
            HealthPermission.getReadPermission(BloodGlucoseRecord::class),
            HealthPermission.getReadPermission(HydrationRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
        )
        val dataLabels = listOf("Langkah", "Detak jantung", "Tidur", "Tekanan darah", "Gula darah", "Hidrasi", "Latihan", "Berat badan", "Jarak")
    }

    fun availability(): HcAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HcAvailability.Available
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcAvailability.NeedsUpdate
        else -> HcAvailability.Unavailable
    }

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun grantedPermissions(): Set<String> =
        if (availability() == HcAvailability.Available) client.permissionController.getGrantedPermissions() else emptySet()

    suspend fun missingPermissions(): Set<String> = readPermissions - grantedPermissions()

    private fun range(from: Long, to: Long) = TimeRangeFilter.between(Instant.ofEpochMilli(from), Instant.ofEpochMilli(to))

    suspend fun steps(from: Long, to: Long): Int =
        (client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), range(from, to)))[StepsRecord.COUNT_TOTAL] ?: 0L).toInt()

    suspend fun hydrationGlasses(from: Long, to: Long): Int {
        val liters = client.readRecords(ReadRecordsRequest(HydrationRecord::class, range(from, to))).records.sumOf { it.volume.inLiters }
        return (liters * 4.0).toInt() // 1 gelas ≈ 250 ml
    }

    suspend fun sleep(from: Long, to: Long): List<HcSleep> =
        client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, range(from, to))).records
            .map { HcSleep(it.metadata.id, it.startTime.toEpochMilli(), it.endTime.toEpochMilli()) }

    suspend fun bloodPressure(from: Long, to: Long): List<HcBp> =
        client.readRecords(ReadRecordsRequest(BloodPressureRecord::class, range(from, to))).records.map {
            HcBp(it.metadata.id, it.time.toEpochMilli(), it.systolic.inMillimetersOfMercury.toInt(), it.diastolic.inMillimetersOfMercury.toInt())
        }

    suspend fun glucose(from: Long, to: Long): List<HcGlucose> =
        client.readRecords(ReadRecordsRequest(BloodGlucoseRecord::class, range(from, to))).records
            .map { HcGlucose(it.metadata.id, it.time.toEpochMilli(), it.level.inMilligramsPerDeciliter.toFloat()) }

    suspend fun weight(from: Long, to: Long): List<HcWeight> =
        client.readRecords(ReadRecordsRequest(WeightRecord::class, range(from, to))).records
            .map { HcWeight(it.metadata.id, it.time.toEpochMilli(), it.weight.inKilograms.toFloat()) }

    suspend fun exercise(from: Long, to: Long): List<HcExercise> =
        client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, range(from, to))).records.map { r ->
            val dist = client.aggregate(
                AggregateRequest(setOf(DistanceRecord.DISTANCE_TOTAL), range(r.startTime.toEpochMilli(), r.endTime.toEpochMilli())),
            )[DistanceRecord.DISTANCE_TOTAL]?.inMeters?.toFloat() ?: 0f
            HcExercise(r.metadata.id, r.startTime.toEpochMilli(), r.endTime.toEpochMilli(), r.exerciseType, dist)
        }

    suspend fun averageHeartRate(from: Long, to: Long): Int? {
        val bpm = client.readRecords(ReadRecordsRequest(HeartRateRecord::class, range(from, to))).records
            .flatMap { it.samples }.map { it.beatsPerMinute }
        return if (bpm.isEmpty()) null else bpm.average().toInt()
    }
}
