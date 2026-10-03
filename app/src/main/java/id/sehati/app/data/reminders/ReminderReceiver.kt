package id.sehati.app.data.reminders

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import id.sehati.app.R
import id.sehati.app.core.util.TimeUtils
import id.sehati.app.data.prefs.SettingsStore
import id.sehati.app.data.repository.DailyRepository
import id.sehati.app.data.repository.MedicationRepository
import id.sehati.app.data.session.SessionManager
import id.sehati.app.domain.rules.AutoRule
import id.sehati.app.domain.rules.ChallengeCatalog
import id.sehati.app.domain.rules.ChallengeStatus
import id.sehati.app.domain.rules.Challenges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Menerima alarm pengingat dan tombol aksi di notifikasi ("Tambah 1 gelas", "Sudah diminum", "Saya berhasil"). */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var session: SessionManager
    @Inject lateinit var daily: DailyRepository
    @Inject lateinit var meds: MedicationRepository
    @Inject lateinit var settings: SettingsStore

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> fire(context, intent.getStringExtra(EXTRA_KEY).orEmpty())
                    ACTION_WATER -> { uid()?.let { daily.addWater(it, today(), 1) }; done(context, intent, "Tercatat 1 gelas air") }
                    ACTION_MED_TAKEN -> {
                        val id = intent.getStringExtra(EXTRA_MED).orEmpty(); val t = intent.getStringExtra(EXTRA_TIME).orEmpty()
                        meds.setTaken(id, LocalDate.now(), t, true); done(context, intent, "Obat tercatat sudah diminum")
                    }
                    ACTION_CHALLENGE -> {
                        val cid = intent.getStringExtra(EXTRA_CHALLENGE).orEmpty()
                        uid()?.let { daily.checkIn(it, cid, LocalDate.now()) }; done(context, intent, "Tantangan hari ini tercatat")
                    }
                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED, ACTION_RESCHEDULE -> scheduler.rescheduleAll()
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    private fun uid(): String? = session.validate()?.sehatiId
    private fun today() = TimeUtils.dateIso(System.currentTimeMillis())

    private suspend fun fire(c: Context, key: String) {
        val p = scheduler.currentPlan().firstOrNull { it.key == key }
        if (p != null && shouldShow(p)) {
            val id = key.hashCode()
            val actions = mutableListOf<NotificationCompat.Action>()
            when (p.kind) {
                ReminderKind.WATER -> actions += action(c, id, "Tambah 1 gelas", Intent(c, ReminderReceiver::class.java).setAction(ACTION_WATER))
                ReminderKind.MEDICATION -> actions += action(c, id, "Sudah diminum",
                    Intent(c, ReminderReceiver::class.java).setAction(ACTION_MED_TAKEN).putExtra(EXTRA_MED, p.medicationId).putExtra(EXTRA_TIME, p.doseTime))
                ReminderKind.CHALLENGE -> pendingChallenge()?.let { cid ->
                    actions += action(c, id, "Saya berhasil hari ini", Intent(c, ReminderReceiver::class.java).setAction(ACTION_CHALLENGE).putExtra(EXTRA_CHALLENGE, cid))
                }
                else -> Unit
            }
            Notifier.post(c, id, p.kind.channel, p.title, p.body, p.kind.route, actions)
        }
        scheduler.rescheduleAll()
    }

    /** Tidak mengganggu bila target sudah tercapai atau tidak ada yang perlu dicatat. */
    private suspend fun shouldShow(p: PlannedReminder): Boolean {
        val u = uid() ?: return false
        val s = settings.current()
        val habit = daily.observeHabitOnce(u, today())
        return when (p.kind) {
            ReminderKind.WATER -> (habit?.waterGlasses ?: 0) < s.targets.waterGlasses
            ReminderKind.WALK -> (habit?.steps ?: 0) < s.targets.steps
            ReminderKind.CHALLENGE -> pendingChallenge() != null
            ReminderKind.MEDICATION -> {
                val logs = meds.observeLogs(u, LocalDate.now()).first()
                logs.none { it.medicationId == p.medicationId && it.time == p.doseTime && it.dateIso == LocalDate.now().toString() }
            }
            else -> true
        }
    }

    private suspend fun pendingChallenge(): String? {
        val u = uid() ?: return null
        val day = LocalDate.now()
        return daily.observeChallenges(u).first().firstOrNull { e ->
            val def = ChallengeCatalog.byId(e.challengeId) ?: return@firstOrNull false
            if (def.auto != AutoRule.NONE) return@firstOrNull false
            val p = Challenges.evaluate(def, LocalDate.parse(e.startDate), Challenges.decode(e.checkIns), day)
            p.status == ChallengeStatus.ACTIVE && !p.checkedToday
        }?.challengeId
    }

    private fun action(c: Context, notifId: Int, label: String, i: Intent): NotificationCompat.Action {
        i.putExtra(EXTRA_NOTIF, notifId)
        val pi = PendingIntent.getBroadcast(c, notifId * 31 + label.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Action.Builder(R.drawable.ic_stat_sehati, label, pi).build()
    }

    /** Ganti notifikasi dengan konfirmasi singkat lalu hilang sendiri. */
    private fun done(c: Context, intent: Intent, text: String) {
        val id = intent.getIntExtra(EXTRA_NOTIF, 0)
        if (id == 0 || !Notifier.canPost(c)) return
        Notifier.ensureChannels(c)
        val n = NotificationCompat.Builder(c, Notifier.CH_DAILY).setSmallIcon(R.drawable.ic_stat_sehati).setColor(0xFF10B981.toInt())
            .setContentTitle(text).setTimeoutAfter(4000).setAutoCancel(true).setSilent(true).build()
        c.getSystemService(android.app.NotificationManager::class.java).notify(id, n)
    }

    companion object {
        const val ACTION_FIRE = "id.sehati.app.REMINDER_FIRE"
        const val ACTION_WATER = "id.sehati.app.REMINDER_WATER"
        const val ACTION_MED_TAKEN = "id.sehati.app.REMINDER_MED_TAKEN"
        const val ACTION_CHALLENGE = "id.sehati.app.REMINDER_CHALLENGE"
        const val ACTION_RESCHEDULE = "id.sehati.app.REMINDER_RESCHEDULE"
        const val EXTRA_KEY = "key"; const val EXTRA_MED = "med"; const val EXTRA_TIME = "time"
        const val EXTRA_CHALLENGE = "challenge"; const val EXTRA_NOTIF = "notif"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
