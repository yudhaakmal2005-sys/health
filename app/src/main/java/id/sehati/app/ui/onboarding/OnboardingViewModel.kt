package id.sehati.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sehati.app.data.repository.AuthRepository
import id.sehati.app.data.repository.AuthResult
import id.sehati.app.data.repository.Registration
import id.sehati.app.domain.model.Sex
import id.sehati.app.domain.rules.AgeCalc
import id.sehati.app.domain.rules.PasswordHasher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class OnboardingForm(
    val name: String = "", val day: String = "", val month: String = "", val year: String = "",
    val sex: Sex? = null, val village: String = "Desa Mirigambar", val rw: String = "", val rt: String = "", val phone: String = "",
    val goals: Set<String> = emptySet(),
    val consentLocal: Boolean = false, val consentServer: Boolean = false, val consentHealthConnect: Boolean = false,
    val password: String = "", val confirm: String = "",
    val loading: Boolean = false, val error: String? = null,
)

val GOAL_OPTIONS = listOf("Lebih aktif", "Menjaga berat badan", "Mengurangi rokok", "Menjaga tekanan darah", "Memperbaiki pola makan", "Memantau kesehatan")

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val _form = MutableStateFlow(OnboardingForm())
    val form: StateFlow<OnboardingForm> = _form.asStateFlow()

    fun update(block: OnboardingForm.() -> OnboardingForm) = _form.update { it.block().copy(error = null) }

    fun birthDate(f: OnboardingForm = _form.value): LocalDate? = runCatching {
        LocalDate.of(f.year.toInt(), f.month.toInt(), f.day.toInt())
    }.getOrNull()?.takeIf { !it.isAfter(LocalDate.now()) && AgeCalc.age(it.toString()) in 0..120 }

    /** Pesan galat untuk halaman [page] (0-based); null bila boleh lanjut. */
    fun pageError(page: Int, f: OnboardingForm = _form.value): String? = when (page) {
        2 -> if (!f.consentLocal) "Persetujuan pencatatan data di perangkat diperlukan untuk memakai SEHATI." else null
        3 -> when {
            f.name.isBlank() -> "Nama wajib diisi."
            birthDate(f) == null -> "Tanggal lahir tidak valid (isi hari, bulan, tahun)."
            f.sex == null -> "Pilih jenis kelamin."
            f.rw.isBlank() -> "RW wajib diisi."
            f.phone.isNotBlank() && f.phone.filter(Char::isDigit).length < 9 -> "Nomor kontak terlalu pendek."
            else -> null
        }
        5 -> PasswordHasher.passwordIssue(f.password) ?: if (f.password != f.confirm) "Konfirmasi kata sandi tidak sama." else null
        else -> null
    }

    fun register(onDone: () -> Unit) {
        val f = _form.value
        pageError(5, f)?.let { e -> _form.update { it.copy(error = e) }; return }
        _form.update { it.copy(loading = true) }
        viewModelScope.launch {
            val r = auth.register(
                Registration(
                    f.name, birthDate(f)!!.toString(), f.sex!!, f.village, f.rw.padStart(2, '0'), f.rt, f.phone.ifBlank { null },
                    f.password, f.goals.toList(), f.consentLocal, f.consentServer, f.consentHealthConnect,
                ),
            )
            when (r) {
                is AuthResult.Success -> { _form.update { OnboardingForm() }; onDone() }
                is AuthResult.Failure -> _form.update { it.copy(loading = false, error = r.message) }
            }
        }
    }
}
