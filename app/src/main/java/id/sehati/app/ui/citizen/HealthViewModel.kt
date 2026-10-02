package id.sehati.app.ui.citizen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sehati.app.data.local.FollowUpEntity
import id.sehati.app.data.local.ReferralEntity
import id.sehati.app.data.local.UserEntity
import id.sehati.app.data.repository.*
import id.sehati.app.data.work.WorkScheduler
import id.sehati.app.domain.model.DataSource
import id.sehati.app.domain.model.RiskLevel
import id.sehati.app.domain.rules.MeasurementInput
import id.sehati.app.domain.rules.MeasurementValidator
import id.sehati.app.ui.app.CurrentUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HealthUiState(
    val loading: Boolean = true,
    val user: UserEntity? = null,
    val level: RiskLevel = RiskLevel.HEALTHY_HABIT,
    val findings: List<FindingDto> = emptyList(),
    val checks: List<HealthCheck> = emptyList(),
    val followUps: List<FollowUpEntity> = emptyList(),
    val referrals: List<ReferralEntity> = emptyList(),
)

data class SelfForm(
    val systolic: String = "", val diastolic: String = "", val heartRate: String = "", val weight: String = "",
    val waist: String = "", val glucose: String = "", val fasting: Boolean = false, val notes: String = "",
    val saving: Boolean = false, val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HealthViewModel @Inject constructor(
    private val current: CurrentUser,
    private val health: HealthRepository,
    private val posyandu: PosyanduRepository,
    private val scheduler: WorkScheduler,
) : ViewModel() {
    val state: StateFlow<HealthUiState> = current.user.filterNotNull().flatMapLatest { u ->
        combine(health.observeProfile(u.sehatiId), health.observeChecks(u.sehatiId), posyandu.observeFollowUpsOf(u.sehatiId), posyandu.observeReferralsOf(u.sehatiId)) { p, c, f, r ->
            HealthUiState(false, u, health.levelOf(p), health.decodeFindings(p), c, f, r)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HealthUiState())

    private val _form = MutableStateFlow(SelfForm())
    val form: StateFlow<SelfForm> = _form.asStateFlow()
    private val _saved = MutableStateFlow<HealthCheck?>(null)
    val saved: StateFlow<HealthCheck?> = _saved.asStateFlow()

    fun update(block: SelfForm.() -> SelfForm) = _form.update { it.block().copy(error = null) }
    fun clearSaved() { _saved.value = null; _form.value = SelfForm() }

    private fun f(s: String) = s.replace(',', '.').toFloatOrNull()

    fun save() {
        val id = current.id ?: return
        val f = _form.value
        val input = MeasurementInput(f.systolic.toIntOrNull(), f.diastolic.toIntOrNull(), f.heartRate.toIntOrNull(), f(f.weight), null, f(f.waist), f(f.glucose), null)
        val issues = MeasurementValidator.validate(input)
        issues.firstOrNull { it.blocking }?.let { e -> _form.update { it.copy(error = e.message) }; return }
        _form.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching { health.saveMeasurement(NewMeasurement(id, DataSource.SELF, input, f.fasting, notes = f.notes.trim())) }
                .onSuccess { _saved.value = it; _form.update { s -> s.copy(saving = false) }; scheduler.requestSync() }
                .onFailure { _form.update { s -> s.copy(saving = false, error = "Data belum tersimpan. Data tetap aman di perangkat; coba lagi.") } }
        }
    }
}
