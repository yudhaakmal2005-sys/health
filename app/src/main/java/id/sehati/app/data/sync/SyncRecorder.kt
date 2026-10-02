package id.sehati.app.data.sync

import id.sehati.app.core.util.Clock
import id.sehati.app.core.util.Ids
import id.sehati.app.data.local.SyncQueueEntity
import id.sehati.app.data.local.SystemDao
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** Mencatat setiap perubahan data ke antrean sinkronisasi (dipanggil di dalam transaksi Room yang sama). */
class SyncRecorder(private val dao: SystemDao, private val clock: Clock, private val json: Json) {
    suspend fun <T> record(type: String, id: String, subjectId: String?, version: Int, serializer: KSerializer<T>, entity: T, op: String = "UPSERT") {
        val now = clock.now()
        dao.enqueue(
            SyncQueueEntity(
                id = Ids.uuid(), entityType = type, entityId = id, subjectId = subjectId, operation = op,
                payload = json.encodeToString(serializer, entity), version = version,
                status = "PENDING", createdAt = now, updatedAt = now,
            ),
        )
    }
}

/** Pemetaan jenis entitas ke tabel Room (daftar putih) untuk memperbarui status sinkron. */
object SyncTables {
    data class T(val table: String, val pk: String, val hasVersion: Boolean = true)
    val map = mapOf(
        "user" to T("users", "sehatiId"),
        "household" to T("households", "id"),
        "cadre" to T("cadres", "sehatiId"),
        "profile" to T("health_profiles", "userId"),
        "assessment" to T("health_assessments", "id"),
        "measurement" to T("health_measurements", "id"),
        "activity" to T("activity_sessions", "id"),
        "sleep" to T("sleep_records", "id"),
        "food" to T("food_entries", "id"),
        "habit" to T("habit_logs", "id"),
        "smoking" to T("smoking_records", "id"),
        "education" to T("education_progress", "id"),
        "visit" to T("posyandu_visits", "id"),
        "followup" to T("follow_ups", "id"),
        "referral" to T("referrals", "id"),
        "homevisit" to T("home_visits", "id"),
        "logistics" to T("logistics_items", "id"),
    )
}
