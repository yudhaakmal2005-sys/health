package id.sehati.app.data.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import id.sehati.app.core.util.Clock
import id.sehati.app.domain.model.MovementKind
import id.sehati.app.domain.rules.MovementClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TrackerState(
    val running: Boolean = false,
    val paused: Boolean = false,
    val declared: MovementKind = MovementKind.WALKING,
    val startAt: Long = 0,
    val activeMillis: Long = 0,
    val distanceMeters: Float = 0f,
    val currentSpeedKmh: Float = 0f,
    val gpsFixed: Boolean = false,
) {
    val avgSpeedKmh: Float get() = if (activeMillis <= 0) 0f else distanceMeters / 1000f / (activeMillis / 3_600_000f)
}

data class TrackerResult(
    val startAt: Long, val endAt: Long, val distanceMeters: Float, val avgSpeedKmh: Float,
    val declared: MovementKind, val classified: MovementKind,
)

/**
 * Sesi aktivitas berbasis GPS. Lokasi hanya dibaca selama sesi berlangsung (mulai → selesai),
 * dan tidak dikirim/disimpan per titik: hanya ringkasan jarak dan durasi yang disimpan.
 */
class ActivityTracker(private val context: Context, private val clock: Clock) : LocationListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(TrackerState())
    val state: StateFlow<TrackerState> = _state.asStateFlow()
    private var last: Location? = null
    private var ticker: Job? = null
    private var lastTick = 0L

    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(kind: MovementKind): Boolean {
        if (!hasPermission() || _state.value.running) return false
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return false
        last = null
        val now = clock.now()
        _state.value = TrackerState(running = true, declared = kind, startAt = now)
        lastTick = now
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 3f, this, Looper.getMainLooper())
        ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java))
        ticker = scope.launch {
            while (isActive) {
                delay(1000)
                val t = clock.now()
                val s = _state.value
                if (s.running && !s.paused) _state.value = s.copy(activeMillis = s.activeMillis + (t - lastTick))
                lastTick = t
            }
        }
        return true
    }

    fun pause() { _state.value = _state.value.copy(paused = true, currentSpeedKmh = 0f); last = null }
    fun resume() { lastTick = clock.now(); _state.value = _state.value.copy(paused = false) }

    fun finish(): TrackerResult? {
        val s = _state.value
        if (!s.running) return null
        stopUpdates()
        val avg = s.avgSpeedKmh
        val res = TrackerResult(s.startAt, clock.now(), s.distanceMeters, avg, s.declared, MovementClassifier.classify(s.declared, avg))
        _state.value = TrackerState()
        return res
    }

    fun cancel() { stopUpdates(); _state.value = TrackerState() }

    private fun stopUpdates() {
        ticker?.cancel(); ticker = null
        (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(this)
        context.stopService(Intent(context, TrackingService::class.java))
        last = null
    }

    override fun onLocationChanged(loc: Location) {
        val s = _state.value
        if (!s.running || s.paused) return
        if (loc.hasAccuracy() && loc.accuracy > 35f) return
        val prev = last
        var add = 0f
        if (prev != null) {
            val d = prev.distanceTo(loc)
            val dt = ((loc.time - prev.time) / 1000f).coerceAtLeast(1f)
            if (d >= 2f && d / dt < 20f) add = d // buang loncatan GPS tak wajar (>72 km/jam)
        }
        last = loc
        val speed = if (loc.hasSpeed()) loc.speed * 3.6f else s.currentSpeedKmh
        _state.value = s.copy(distanceMeters = s.distanceMeters + add, currentSpeedKmh = speed, gpsFixed = true)
    }

    @Deprecated("Deprecated in Java") override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}

/** Layanan latar depan agar sesi tidak dimatikan sistem saat layar mati. */
class TrackingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("sehati_tracking", "Sesi aktivitas", NotificationManager.IMPORTANCE_LOW))
        val n: Notification = NotificationCompat.Builder(this, "sehati_tracking")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("SEHATI sedang merekam aktivitas")
            .setContentText("Lokasi hanya dipakai selama sesi ini.")
            .setOngoing(true).build()
        ServiceCompat.startForeground(this, 4242, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        return START_NOT_STICKY
    }
}
