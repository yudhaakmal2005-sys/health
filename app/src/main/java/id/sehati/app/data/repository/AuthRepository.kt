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
) {
    private val users get() = db.userDao()

    suspend fun register(r: Registration): AuthResult {
        PasswordHasher.passwordIssue(r.password)?.let { return AuthResult.Failure(it) }
        if (r.fullName.isBlank()) return AuthResult.Failure("Nama wajib diisi.")
        if (!r.phone.isNullOrBlank() && users.getByPhone(r.phone) != null) return AuthResult.Failure("Nomor kontak sudah terdaftar.")
        val user = db.withTransaction {
            val id = SehatiId.format((users.maxIdNumber() ?: 0) + 1)
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
        return AuthResult.Success(user, s)
    }

    /** [identifier]: SEHATI ID atau nomor kontak. */
    suspend fun login(identifier: String, password: String): AuthResult {
        val key = identifier.trim()
        val user = users.get(SehatiId.normalize(key)) ?: users.get(key.uppercase()) ?: users.getByPhone(key)
            ?: return AuthResult.Failure("SEHATI ID atau kata sandi tidak cocok.")
        val cred = users.credential(user.sehatiId) ?: return AuthResult.Failure("Akun ini belum memiliki kata sandi. Hubungi kader Posyandu.")
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
        return AuthResult.Success(user, s)
    }

    suspend fun logout() { audit.log("logout"); session.end() }

    private companion object { const val MAX_ATTEMPTS = 5; const val LOCK_MS = 5 * 60_000L }
}
