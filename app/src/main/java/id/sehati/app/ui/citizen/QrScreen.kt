package id.sehati.app.ui.citizen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sehati.app.domain.rules.QrPayload
import id.sehati.app.ui.app.CurrentUser
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*
import javax.inject.Inject

@HiltViewModel
class QrViewModel @Inject constructor(current: CurrentUser) : ViewModel() { val user = current.user }

@Composable
fun QrScreen(onBack: () -> Unit, vm: QrViewModel = hiltViewModel()) {
    val u by vm.user.collectAsStateWithLifecycle(null)
    ScreenColumn(Modifier.testTag("qr_screen")) {
        ScreenHeader("QR SEHATI", "Tunjukkan ke kader saat di Posyandu", onBack = onBack)
        u?.let {
            Column(Modifier.fillMaxWidth().staggerIn(0), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QrImage(QrPayload.build(it.sehatiId, it.qrToken), size = 280.dp, modifier = Modifier.testTag("qr_image"))
                Text(it.sehatiId, style = MaterialTheme.typography.headlineMedium, color = PrimaryDark, modifier = Modifier.testTag("qr_sehati_id"))
                Text(it.fullName, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            }
            InfoNote("QR hanya berisi SEHATI ID dan kode acak. Tidak ada NIK, alamat, atau data kesehatan di dalamnya. Kader tetap membutuhkan SEHATI ID bila kamera tidak dapat membaca.", icon = Icons.Rounded.Shield)
        }
    }
}
