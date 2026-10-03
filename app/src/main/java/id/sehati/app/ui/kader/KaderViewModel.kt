package id.sehati.app.ui.kader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sehati.app.core.util.Clock
import id.sehati.app.core.util.TimeUtils
import id.sehati.app.data.local.*
import id.sehati.app.data.repository.*
import id.sehati.app.data.sync.SyncController
import id.sehati.app.data.sync.SyncOverview
import id.sehati.app.data.work.WorkScheduler
import id.sehati.app.domain.model.*
import id.sehati.app.domain.rules.*
import id.sehati.app.ui.app.CurrentUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class RegisterFilter(val label: String) {
    ALL("Semua"), COMPLETED("Selesai"), ATTENTION("Perlu perhatian"), FOLLOW_UP("Follow-up"), NOT_SYNCED("Belum tersinkron")
}

data class TodayRow(
    val visit: PosyanduVisitEntity, val name: String, val completed: Boolean, val needsAttention: Boolean,
    val hasFollowUp: Boolean, val sync: SyncStatus,
)

data class QueueItem(val home: HomeVisitEntity, val follow: FollowUpEntity?, val name: String, val rw: String, val lastVisit: Long?)
data class FollowRow(val follow: FollowUpEntity, val name: String, val rw: String, val lastCheck: Long?)

data class KaderUiState(
    val cadre: UserEntity? = null, val facility: String = "", val rows: List<TodayRow> = emptyList(), val allRows: List<TodayRow> = emptyList(),
    val filter: RegisterFilter = RegisterFilter.ALL, val followUps: List<FollowRow> = emptyList(), val queue: List<QueueItem> = emptyList(),
    val sync: SyncOverview = SyncOverview(0, 0, 0, null), val destination: String = "", val inProgress: List<TodayRow> = emptyList(),
)

data class NewCitizenForm(
    val name: String = "", val day: String = "", val month: String = "", val year: String = "", val sex: Sex? = null,
    val rw: String = "", val rt: String = "", val phone: String = "", val consent: Boolean = false, val consentServer: Boolean = true,
    val error: String? = null, val saving: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class KaderViewModel @Inject constructor(
    private val current: CurrentUser,
    private val citizens: CitizenRepository,
    private val health: HealthRepository,
    private val posyandu: PosyanduRepository,
    private val syncController: SyncController,
    private val scheduler: WorkScheduler,
    private val clock: Clock,
) : ViewModel() {
    private val filter = MutableStateFlow(RegisterFilter.ALL)
    private val destination = MutableStateFlow("")
    private val today get() = TimeUtils.dateIso(clock.now())

    init { viewModelScope.launch { destination.value = syncController.destinationLabel() } }

    private val checksByUser = health.observeAllChecks().map { it.groupBy { c -> c.userId } }
    private val users = citizens.observeCitizens().map { l -> l.associateBy { it.sehatiId } }

    private data class Base(val users: Map<String, UserEntity>, val checks: Map<String, List<HealthCheck>>, val follow: List<FollowUpEntity>, val visits: List<PosyanduVisitEntity>)

    private val base = combine(users, checksByUser, posyandu.observeFollowUps(), posyandu.observeVisitsOn(today)) { u, c, f, v -> Base(u, c, f, v) }

    val state: StateFlow<KaderUiState> = combine(current.user, base, filter, syncController.overview, posyandu.observeHomeVisits()) { cadre, b, flt, sync, homes ->
        val rows = b.visits.map { v ->
            val last = b.checks[v.userId]?.firstOrNull()
            val attention = last?.let { c -> isAttention(c) } ?: false
            TodayRow(v, b.users[v.userId]?.fullName ?: v.userId, v.status == "COMPLETED", attention,
                b.follow.any { it.userId == v.userId && it.status != "DONE" && it.status != "CANCELLED" }, SyncStatus.parse(v.syncStatus))
        }
        val filtered = when (flt) {
            RegisterFilter.ALL -> rows
            RegisterFilter.COMPLETED -> rows.filter { it.completed }
            RegisterFilter.ATTENTION -> rows.filter { it.needsAttention }
            RegisterFilter.FOLLOW_UP -> rows.filter { it.hasFollowUp }
            RegisterFilter.NOT_SYNCED -> rows.filter { it.sync != SyncStatus.SYNCED }
        }
        val myFollow = b.follow.filter { it.status != "DONE" && it.status != "CANCELLED" }.map { f ->
            FollowRow(f, b.users[f.userId]?.fullName ?: f.userId, b.users[f.userId]?.rw ?: "", b.checks[f.userId]?.firstOrNull()?.measuredAt)
        }
        val queue = homes.filter { it.status != "CLOSED" }.map { h ->
            QueueItem(h, b.follow.firstOrNull { it.id == h.followUpId }, b.users[h.userId]?.fullName ?: h.userId, b.users[h.userId]?.rw ?: "", b.checks[h.userId]?.firstOrNull()?.measuredAt)
        }
        KaderUiState(cadre, "Posyandu Melati / Mawar", filtered, rows, flt, myFollow, queue, sync, "", rows.filter { !it.completed })
    }.combine(destination) { s, d -> s.copy(destination = d) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), KaderUiState())

    private fun isAttention(c: HealthCheck): Boolean {
        val bp = c.bloodPressure?.let { BloodPressureRules.interpret(it.first, it.second).severity.rank >= Severity.ATTENTION.rank } ?: false
        val gl = c.glucose?.let { GlucoseRules.interpret(it, c.glucoseFasting).severity.rank >= Severity.WATCH.rank } ?: false
        return bp || gl
    }

    fun setFilter(f: RegisterFilter) { filter.value = f }

    // ---------- Langkah 1: pendaftaran ----------
    private val query = MutableStateFlow("")
    val results: StateFlow<List<CitizenSummary>> = query.debounce(150).mapLatest { q ->
        if (q.isBlank()) emptyList() else summaries(citizens.search(q).let { l -> if (l.isEmpty()) listOfNotNull(citizens.get(SehatiId.normalize(q))?.takeIf { it.role == "WARGA" }) else l })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQuery(q: String) { query.value = q }

    private suspend fun summaries(l: List<UserEntity>): List<CitizenSummary> {
        val follow = posyandu.observeFollowUps().first()
        return l.map { u ->
            CitizenSummary(u.sehatiId, u.fullName, AgeCalc.age(u.birthDate), u.rw, u.village, health.checks(u.sehatiId).firstOrNull()?.measuredAt,
                follow.any { it.userId == u.sehatiId && it.status != "DONE" && it.status != "CANCELLED" })
        }
    }

    private val _scanMessage = MutableStateFlow<String?>(null)
    val scanMessage: StateFlow<String?> = _scanMessage.asStateFlow()
    private val _scanned = MutableStateFlow<CitizenSummary?>(null)
    val scanned: StateFlow<CitizenSummary?> = _scanned.asStateFlow()
    fun clearScan() { _scanned.value = null; _scanMessage.value = null }

    fun onScanned(raw: String) {
        viewModelScope.launch {
            val u = citizens.resolveQr(raw)
            if (u == null || u.role != "WARGA") { _scanMessage.value = "QR tidak dikenali atau bukan QR SEHATI warga. Coba cari dengan SEHATI ID."; _scanned.value = null }
            else { _scanned.value = summaries(listOf(u)).first(); _scanMessage.value = null }
        }
    }

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    fun clearError() { _error.value = null }

    fun registerVisit(citizenId: String, onVisit: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { posyandu.registerVisit(citizenId, "fac-melati") }
                .onSuccess { scheduler.requestSync(); onVisit(it.id) }
                .onFailure { _error.value = it.message ?: "Gagal mendaftarkan kunjungan." }
        }
    }

    private val _newForm = MutableStateFlow(NewCitizenForm())
    val newForm: StateFlow<NewCitizenForm> = _newForm.asStateFlow()
    fun updateNew(block: NewCitizenForm.() -> NewCitizenForm) = _newForm.update { it.block().copy(error = null) }
    fun resetNew() { _newForm.value = NewCitizenForm() }

    fun registerCitizen(onDone: (UserEntity) -> Unit) {
        val f = _newForm.value
        val birth = runCatching { LocalDate.of(f.year.toInt(), f.month.toInt(), f.day.toInt()) }.getOrNull()?.takeIf { !it.isAfter(LocalDate.now()) }
        val err = when {
            f.name.isBlank() -> "Nama wajib diisi."
            birth == null -> "Tanggal lahir tidak valid."
            f.sex == null -> "Pilih jenis kelamin."
            f.rw.isBlank() -> "RW wajib diisi."
            !f.consent -> "Persetujuan warga untuk pencatatan data wajib."
            else -> null
        }
        if (err != null) { _newForm.update { it.copy(error = err) }; return }
        _newForm.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching {
                citizens.registerByCadre(current.id ?: "", f.name, birth!!.toString(), f.sex!!, "Desa Mirigambar", f.rw.padStart(2, '0'), f.rt, f.phone, f.consent, f.consentServer)
            }.onSuccess { _newForm.value = NewCitizenForm(); scheduler.requestSync(); onDone(it) }
                .onFailure { e -> _newForm.update { it.copy(saving = false, error = e.message) } }
        }
    }

    // ---------- Follow-up & kunjungan rumah ----------
    fun setFollowStatus(id: String, status: FollowUpStatus) { viewModelScope.launch { runCatching { posyandu.updateFollowUpStatus(id, status) }.onFailure { _error.value = it.message } } }

    fun requestReferral(followUpId: String, verified: Boolean, reason: String) {
        if (!verified) { _error.value = "Verifikasi hasil skrining oleh kader/tenaga kesehatan diperlukan sebelum rujukan."; return }
        viewModelScope.launch { runCatching { posyandu.requestReferral(followUpId, "fac-pkm", reason) }.onSuccess { scheduler.requestSync() }.onFailure { _error.value = it.message } }
    }

    fun scheduleHomeVisit(followUpId: String, inDays: Int) {
        val id = current.id ?: return
        viewModelScope.launch { runCatching { posyandu.scheduleHomeVisit(followUpId, id, clock.now() + inDays * 86_400_000L) }.onSuccess { scheduler.requestSync() }.onFailure { _error.value = it.message } }
    }

    fun arrive(id: String) { viewModelScope.launch { posyandu.arriveHomeVisit(id) } }
    fun verify(id: String, ok: Boolean) { viewModelScope.launch { posyandu.verifyCitizenAtHome(id, ok) } }
    fun homeMeasure(id: String, input: MeasurementInput, onDone: () -> Unit) {
        viewModelScope.launch { runCatching { posyandu.recordHomeVisitMeasurement(id, input) }.onSuccess { onDone() }.onFailure { _error.value = it.message } }
    }
    fun closeHome(id: String, assessment: String, education: String, action: String, resolved: Boolean) {
        viewModelScope.launch { runCatching { posyandu.closeHomeVisit(id, assessment, education, action, resolved) }.onSuccess { scheduler.requestSync() }.onFailure { _error.value = it.message } }
    }

    // ---------- Sinkronisasi ----------
    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()
    val recentQueue: StateFlow<List<SyncQueueEntity>> = syncController.recent(30).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun syncNow() {
        _syncing.value = true
        viewModelScope.launch { _syncMessage.value = syncController.describe(syncController.syncNow()); _syncing.value = false }
    }
}
