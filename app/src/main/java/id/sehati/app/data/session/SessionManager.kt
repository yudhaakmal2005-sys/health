package id.sehati.app.data.session

import id.sehati.app.core.security.SecureStore
import id.sehati.app.core.util.Clock
import id.sehati.app.domain.model.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Session(val sehatiId: String, val role: Role, val expiresAt: Long)

/**
 * Sesi login disimpan terenkripsi. Warga: 30 hari. Kader/Admin: 12 jam (least privilege, token kedaluwarsa).
 */
class SessionManager(private val store: SecureStore, private val clock: Clock) {
    private val _session = MutableStateFlow(load())
    val session: StateFlow<Session?> = _session.asStateFlow()

    fun start(sehatiId: String, role: Role): Session {
        val ttl = if (role == Role.WARGA) 30L * 24 * 3600_000 else 12L * 3600_000
        val s = Session(sehatiId, role, clock.now() + ttl)
        store.putString(KEY, "${s.sehatiId}|${s.role.name}|${s.expiresAt}")
        _session.value = s
        return s
    }

    fun end() { store.remove(KEY); _session.value = null }

    /** Dipanggil saat app dibuka / berpindah layar: menghapus sesi kedaluwarsa. */
    fun validate(): Session? {
        val s = _session.value ?: return null
        if (clock.now() > s.expiresAt) { end(); return null }
        return s
    }

    private fun load(): Session? {
        val raw = store.getString(KEY) ?: return null
        val p = raw.split('|')
        if (p.size != 3) return null
        val exp = p[2].toLongOrNull() ?: return null
        if (clock.now() > exp) { store.remove(KEY); return null }
        return Session(p[0], Role.parse(p[1]), exp)
    }

    private companion object { const val KEY = "session" }
}
