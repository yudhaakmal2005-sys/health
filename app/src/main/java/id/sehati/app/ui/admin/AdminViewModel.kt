package id.sehati.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sehati.app.core.util.Clock
import id.sehati.app.core.util.TimeUtils
import id.sehati.app.data.local.*
import id.sehati.app.data.repository.*
import id.sehati.app.data.sync.SyncController
import id.sehati.app.data.sync.SyncOverview
import id.sehati.app.domain.model.FollowUpStatus
import id.sehati.app.domain.model.FollowUpType
import id.sehati.app.domain.model.RiskLevel
import id.sehati.app.domain.rules.*
import id.sehati.app.ui.app.CurrentUser
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Baris registri tindak lanjut untuk admin: hanya ID, alasan, RW — tanpa nama atau hasil individu. */
data class RegistryRow(val follow: FollowUpEntity, val rw: String, val lastCheck: Long?, val cadreName: String?)
data class CadreRow(val cadre: CadreEntity, val name: String)

data class AdminUiState(
    val loading: Boolean = true,
    val admin: UserEntity? = null,
    val stats: CommunityStats? = null,
    val registry: List<RegistryRow> = emptyList(),
    val cadres: List<CadreRow> = emptyList(),
    val logistics: List<LogisticsItemEntity> = emptyList(),
    val audit: List<AuditLogEntity> = emptyList(),
    val sync: SyncOverview = SyncOverview(0, 0, 0, null),
    val destination: String = "",
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val current: CurrentUser,
    private val citizens: CitizenRepository,
    private val health: HealthRepository,
    private val posyandu: PosyanduRepository,
    private val syncController: SyncController,
    private val clock: Clock,
    private val thresholds: ThresholdService,
) : ViewModel() {
    private val destination = MutableStateFlow("")
    init { viewModelScope.launch { destination.value = syncController.destinationLabel() } }

    private data class G1(val users: List<UserEntity>, val checks: List<HealthCheck>, val assessments: List<HealthAssessmentEntity>, val follow: List<FollowUpEntity>, val profiles: List<HealthProfileEntity>)
    private data class G2(val cadres: List<CadreEntity>, val staff: List<UserEntity>, val logistics: List<LogisticsItemEntity>, val audit: List<AuditLogEntity>)

    private val g1 = combine(citizens.observeCitizens(), health.observeAllChecks(), health.observeAllAssessments(), posyandu.observeFollowUps(), health.observeAllProfiles(), ::G1)
    private val g2 = combine(posyandu.observeCadres(), citizens.observeByRole(id.sehati.app.domain.model.Role.KADER), posyandu.observeLogistics(), posyandu.observeAudit(100), ::G2)

    val state: StateFlow<AdminUiState> = combine(current.user, g1, g2, syncController.overview, destination) { admin, a, b, sync, dest ->
        val rwOf = a.users.associate { it.sehatiId to it.rw }
        val stats = AnalyticsMapper.stats(a.users, a.checks, a.assessments, a.follow, a.profiles)
        val names = b.staff.associate { it.sehatiId to it.fullName }
        val lastCheck = a.checks.groupBy { it.userId }.mapValues { it.value.maxOf { c -> c.measuredAt } }
        val registry = a.follow.filter { it.status != "DONE" && it.status != "CANCELLED" }
            .sortedWith(compareByDescending<FollowUpEntity> { it.priority }.thenBy { it.dueAt })
            .map { RegistryRow(it, rwOf[it.userId] ?: "", lastCheck[it.userId], it.assignedCadreId?.let { id -> names[id] }) }
        AdminUiState(false, admin, stats, registry, b.cadres.map { CadreRow(it, names[it.sehatiId] ?: it.sehatiId) }, b.logistics, b.audit, sync, dest)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AdminUiState())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    fun clearError() { _error.value = null }

    fun assign(followUpId: String, cadreId: String?) { viewModelScope.launch { runCatching { posyandu.assignCadre(followUpId, cadreId) }.onFailure { _error.value = it.message } } }
    fun scheduleRecheck(followUpId: String, inDays: Int) { viewModelScope.launch { runCatching { posyandu.scheduleRecheck(followUpId, clock.now() + inDays * 86_400_000L) }.onFailure { _error.value = it.message } } }
    fun setCadreActive(id: String, active: Boolean) { viewModelScope.launch { runCatching { posyandu.setCadreActive(id, active) }.onFailure { _error.value = it.message } } }
    fun addCadre(name: String, rw: String, password: String, onDone: (String) -> Unit) {
        viewModelScope.launch { runCatching { posyandu.addCadre(name, rw.padStart(2, '0'), password) }.onSuccess { onDone(it.sehatiId) }.onFailure { _error.value = it.message } }
    }
    fun adjustStock(id: String, delta: Int) { viewModelScope.launch { runCatching { posyandu.adjustStock(id, delta) }.onFailure { _error.value = it.message } } }

    private val _thresholdMsg = MutableStateFlow<String?>(null)
    val thresholdMessage: StateFlow<String?> = _thresholdMsg.asStateFlow()
    private val _thresholdVersion = MutableStateFlow(0)
    val thresholdVersion: StateFlow<Int> = _thresholdVersion.asStateFlow()
    fun saveThresholds(t: ClinicalThresholds) {
        viewModelScope.launch {
            val err = runCatching { thresholds.save(t) }.getOrElse { it.message }
            _thresholdMsg.value = err ?: "Ambang disimpan. Semua profil dihitung ulang."
            _thresholdVersion.value++
        }
    }
    fun resetThresholds() {
        viewModelScope.launch {
            val err = runCatching { thresholds.reset() }.getOrElse { it.message }
            _thresholdMsg.value = err ?: "Ambang dikembalikan ke bawaan aplikasi."
            _thresholdVersion.value++
        }
    }

    fun reportText(): String? = state.value.stats?.let { ReportBuilder.text(it, TimeUtils.dateTime(clock.now()), "Desa Mirigambar") }
    fun reportCsv(): String? = state.value.stats?.let { ReportBuilder.csv(it) }

    private val _syncMsg = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMsg.asStateFlow()
    fun syncNow() { viewModelScope.launch { _syncMsg.value = syncController.describe(syncController.syncNow()) } }
}
