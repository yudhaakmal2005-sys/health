package id.sehati.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.sehati.app.ui.theme.SehatiTheme

/** Penjelasan izin Health Connect: apa yang dibaca, untuk apa, dan bagaimana mencabutnya. */
class PermissionRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SehatiTheme {
                Column(Modifier.padding(24.dp)) {
                    Text("Data kesehatan di SEHATI", style = MaterialTheme.typography.headlineSmall)
                    Text("SEHATI membaca langkah, tidur, detak jantung, tekanan darah, gula darah, hidrasi, latihan, berat badan, dan jarak dari Health Connect untuk membantumu memantau kesehatan. Data diproses di perangkatmu dan hanya dikirim ke server bila kamu mengizinkan. Kamu dapat mencabut izin kapan saja di pengaturan Health Connect.", modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}
