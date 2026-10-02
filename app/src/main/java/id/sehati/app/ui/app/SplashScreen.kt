package id.sehati.app.ui.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.sehati.app.ui.components.IconBadge
import id.sehati.app.ui.components.heartbeatScale
import id.sehati.app.ui.theme.*

@Composable
fun SplashScreen() {
    val s by heartbeatScale()
    Column(Modifier.fillMaxSize().testTag("splash_screen"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.scale(s)) { IconBadge(Icons.Rounded.Favorite, Color_White, Primary, 88) }
        Spacer(Modifier.height(20.dp))
        Text("SEHATI", style = MaterialTheme.typography.headlineLarge, color = PrimaryDark)
        Text("Sistem Edukasi & Pemantauan Kesehatan Komunitas", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Spacer(Modifier.height(28.dp))
        CircularProgressIndicator(Modifier.size(24.dp), color = Primary, strokeWidth = 2.5.dp)
        Spacer(Modifier.height(8.dp))
        Text("Menyiapkan aplikasi…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

private val Color_White = androidx.compose.ui.graphics.Color.White
