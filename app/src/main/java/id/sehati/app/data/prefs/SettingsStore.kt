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

private val reminderJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }

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
    /** Alamat server VPS yang diatur pengguna/kader (kosong = bawaan build). */
    val serverUrl: String = "",
    val aiConsent: Boolean = false,
    val aiShareContext: Boolean = false,
    val reminders: ReminderPrefs = ReminderPrefs(),
)

/** Pengingat berbasis jam. Semua dapat dimatikan; waktu "HH:mm" waktu lokal perangkat. */
@kotlinx.serialization.Serializable
data class ReminderPrefs(
    val water: Boolean = true,
    val waterTimes: List<String> = listOf("08:00", "11:00", "14:00", "17:00"),
    val walk: Boolean = true,
    val walkTime: String = "16:30",
    val bpCheck: Boolean = true,
    /** 1 = Senin … 7 = Minggu */
    val bpDay: Int = 7,
    val bpTime: String = "08:00",
    val sleep: Boolean = false,
    val sleepTime: String = "21:30",
    val fact: Boolean = true,
    val factTime: String = "09:00",
    val challenge: Boolean = true,
    val challengeTime: String = "19:30",
    val medication: Boolean = true,
    val posyandu: Boolean = true,
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
        val thresholds = stringPreferencesKey("clinical_thresholds"); val seeded = booleanPreferencesKey("demo_seeded"); val server = stringPreferencesKey("server_url"); val aiConsent = booleanPreferencesKey("ai_consent"); val aiCtx = booleanPreferencesKey("ai_ctx"); val reminders = stringPreferencesKey("reminders_v2"); val remoteConfig = stringPreferencesKey("remote_config"); val idPool = stringPreferencesKey("id_pool"); val device = stringPreferencesKey("device_id")
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
            serverUrl = p[K.server] ?: "",
            aiConsent = p[K.aiConsent] ?: false,
            aiShareContext = p[K.aiCtx] ?: false,
            reminders = p[K.reminders]?.let { runCatching { reminderJson.decodeFromString(ReminderPrefs.serializer(), it) }.getOrNull() } ?: ReminderPrefs(),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setServerUrl(v: String) = context.dataStore.edit { it[K.server] = v.trim() }.let { }
    suspend fun setAiConsent(consent: Boolean, shareContext: Boolean) = context.dataStore.edit { it[K.aiConsent] = consent; it[K.aiCtx] = shareContext }.let { }
    suspend fun setReminders(r: ReminderPrefs) = context.dataStore.edit { it[K.reminders] = reminderJson.encodeToString(ReminderPrefs.serializer(), r) }.let { }

    val remoteConfigJson: Flow<String?> = context.dataStore.data.map { it[K.remoteConfig] }
    suspend fun setRemoteConfigJson(v: String) = context.dataStore.edit { it[K.remoteConfig] = v }.let { }

    /** Kursor pull sinkronisasi per akun. */
    suspend fun pullCursor(account: String): Long = context.dataStore.data.first()[longPreferencesKey("pull_$account")] ?: 0L
    suspend fun setPullCursor(account: String, v: Long) = context.dataStore.edit { it[longPreferencesKey("pull_$account")] = v }.let { }

    /** Kolam SEHATI ID yang sudah dipesan dari server (mencegah bentrok antar-perangkat). */
    suspend fun idPool(): List<String> = context.dataStore.data.first()[K.idPool]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
    suspend fun setIdPool(ids: List<String>) = context.dataStore.edit { it[K.idPool] = ids.joinToString(",") }.let { }

    suspend fun thresholdsJson(): String? = context.dataStore.data.first()[K.thresholds]
    suspend fun setThresholdsJson(v: String?) = context.dataStore.edit { if (v == null) it.remove(K.thresholds) else it[K.thresholds] = v }.let { }

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
