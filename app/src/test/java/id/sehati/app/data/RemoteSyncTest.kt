package id.sehati.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import id.sehati.app.data.local.HabitLogEntity
import id.sehati.app.data.remote.PullItem
import id.sehati.app.data.remote.ServerClient
import id.sehati.app.data.sync.SyncApplier
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RemoteSyncTest {
    private lateinit var env: TestEnv
    @Before fun setUp() { env = TestEnv() }
    @After fun tearDown() = env.close()

    @Test fun normalizeServerUrl() {
        assertEquals("https://sehati.desa.id/api/v1/", ServerClient.normalize("https://sehati.desa.id", allowHttp = false))
        assertEquals("https://sehati.desa.id/api/v1/", ServerClient.normalize(" https://sehati.desa.id/api/v1 ", allowHttp = false))
        assertNull(ServerClient.normalize("http://sehati.desa.id", allowHttp = false))
        assertEquals("http://10.0.2.2:8080/api/v1/", ServerClient.normalize("http://10.0.2.2:8080/", allowHttp = true))
        assertNull(ServerClient.normalize("https://api.sehati.invalid/", allowHttp = true))
        assertNull(ServerClient.normalize("", allowHttp = true))
    }

    @Test fun pullAppliesNewerVersionsOnly() = blocking {
        val u = env.citizen()
        val applier = SyncApplier(env.db, env.json)
        fun habit(v: Int, steps: Int) = HabitLogEntity("${u.sehatiId}|2026-05-01", u.sehatiId, "2026-05-01", steps = steps, updatedAt = 1L, version = v)
        val item = { v: Int, steps: Int, seq: Long -> PullItem(seq, "habit", "${u.sehatiId}|2026-05-01", u.sehatiId, v, false, env.json.encodeToString(HabitLogEntity.serializer(), habit(v, steps))) }
        assertEquals(1, applier.apply(listOf(item(2, 5000, 1))).applied)
        assertEquals(0, applier.apply(listOf(item(1, 100, 2))).applied)          // versi lama diabaikan
        val h = env.daily.observeHabit(u.sehatiId, "2026-05-01").first()!!
        assertEquals(5000, h.steps)
        assertEquals("SYNCED", h.syncStatus)
        assertEquals(0, applier.apply(listOf(PullItem(3, "unknown", "x", null, 1, false, "{}"))).applied)
    }

    @Test fun pulledUserComesBeforeChildRows() = blocking {
        val applier = SyncApplier(env.db, env.json)
        val now = env.clock.now()
        val user = id.sehati.app.data.local.UserEntity("HM-000900", "Warga Baru", "1990-01-01", "MALE", "Desa Uji", "03", qrToken = "t",
            consentLocal = true, consentServerSync = true, createdAt = now, updatedAt = now, version = 1)
        val prof = id.sehati.app.data.local.HealthProfileEntity(userId = "HM-000900", level = "RISK_AWARENESS", findingsJson = "[]", planJson = "[]", rulesetVersion = "x", computedAt = now, version = 1)
        val r = applier.apply(listOf(
            PullItem(2, "profile", "HM-000900", "HM-000900", 1, false, env.json.encodeToString(id.sehati.app.data.local.HealthProfileEntity.serializer(), prof)),
            PullItem(1, "user", "HM-000900", "HM-000900", 1, false, env.json.encodeToString(id.sehati.app.data.local.UserEntity.serializer(), user)),
        ))
        assertEquals(2, r.applied)
        assertNotNull(env.db.userDao().get("HM-000900"))
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MedicationRepositoryTest {
    private lateinit var env: TestEnv
    @Before fun setUp() { env = TestEnv() }
    @After fun tearDown() = env.close()

    @Test fun saveTakeAndUntake() = blocking {
        val u = env.citizen()
        val m = env.meds.save(u.sehatiId, "Amlodipin", "1 tablet pagi", listOf("19:00", "7.00"))
        assertEquals("07:00,19:00", m.times)
        val day = java.time.LocalDate.of(2026, 10, 5)
        env.meds.setTaken(m.id, day, "07:00", true)
        env.meds.setTaken(m.id, day, "07:00", true) // idempoten
        assertEquals(1, env.meds.observeLogs(u.sehatiId, day).first().size)
        env.meds.setTaken(m.id, day, "07:00", false)
        assertEquals(0, env.meds.observeLogs(u.sehatiId, day).first().size)
        assertFailsWith<IllegalArgumentException> { env.meds.save(u.sehatiId, "", "", listOf("07:00")) }
        assertFailsWith<IllegalArgumentException> { env.meds.save(u.sehatiId, "X", "", listOf("99:00")) }
    }
}
