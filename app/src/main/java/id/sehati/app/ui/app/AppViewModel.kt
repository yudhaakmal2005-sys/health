package id.sehati.app.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sehati.app.BuildConfig
import id.sehati.app.data.demo.DemoSeeder
import id.sehati.app.data.local.UserEntity
import id.sehati.app.data.prefs.SettingsStore
import id.sehati.app.data.repository.AuthRepository
import id.sehati.app.data.repository.CitizenRepository
import id.sehati.app.data.session.Session
import id.sehati.app.data.session.SessionManager
import id.sehati.app.data.work.WorkScheduler
import id.sehati.app.domain.model.Role
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Pengguna yang sedang login, diamati dari Room (sumber kebenaran tunggal). */
@Singleton
class CurrentUser @Inject constructor(private val session: SessionManager, private val citizens: CitizenRepository) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val user: Flow<UserEntity?> = session.session.flatMapLatest { s -> if (s == null) flowOf(null) else citizens.observe(s.sehatiId) }
    val id: String? get() = session.session.value?.sehatiId
    val role: Role? get() = session.session.value?.role
}

sealed interface RootState {
    data object Preparing : RootState
    data object LoggedOut : RootState
    data object Assessment : RootState
    data object Citizen : RootState
    data object Kader : RootState
    data object Admin : RootState
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val auth: AuthRepository,
    private val seeder: DemoSeeder,
    private val settings: SettingsStore,
    private val scheduler: WorkScheduler,
    currentUser: CurrentUser,
) : ViewModel() {

    private val ready = MutableStateFlow(!BuildConfig.DEMO_MODE)
    private val inOnboardingFlow = MutableStateFlow(false)

    val session: StateFlow<Session?> = sessionManager.session
    val user: StateFlow<UserEntity?> = currentUser.user.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val root: StateFlow<RootState> = combine(ready, session, user, inOnboardingFlow) { r, s, u, flow ->
        when {
            !r -> RootState.Preparing
            s == null -> RootState.LoggedOut
            s.role == Role.KADER -> RootState.Kader
            s.role == Role.ADMIN -> RootState.Admin
            u == null -> RootState.Preparing
            flow || !u.assessmentDone -> RootState.Assessment
            else -> RootState.Citizen
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RootState.Preparing)

    init {
        // Warga yang belum menuntaskan asesmen masuk alur: asesmen → profil → rencana (tidak boleh terpotong)
        viewModelScope.launch { user.collect { u -> if (u != null && u.role == Role.WARGA.name && !u.assessmentDone) inOnboardingFlow.value = true } }
        viewModelScope.launch {
            if (BuildConfig.DEMO_MODE) { seeder.seedIfNeeded(); ready.value = true }
            sessionManager.validate()
            scheduler.schedulePeriodicSync()
            scheduler.scheduleReminders()
        }
    }

    /** Dipanggil setelah registrasi baru: alur asesmen → profil → rencana harus tuntas dulu. */
    fun beginAssessmentFlow() { inOnboardingFlow.value = true }
    fun finishAssessmentFlow() { inOnboardingFlow.value = false }

    fun logout() { viewModelScope.launch { inOnboardingFlow.value = false; auth.logout() } }

    /** Sesi dicek saat kembali ke app agar token kedaluwarsa tidak dipakai. */
    fun revalidate() { sessionManager.validate() }
}
