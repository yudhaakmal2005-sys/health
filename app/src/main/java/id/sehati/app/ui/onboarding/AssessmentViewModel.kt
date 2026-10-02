package id.sehati.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sehati.app.core.util.Clock
import id.sehati.app.core.util.Ids
import id.sehati.app.data.local.HealthAssessmentEntity
import id.sehati.app.data.repository.FindingDto
import id.sehati.app.data.repository.HealthRepository
import id.sehati.app.data.repository.PlanDto
import id.sehati.app.domain.rules.AnthropometryRules
import id.sehati.app.domain.rules.MeasurementInput
import id.sehati.app.domain.rules.MeasurementValidator
import id.sehati.app.ui.app.CurrentUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AssessmentForm(
    val heightCm: String = "", val weightKg: String = "", val waistCm: String = "",
    val conditions: Set<String> = emptySet(), val otherConditions: String = "",
    val family: Set<String> = emptySet(),
    val smoking: String = "NEVER", val product: String = "Rokok kretek/filter", val cigarettes: Int = 5,
    val vegetableDays: Int = 4, val fruitDays: Int = 3, val salty: Boolean = false, val sugary: Boolean = false, val fatty: Boolean = false,
    val activeDays: Int = 2, val activeMinutes: Int = 20, val intensity: String = "Sedang", val sedentaryHours: Int = 6,
    val sleepHours: Int = 7, val sleepQuality: String = "Cukup", val stress: Int = 2,
    val systolic: String = "", val diastolic: String = "", val heartRate: String = "", val glucose: String = "", val cholesterol: String = "",
    val redFlag: Boolean = false,
    val saving: Boolean = false, val error: String? = null,
)

val CONDITIONS = listOf("Hipertensi", "Diabetes", "Dislipidemia", "Penyakit jantung", "Penyakit ginjal")
val FAMILY = listOf("Hipertensi", "Diabetes", "Penyakit kardiovaskular")
val SMOKING = listOf("NEVER" to "Tidak pernah", "FORMER" to "Mantan perokok", "CURRENT" to "Perokok aktif")
val PRODUCTS = listOf("Rokok kretek/filter", "Rokok elektrik/vape", "Linting", "Lainnya")
val INTENSITY = listOf("Ringan", "Sedang", "Berat")
val SLEEP_Q = listOf("Buruk", "Cukup", "Baik")

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AssessmentViewModel @Inject constructor(
    private val health: HealthRepository,
    private val current: CurrentUser,
    private val clock: Clock,
) : ViewModel() {
    private val _form = MutableStateFlow(AssessmentForm())
    val form: StateFlow<AssessmentForm> = _form.asStateFlow()
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    val level = current.user.filterNotNull().flatMapLatest { health.observeProfile(it.sehatiId) }
        .map { health.levelOf(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val findings: StateFlow<List<FindingDto>> = current.user.filterNotNull().flatMapLatest { health.observeProfile(it.sehatiId) }
        .map { health.decodeFindings(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val plan: StateFlow<List<PlanDto>> = current.user.filterNotNull().flatMapLatest { health.observeProfile(it.sehatiId) }
        .map { health.decodePlan(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun update(block: AssessmentForm.() -> AssessmentForm) = _form.update { it.block().copy(error = null) }

    private fun f(s: String) = s.replace(',', '.').toFloatOrNull()

    fun bmiText(form: AssessmentForm = _form.value): String? {
        val h = f(form.heightCm); val w = f(form.weightKg)
        if (h == null || w == null) return null
        val c = AnthropometryRules.classify(h, w)
        return if (c.bmi > 0) "IMT ${c.bmi} · ${c.category}" else null
    }

    fun stepError(step: Int, form: AssessmentForm = _form.value): String? = when (step) {
        0 -> {
            val issues = MeasurementValidator.validate(MeasurementInput(weightKg = f(form.weightKg), heightCm = f(form.heightCm), waistCm = f(form.waistCm)))
            when {
                f(form.heightCm) == null -> "Isi tinggi badan."
                f(form.weightKg) == null -> "Isi berat badan."
                MeasurementValidator.hasBlocking(issues) -> issues.first { it.blocking }.message
                else -> null
            }
        }
        7 -> {
            val issues = MeasurementValidator.validate(
                MeasurementInput(form.systolic.toIntOrNull(), form.diastolic.toIntOrNull(), form.heartRate.toIntOrNull(), null, null, null, f(form.glucose), f(form.cholesterol)),
            ).filter { it.field != "all" }
            issues.firstOrNull { it.blocking }?.message
        }
        else -> null
    }

    fun submit() {
        val user = current.id ?: return
        val form = _form.value
        stepError(0, form)?.let { e -> _form.update { it.copy(error = e) }; return }
        stepError(7, form)?.let { e -> _form.update { it.copy(error = e) }; return }
        _form.update { it.copy(saving = true) }
        viewModelScope.launch {
            val now = clock.now()
            val smoker = form.smoking == "CURRENT"
            runCatching {
                health.saveAssessment(
                    HealthAssessmentEntity(
                        id = Ids.uuid(), userId = user, takenAt = now,
                        heightCm = f(form.heightCm)!!, weightKg = f(form.weightKg)!!, waistCm = f(form.waistCm) ?: 0f,
                        knownHypertension = "Hipertensi" in form.conditions, knownDiabetes = "Diabetes" in form.conditions,
                        knownDyslipidemia = "Dislipidemia" in form.conditions, knownHeartDisease = "Penyakit jantung" in form.conditions,
                        knownKidneyDisease = "Penyakit ginjal" in form.conditions, otherConditions = form.otherConditions.trim(),
                        familyHypertension = "Hipertensi" in form.family, familyDiabetes = "Diabetes" in form.family,
                        familyCardio = "Penyakit kardiovaskular" in form.family,
                        smokingStatus = form.smoking, smokingProduct = if (smoker) form.product else "", cigarettesPerDay = if (smoker) form.cigarettes else 0,
                        vegetableDays = form.vegetableDays, fruitDays = form.fruitDays, saltyFrequent = form.salty, sugaryFrequent = form.sugary, fattyFrequent = form.fatty,
                        activeDays = form.activeDays, activeMinutes = form.activeMinutes, activityIntensity = form.intensity, sedentaryHours = form.sedentaryHours,
                        sleepHours = form.sleepHours.toFloat(), sleepQuality = form.sleepQuality, stressLevel = form.stress + 1,
                        bpSystolic = form.systolic.toIntOrNull(), bpDiastolic = form.diastolic.toIntOrNull(), heartRate = form.heartRate.toIntOrNull(),
                        glucose = f(form.glucose), cholesterol = f(form.cholesterol), redFlagSymptom = form.redFlag,
                        createdAt = now, updatedAt = now,
                    ),
                )
            }.onSuccess { _saved.value = true; _form.update { it.copy(saving = false) } }
                .onFailure { _form.update { s -> s.copy(saving = false, error = "Data belum tersimpan. Data tetap aman di perangkat; coba lagi.") } }
        }
    }
}
