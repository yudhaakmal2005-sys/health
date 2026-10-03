package id.sehati.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import id.sehati.app.BuildConfig
import id.sehati.app.core.security.DatabaseKeyProvider
import id.sehati.app.core.security.KeystoreSecureStore
import id.sehati.app.core.security.SecureStore
import id.sehati.app.core.util.Clock
import id.sehati.app.core.util.Ids
import id.sehati.app.core.util.SystemClock
import id.sehati.app.data.local.SehatiDatabase
import id.sehati.app.data.prefs.SettingsStore
import id.sehati.app.data.repository.*
import id.sehati.app.data.session.SessionManager
import id.sehati.app.data.sync.*
import kotlinx.serialization.json.Json
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

/** Lingkup coroutine seumur aplikasi untuk kerja latar yang tidak boleh terputus saat layar berganti. */
class AppScope(val scope: kotlinx.coroutines.CoroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO))

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun clock(): Clock = SystemClock()

    @Provides @Singleton fun json(): Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Provides @Singleton fun secureStore(@ApplicationContext c: Context): SecureStore = KeystoreSecureStore(c)

    @Provides @Singleton fun settings(@ApplicationContext c: Context) = SettingsStore(c)

    @Provides @Singleton
    fun database(@ApplicationContext c: Context, store: SecureStore): SehatiDatabase {
        System.loadLibrary("sqlcipher")
        fun open(): SehatiDatabase {
            val factory = SupportOpenHelperFactory(DatabaseKeyProvider(store).passphrase())
            val db = Room.databaseBuilder(c, SehatiDatabase::class.java, SehatiDatabase.NAME).openHelperFactory(factory).addMigrations(SehatiDatabase.MIGRATION_1_2, SehatiDatabase.MIGRATION_2_3).build()
            db.openHelper.writableDatabase // memaksa pembukaan agar kunci yang salah terdeteksi sekarang, bukan saat layar dibuka
            return db
        }
        return try {
            open()
        } catch (e: Exception) {
            // Kunci Keystore hilang/tidak valid (mis. setelah reset kunci perangkat): data lama tak dapat dibaca.
            // Buat ulang basis data kosong daripada crash berulang; data tetap dapat dipulihkan dari server bila sinkron aktif.
            c.deleteDatabase(SehatiDatabase.NAME)
            open()
        }
    }

    @Provides @Singleton fun session(store: SecureStore, clock: Clock) = SessionManager(store, clock)

    @Provides @Singleton fun syncRecorder(db: SehatiDatabase, clock: Clock, json: Json) = SyncRecorder(db.systemDao(), clock, json)

    @Provides @Singleton
    fun thresholds(db: SehatiDatabase, settings: SettingsStore, health: HealthRepository, s: SessionManager, audit: AuditLogger, json: Json) =
        id.sehati.app.data.repository.ThresholdService(db, settings, health, s, audit, json)

    @Provides @Singleton fun audit(db: SehatiDatabase, s: SessionManager, clock: Clock) = AuditLogger(db.systemDao(), s, clock)

    @Provides @Singleton
    fun health(db: SehatiDatabase, sync: SyncRecorder, clock: Clock, json: Json) = HealthRepository(db, sync, clock, json)

    @Provides @Singleton
    fun server(settings: SettingsStore, secure: SecureStore, json: Json) = id.sehati.app.data.remote.ServerClient(settings, secure, json)

    @Provides @Singleton fun applier(db: SehatiDatabase, json: Json) = SyncApplier(db, json)

    @Provides @Singleton
    fun remote(server: id.sehati.app.data.remote.ServerClient, db: SehatiDatabase, settings: SettingsStore, applier: SyncApplier, json: Json, clock: Clock) =
        id.sehati.app.data.remote.RemoteAccount(server, db, settings, applier, json, clock)

    @Provides @Singleton
    fun auth(
        db: SehatiDatabase, s: SessionManager, sync: SyncRecorder, audit: AuditLogger, clock: Clock,
        remote: id.sehati.app.data.remote.RemoteAccount, scope: AppScope, scheduler: id.sehati.app.data.work.WorkScheduler,
    ) = AuthRepository(db, s, sync, audit, clock, remote, scope.scope) { scheduler.requestSync() }

    @Provides @Singleton fun appScope() = AppScope()

    @Provides @Singleton
    fun citizens(db: SehatiDatabase, sync: SyncRecorder, audit: AuditLogger, clock: Clock, remote: id.sehati.app.data.remote.RemoteAccount) =
        CitizenRepository(db, sync, audit, clock) { remote.takeReservedId(refillTo = 20) }

    @Provides @Singleton
    fun daily(db: SehatiDatabase, sync: SyncRecorder, clock: Clock) = DailyRepository(db, sync, clock)

    @Provides @Singleton
    fun posyandu(db: SehatiDatabase, h: HealthRepository, sync: SyncRecorder, s: SessionManager, audit: AuditLogger, clock: Clock) =
        PosyanduRepository(db, h, sync, s, audit, clock)

    @Provides @Singleton
    fun syncEngine(db: SehatiDatabase, settings: SettingsStore, server: id.sehati.app.data.remote.ServerClient, clock: Clock): SyncEngine {
        val loopback = LoopbackSyncTransport()
        return SyncEngine(
            db = db,
            transportProvider = {
                // Mode demo memakai server simulasi (dilabeli jelas di UI); selain itu HTTPS ke BASE_URL.
                if (BuildConfig.DEMO_MODE && settings.current().demoServerSimulation) loopback
                else HttpSyncTransport(server)
            },
            deviceId = { settings.ensureDeviceId { Ids.uuid() } },
            clock = clock,
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object PlatformModule {
    @Provides @Singleton fun gateway(@ApplicationContext c: Context) = id.sehati.app.data.healthconnect.HealthConnectGateway(c)
    @Provides @Singleton fun hcImporter(
        g: id.sehati.app.data.healthconnect.HealthConnectGateway, h: HealthRepository, d: DailyRepository, s: SettingsStore, clock: Clock,
    ) = id.sehati.app.data.healthconnect.HealthConnectImporter(g, h, d, s, clock)
    @Provides @Singleton fun tracker(@ApplicationContext c: Context, clock: Clock) = id.sehati.app.data.tracking.ActivityTracker(c, clock)
    @Provides @Singleton fun scheduler(@ApplicationContext c: Context) = id.sehati.app.data.work.WorkScheduler(c)
    @Provides @Singleton fun seeder(db: SehatiDatabase, h: HealthRepository, s: SettingsStore, clock: Clock) =
        id.sehati.app.data.demo.DemoSeeder(db, h, s, clock)
}
