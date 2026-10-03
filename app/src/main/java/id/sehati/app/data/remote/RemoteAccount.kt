package id.sehati.app.data.remote

import id.sehati.app.core.util.Clock
import id.sehati.app.core.util.Ids
import id.sehati.app.data.local.CredentialEntity
import id.sehati.app.data.local.SehatiDatabase
import id.sehati.app.data.local.UserEntity
import id.sehati.app.data.prefs.SettingsStore
import id.sehati.app.data.sync.SyncApplier
import id.sehati.app.domain.model.Role
import id.sehati.app.domain.rules.ClinicalConfig
import id.sehati.app.domain.rules.ClinicalThresholds
import id.sehati.app.domain.rules.PasswordHasher
import id.sehati.app.domain.rules.QrPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

sealed interface LinkResult {
    data object Linked : LinkResult
    data object Skipped : LinkResult
    data class Failed(val message: String) : LinkResult
}

data class PullOutcome(val applied: Int, val pages: Int, val error: String? = null)

/**
 * Akun server + data bersama: login/daftar/aktivasi ke server, tarik data (pull), kolam SEHATI ID, dan konfigurasi
 * (jadwal Posyandu, ambang). Semua opsional: tanpa server aplikasi tetap berjalan penuh secara lokal.
 */
class RemoteAccount(
    private val server: ServerClient,
    private val db: SehatiDatabase,
    private val settings: SettingsStore,
    private val applier: SyncApplier,
    private val json: Json,
    private val clock: Clock,
) {
    private val users get() = db.userDao()

    suspend fun isConfigured() = server.isConfigured()
    val isLinked: Boolean get() = server.hasToken

    private suspend fun deviceId() = settings.ensureDeviceId { Ids.uuid() }

    /** Login lokal sukses → pastikan akun server juga tertaut (dipanggil di latar belakang). */
    suspend fun ensureLinked(user: UserEntity, password: String): LinkResult {
        if (!server.isConfigured()) return LinkResult.Skipped
        if (user.role == Role.WARGA.name && !user.consentServerSync) return LinkResult.Skipped
        if (server.token() != null && server.tokenOwner() == user.sehatiId) return LinkResult.Linked
        when (val r = server.call { it.login(LoginRequest(user.sehatiId, password, deviceId())) }) {
            is ApiResult.Ok -> { server.saveToken(r.value); return LinkResult.Linked }
            is ApiResult.Failure -> if (r.status != 401 || user.role != Role.WARGA.name) return LinkResult.Failed(r.message)
            is ApiResult.Offline -> return LinkResult.Failed(r.message)
            ApiResult.NotConfigured -> return LinkResult.Skipped
        }
        // Akun warga belum ada di server: daftarkan dengan data yang sama.
        val reg = RegisterRequest(
            user.sehatiId, user.fullName, user.birthDate, user.sex, user.village, user.rw, user.rt, user.phone,
            password, true, deviceId(),
        )
        return when (val r = server.call { it.register(reg) }) {
            is ApiResult.Ok -> { server.saveToken(r.value); LinkResult.Linked }
            is ApiResult.Failure -> LinkResult.Failed(if (r.code == "ID_TAKEN") "SEHATI ID ini sudah dipakai di server. Hubungi kader Posyandu." else r.message)
            is ApiResult.Offline -> LinkResult.Failed(r.message)
            ApiResult.NotConfigured -> LinkResult.Skipped
        }
    }

    /**
     * Masuk di perangkat baru: akun belum ada di HP ini. Bila server mengenali akun, profil lokal dibuat,
     * kata sandi disimpan sebagai hash agar berikutnya bisa masuk offline, lalu data ditarik dari server.
     */
    suspend fun loginNewDevice(id: String, password: String): ApiResult<UserEntity> =
        adopt(password) { server.call { it.login(LoginRequest(id, password, deviceId())) } }

    /** Warga yang didaftarkan kader mengaktifkan akunnya sendiri (verifikasi tanggal lahir). */
    suspend fun activate(id: String, birthDate: String, password: String): ApiResult<UserEntity> =
        adopt(password) { server.call { it.activate(ActivateRequest(id, birthDate, password, deviceId())) } }

    private suspend fun adopt(password: String, call: suspend () -> ApiResult<LoginResponse>): ApiResult<UserEntity> {
        val r = call()
        if (r !is ApiResult.Ok) @Suppress("UNCHECKED_CAST") return r as ApiResult<UserEntity>
        server.saveToken(r.value)
        val ru = r.value.user
        val now = clock.now()
        if (users.get(ru.sehatiId) == null) {
            users.upsert(
                UserEntity(
                    sehatiId = ru.sehatiId, fullName = ru.fullName, birthDate = "", sex = "FEMALE", village = ru.village, rw = ru.rw, rt = "",
                    phone = null, role = ru.role, qrToken = QrPayload.newToken(), consentLocal = true, consentServerSync = true,
                    consentAt = now, onboardingDone = true, assessmentDone = ru.role != Role.WARGA.name, createdAt = now, updatedAt = now,
                    syncStatus = "SYNCED", version = 0,
                ),
            )
        }
        val h = PasswordHasher.hash(password.toCharArray())
        users.upsertCredential(CredentialEntity(ru.sehatiId, h.salt, h.hash, h.iterations))
        withTimeoutOrNull(25_000) { pullNow() }
        return ApiResult.Ok(users.get(ru.sehatiId)!!)
    }

    /** Tarik semua perubahan sejak kursor terakhir (bertahap per halaman). */
    suspend fun pullNow(): PullOutcome {
        val owner = server.tokenOwner() ?: return PullOutcome(0, 0, "Belum tertaut ke server")
        if (server.token() == null) return PullOutcome(0, 0, "Sesi server berakhir")
        var cursor = settings.pullCursor(owner)
        var applied = 0
        var pages = 0
        while (pages < 40) {
            when (val r = server.call { it.pull(cursor) }) {
                is ApiResult.Ok -> {
                    applied += applier.apply(r.value.items).applied
                    cursor = maxOf(cursor, r.value.nextCursor)
                    settings.setPullCursor(owner, cursor)
                    pages++
                    if (!r.value.hasMore || r.value.items.isEmpty()) return PullOutcome(applied, pages)
                }
                is ApiResult.Failure -> return PullOutcome(applied, pages, r.message)
                is ApiResult.Offline -> return PullOutcome(applied, pages, r.message)
                ApiResult.NotConfigured -> return PullOutcome(applied, pages, "Server belum diatur")
            }
        }
        return PullOutcome(applied, pages)
    }

    /** Konfigurasi bersama: jadwal Posyandu dan ambang klinis terpusat. */
    suspend fun refreshConfig(): RemoteConfig? {
        val r = server.call { it.config() }
        if (r !is ApiResult.Ok) return null
        settings.setRemoteConfigJson(json.encodeToString(RemoteConfig.serializer(), r.value))
        applyThresholds(r.value)
        return r.value
    }

    val config: Flow<RemoteConfig?> = settings.remoteConfigJson.map { raw ->
        raw?.let { runCatching { json.decodeFromString(RemoteConfig.serializer(), it) }.getOrNull() }
    }

    /** Dipanggil saat aplikasi dibuka agar ambang terpusat langsung berlaku walau offline. */
    suspend fun applyCachedConfig() {
        config.first()?.let(::applyThresholds)
    }

    fun applyThresholds(c: RemoteConfig) {
        val el = c.thresholds ?: return
        val t = runCatching { json.decodeFromJsonElement(ClinicalThresholds.serializer(), el) }.getOrNull() ?: return
        if (t.validate() == null) ClinicalConfig.current = t
    }

    /** Ambil SEHATI ID dari kolam; isi ulang dari server bila tersedia. Null → pakai penghitung lokal. */
    suspend fun takeReservedId(refillTo: Int = 1): String? {
        var pool = settings.idPool()
        if (pool.isEmpty() && server.isConfigured()) {
            val count = if (server.token() != null) maxOf(refillTo, 1) else 1
            val r = withTimeoutOrNull(5_000) { server.call { it.reserve(ReserveRequest(deviceId(), count)) } }
            if (r is ApiResult.Ok) pool = r.value.ids
        }
        val next = pool.firstOrNull { users.get(it) == null } ?: return null
        settings.setIdPool(pool.filter { it != next })
        return next
    }

    /** Hapus token segera; pencabutan di server dilakukan di latar belakang. */
    fun logout(background: kotlinx.coroutines.CoroutineScope?) {
        val old = server.token()
        server.clearToken()
        if (old != null) background?.launch { server.revoke(old) }
    }
}
