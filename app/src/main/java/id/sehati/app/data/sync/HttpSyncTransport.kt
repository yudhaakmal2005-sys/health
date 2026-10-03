package id.sehati.app.data.sync

import id.sehati.app.data.remote.ApiResult
import id.sehati.app.data.remote.ServerClient

/** Transport nyata via HTTPS ke server SEHATI (VPS). Alamat & token dikelola [ServerClient]. */
class HttpSyncTransport(private val server: ServerClient) : SyncTransport {
    override val label = "Server SEHATI"
    override val supportsPull: Boolean get() = true

    override suspend fun push(request: SyncPushRequest): TransportResult {
        if (!server.isConfigured()) return TransportResult.NotConfigured("Alamat server belum diatur. Atur di Profil → Server SEHATI.")
        if (server.token() == null) return TransportResult.NotConfigured("Belum tertaut ke server. Masuk ulang saat online untuk menautkan akun.")
        return when (val r = server.call { it.push(request) }) {
            is ApiResult.Ok -> TransportResult.Success(r.value.results)
            is ApiResult.Offline -> TransportResult.Offline(r.message)
            ApiResult.NotConfigured -> TransportResult.NotConfigured("Alamat server belum diatur.")
            is ApiResult.Failure ->
                if (r.status in 500..599 || r.status == 429 || r.status == 401) TransportResult.Offline(r.message)
                else TransportResult.Success(request.items.map { SyncAckDto(it.id, AckStatus.REJECTED, null, r.message) })
        }
    }
}
