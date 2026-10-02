package id.sehati.app.data.sync

import androidx.room.withTransaction
import id.sehati.app.core.util.Clock
import id.sehati.app.data.local.SehatiDatabase
import id.sehati.app.data.local.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

sealed interface SyncOutcome {
    data class Done(val pushed: Int, val failed: Int) : SyncOutcome
    data class Offline(val pending: Int) : SyncOutcome
    data class NotConfigured(val reason: String) : SyncOutcome
    data object NothingToSync : SyncOutcome
}

data class SyncOverview(val pending: Int, val failed: Int, val done: Int, val lastSyncAt: Long?)

/**
 * Alur: Room → Sync Queue → (jaringan?) → Upload → Server → Konfirmasi → tandai SYNCED.
 * Idempoten: kunci antrean = id mutasi; server menjawab DUPLICATE untuk kiriman ulang.
 */
class SyncEngine(
    private val db: SehatiDatabase,
    private val transportProvider: suspend () -> SyncTransport,
    private val deviceId: suspend () -> String,
    private val clock: Clock,
    private val batchSize: Int = 50,
) {
    private val sys get() = db.systemDao()
    private val users get() = db.userDao()
    private companion object { const val MAX_PER_RUN = 500 }

    fun overview(): Flow<Int> = sys.observePendingCount()

    suspend fun syncNow(): SyncOutcome {
        sys.requeueStuck()
        val all = eligible(sys.pending(MAX_PER_RUN))
        if (all.isEmpty()) return SyncOutcome.NothingToSync
        var pushed = 0
        var failed = 0
        for (batch in all.chunked(batchSize)) {
            db.withTransaction {
                sys.mark(batch.map { it.id }, "SYNCING", 0, null, clock.now())
                batch.forEach { setEntityStatus(it, "SYNCING", null) }
            }
            val request = SyncPushRequest(
                deviceId(),
                batch.map { SyncItemDto(it.id, it.entityType, it.entityId, it.operation, it.version, it.payload) },
            )
            when (val r = transportProvider().push(request)) {
                is TransportResult.Offline -> { revert(batch, r.reason); return SyncOutcome.Offline(all.size - pushed) }
                is TransportResult.NotConfigured -> { revert(batch, r.reason); return SyncOutcome.NotConfigured(r.reason) }
                is TransportResult.Success -> {
                    val byId = r.acks.associateBy { it.id }
                    db.withTransaction {
                        val now = clock.now()
                        for (item in batch) {
                            val ack = byId[item.id]
                            val ok = ack != null && (ack.status == AckStatus.OK || ack.status == AckStatus.DUPLICATE)
                            if (ok) {
                                sys.mark(listOf(item.id), "DONE", 1, null, now)
                                setEntityStatus(item, "SYNCED", ack?.serverId)
                                pushed++
                            } else {
                                val msg = ack?.message ?: ack?.status ?: "Tidak ada konfirmasi dari server"
                                sys.mark(listOf(item.id), "FAILED", 1, msg, now)
                                setEntityStatus(item, "SYNC_FAILED", null)
                                failed++
                            }
                        }
                    }
                }
            }
        }
        return SyncOutcome.Done(pushed, failed)
    }

    /** Hanya data milik pengguna yang menyetujui sinkronisasi server yang boleh diunggah. */
    private suspend fun eligible(items: List<SyncQueueEntity>): List<SyncQueueEntity> = items.filter { item ->
        val subject = item.subjectId ?: return@filter true
        users.get(subject)?.consentServerSync ?: false
    }

    private suspend fun revert(batch: List<SyncQueueEntity>, reason: String) = db.withTransaction {
        sys.mark(batch.map { it.id }, "PENDING", 0, reason, clock.now())
        batch.forEach { setEntityStatus(it, "LOCAL_ONLY", null) }
    }

    private fun setEntityStatus(item: SyncQueueEntity, status: String, serverId: String?) {
        val t = SyncTables.map[item.entityType] ?: return
        val sql = "UPDATE ${t.table} SET syncStatus = ?, serverId = COALESCE(?, serverId) WHERE ${t.pk} = ? AND version <= ?"
        db.openHelper.writableDatabase.execSQL(sql, arrayOf<Any?>(status, serverId, item.entityId, item.version))
    }
}
