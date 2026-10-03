package id.sehati.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import id.sehati.app.data.repository.ThresholdService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SehatiApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var thresholds: dagger.Lazy<ThresholdService>

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.Default).launch { try { thresholds.get().restore() } catch (_: Throwable) { } }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
