package id.sehati.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import id.sehati.app.core.security.InMemorySecureStore
import id.sehati.app.core.util.Clock
import id.sehati.app.data.local.SehatiDatabase
import id.sehati.app.data.local.UserEntity
import id.sehati.app.data.repository.*
import id.sehati.app.data.session.SessionManager
import id.sehati.app.data.sync.SyncEngine
import id.sehati.app.data.sync.SyncRecorder
import id.sehati.app.data.sync.SyncTransport
import id.sehati.app.domain.model.Role
import id.sehati.app.domain.model.Sex
import id.sehati.app.domain.rules.QrPayload
import kotlinx.serialization.json.Json

/** JUnit4 mewajibkan metode uji bertipe void: pembungkus ini selalu mengembalikan Unit. */
fun blocking(block: suspend kotlinx.coroutines.CoroutineScope.() -> Any?) { kotlinx.coroutines.runBlocking { block() } }

class FakeClock(var t: Long = 1_790_000_000_000L) : Clock { override fun now() = t }

/** Lingkungan uji: Room in-memory (tanpa SQLCipher) + repositori nyata. */
class TestEnv(transport: SyncTransport? = null) {
    val clock = FakeClock()
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val db: SehatiDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SehatiDatabase::class.java)
        .allowMainThreadQueries().build()
    val session = SessionManager(InMemorySecureStore(), clock)
    val recorder = SyncRecorder(db.systemDao(), clock, json)
    val audit = AuditLogger(db.systemDao(), session, clock)
    val health = HealthRepository(db, recorder, clock, json)
    val auth = AuthRepository(db, session, recorder, audit, clock)
    val citizens = CitizenRepository(db, recorder, audit, clock)
    val daily = DailyRepository(db, recorder, clock)
    val meds = MedicationRepository(db, recorder, clock)
    val server = id.sehati.app.data.remote.ServerClient(id.sehati.app.data.prefs.SettingsStore(ApplicationProvider.getApplicationContext()), InMemorySecureStore(), json, defaultBaseUrl = "")
    val remote = id.sehati.app.data.remote.RemoteAccount(server, db, id.sehati.app.data.prefs.SettingsStore(ApplicationProvider.getApplicationContext()), id.sehati.app.data.sync.SyncApplier(db, json), json, clock)
    val posyandu = PosyanduRepository(db, health, recorder, session, audit, clock)
    val engine = SyncEngine(db, { transport ?: error("no transport") }, { "device-test" }, clock)

    suspend fun staff(id: String, role: Role, rw: String = "01") {
        val now = clock.now()
        db.userDao().upsert(UserEntity(id, "Staf $id", "1985-01-01", Sex.FEMALE.name, "Desa Uji", rw, role = role.name, qrToken = QrPayload.newToken(),
            consentLocal = true, consentServerSync = true, onboardingDone = true, assessmentDone = true, createdAt = now, updatedAt = now))
    }

    suspend fun citizen(name: String = "Tariska", consentServer: Boolean = true): UserEntity =
        citizens.registerByCadre("KD-000001", name, "1992-03-01", Sex.FEMALE, "Desa Uji", "01", "02", null, true, consentServer)

    fun close() = db.close()
}
