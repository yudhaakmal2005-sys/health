package id.sehati.app.data.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import id.sehati.app.MainActivity
import id.sehati.app.data.prefs.SettingsStore
import id.sehati.app.data.sync.SyncEngine
import id.sehati.app.data.sync.SyncOutcome
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Sinkronisasi latar belakang dengan retry berjenjang (exponential backoff) ketika jaringan putus. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val controller: id.sehati.app.data.sync.SyncController,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (val r = controller.syncNow()) {
        is SyncOutcome.Offline -> Result.retry()
        is SyncOutcome.Done -> if (r.failed > 0) Result.retry() else Result.success()
        is SyncOutcome.NotConfigured, SyncOutcome.NothingToSync -> Result.success()
    }
}

object Reminders {
    const val CHANNEL = "sehati_reminders"
    const val KIND = "kind"
}

/** Pengingat lokal yang dapat dimatikan pengguna: pemeriksaan, aktivitas, air, edukasi, Posyandu, tindak lanjut. */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsStore,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val kind = inputData.getString(Reminders.KIND) ?: return Result.success()
        val prefs = settings.settings.first().notifications
        val enabled = when (kind) {
            "WATER" -> prefs.water; "ACTIVITY" -> prefs.activity; "HEALTH_CHECK" -> prefs.healthCheck
            "EDUCATION" -> prefs.education; "POSYANDU" -> prefs.posyandu; "FOLLOW_UP" -> prefs.followUp; else -> false
        }
        if (!enabled) return Result.success()
        val (title, body) = when (kind) {
            "WATER" -> "Waktunya minum" to "Yuk minum segelas air putih."
            "ACTIVITY" -> "Waktunya bergerak" to "Jalan santai 10–15 menit sore ini."
            "HEALTH_CHECK" -> "Pengingat pemeriksaan" to "Ukur tekanan darah dan berat badanmu secara berkala."
            "EDUCATION" -> "Health Academy" to "Baca satu materi singkat hari ini."
            "POSYANDU" -> "Jadwal Posyandu" to "Jangan lupa membawa QR SEHATI saat ke Posyandu."
            else -> "Tindak lanjut" to "Ada tindak lanjut kesehatan yang menunggu."
        }
        notify(kind.hashCode(), title, body)
        return Result.success()
    }

    private fun notify(id: Int, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(Reminders.CHANNEL, "Pengingat SEHATI", NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, Reminders.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body)
            .setContentIntent(pi).setAutoCancel(true).build()
        nm.notify(id, n)
    }
}

class WorkScheduler(private val context: Context) {
    private val wm get() = WorkManager.getInstance(context)

    /** Dijadwalkan ulang saat ada perubahan data (langsung) dan periodik sebagai jaring pengaman. */
    fun requestSync() {
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        wm.enqueueUniqueWork("sync-now", ExistingWorkPolicy.REPLACE, req)
    }

    fun schedulePeriodicSync() {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
        wm.enqueueUniquePeriodicWork("sync-periodic", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun scheduleReminders() {
        fun periodic(kind: String, hours: Long) {
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(hours, TimeUnit.HOURS)
                .setInputData(workDataOf(Reminders.KIND to kind)).setInitialDelay(hours, TimeUnit.HOURS).build()
            wm.enqueueUniquePeriodicWork("reminder-$kind", ExistingPeriodicWorkPolicy.KEEP, req)
        }
        periodic("WATER", 4); periodic("ACTIVITY", 24); periodic("HEALTH_CHECK", 24 * 7)
        periodic("EDUCATION", 24 * 2); periodic("POSYANDU", 24 * 7); periodic("FOLLOW_UP", 24)
    }
}
