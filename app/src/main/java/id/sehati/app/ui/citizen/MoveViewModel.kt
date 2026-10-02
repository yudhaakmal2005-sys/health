package id.sehati.app.ui.citizen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sehati.app.core.util.Clock
import id.sehati.app.core.util.TimeUtils
import id.sehati.app.data.healthconnect.HcSyncResult
import id.sehati.app.data.healthconnect.HealthConnectGateway
import id.sehati.app.data.healthconnect.HealthConnectImporter
import id.sehati.app.data.local.ActivitySessionEntity
import id.sehati.app.data.prefs.SettingsStore
import id.sehati.app.data.repository.DailyRepository
import id.sehati.app.data.repository.HealthRepository
import id.sehati.app.data.tracking.ActivityTracker
import id.sehati.app.data.tracking.TrackerResult
import id.sehati.app.data.tracking.TrackerState
import id.sehati.app.domain.model.MovementKind
import id.sehati.app.domain.rules.ActivityAggregator
import id.sehati.app.domain.rules.MovementSegment
import id.sehati.app.domain.rules.MovementSummary
import id.sehati.app.ui.app.CurrentUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MoveUiState(
    val loading: Boolean = true,
    val steps: Int = 0, val stepTarget: Int = 6000, val stepsSource: String = "SELF",
    val timeline: List<ActivitySessionEntity> = emptyList(),
    val summary: MovementSummary = MovementSummary(0, 0, 0f, 0f),
    val recent: List<ActivitySessionEntity> = emptyList(),
    val hcConsent: Boolean = false, val hcLastSync: Long = 0,
)

sealed interface HcUi {
    data object Idle : HcUi
    data object Syncing : HcUi
    data class Message(val text: String, val needsPermission: Boolean = false, val needsInstall: Boolean = false) : HcUi
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MoveViewModel @Inject constructor(
    private val current: CurrentUser,
    private val daily: DailyRepository,
    private val health: HealthRepository,
    private val settings: SettingsStore,
    private val importer: HealthConnectImporter,
    private val gateway: HealthConnectGateway,
    private val tracker: ActivityTracker,
    private val clock: Clock,
) : ViewModel() {
    val trackerState: StateFlow<TrackerState> = tracker.state
    val hcPermissions: Set<String> = HealthConnectGateway.readPermissions

    private val _hc = MutableStateFlow<HcUi>(HcUi.Idle)
    val hc: StateFlow<HcUi> = _hc.asStateFlow()
    private val _lastResult = MutableStateFlow<TrackerResult?>(null)
    val lastResult: StateFlow<TrackerResult?> = _lastResult.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    val state: StateFlow<MoveUiState> = current.user.filterNotNull().flatMapLatest { u ->
        val iso = TimeUtils.dateIso(clock.now())
        combine(daily.observeHabit(u.sehatiId, iso), daily.observeActivity(u.sehatiId, iso), daily.observeRecentActivity(u.sehatiId, 15), settings.settings) { h, tl, rc, s ->
            val seg = ActivityAggregator.summarize(tl.map { MovementSegment(MovementKind.parse(it.kind), it.startAt, it.endAt, it.distanceMeters) })
            MoveUiState(false, h?.steps ?: 0, s.targets.steps, h?.stepsSource ?: "SELF", tl, seg, rc, u.consentHealthConnect, s.lastHealthConnectSync)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MoveUiState())

    fun trackerHasPermission() = tracker.hasPermission()
    fun startSession(kind: MovementKind) {
        if (!tracker.start(kind)) _notice.value = "GPS belum aktif atau izin lokasi belum diberikan. Aktifkan lokasi, lalu coba lagi."
    }
    fun pause() = tracker.pause()
    fun resume() = tracker.resume()
    fun discard() = tracker.cancel()
    fun dismissNotice() { _notice.value = null }
    fun dismissResult() { _lastResult.value = null }

    fun finishSession() {
        val r = tracker.finish() ?: return
        _lastResult.value = r
        val id = current.id ?: return
        viewModelScope.launch {
            val weight = health.checks(id).firstNotNullOfOrNull { it.weightKg } ?: health.observeLatestAssessmentOnce(id)?.weightKg ?: 60f
            if (r.distanceMeters >= 20f || (r.endAt - r.startAt) >= 60_000L) {
                daily.saveActivity(id, r.classified, r.startAt, r.endAt, r.distanceMeters, weight, "SELF", true)
            }
        }
    }

    fun addManual(kind: MovementKind, minutes: Int) {
        val id = current.id ?: return
        val now = clock.now()
        viewModelScope.launch {
            val weight = health.checks(id).firstNotNullOfOrNull { it.weightKg } ?: 60f
            daily.saveActivity(id, kind, now - minutes * 60_000L, now, 0f, weight, "SELF", false)
        }
    }

    fun syncHealthConnect() {
        val id = current.id ?: return
        _hc.value = HcUi.Syncing
        viewModelScope.launch {
            val u = current.user.first() ?: return@launch
            val weight = health.checks(id).firstNotNullOfOrNull { it.weightKg } ?: 60f
            _hc.value = when (val r = importer.sync(id, u.consentHealthConnect, weight)) {
                is HcSyncResult.Success -> HcUi.Message("Tersinkron: ${r.summary.steps} langkah hari ini, ${r.summary.sleep} data tidur, ${r.summary.exercise} latihan.")
                HcSyncResult.NotAvailable -> HcUi.Message("Health Connect tidak tersedia di perangkat ini. Kamu tetap dapat mencatat manual.")
                HcSyncResult.NeedsUpdate -> HcUi.Message("Health Connect perlu diperbarui atau dipasang.", needsInstall = true)
                is HcSyncResult.PermissionMissing -> HcUi.Message("Izin data kesehatan belum diberikan.", needsPermission = true)
                HcSyncResult.NoConsent -> HcUi.Message("Kamu belum mengizinkan SEHATI membaca Health Connect. Aktifkan di Profil → Privasi.")
                is HcSyncResult.Error -> HcUi.Message(r.message)
            }
        }
    }

    fun onPermissionsResult(granted: Set<String>) {
        if (granted.containsAll(hcPermissions)) syncHealthConnect()
        else _hc.value = HcUi.Message("Izin data kesehatan belum lengkap.", needsPermission = true)
    }

    fun dismissHc() { _hc.value = HcUi.Idle }
}
