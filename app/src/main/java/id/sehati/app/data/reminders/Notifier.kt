package id.sehati.app.data.reminders

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
import id.sehati.app.MainActivity
import id.sehati.app.R

/** Kanal & pembuat notifikasi SEHATI. Notifikasi tidak memuat nilai pemeriksaan (privasi layar kunci). */
object Notifier {
    const val CH_DAILY = "sehati_daily"
    const val CH_MEDS = "sehati_meds"
    const val CH_POSYANDU = "sehati_posyandu"
    const val EXTRA_ROUTE = "route"

    fun ensureChannels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_DAILY, "Pengingat kebiasaan sehat", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Minum air, jalan sehat, tidur, fakta harian, tantangan" })
        nm.createNotificationChannel(NotificationChannel(CH_MEDS, "Pengingat obat", NotificationManager.IMPORTANCE_HIGH).apply { description = "Jadwal minum obat sesuai resep" })
        nm.createNotificationChannel(NotificationChannel(CH_POSYANDU, "Jadwal Posyandu", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Pengingat hari Posyandu di RW-mu" })
    }

    fun canPost(c: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openApp(c: Context, route: String, code: Int): PendingIntent = PendingIntent.getActivity(
        c, code, Intent(c, MainActivity::class.java).putExtra(EXTRA_ROUTE, route).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun post(c: Context, id: Int, channel: String, title: String, body: String, route: String, actions: List<NotificationCompat.Action> = emptyList()) {
        if (!canPost(c)) return
        ensureChannels(c)
        val b = NotificationCompat.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_stat_sehati)
            .setColor(0xFF0284C7.toInt())
            .setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp(c, route, id))
            .setAutoCancel(true)
            .setCategory(if (channel == CH_MEDS) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_RECOMMENDATION)
        actions.forEach(b::addAction)
        c.getSystemService(NotificationManager::class.java).notify(id, b.build())
    }

    fun cancel(c: Context, id: Int) = c.getSystemService(NotificationManager::class.java).cancel(id)
}
