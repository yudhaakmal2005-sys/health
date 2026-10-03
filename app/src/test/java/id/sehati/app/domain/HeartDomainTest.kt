package id.sehati.app.domain

import id.sehati.app.domain.content.*
import id.sehati.app.domain.model.*
import id.sehati.app.domain.rules.*
import java.time.LocalDate
import kotlin.test.*

class HeartRiskTest {
    @Test fun unassessedHasInsufficientData() {
        val r = HeartRisk.evaluate(HealthSnapshot(assessed = false))
        assertEquals(HeartBand.INSUFFICIENT, r.band)
    }
    @Test fun smokerWithHighBpCountsFactors() {
        val s = HealthSnapshot(age = 50, male = true, heightCm = 170f, weightKg = 62f, bpReadings = listOf(160 to 100), smokingStatus = "CURRENT", cigarettesPerDay = 10, cholesterol = 190f, glucose = 95f, activeDaysPerWeek = 5, activeMinutesPerSession = 40)
        val r = HeartRisk.evaluate(s)
        val ids = r.factors.filter { it.status == FactorStatus.PRESENT }.map { it.id }
        assertTrue("bp" in ids && "smoking" in ids)
        assertTrue(r.ageFactor)
        assertEquals(r.present, ids.size)
    }
    @Test fun healthyProfileIsFew() {
        val s = HealthSnapshot(age = 30, heightCm = 170f, weightKg = 60f, waistCm = 75f, bpReadings = listOf(115 to 75), glucose = 90f, cholesterol = 170f, activeDaysPerWeek = 5, activeMinutesPerSession = 40)
        assertEquals(HeartBand.FEW, HeartRisk.evaluate(s).band)
    }
    @Test fun pillarsReflectDay() {
        val p = HeartPillars.evaluate(9000, 8000, 0, 3, 1500f, 2000, 7f, 7f, 30)
        assertTrue(p.all { it.done })
        val q = HeartPillars.evaluate(100, 8000, 3, 0, 0f, 2000, null, 7f, null)
        assertTrue(q.none { it.done })
    }
}

class ThresholdConfigTest {
    @Test fun defaultsAreValid() = assertNull(ClinicalThresholds().validate())
    @Test fun customThresholdChangesInterpretation() {
        val old = ClinicalConfig.current
        try {
            ClinicalConfig.current = old.copy(bpHighSys = 150)
            assertTrue(ClinicalConfig.isCustom)
            assertFalse(BloodPressureRules.isElevated(145, 85))
        } finally { ClinicalConfig.current = old }
    }
}

class ChallengeTest {
    private val d = LocalDate.of(2026, 1, 1)
    @Test fun completesWhenTargetReachedInWindow() {
        val def = ChallengeCatalog.byId("salt")!!
        val days = (0 until 7).map { d.plusDays(it.toLong()) }.toSet()
        assertEquals(ChallengeStatus.COMPLETED, Challenges.evaluate(def, d, days, d.plusDays(7)).status)
    }
    @Test fun expiresAfterWindow() {
        val def = ChallengeCatalog.byId("salt")!!
        val p = Challenges.evaluate(def, d, setOf(d), d.plusDays(20))
        assertEquals(ChallengeStatus.EXPIRED, p.status)
    }
    @Test fun checkInsOutsideWindowIgnored() {
        val def = ChallengeCatalog.byId("salt")!!
        val p = Challenges.evaluate(def, d, setOf(d.minusDays(1), d.plusDays(30)), d.plusDays(2))
        assertEquals(0, p.done)
        assertEquals(ChallengeStatus.ACTIVE, p.status)
    }
    @Test fun encodeDecodeRoundTrip() {
        val s = setOf(d, d.plusDays(3))
        assertEquals(s, Challenges.decode(Challenges.encode(s)))
        assertTrue(Challenges.decode("x,,2026-01-02").size == 1)
    }
    @Test fun autoDaysFromSteps() {
        val def = ChallengeCatalog.byId("walk")!!
        val a = Challenges.autoDays(def, mapOf(d to 9000, d.plusDays(1) to 100), 8000, emptyMap(), 8)
        assertEquals(setOf(d), a)
    }
}

class ContentTest {
    @Test fun dailyFactIsStablePerDay() {
        val day = LocalDate.of(2026, 3, 5)
        assertEquals(DailyFacts.forDay(day), DailyFacts.forDay(day))
        assertTrue(DailyFacts.all.size >= 20)
    }
    @Test fun faqFindsEmergencyAndSalt() {
        assertEquals("tanda", HeartKnowledge.answer("apa tanda serangan jantung").entry?.id)
        assertEquals("garam", HeartKnowledge.answer("berapa batas garam").entry?.id)
        assertNull(HeartKnowledge.answer("zzzzz qqqq").entry)
    }
    @Test fun emergencyTextDetected() {
        assertTrue(HeartKnowledge.isEmergencyText("saya nyeri dada sekarang dan keringat dingin"))
        assertFalse(HeartKnowledge.isEmergencyText("bagaimana cara olahraga"))
    }
    @Test fun generalQuestionVsPersonalSymptom() {
        assertTrue(HeartKnowledge.isGeneralQuestion("Apa tanda bahaya serangan jantung?"))
        assertFalse(HeartKnowledge.isGeneralQuestion("saya nyeri dada sekarang"))
    }
    @Test fun allModuleLinksResolve() {
        HeartKnowledge.faq.mapNotNull { it.moduleId }.forEach { assertNotNull(Academy.byId(it), it) }
        ChallengeCatalog.all.mapNotNull { it.moduleId }.forEach { assertNotNull(Academy.byId(it), it) }
        HeartRisk.evaluate(HealthSnapshot()).factors.mapNotNull { it.moduleId }.forEach { assertNotNull(Academy.byId(it), it) }
    }
    @Test fun adminCanConfigureRulesButNotWarga() {
        assertTrue(RbacPolicy.can(Role.ADMIN, Permission.CONFIGURE_RULES))
        assertFalse(RbacPolicy.can(Role.KADER, Permission.CONFIGURE_RULES))
        assertFalse(RbacPolicy.can(Role.WARGA, Permission.CONFIGURE_RULES))
    }
}

class FoodMatcherTest {
    @Test fun mapsLabelsToLocalFoods() {
        val r = FoodLabelMatcher.suggest(listOf("Food" to 0.99f, "Rice" to 0.8f, "Egg" to 0.6f))
        assertEquals("nasi_putih", r.first().item.id)
        assertTrue(r.any { it.item.id == "telur_rebus" })
    }
    @Test fun genericOnlyGivesNothing() = assertTrue(FoodLabelMatcher.suggest(listOf("Food" to 0.9f, "Tableware" to 0.8f)).isEmpty())
}
