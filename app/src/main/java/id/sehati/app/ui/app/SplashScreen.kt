package id.sehati.app.ui.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.sehati.app.ui.components.HeartLogo
import id.sehati.app.ui.theme.*

@Composable
fun SplashScreen() {
    Column(Modifier.fillMaxSize().testTag("splash_screen"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        HeartLogo(120.dp)
        Spacer(Modifier.height(24.dp))
        Text("SEHATI", style = MaterialTheme.typography.headlineLarge, color = PrimaryDark)
        Text("Sehat Hati, Sehat Jantung", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Spacer(Modifier.height(20.dp))
        Text("Menyiapkan aplikasi…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}
