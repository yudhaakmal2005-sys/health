package id.sehati.app.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable data class LoginRequest(val sehatiId: String, val password: String, val deviceId: String)
@Serializable data class ActivateRequest(val sehatiId: String, val birthDate: String, val password: String, val deviceId: String)
@Serializable data class RegisterRequest(
    val sehatiId: String, val fullName: String, val birthDate: String, val sex: String, val village: String,
    val rw: String, val rt: String, val phone: String?, val password: String, val consentServerSync: Boolean, val deviceId: String,
)
@Serializable data class RemoteUser(val sehatiId: String, val fullName: String = "", val role: String = "WARGA", val rw: String = "", val village: String = "")
@Serializable data class LoginResponse(val token: String, val expiresAt: Long, val user: RemoteUser)

@Serializable data class ReserveRequest(val deviceId: String, val count: Int, val prefix: String = "HM")
@Serializable data class ReserveResponse(val ids: List<String> = emptyList(), val expiresAt: Long = 0)

@Serializable data class PullItem(
    val seq: Long, val type: String, val entityId: String, val subjectId: String? = null,
    val version: Int = 1, val deleted: Boolean = false, val payload: String? = null,
)
@Serializable data class PullResponse(val items: List<PullItem> = emptyList(), val nextCursor: Long = 0, val hasMore: Boolean = false)

@Serializable data class PosyanduSlot(
    val id: String, val rw: String, val date: String, val startTime: String = "", val endTime: String = "",
    val location: String = "", val notes: String = "",
)
@Serializable data class RemoteConfig(
    val thresholds: JsonElement? = null,
    val thresholdsVersion: Int = 0,
    val emergencyNumbers: String = "119 atau 112",
    val posyandu: List<PosyanduSlot> = emptyList(),
)

@Serializable data class ServerHealth(val status: String = "", val version: String = "", val ai: Boolean = false)

@Serializable data class ApiErrorDetail(val code: String = "", val message: String = "")
@Serializable data class ApiErrorBody(val error: ApiErrorDetail = ApiErrorDetail())

@Serializable data class ChatTurn(val role: String, val content: String)
@Serializable data class ChatContext(val ageBand: String? = null, val sex: String? = null, val factors: List<String> = emptyList(), val steps: Int? = null)
@Serializable data class ChatRequest(val messages: List<ChatTurn>, val context: ChatContext? = null)

/** Hasil panggilan server yang dapat ditangani UI tanpa exception. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data object NotConfigured : ApiResult<Nothing>
    data class Offline(val message: String) : ApiResult<Nothing>
    data class Failure(val status: Int, val code: String, val message: String) : ApiResult<Nothing>
}

sealed interface ChatEvent {
    data class Meta(val emergency: Boolean) : ChatEvent
    data class Delta(val text: String) : ChatEvent
    data class Done(val stopReason: String) : ChatEvent
    data class Failure(val code: String, val message: String) : ChatEvent
}

@Serializable data class NewCadreRequest(val fullName: String, val rw: String, val password: String)
@Serializable data class NewCadreResponse(val sehatiId: String)
