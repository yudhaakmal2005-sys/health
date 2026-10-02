package id.sehati.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import id.sehati.app.domain.rules.DailyTargets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sehati_settings")

data class NotificationPrefs(
    val healthCheck: Boolean = true,
    val activity: Boolean = true,
    val water: Boolean = true,
    val education: Boolean = false,
    val posyandu: Boolean = true,
    val followUp: Boolean = true,
)

data class AppSettings(
    val targets: DailyTargets = DailyTargets(),
    val notifications: NotificationPrefs = NotificationPrefs(),
    val demoServerSimulation: Boolean = true,
    val lastHealthConnectSync: Long = 0,
    val demoSeeded: Boolean = false,
    val deviceId: String = "",
)

class SettingsStore(private val context: Context) {
    private object K {
        val steps = intPreferencesKey("t_steps"); val water = intPreferencesKey("t_water")
        val sleep = intPreferencesKey("t_sleep_x10"); val sodium = intPreferencesKey("t_sodium")
        val sugar = intPreferencesKey("t_sugar")
        val nHealth = booleanPreferencesKey("n_health"); val nAct = booleanPreferencesKey("n_act")
        val nWater = booleanPreferencesKey("n_water"); val nEdu = booleanPreferencesKey("n_edu")
        val nPos = booleanPreferencesKey("n_pos"); val nFollow = booleanPreferencesKey("n_follow")
        val demoSim = booleanPreferencesKey("demo_sim"); val hcSync = longPreferencesKey("hc_sync")
        val seeded = booleanPreferencesKey("demo_seeded"); val device = stringPreferencesKey("device_id")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = DailyTargets()
        AppSettings(
            targets = DailyTargets(
                steps = p[K.steps] ?: d.steps, waterGlasses = p[K.water] ?: d.waterGlasses,
                sleepHours = (p[K.sleep] ?: (d.sleepHours * 10).toInt()) / 10f,
                sodiumMgLimit = p[K.sodium] ?: d.sodiumMgLimit, sugarGramsLimit = p[K.sugar] ?: d.sugarGramsLimit,
            ),
            notifications = NotificationPrefs(
                p[K.nHealth] ?: true, p[K.nAct] ?: true, p[K.nWater] ?: true,
                p[K.nEdu] ?: false, p[K.nPos] ?: true, p[K.nFollow] ?: true,
            ),
            demoServerSimulation = p[K.demoSim] ?: true,
            lastHealthConnectSync = p[K.hcSync] ?: 0,
            demoSeeded = p[K.seeded] ?: false,
            deviceId = p[K.device] ?: "",
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTargets(t: DailyTargets) = context.dataStore.edit {
        it[K.steps] = t.steps; it[K.water] = t.waterGlasses; it[K.sleep] = (t.sleepHours * 10).toInt()
        it[K.sodium] = t.sodiumMgLimit; it[K.sugar] = t.sugarGramsLimit
    }.let { }

    suspend fun setNotifications(n: NotificationPrefs) = context.dataStore.edit {
        it[K.nHealth] = n.healthCheck; it[K.nAct] = n.activity; it[K.nWater] = n.water
        it[K.nEdu] = n.education; it[K.nPos] = n.posyandu; it[K.nFollow] = n.followUp
    }.let { }

    suspend fun setDemoServerSimulation(v: Boolean) = context.dataStore.edit { it[K.demoSim] = v }.let { }
    suspend fun setHealthConnectSynced(at: Long) = context.dataStore.edit { it[K.hcSync] = at }.let { }
    suspend fun setDemoSeeded(v: Boolean) = context.dataStore.edit { it[K.seeded] = v }.let { }
    suspend fun ensureDeviceId(generate: () -> String): String {
        val cur = current().deviceId
        if (cur.isNotBlank()) return cur
        val id = generate()
        context.dataStore.edit { it[K.device] = id }
        return id
    }
}
