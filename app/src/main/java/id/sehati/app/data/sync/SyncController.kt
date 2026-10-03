package id.sehati.app.data.sync

import id.sehati.app.BuildConfig
import id.sehati.app.data.local.SehatiDatabase
import id.sehati.app.data.local.SyncQueueEntity
import id.sehati.app.data.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** Fasad sinkronisasi untuk UI: ringkasan status + tombol "Sinkronkan sekarang". */
@Singleton
class SyncController @Inject constructor(
    private val db: SehatiDatabase,
    private val engine: SyncEngine,
    private val settings: SettingsStore,
    private val remote: id.sehati.app.data.remote.RemoteAccount,
) {
    val overview: Flow<SyncOverview> = combine(
        db.systemDao().observePendingCount(), db.systemDao().observeFailedCount(),
        db.systemDao().observeDoneCount(), db.systemDao().observeLastSync(),
    ) { p, f, d, l -> SyncOverview(p, f, d, l) }

    fun recent(limit: Int = 30): Flow<List<SyncQueueEntity>> = db.systemDao().observeRecent(limit)

    /** Kirim antrean, lalu tarik perubahan dari server (data dari kader/perangkat lain) dan konfigurasi terbaru. */
    suspend fun syncNow(): SyncOutcome {
        val out = engine.syncNow()
        if (remote.isLinked) { remote.pullNow(); remote.refreshConfig() }
        return out
    }

    /** Label tujuan sinkronisasi; mode simulasi harus selalu jelas bagi pengguna. */
    suspend fun destinationLabel(): String =
        if (BuildConfig.DEMO_MODE && settings.current().demoServerSimulation) "Simulasi demo (tidak dikirim ke server)" else "Server SEHATI" + (if (remote.isLinked) " · tertaut" else " · belum tertaut")

    fun describe(o: SyncOutcome): String = when (o) {
        is SyncOutcome.Done -> if (o.failed == 0) "${o.pushed} data tersinkron." else "${o.pushed} tersinkron, ${o.failed} gagal. Akan dicoba lagi."
        is SyncOutcome.Offline -> "Belum ada koneksi. Data tetap aman di perangkat dan akan dikirim saat online."
        is SyncOutcome.NotConfigured -> "Server belum dikonfigurasi. Data tetap aman di perangkat."
        SyncOutcome.NothingToSync -> "Semua data sudah tersinkron."
    }
}
