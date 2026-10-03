package id.sehati.app.ui.citizen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sehati.app.core.util.TimeUtils
import id.sehati.app.data.local.NotificationEntity
import id.sehati.app.data.repository.NotificationRepository
import id.sehati.app.ui.app.CurrentUser
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotificationsViewModel @Inject constructor(private val current: CurrentUser, private val repo: NotificationRepository) : ViewModel() {
    val items: StateFlow<List<NotificationEntity>> = current.user.filterNotNull().flatMapLatest { repo.observe(it.sehatiId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun markAllRead() { val id = current.id ?: return; viewModelScope.launch { repo.markAllRead(id) } }
}

@Composable
fun NotificationsScreen(onBack: () -> Unit, vm: NotificationsViewModel = hiltViewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    LaunchedEffect(items.size) { vm.markAllRead() }
    ScreenColumn(Modifier.testTag("notifications_screen")) {
        ScreenHeader("Notifikasi", "Pengingat dan tindak lanjut", onBack = onBack)
        if (items.isEmpty()) EmptyState(Icons.Outlined.NotificationsNone, "Belum ada notifikasi", "Tindak lanjut dari kader akan muncul di sini.")
        items.forEachIndexed { i, n ->
            SehatiCard(Modifier.staggerIn(i)) {
                Text(n.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(n.body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Text(TimeUtils.dateTime(n.createdAt), style = MaterialTheme.typography.labelMedium, color = TextMuted)
            }
        }
    }
}
