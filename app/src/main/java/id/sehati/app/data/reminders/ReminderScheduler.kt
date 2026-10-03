package id.sehati.app.data.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import id.sehati.app.data.local.SehatiDatabase
import id.sehati.app.data.prefs.SettingsStore
import id.sehati.app.data.remote.RemoteAccount
import id.sehati.app.data.repository.MedicationRepository
import id.sehati.app.data.session.SessionManager
import id.sehati.app.domain.model.Role
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Menjadwalkan pengingat berbasis jam dengan AlarmManager (jendela 10 menit, hemat baterai, tanpa izin alarm presisi).
 * Dijadwalkan ulang setelah setiap pengingat berbunyi, saat pengaturan berubah, saat login, dan setelah HP dinyalakan ulang.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val meds: MedicationRepository,
    private val session: SessionManager,
    private val remote: RemoteAccount,
    private val db: SehatiDatabase,
) {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)
    private val store get() = context.getSharedPreferences("sehati_reminders", Context.MODE_PRIVATE)

    suspend fun currentPlan(): List<PlannedReminder> {
        val s = session.session.value ?: return emptyList()
        if (s.role != Role.WARGA) return emptyList()
        val user = db.userDao().get(s.sehatiId) ?: return emptyList()
        val prefs = settings.current().reminders
        val myMeds = meds.active().filter { it.userId == s.sehatiId }
        val slots = remote.config.first()?.posyandu.orEmpty()
        return ReminderPlanner.plan(ZonedDateTime.now(), prefs, myMeds, slots, user.rw.ifBlank { null })
    }

    suspend fun rescheduleAll() {
        val plan = currentPlan()
        val old = store.getStringSet(KEY_CODES, emptySet()).orEmpty()
        val keep = plan.map { it.key }.toSet()
        (old - keep).forEach { cancel(it) }
        plan.forEach { schedule(it) }
        store.edit().putStringSet(KEY_CODES, keep).apply()
    }

    fun cancelAll() {
        store.getStringSet(KEY_CODES, emptySet()).orEmpty().forEach(::cancel)
        store.edit().remove(KEY_CODES).apply()
    }

    private fun schedule(p: PlannedReminder) {
        val pi = PendingIntent.getBroadcast(context, p.key.hashCode(), fireIntent(p.key), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        alarms.setWindow(AlarmManager.RTC_WAKEUP, p.at, WINDOW_MS, pi)
    }

    private fun cancel(key: String) {
        val pi = PendingIntent.getBroadcast(context, key.hashCode(), fireIntent(key), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
        if (pi != null) { alarms.cancel(pi); pi.cancel() }
    }

    private fun fireIntent(key: String) = Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE).putExtra(ReminderReceiver.EXTRA_KEY, key)

    private companion object {
        const val KEY_CODES = "codes"
        const val WINDOW_MS = 10 * 60_000L
    }
}
