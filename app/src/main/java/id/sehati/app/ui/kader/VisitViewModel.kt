package id.sehati.app.ui.kader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sehati.app.data.local.PosyanduVisitEntity
import id.sehati.app.data.local.UserEntity
import id.sehati.app.data.repository.*
import id.sehati.app.data.sync.SyncController
import id.sehati.app.data.sync.SyncOutcome
import id.sehati.app.data.work.WorkScheduler
import id.sehati.app.domain.content.Academy
import id.sehati.app.domain.content.EducationModule
import id.sehati.app.domain.rules.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MeasureForm(
    val weight: String = "", val height: String = "", val waist: String = "",
    val systolic: String = "", val diastolic: String = "", val heartRate: String = "",
    val glucose: String = "", val fasting: Boolean = false, val cholesterol: String = "",
    val cuffOk: Boolean = true, val rested: Boolean = true, val repeatNeeded: Boolean = false,
)

enum class SyncState { NONE, LOCAL, SYNCED }

data class VisitUi(
    val loading: Boolean = true,
    val visit: PosyanduVisitEntity? = null,
    val citizen: UserEntity? = null,
    val step: Int = 0,
    val history: List<HealthCheck> = emptyList(),
    val form: MeasureForm = MeasureForm(),
    val saved: HealthCheck? = null,
    val recommended: List<EducationModule> = emptyList(),
    val delivered: Set<String> = emptySet(),
    val confirmed: Boolean = false,
    val result: VisitResult? = null,
    val syncState: SyncState = SyncState.NONE,
    val syncMessage: String? = null,
    val error: String? = null,
    val working: Boolean = false,
)

@HiltViewModel
class VisitViewModel @Inject constructor(
    private val posyandu: PosyanduRepository,
    private val citizens: CitizenRepository,
    private val health: HealthRepository,
    private val syncController: SyncController,
    private val scheduler: WorkScheduler,
) : ViewModel() {
    private val _ui = MutableStateFlow(VisitUi())
    val ui: StateFlow<VisitUi> = _ui.asStateFlow()
    private var loadedId: String? = null

    fun load(visitId: String) {
        if (loadedId == visitId) return
        loadedId = visitId
        _ui.value = VisitUi()
        viewModelScope.launch {
            val v = posyandu.observeVisit(visitId).first() ?: run { _ui.update { it.copy(loading = false, error = "Kunjungan tidak ditemukan.") }; return@launch }
            val u = citizens.get(v.userId)
            val history = runCatching { posyandu.historyForVisit(visitId) }.getOrDefault(emptyList())
            val latestHeight = history.firstNotNullOfOrNull { it.heightCm } ?: health.observeLatestAssessmentOnce(v.userId)?.heightCm
            val done = v.status == "COMPLETED"
            _ui.value = VisitUi(
                loading = false, visit = v, citizen = u, history = history, step = if (done) 3 else 0,
                form = MeasureForm(height = latestHeight?.let { if (it > 0) it.fmt1() else "" } ?: ""),
            )
        }
    }

    fun update(block: MeasureForm.() -> MeasureForm) = _ui.update { it.copy(form = it.form.block(), error = null) }

    private fun f(s: String) = s.replace(',', '.').toFloatOrNull()
    fun input(form: MeasureForm = _ui.value.form) = MeasureInput(
        form.systolic.toIntOrNull(), form.diastolic.toIntOrNull(), form.heartRate.toIntOrNull(),
        f(form.weight), f(form.height), f(form.waist), f(form.glucose), f(form.cholesterol),
    )

    fun issues(form: MeasureForm = _ui.value.form) = MeasurementValidator.validate(input(form))
    fun bmi(form: MeasureForm = _ui.value.form): Float {
        val h = f(form.height); val w = f(form.weight)
        return if (h != null && w != null) AnthropometryRules.bmi(h, w) else 0f
    }

    fun goReview() {
        val bl = issues().firstOrNull { it.blocking }
        if (bl != null) { _ui.update { it.copy(error = bl.message) }; return }
        _ui.update { it.copy(step = 1, error = null) }
    }

    fun back() = _ui.update { if (it.step > 0 && it.saved == null) it.copy(step = it.step - 1) else it }

    /** Langkah 3: konfirmasi → simpan ke Room (rekam kesehatan warga + kunjungan Posyandu). */
    fun confirmAndSave() {
        val s = _ui.value; val v = s.visit ?: return
        _ui.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            runCatching {
                posyandu.recordMeasurement(v.id, input(), s.form.fasting, QualityCheck(s.form.cuffOk, s.form.rested, s.form.repeatNeeded))
            }.onSuccess { saved ->
                val findings = health.decodeFindings(health.observeProfile(v.userId).first())
                val rec = Academy.recommendFor(findings.map { it.id }).take(3)
                _ui.update { it.copy(working = false, saved = saved, recommended = rec, delivered = rec.map { m -> m.id }.toSet(), step = 2) }
                scheduler.requestSync()
            }.onFailure { e -> _ui.update { it.copy(working = false, error = e.message ?: "Gagal menyimpan. Data input tetap ada; coba lagi.") } }
        }
    }

    fun toggleModule(id: String) = _ui.update { it.copy(delivered = if (id in it.delivered) it.delivered - id else it.delivered + id) }

    fun saveEducation() {
        val v = _ui.value.visit ?: return
        viewModelScope.launch {
            runCatching { posyandu.recordEducation(v.id, _ui.value.delivered.toList()) }
            _ui.update { it.copy(step = 3) }
        }
    }

    fun setConfirmed(b: Boolean) = _ui.update { it.copy(confirmed = b) }

    /** Langkah 5: validasi kader → simpan → sinkronkan. Tersimpan lokal dahulu; sinkronisasi menyusul. */
    fun validateAndSync() {
        val s = _ui.value; val v = s.visit ?: return
        if (!s.confirmed) { _ui.update { it.copy(error = "Centang konfirmasi kader terlebih dahulu.") }; return }
        _ui.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            runCatching { posyandu.completeVisit(v.id) }.onSuccess { res ->
                _ui.update { it.copy(result = res, visit = res.visit, syncState = SyncState.LOCAL, working = false) }
                scheduler.requestSync()
                val outcome = syncController.syncNow()
                // Status ditentukan dari baris kunjungan itu sendiri (bukan sekadar hasil umum sinkronisasi)
                val synced = posyandu.observeVisit(v.id).first()?.syncStatus == "SYNCED"
                val note = if (synced) syncController.describe(outcome) else when (outcome) {
                    is SyncOutcome.Offline -> "Tersimpan di perangkat. Menunggu internet."
                    else -> "Tersimpan di perangkat. Menunggu sinkronisasi (internet atau persetujuan warga)."
                }
                _ui.update { it.copy(syncState = if (synced) SyncState.SYNCED else SyncState.LOCAL, syncMessage = note) }
            }.onFailure { e -> _ui.update { it.copy(working = false, error = e.message ?: "Gagal menyelesaikan kunjungan.") } }
        }
    }
}
