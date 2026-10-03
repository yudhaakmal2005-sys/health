package id.sehati.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import id.sehati.app.data.repository.AccessDenied
import id.sehati.app.data.repository.AnalyticsMapper
import id.sehati.app.data.repository.NewMeasurement
import id.sehati.app.data.sync.*
import id.sehati.app.domain.model.*
import id.sehati.app.domain.rules.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.*

/** Bukti alur data end-to-end: Kader → Room → Riwayat warga → Profil → Follow-up → Dashboard admin. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class GoldenPathTest {
    private lateinit var env: TestEnv
    private val loopback = LoopbackSyncTransport()

    @Before fun setUp() { env = TestEnv(loopback) }
    @After fun tearDown() { env.close() }

    private fun input() = MeasurementInput(systolic = 145, diastolic = 92, heartRate = 78, weightKg = 72f, heightCm = 165f, waistCm = 90f, glucose = 168f)

    @Test fun kaderMeasurementReachesCitizenHistoryAndAdminDashboard() = blocking {
        env.staff("KD-000001", Role.KADER); env.staff("AD-000001", Role.ADMIN)
        val citizen = env.citizen()
        env.session.start("KD-000001", Role.KADER)

        val visit = env.posyandu.registerVisit(citizen.sehatiId, "fac-melati")
        val saved = env.posyandu.recordMeasurement(visit.id, input(), false, QualityCheck(true, true, false))
        env.posyandu.recordEducation(visit.id, listOf("hipertensi"))
        val result = env.posyandu.completeVisit(visit.id)

        // 1) Riwayat kesehatan warga memakai userId yang sama
        val history = env.health.checks(citizen.sehatiId)
        assertEquals(1, history.size)
        val c = history.single()
        assertEquals(saved.id, c.id)
        assertEquals(145 to 92, c.bloodPressure)
        assertEquals(168f, c.glucose)
        assertEquals(26.4f, c.bmi)
        // provenance
        assertEquals(DataSource.POSYANDU, c.source); assertEquals("KD-000001", c.operatorId); assertEquals(VerificationStatus.VERIFIED, c.verification)

        // 2) Profil diturunkan dari data yang sama, bukan diagnosis
        val profile = env.db.healthDao().profile(citizen.sehatiId)!!
        assertTrue(RiskLevel.parse(profile.level).rank >= RiskLevel.HIGHER_MONITORING.rank)
        assertFalse(profile.findingsJson.contains("menderita", true))

        // 3) Tindak lanjut tercipta dari hasil
        val fus = env.posyandu.observeFollowUpsOf(citizen.sehatiId).first()
        assertTrue(fus.any { it.reasonCode == "bp_elevated" && it.type == FollowUpType.REPEAT_MEASUREMENT.name })
        assertTrue(fus.any { it.reasonCode == "glucose_watch" })
        assertEquals(fus.size, result.followUps.size)

        // 4) Dashboard admin membaca sumber yang sama
        val users = env.citizens.observeCitizens().first()
        val stats = AnalyticsMapper.stats(users, env.health.observeAllChecks().first(), env.health.observeAllAssessments().first(),
            env.posyandu.observeFollowUps().first(), env.health.observeAllProfiles().first())
        assertEquals(1, stats.totalRegistered)
        assertEquals(Ratio(1, 1), stats.screeningCoverage)
        assertEquals(Ratio(1, 1), stats.elevatedBp)
        assertTrue(stats.openFollowUps >= 2)

        // 5) Audit mencatat aksi kader
        val audit = env.db.systemDao().observeAudit(50).first()
        assertTrue(audit.any { it.action == "visit_register" && it.subjectId == citizen.sehatiId })
        assertTrue(audit.none { it.detail.contains("145") }) // nilai kesehatan tidak masuk log
    }

    @Test fun repeatedElevatedBpEscalatesToHomeVisitFollowUp() = blocking {
        env.staff("KD-000001", Role.KADER)
        val citizen = env.citizen()
        env.session.start("KD-000001", Role.KADER)
        repeat(2) { n ->
            env.clock.t += n * 86_400_000L
            env.session.start("KD-000001", Role.KADER)   // sesi kader berlaku 12 jam; masuk kembali di hari berikutnya
            val v = env.posyandu.registerVisit(citizen.sehatiId, null)
            env.posyandu.recordMeasurement(v.id, MeasurementInput(150, 95), false, null)
            env.posyandu.completeVisit(v.id)
        }
        val fu = env.posyandu.observeFollowUpsOf(citizen.sehatiId).first()
        assertTrue(fu.any { it.reasonCode == "bp_repeated" && it.type == FollowUpType.HOME_VISIT.name })
        assertEquals(RiskLevel.MEDICAL_FOLLOW_UP.name, env.db.healthDao().profile(citizen.sehatiId)!!.level)
    }

    @Test fun duplicateFollowUpIsNotCreatedForSameReason() = blocking {
        env.staff("KD-000001", Role.KADER)
        val citizen = env.citizen()
        env.session.start("KD-000001", Role.KADER)
        repeat(2) {
            val v = env.posyandu.registerVisit(citizen.sehatiId, null)
            env.posyandu.recordMeasurement(v.id, MeasurementInput(145, 92), false, null)
            env.posyandu.completeVisit(v.id)
        }
        assertEquals(1, env.posyandu.observeFollowUpsOf(citizen.sehatiId).first().count { it.reasonCode == "bp_elevated" })
    }

    @Test fun rbacBlocksWrongRoles() = blocking {
        env.staff("KD-000001", Role.KADER); env.staff("AD-000001", Role.ADMIN)
        val citizen = env.citizen()
        env.session.start("AD-000001", Role.ADMIN)
        assertFailsWith<AccessDenied> { env.posyandu.registerVisit(citizen.sehatiId, null) }
        env.session.start(citizen.sehatiId, Role.WARGA)
        assertFailsWith<AccessDenied> { env.posyandu.registerVisit(citizen.sehatiId, null) }
        env.session.end()
        assertFailsWith<AccessDenied> { env.posyandu.registerVisit(citizen.sehatiId, null) }
        env.session.start("KD-000001", Role.KADER)
        assertFailsWith<AccessDenied> { env.posyandu.assignCadre("x", "KD-000001") } // kader tidak boleh menugaskan
    }

    @Test fun adminCanAssignCadreToFollowUp() = blocking {
        env.staff("KD-000001", Role.KADER); env.staff("AD-000001", Role.ADMIN)
        val citizen = env.citizen()
        env.session.start("KD-000001", Role.KADER)
        val v = env.posyandu.registerVisit(citizen.sehatiId, null)
        env.posyandu.recordMeasurement(v.id, MeasurementInput(145, 92), false, null); env.posyandu.completeVisit(v.id)
        val fu = env.posyandu.observeFollowUpsOf(citizen.sehatiId).first().first()
        env.session.start("AD-000001", Role.ADMIN)
        env.posyandu.assignCadre(fu.id, "KD-000001")
        val updated = env.posyandu.observeFollowUpsOf(citizen.sehatiId).first().first()
        assertEquals("KD-000001", updated.assignedCadreId); assertEquals(FollowUpStatus.SCHEDULED.name, updated.status)
    }

    @Test fun completeVisitRequiresMeasurement() = blocking {
        env.staff("KD-000001", Role.KADER)
        val citizen = env.citizen()
        env.session.start("KD-000001", Role.KADER)
        val v = env.posyandu.registerVisit(citizen.sehatiId, null)
        assertFailsWith<IllegalStateException> { env.posyandu.completeVisit(v.id) }
    }

    @Test fun invalidMeasurementIsRejectedAndNothingIsSaved() = blocking {
        env.staff("KD-000001", Role.KADER)
        val citizen = env.citizen()
        env.session.start("KD-000001", Role.KADER)
        val v = env.posyandu.registerVisit(citizen.sehatiId, null)
        assertFailsWith<IllegalArgumentException> { env.posyandu.recordMeasurement(v.id, MeasurementInput(80, 120), false, null) }
        assertTrue(env.health.checks(citizen.sehatiId).isEmpty())
    }

    @Test fun registeringSameCitizenTwiceReturnsSameOpenVisit() = blocking {
        env.staff("KD-000001", Role.KADER)
        val citizen = env.citizen()
        env.session.start("KD-000001", Role.KADER)
        assertEquals(env.posyandu.registerVisit(citizen.sehatiId, null).id, env.posyandu.registerVisit(citizen.sehatiId, null).id)
    }

    @Test fun healthConnectImportWithFixedIdDoesNotDuplicate() = blocking {
        val citizen = env.citizen()
        val m = NewMeasurement(citizen.sehatiId, DataSource.HEALTH_CONNECT, MeasurementInput(systolic = 120, diastolic = 80), fixedId = "hc-bp-1")
        env.health.saveMeasurement(m); env.health.saveMeasurement(m)
        assertEquals(1, env.health.checks(citizen.sehatiId).size)
        assertEquals(DataSource.HEALTH_CONNECT, env.health.checks(citizen.sehatiId).single().source)
    }

    @Test fun deletingMyDataRemovesEverythingAndQueuesServerDelete() = blocking {
        val citizen = env.citizen()
        env.health.saveMeasurement(NewMeasurement(citizen.sehatiId, DataSource.SELF, MeasurementInput(systolic = 120, diastolic = 80)))
        env.daily.addWater(citizen.sehatiId, "2026-10-02", 3)
        env.citizens.deleteAllDataOf(citizen.sehatiId)
        assertNull(env.citizens.get(citizen.sehatiId))
        assertTrue(env.health.checks(citizen.sehatiId).isEmpty())
        assertNull(env.db.dailyDao().habit("${citizen.sehatiId}|2026-10-02"))
        val queue = env.db.systemDao().pending(100)
        assertTrue(queue.all { it.operation == "DELETE" })
        assertTrue(queue.none { it.payload.contains("Tariska") })
    }

    @Test fun assessmentMarksUserDoneAndBuildsProfile() = blocking {
        val citizen = env.citizen()
        val now = env.clock.now()
        env.health.saveAssessment(id.sehati.app.data.local.HealthAssessmentEntity(
            id = "a1", userId = citizen.sehatiId, takenAt = now, heightCm = 160f, weightKg = 55f, waistCm = 70f, knownHypertension = false,
            knownDiabetes = false, knownDyslipidemia = false, knownHeartDisease = false, knownKidneyDisease = false, otherConditions = "",
            familyHypertension = false, familyDiabetes = false, familyCardio = false, smokingStatus = "CURRENT", smokingProduct = "Rokok", cigarettesPerDay = 6,
            vegetableDays = 6, fruitDays = 6, saltyFrequent = false, sugaryFrequent = false, fattyFrequent = false, activeDays = 5, activeMinutes = 40,
            activityIntensity = "Sedang", sedentaryHours = 4, sleepHours = 7f, sleepQuality = "Baik", stressLevel = 2, createdAt = now, updatedAt = now,
        ))
        assertTrue(env.citizens.get(citizen.sehatiId)!!.assessmentDone)
        val p = env.db.healthDao().profile(citizen.sehatiId)!!
        assertEquals(RiskLevel.RISK_AWARENESS.name, p.level) // perokok saja → kewaspadaan, bukan diagnosis
        assertTrue(env.health.decodeFindings(p).any { it.id == "smoking" })
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CadreProvisioningTest {
    private lateinit var env: TestEnv
    @Before fun setUp() { env = TestEnv() }
    @After fun tearDown() { env.close() }

    @Test fun adminAddsCadreWhoCanThenLogInButCannotAdminister() = blocking {
        env.staff("AD-000001", Role.ADMIN)
        env.session.start("AD-000001", Role.ADMIN)
        val k = env.posyandu.addCadre("Dewi Lestari", "03", "rahasia1")
        assertEquals("KD-000001", k.sehatiId); assertEquals(Role.KADER.name, k.role)
        assertTrue(env.posyandu.observeCadres().first().any { it.sehatiId == k.sehatiId && it.active })
        assertEquals("KD-000002", env.posyandu.addCadre("Ratna", "04", "rahasia2").sehatiId)

        env.session.end()
        val login = env.auth.login("KD-000001", "rahasia1")
        assertIs<id.sehati.app.data.repository.AuthResult.Success>(login)
        assertEquals(Role.KADER, login.session.role)
        assertFailsWith<AccessDenied> { env.posyandu.addCadre("X", "01", "rahasia1") } // kader tidak boleh menambah kader
    }

    @Test fun weakPasswordOrBlankNameRejected() = blocking {
        env.staff("AD-000001", Role.ADMIN); env.session.start("AD-000001", Role.ADMIN)
        assertFailsWith<IllegalArgumentException> { env.posyandu.addCadre("A", "01", "123") }
        assertFailsWith<IllegalArgumentException> { env.posyandu.addCadre(" ", "01", "rahasia1") }
    }
}
