package id.sehati.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sehati.app.BuildConfig
import id.sehati.app.data.prefs.SettingsStore
import id.sehati.app.data.remote.ApiResult
import id.sehati.app.data.remote.ServerClient
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerUiState(
    val url: String = "",
    val testing: Boolean = false,
    val message: String? = null,
    val ok: Boolean? = null,
    val linked: Boolean = false,
)

@HiltViewModel
class ServerSettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val server: ServerClient,
) : ViewModel() {
    private val _s = MutableStateFlow(ServerUiState())
    val state: StateFlow<ServerUiState> = _s.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = settings.current().serverUrl
            _s.update { it.copy(url = saved.ifBlank { BuildConfig.BASE_URL.takeUnless { u -> u.contains(".invalid") }.orEmpty() }, linked = server.hasToken) }
        }
    }

    fun onUrl(v: String) = _s.update { it.copy(url = v, message = null, ok = null) }

    /** Simpan lalu uji `GET /health`. Rilis hanya menerima HTTPS. */
    fun saveAndTest() {
        val raw = _s.value.url.trim()
        if (raw.isNotEmpty() && ServerClient.normalize(raw, BuildConfig.DEBUG) == null) {
            _s.update { it.copy(ok = false, message = "Alamat harus diawali https:// (contoh: https://sehati.desaku.id)") }
            return
        }
        _s.update { it.copy(testing = true, message = null, ok = null) }
        viewModelScope.launch {
            settings.setServerUrl(raw)
            val r = server.call { it.health() }
            _s.update {
                when (r) {
                    is ApiResult.Ok -> it.copy(testing = false, ok = true, message = "Terhubung ke server SEHATI ${r.value.version}" + if (r.value.ai) " · Tanya SEHATI AI aktif" else "")
                    is ApiResult.Offline -> it.copy(testing = false, ok = false, message = r.message)
                    is ApiResult.Failure -> it.copy(testing = false, ok = false, message = r.message)
                    ApiResult.NotConfigured -> it.copy(testing = false, ok = null, message = "Alamat server dikosongkan. Aplikasi berjalan offline.")
                }
            }
        }
    }
}

/** Kartu pengaturan alamat server VPS. Tanpa server aplikasi tetap bekerja penuh secara offline. */
@Composable
fun ServerSettingsCard(modifier: Modifier = Modifier, vm: ServerSettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    SehatiCard(modifier.testTag("server_settings_card")) {
        Row { IconBadge(Icons.Rounded.Dns, PrimaryDark, PrimaryLight, 40); Spacer(Modifier.width(12.dp))
            Column {
                Text("Server SEHATI", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(if (s.linked) "Akun tertaut ke server" else "Opsional: untuk berbagi data dengan kader & Puskesmas", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
        SehatiTextField(s.url, vm::onUrl, "Alamat server (dari Puskesmas/kader)", keyboardType = KeyboardType.Uri, tag = "server_url_field", supporting = "Contoh: https://sehati.desaku.id")
        s.message?.let { m ->
            InfoNote(m, icon = if (s.ok == true) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                color = if (s.ok == false) RiskRedText else if (s.ok == true) RiskGreenText else PrimaryDark,
                bg = if (s.ok == false) RiskRedBg else if (s.ok == true) RiskGreenBg else PrimaryLight)
        }
        SecondaryButton("Simpan & uji koneksi", vm::saveAndTest, icon = Icons.Rounded.WifiTethering, enabled = !s.testing, tag = "server_test_button")
    }
}
