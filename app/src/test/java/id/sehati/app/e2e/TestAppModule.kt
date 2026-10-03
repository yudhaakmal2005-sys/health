package id.sehati.app.e2e

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import id.sehati.app.core.security.InMemorySecureStore
import id.sehati.app.core.security.SecureStore
import id.sehati.app.core.util.Clock
import id.sehati.app.core.util.Ids
import id.sehati.app.core.util.SystemClock
import id.sehati.app.data.local.SehatiDatabase
import id.sehati.app.data.prefs.SettingsStore
import id.sehati.app.data.repository.*
import id.sehati.app.data.session.SessionManager
import id.sehati.app.data.sync.*
import id.sehati.app.di.AppModule
import kotlinx.serialization.json.Json
import javax.inject.Singleton

/** Pengganti AppModule untuk uji end-to-end: Room in-memory (tanpa SQLCipher/Keystore), sinkron simulasi. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AppModule::class])
object TestAppModule {
    @Provides @Singleton fun clock(): Clock = SystemClock()
    @Provides @Singleton fun json(): Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    @Provides @Singleton fun secureStore(): SecureStore = InMemorySecureStore()
    @Provides @Singleton fun settings(@ApplicationContext c: Context) = SettingsStore(c)
    @Provides @Singleton fun database(@ApplicationContext c: Context): SehatiDatabase =
        Room.inMemoryDatabaseBuilder(c, SehatiDatabase::class.java).allowMainThreadQueries().build()
    @Provides @Singleton fun session(store: SecureStore, clock: Clock) = SessionManager(store, clock)
    @Provides @Singleton fun syncRecorder(db: SehatiDatabase, clock: Clock, json: Json) = SyncRecorder(db.systemDao(), clock, json)
    @Provides @Singleton fun audit(db: SehatiDatabase, s: SessionManager, clock: Clock) = AuditLogger(db.systemDao(), s, clock)
    @Provides @Singleton fun health(db: SehatiDatabase, sync: SyncRecorder, clock: Clock, json: Json) = HealthRepository(db, sync, clock, json)
    @Provides @Singleton fun auth(db: SehatiDatabase, s: SessionManager, sync: SyncRecorder, audit: AuditLogger, clock: Clock) = AuthRepository(db, s, sync, audit, clock)
    @Provides @Singleton fun citizens(db: SehatiDatabase, sync: SyncRecorder, audit: AuditLogger, clock: Clock) = CitizenRepository(db, sync, audit, clock)
    @Provides @Singleton fun medications(db: SehatiDatabase, sync: SyncRecorder, clock: Clock) = MedicationRepository(db, sync, clock)
    @Provides @Singleton fun daily(db: SehatiDatabase, sync: SyncRecorder, clock: Clock) = DailyRepository(db, sync, clock)
    @Provides @Singleton fun posyandu(db: SehatiDatabase, h: HealthRepository, sync: SyncRecorder, s: SessionManager, audit: AuditLogger, clock: Clock) = PosyanduRepository(db, h, sync, s, audit, clock)
    @Provides @Singleton fun thresholds(db: SehatiDatabase, settings: SettingsStore, h: HealthRepository, s: SessionManager, audit: AuditLogger, json: Json) =
        id.sehati.app.data.repository.ThresholdService(db, settings, h, s, audit, json)
    @Provides @Singleton fun server(settings: SettingsStore, secure: SecureStore, json: Json) = id.sehati.app.data.remote.ServerClient(settings, secure, json, defaultBaseUrl = "")
    @Provides @Singleton fun applier(db: SehatiDatabase, json: Json) = id.sehati.app.data.sync.SyncApplier(db, json)
    @Provides @Singleton fun remote(server: id.sehati.app.data.remote.ServerClient, db: SehatiDatabase, settings: SettingsStore, applier: id.sehati.app.data.sync.SyncApplier, json: Json, clock: Clock) =
        id.sehati.app.data.remote.RemoteAccount(server, db, settings, applier, json, clock)
    @Provides @Singleton fun appScope() = id.sehati.app.di.AppScope()
    @Provides @Singleton fun syncEngine(db: SehatiDatabase, clock: Clock): SyncEngine {
        val loopback = LoopbackSyncTransport()
        return SyncEngine(db, { loopback }, { "device-e2e" }, clock)
    }
}
