package id.sehati.app.ui.citizen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sehati.app.data.local.UserEntity
import id.sehati.app.data.prefs.AppSettings
import id.sehati.app.data.prefs.NotificationPrefs
import id.sehati.app.data.prefs.SettingsStore
import id.sehati.app.data.repository.CitizenRepository
import id.sehati.app.data.sync.SyncController
import id.sehati.app.data.sync.SyncOverview
import id.sehati.app.domain.rules.DailyTargets
import id.sehati.app.ui.app.CurrentUser
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val user: UserEntity? = null, val settings: AppSettings = AppSettings(), val sync: SyncOverview = SyncOverview(0, 0, 0, null),
    val destination: String = "", val syncMessage: String? = null, val syncing: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val current: CurrentUser,
    private val citizens: CitizenRepository,
    private val settingsStore: SettingsStore,
    private val syncController: SyncController,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)
    private val syncing = MutableStateFlow(false)
    private val destination = MutableStateFlow("")

    init { viewModelScope.launch { destination.value = syncController.destinationLabel() } }

    val state: StateFlow<ProfileUiState> = combine(current.user, settingsStore.settings, syncController.overview, message, syncing, destination) { values ->
        ProfileUiState(values[0] as UserEntity?, values[1] as AppSettings, values[2] as SyncOverview, values[5] as String, values[3] as String?, values[4] as Boolean)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileUiState())

    fun setConsent(local: Boolean, server: Boolean, hc: Boolean) { val id = current.id ?: return; viewModelScope.launch { citizens.updateConsent(id, local, server, hc) } }
    fun setTargets(t: DailyTargets) { viewModelScope.launch { settingsStore.setTargets(t) } }
    fun setNotifications(n: NotificationPrefs) { viewModelScope.launch { settingsStore.setNotifications(n) } }
    fun setDemoSimulation(v: Boolean) { viewModelScope.launch { settingsStore.setDemoServerSimulation(v); destination.value = syncController.destinationLabel() } }
    fun syncNow() {
        syncing.value = true
        viewModelScope.launch { message.value = syncController.describe(syncController.syncNow()); syncing.value = false }
    }
    fun deleteMyData(onDone: () -> Unit) { val id = current.id ?: return; viewModelScope.launch { citizens.deleteAllDataOf(id); onDone() } }
}
