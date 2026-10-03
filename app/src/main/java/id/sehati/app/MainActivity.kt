package id.sehati.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import id.sehati.app.ui.app.SehatiRoot
import id.sehati.app.ui.theme.SehatiTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        id.sehati.app.ui.app.DeepLinks.open(intent?.getStringExtra(id.sehati.app.data.reminders.Notifier.EXTRA_ROUTE))
        setContent { SehatiTheme { SehatiRoot() } }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        id.sehati.app.ui.app.DeepLinks.open(intent.getStringExtra(id.sehati.app.data.reminders.Notifier.EXTRA_ROUTE))
    }
}
