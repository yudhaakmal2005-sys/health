package id.sehati.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import id.sehati.app.domain.model.Role
import id.sehati.app.domain.rules.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.*

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ChallengeAndThresholdTest {
    private lateinit var env: TestEnv
    @org.junit.Before fun setUp() { env = TestEnv() }
    private val day = LocalDate.of(2026, 5, 1)
    @After fun tearDown() { env.close(); ClinicalConfig.current = ClinicalThresholds() }

    @Test fun challengeLifecycle() = blocking {
        val u = env.citizen()
        env.daily.startChallenge(u.sehatiId, "salt", day)
        for (i in 0 until 7) env.daily.checkIn(u.sehatiId, "salt", day.plusDays(i.toLong()))
        val c = env.daily.observeChallenges(u.sehatiId).first().single { it.challengeId == "salt" }
        assertNotNull(c.completedAt)
        assertEquals(7, Challenges.decode(c.checkIns).size)
    }

    @Test fun answeringFactIsIdempotentPerDay() = blocking {
        val u = env.citizen()
        env.daily.answerFact(u.sehatiId, day)
        env.daily.answerFact(u.sehatiId, day)
        env.daily.answerFact(u.sehatiId, day.plusDays(1))
        val f = env.daily.observeChallenges(u.sehatiId).first().single { it.challengeId == "fact" }
        assertEquals(2, Challenges.decode(f.checkIns).size)
    }

    @Test fun heartRiskUsesLatestSnapshot() = blocking {
        val u = env.citizen()
        val r = env.health.heartRisk(u.sehatiId)
        assertEquals(HeartBand.INSUFFICIENT, r.band)
    }

    @Test fun onlyAdminCanChangeThresholds() = blocking {
        val svc = id.sehati.app.data.repository.ThresholdService(env.db, id.sehati.app.data.prefs.SettingsStore(androidx.test.core.app.ApplicationProvider.getApplicationContext()), env.health, env.session, env.audit, env.json)
        env.staff("KD-000001", Role.KADER)
        env.session.start("KD-000001", Role.KADER)
        assertNotNull(svc.save(ClinicalThresholds(bpHighSys = 150)))
        env.staff("AD-000001", Role.ADMIN)
        env.session.start("AD-000001", Role.ADMIN)
        assertNotNull(svc.save(ClinicalThresholds(bpHighSys = 10)))
        assertNull(svc.save(ClinicalThresholds(bpHighSys = 150)))
        assertEquals(150, ClinicalConfig.current.bpHighSys)
        assertNull(svc.reset())
        assertFalse(ClinicalConfig.isCustom)
    }
}
