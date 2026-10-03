package id.sehati.app.data.repository

import androidx.room.withTransaction
import id.sehati.app.core.util.Clock
import id.sehati.app.data.local.CredentialEntity
import id.sehati.app.data.local.SehatiDatabase
import id.sehati.app.data.local.UserEntity
import id.sehati.app.data.session.Session
import id.sehati.app.data.session.SessionManager
import id.sehati.app.data.sync.SyncRecorder
import id.sehati.app.domain.model.Role
import id.sehati.app.domain.model.Sex
import id.sehati.app.domain.rules.PasswordHasher
import id.sehati.app.domain.rules.QrPayload
import id.sehati.app.domain.rules.SehatiId
import kotlinx.coroutines.launch

sealed interface AuthResult {
    data class Success(val user: UserEntity, val session: Session) : AuthResult
    data class Failure(val message: String) : AuthResult
}

data class Registration(
    val fullName: String, val birthDate: String, val sex: Sex, val village: String, val rw: String, val rt: String,
    val phone: String?, val password: String, val goals: List<String>,
    val consentLocal: Boolean, val consentServerSync: Boolean, val consentHealthConnect: Boolean,
)

/**
 * Autentikasi lokal (offline-first): kata sandi di-hash PBKDF2, percobaan gagal dibatasi,
 * sesi berkedaluwarsa. Role ditentukan data akun, bukan pilihan pengguna.
 */
class AuthRepository(
    private val db: SehatiDatabase,
    private val session: SessionManager,
    private val sync: SyncRecorder,
    private val audit: AuditLogger,
    private val clock: Clock,
    private val remote: id.sehati.app.data.remote.RemoteAccount? = null,
    /** Lingkup latar belakang aplikasi untuk menautkan akun server tanpa menahan UI. */
    private val background: kotlinx.coroutines.CoroutineScope? = null,
    private val afterLink: (suspend () -> Unit)? = null,
) {
    private val users get() = db.userDao()

    suspend fun register(r: Registration): AuthResult {
        PasswordHasher.passwordIssue(r.password)?.let { return AuthResult.Failure(it) }
        if (r.fullName.isBlank()) return AuthResult.Failure("Nama wajib diisi.")
        if (!r.phone.isNullOrBlank() && users.getByPhone(r.phone) != null) return AuthResult.Failure("Nomor kontak sudah terdaftar.")
        val reserved = if (r.consentServerSync) remote?.takeReservedId() else null
        val user = db.withTransaction {
            val id = reserved ?: SehatiId.format((users.maxIdNumber() ?: 0) + 1)
            val now = clock.now()
            val u = UserEntity(
                sehatiId = id, fullName = r.fullName.trim(), birthDate = r.birthDate, sex = r.sex.name,
                village = r.village.trim(), rw = r.rw.trim(), rt = r.rt.trim(), phone = r.phone?.takeIf { it.isNotBlank() },
                role = Role.WARGA.name, qrToken = QrPayload.newToken(), goals = r.goals.joinToString(","),
                consentLocal = r.consentLocal, consentServerSync = r.consentServerSync, consentHealthConnect = r.consentHealthConnect,
                consentAt = now, onboardingDone = true, createdAt = now, updatedAt = now,
            )
            users.upsert(u)
            val h = PasswordHasher.hash(r.password.toCharArray())
            users.upsertCredential(CredentialEntity(id, h.salt, h.hash, h.iterations))
            sync.record("user", id, id, u.version, UserEntity.serializer(), u)
            u
        }
        val s = session.start(user.sehatiId, Role.WARGA)
        audit.log("register", user.sehatiId)
        if (r.consentServerSync) linkInBackground(user, r.password)
        return AuthResult.Success(user, s)
    }

    /** [identifier]: SEHATI ID atau nomor kontak. */
    suspend fun login(identifier: String, password: String): AuthResult {
        val key = identifier.trim()
        val user = users.get(SehatiId.normalize(key)) ?: users.get(key.uppercase()) ?: users.getByPhone(key)
        val cred = user?.let { users.credential(it.sehatiId) }
        if (user == null || cred == null) return loginViaServer(SehatiId.normalize(key), password)
        val now = clock.now()
        if (cred.lockedUntil > now) {
            val mins = ((cred.lockedUntil - now) / 60000L).toInt() + 1
            return AuthResult.Failure("Terlalu banyak percobaan. Coba lagi dalam $mins menit.")
        }
        val ok = PasswordHasher.verify(password.toCharArray(), PasswordHasher.Hash(cred.salt, cred.hash, cred.iterations))
        if (!ok) {
            val attempts = cred.failedAttempts + 1
            users.upsertCredential(cred.copy(failedAttempts = attempts, lockedUntil = if (attempts >= MAX_ATTEMPTS) now + LOCK_MS else 0))
            audit.log("login_failed", user.sehatiId)
            return AuthResult.Failure("SEHATI ID atau kata sandi tidak cocok.")
        }
        users.upsertCredential(cred.copy(failedAttempts = 0, lockedUntil = 0))
        val s = session.start(user.sehatiId, Role.parse(user.role))
        audit.log("login", user.sehatiId)
        linkInBackground(user, password)
        return AuthResult.Success(user, s)
    }

    private fun linkInBackground(user: UserEntity, password: String) {
        val r = remote ?: return
        val scope = background ?: return
        scope.launch {
            runCatching {
                if (r.ensureLinked(user, password) == id.sehati.app.data.remote.LinkResult.Linked) afterLink?.invoke()
            }
        }
    }

    /** Akun belum ada di perangkat ini: coba server (HP baru, atau akun kader/admin yang dibuat Puskesmas). */
    private suspend fun loginViaServer(id: String, password: String): AuthResult {
        val r = remote ?: return AuthResult.Failure("SEHATI ID atau kata sandi tidak cocok.")
        return adopt(r.loginNewDevice(id, password), notFound = "SEHATI ID atau kata sandi tidak cocok.")
    }

    /** Aktivasi akun warga yang didaftarkan kader, dari HP warga sendiri. */
    suspend fun activate(id: String, birthDate: String, password: String): AuthResult {
        PasswordHasher.passwordIssue(password)?.let { return AuthResult.Failure(it) }
        val r = remote ?: return AuthResult.Failure("Aktivasi memerlukan server SEHATI.")
        return adopt(r.activate(SehatiId.normalize(id), birthDate, password), notFound = "Data tidak cocok. Periksa SEHATI ID dan tanggal lahir.")
    }

    private suspend fun adopt(res: id.sehati.app.data.remote.ApiResult<UserEntity>, notFound: String): AuthResult = when (res) {
        is id.sehati.app.data.remote.ApiResult.Ok -> {
            val s = session.start(res.value.sehatiId, Role.parse(res.value.role))
            audit.log("login_server", res.value.sehatiId)
            AuthResult.Success(res.value, s)
        }
        is id.sehati.app.data.remote.ApiResult.Failure -> AuthResult.Failure(if (res.status == 401 || res.status == 404) notFound else res.message)
        is id.sehati.app.data.remote.ApiResult.Offline -> AuthResult.Failure("Akun tidak ditemukan di perangkat ini dan server tidak terjangkau. Periksa internet.")
        id.sehati.app.data.remote.ApiResult.NotConfigured -> AuthResult.Failure(notFound)
    }

    suspend fun logout() { audit.log("logout"); remote?.logout(background); session.end() }

    private companion object { const val MAX_ATTEMPTS = 5; const val LOCK_MS = 5 * 60_000L }
}
