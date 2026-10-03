package id.sehati.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sehati.app.data.demo.DemoSeeder
import id.sehati.app.data.repository.AuthRepository
import id.sehati.app.data.repository.AuthResult
import id.sehati.app.domain.model.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActivationState(
    val id: String = "", val day: String = "", val month: String = "", val year: String = "",
    val password: String = "", val confirm: String = "", val loading: Boolean = false, val error: String? = null,
)

data class LoginState(val identifier: String = "", val password: String = "", val loading: Boolean = false, val error: String? = null)

@HiltViewModel
class AuthViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private val _activation = MutableStateFlow(ActivationState())
    val activation: StateFlow<ActivationState> = _activation.asStateFlow()
    fun onActivation(f: (ActivationState) -> ActivationState) = _activation.update { f(it).copy(error = null) }

    /** Warga yang didaftarkan kader: aktifkan akun dengan SEHATI ID + tanggal lahir lalu buat kata sandi. */
    fun activate() {
        val a = _activation.value
        val birth = runCatching { java.time.LocalDate.of(a.year.toInt(), a.month.toInt(), a.day.toInt()).toString() }.getOrNull()
        when {
            a.id.isBlank() -> { _activation.update { it.copy(error = "Isi SEHATI ID yang tertera di kartu/QR dari kader.") }; return }
            birth == null -> { _activation.update { it.copy(error = "Tanggal lahir belum valid.") }; return }
            a.password != a.confirm -> { _activation.update { it.copy(error = "Konfirmasi kata sandi belum sama.") }; return }
        }
        _activation.update { it.copy(loading = true) }
        viewModelScope.launch {
            when (val r = auth.activate(a.id, birth!!, a.password)) {
                is AuthResult.Success -> _activation.update { ActivationState() }
                is AuthResult.Failure -> _activation.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun onIdentifier(v: String) = _state.update { it.copy(identifier = v, error = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, error = null) }

    fun login() {
        val s = _state.value
        if (s.identifier.isBlank() || s.password.isBlank()) { _state.update { it.copy(error = "Isi SEHATI ID/nomor kontak dan kata sandi.") }; return }
        submit(s.identifier, s.password)
    }

    /** Mode demo: tetap melalui autentikasi yang sama (bukan pintasan role). */
    fun demoLogin(role: Role) {
        val id = when (role) { Role.WARGA -> DemoSeeder.GOLDEN_ID; Role.KADER -> DemoSeeder.CADRE_ID; Role.ADMIN -> DemoSeeder.ADMIN_ID }
        _state.update { it.copy(identifier = id, password = DemoSeeder.DEMO_PASSWORD) }
        submit(id, DemoSeeder.DEMO_PASSWORD)
    }

    private fun submit(id: String, pw: String) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = auth.login(id, pw)) {
                is AuthResult.Success -> _state.update { LoginState() }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }
}
