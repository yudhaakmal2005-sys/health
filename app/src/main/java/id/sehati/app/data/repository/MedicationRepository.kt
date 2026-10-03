package id.sehati.app.data.repository

import androidx.room.withTransaction
import id.sehati.app.core.util.Clock
import id.sehati.app.core.util.Ids
import id.sehati.app.data.local.MedicationEntity
import id.sehati.app.data.local.MedicationLogEntity
import id.sehati.app.data.local.SehatiDatabase
import id.sehati.app.data.sync.SyncRecorder
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Satu jadwal minum hari ini: obat + jam + sudah/belum. */
data class Dose(val medication: MedicationEntity, val time: String, val taken: Boolean)

/**
 * "Obat saya": pengingat minum obat sesuai resep tenaga kesehatan. Aplikasi tidak menyarankan obat atau dosis.
 * Kepatuhan minum obat penting untuk tekanan darah, gula darah, dan kolesterol.
 */
class MedicationRepository(private val db: SehatiDatabase, private val sync: SyncRecorder, private val clock: Clock) {
    private val dao get() = db.dailyDao()

    fun observe(userId: String): Flow<List<MedicationEntity>> = dao.observeMedications(userId)
    fun observeLogs(userId: String, from: LocalDate): Flow<List<MedicationLogEntity>> = dao.observeMedLogs(userId, from.toString())
    suspend fun active(): List<MedicationEntity> = dao.activeMedications()
    suspend fun get(id: String) = dao.medication(id)

    suspend fun save(userId: String, name: String, instructions: String, times: List<String>, id: String? = null): MedicationEntity {
        require(name.isNotBlank()) { "Nama obat wajib diisi." }
        val clean = times.mapNotNull(::normalizeTime).distinct().sorted()
        require(clean.isNotEmpty()) { "Tambahkan minimal satu jam minum." }
        val now = clock.now()
        val cur = id?.let { dao.medication(it) }
        val m = (cur ?: MedicationEntity(Ids.uuid(), userId, name.trim(), instructions.trim(), "", true, now, now)).copy(
            name = name.trim(), instructions = instructions.trim(), times = clean.joinToString(","), active = true,
            updatedAt = now, version = (cur?.version ?: 0) + 1, syncStatus = "LOCAL_ONLY",
        )
        db.withTransaction { dao.upsertMedication(m); sync.record("medication", m.id, userId, m.version, MedicationEntity.serializer(), m) }
        return m
    }

    suspend fun setActive(id: String, active: Boolean) {
        val cur = dao.medication(id) ?: return
        val m = cur.copy(active = active, updatedAt = clock.now(), version = cur.version + 1, syncStatus = "LOCAL_ONLY")
        db.withTransaction { dao.upsertMedication(m); sync.record("medication", m.id, m.userId, m.version, MedicationEntity.serializer(), m) }
    }

    /** Tandai/batalkan dosis pada [date] jam [time]. */
    suspend fun setTaken(medicationId: String, date: LocalDate, time: String, taken: Boolean) {
        val med = dao.medication(medicationId) ?: return
        val id = "$medicationId|$date|$time"
        val now = clock.now()
        val cur = dao.medLog(id)
        if (!taken) {
            if (cur != null) db.withTransaction {
                dao.deleteMedLog(id)
                sync.record("medlog", id, med.userId, cur.version + 1, MedicationLogEntity.serializer(), cur.copy(version = cur.version + 1), op = "DELETE")
            }
            return
        }
        if (cur != null) return
        val log = MedicationLogEntity(id, med.userId, medicationId, date.toString(), time, now, now)
        db.withTransaction { dao.upsertMedLog(log); sync.record("medlog", id, med.userId, log.version, MedicationLogEntity.serializer(), log) }
    }

    companion object {
        fun times(m: MedicationEntity): List<String> = m.times.split(',').filter { it.isNotBlank() }

        fun doses(meds: List<MedicationEntity>, logs: List<MedicationLogEntity>, date: LocalDate): List<Dose> {
            val taken = logs.filter { it.dateIso == date.toString() }.map { "${it.medicationId}|${it.time}" }.toSet()
            return meds.filter { it.active }.flatMap { m -> times(m).map { t -> Dose(m, t, "${m.id}|$t" in taken) } }.sortedBy { it.time }
        }

        /** Persentase dosis yang diminum dalam [days] hari terakhir (termasuk hari ini, hanya dosis yang jamnya sudah lewat). */
        fun adherence(meds: List<MedicationEntity>, logs: List<MedicationLogEntity>, today: LocalDate, nowTime: String, days: Int = 7): Int? {
            var due = 0; var done = 0
            for (d in 0 until days) {
                val date = today.minusDays(d.toLong())
                doses(meds.filter { !java.time.Instant.ofEpochMilli(it.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().isAfter(date) }, logs, date)
                    .filter { date != today || it.time <= nowTime }
                    .forEach { due++; if (it.taken) done++ }
            }
            return if (due == 0) null else done * 100 / due
        }

        fun normalizeTime(raw: String): String? {
            val m = Regex("^(\\d{1,2})[:.](\\d{2})$").find(raw.trim()) ?: return null
            val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
            return if (h in 0..23 && mi in 0..59) "%02d:%02d".format(h, mi) else null
        }
    }
}
