package id.sehati.app.data.sync

import kotlinx.serialization.Serializable

@Serializable
data class SyncItemDto(
    val id: String, // idempotency key
    val type: String,
    val entityId: String,
    val operation: String,
    val version: Int,
    val payload: String,
)

@Serializable
data class SyncPushRequest(val deviceId: String, val items: List<SyncItemDto>)

@Serializable
data class SyncAckDto(val id: String, val status: String, val serverId: String? = null, val message: String? = null)

@Serializable
data class SyncPushResponse(val results: List<SyncAckDto>)

/** Status ack dari server. DUPLICATE dianggap sukses (idempotent). */
object AckStatus {
    const val OK = "OK"
    const val DUPLICATE = "DUPLICATE"
    const val CONFLICT = "CONFLICT"
    const val REJECTED = "REJECTED"
}

sealed interface TransportResult {
    data class Success(val acks: List<SyncAckDto>) : TransportResult
    /** Tidak ada jaringan / server tak terjangkau: data aman di perangkat dan dicoba lagi. */
    data class Offline(val reason: String) : TransportResult
    /** Server belum dikonfigurasi (mis. BASE_URL placeholder). */
    data class NotConfigured(val reason: String) : TransportResult
}

interface SyncTransport {
    val label: String
    suspend fun push(request: SyncPushRequest): TransportResult
}
