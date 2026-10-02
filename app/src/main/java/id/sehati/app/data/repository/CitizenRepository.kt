package id.sehati.app.data.repository

import androidx.room.withTransaction
import id.sehati.app.core.util.Clock
import id.sehati.app.core.util.Ids
import id.sehati.app.data.local.HouseholdEntity
import id.sehati.app.data.local.SehatiDatabase
import id.sehati.app.data.local.UserEntity
import id.sehati.app.data.sync.SyncRecorder
import id.sehati.app.domain.model.Role
import id.sehati.app.domain.model.Sex
import id.sehati.app.domain.rules.QrPayload
import id.sehati.app.domain.rules.SehatiId
import kotlinx.coroutines.flow.Flow

/** Ringkasan minimal warga untuk pendaftaran di Posyandu: tidak memuat data kesehatan sensitif. */
data class CitizenSummary(
    val sehatiId: String, val name: String, val age: Int, val rw: String, val village: String,
    val lastCheckAt: Long?, val followUpOpen: Boolean,
)

class CitizenRepository(
    private val db: SehatiDatabase,
    private val sync: SyncRecorder,
    private val audit: AuditLogger,
    private val clock: Clock,
) {
    private val users get() = db.userDao()

    fun observe(id: String): Flow<UserEntity?> = users.observe(id)
    fun observeCitizens(): Flow<List<UserEntity>> = users.observeCitizens()
    fun observeByRole(role: Role) = users.observeByRole(role.name)
    suspend fun get(id: String) = users.get(id)
    suspend fun search(q: String) = users.searchCitizens(q.trim())

    suspend fun update(u: UserEntity) {
        val next = u.copy(updatedAt = clock.now(), version = u.version + 1, syncStatus = "LOCAL_ONLY")
        db.withTransaction { users.upsert(next); sync.record("user", next.sehatiId, next.sehatiId, next.version, UserEntity.serializer(), next) }
    }

    suspend fun updateConsent(id: String, local: Boolean, server: Boolean, healthConnect: Boolean) {
        val u = users.get(id) ?: return
        update(u.copy(consentLocal = local, consentServerSync = server, consentHealthConnect = healthConnect, consentAt = clock.now()))
        audit.log("consent_update", id, "server=$server hc=$healthConnect")
    }

    suspend fun updateGoals(id: String, goals: List<String>) {
        val u = users.get(id) ?: return
        update(u.copy(goals = goals.joinToString(",")))
    }

    /** Kader mendaftarkan warga baru (tanpa akun). Persetujuan dicatat pada saat pendaftaran. */
    suspend fun registerByCadre(
        cadreId: String, name: String, birthDate: String, sex: Sex, village: String, rw: String, rt: String,
        phone: String?, consentLocal: Boolean, consentServerSync: Boolean,
    ): UserEntity {
        require(name.isNotBlank()) { "Nama wajib diisi." }
        require(consentLocal) { "Persetujuan pencatatan data wajib dari warga." }
        return db.withTransaction {
            val id = SehatiId.format((users.maxIdNumber() ?: 0) + 1)
            val now = clock.now()
            val hh = HouseholdEntity(Ids.uuid(), name.trim(), rw, rt, now, now)
            users.upsertHousehold(hh)
            val u = UserEntity(
                sehatiId = id, fullName = name.trim(), birthDate = birthDate, sex = sex.name, village = village, rw = rw, rt = rt,
                phone = phone?.takeIf { it.isNotBlank() }, qrToken = QrPayload.newToken(),
                consentLocal = true, consentServerSync = consentServerSync, consentAt = now,
                onboardingDone = true, hasAccount = false, registeredBy = cadreId, householdId = hh.id,
                createdAt = now, updatedAt = now,
            )
            users.upsert(u)
            sync.record("household", hh.id, id, hh.version, HouseholdEntity.serializer(), hh)
            sync.record("user", id, id, u.version, UserEntity.serializer(), u)
            audit.log("register_citizen", id)
            u
        }
    }

    /** Validasi QR: ID dan token opak harus cocok dengan data lokal. */
    suspend fun resolveQr(raw: String): UserEntity? {
        val p = QrPayload.parse(raw) ?: return null
        val u = users.get(p.sehatiId) ?: return null
        return if (u.qrToken == p.token) u else null
    }

    suspend fun deleteAllDataOf(id: String) {
        db.withTransaction { users.delete(id) }
        audit.log("delete_account", id)
    }
}
