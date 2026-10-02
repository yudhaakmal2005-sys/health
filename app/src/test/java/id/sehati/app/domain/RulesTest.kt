package id.sehati.app.domain

import id.sehati.app.domain.model.FollowUpStatus
import id.sehati.app.domain.model.FollowUpType
import id.sehati.app.domain.model.MovementKind
import id.sehati.app.domain.model.RiskLevel
import id.sehati.app.domain.rules.*
import java.time.LocalDate
import kotlin.test.*

class AnthropometryTest {
    @Test fun bmiIsComputed() { assertEquals(26.4f, AnthropometryRules.bmi(165f, 72f)) }
    @Test fun bmiInvalidInputGivesZero() { assertEquals(0f, AnthropometryRules.bmi(0f, 70f)) }
    @Test fun asiaPacificCategories() {
        assertEquals("Normal", AnthropometryRules.classify(170f, 60f).category)
        assertEquals("Berat badan lebih", AnthropometryRules.classify(170f, 69f).category)
        assertEquals("Obesitas tingkat I", AnthropometryRules.classify(170f, 78f).category)
        assertEquals("Obesitas tingkat II", AnthropometryRules.classify(170f, 95f).category)
    }
    @Test fun waistThresholdsDependOnSex() {
        assertTrue(AnthropometryRules.centralObesity(90f, male = true))
        assertFalse(AnthropometryRules.centralObesity(85f, male = true))
        assertTrue(AnthropometryRules.centralObesity(85f, male = false))
    }
}

class BloodPressureTest {
    @Test fun normal() = assertEquals(Severity.INFO, BloodPressureRules.interpret(115, 75).severity)
    @Test fun elevatedNormal() = assertEquals("Normal-tinggi", BloodPressureRules.interpret(130, 85).category)
    @Test fun high() = assertEquals("Tinggi", BloodPressureRules.interpret(145, 92).category)
    @Test fun urgent() = assertTrue(BloodPressureRules.interpret(185, 100).needsUrgentCare)
    @Test fun singleReadingIsNeverADiagnosis() {
        val r = BloodPressureRules.interpret(145, 92)
        assertFalse(r.interpretation.contains("menderita", true))
        assertFalse(r.interpretation.contains("diagnosis hipertensi", true))
        assertTrue(r.interpretation.contains("dikonfirmasi"))
    }
    @Test fun thresholdsAreConfigurable() {
        val strict = ClinicalThresholds(bpHighSys = 130)
        assertTrue(BloodPressureRules.isElevated(132, 70, strict))
        assertFalse(BloodPressureRules.isElevated(132, 70))
    }
    @Test fun everyRuleDocumentsItsSourceAndLimits() {
        val r = BloodPressureRules.interpret(120, 80).rule
        assertTrue(r.source.isNotBlank() && r.limitations.isNotBlank() && r.action.isNotBlank())
        assertFalse(r.source.contains("standar WHO", true))
    }
}

class GlucoseTest {
    @Test fun gdsNormalVsElevatedVsHigh() {
        assertEquals(Severity.INFO, GlucoseRules.interpret(110f).severity)
        assertEquals(Severity.WATCH, GlucoseRules.interpret(168f).severity)
        assertEquals(Severity.ATTENTION, GlucoseRules.interpret(215f).severity)
    }
    @Test fun fastingUsesLowerCutoffs() { assertEquals(Severity.WATCH, GlucoseRules.interpret(110f, fasting = true).severity) }
    @Test fun lowGlucoseIsUrgent() { assertTrue(GlucoseRules.interpret(55f).needsUrgentCare) }
    @Test fun neverSaysDiabetes() { assertFalse(GlucoseRules.interpret(250f).interpretation.contains("menderita", true)) }
}

class RiskProfileTest {
    private val healthy = HealthSnapshot(age = 30, heightCm = 170f, weightKg = 62f, waistCm = 75f,
        activeDaysPerWeek = 5, activeMinutesPerSession = 40, sedentaryHoursPerDay = 4)

    @Test fun healthyHabit() = assertEquals(RiskLevel.HEALTHY_HABIT, RiskProfileEngine.evaluate(healthy).level)

    @Test fun lifestyleOnlyIsRiskAwareness() {
        val p = RiskProfileEngine.evaluate(healthy.copy(smokingStatus = "CURRENT", cigarettesPerDay = 4))
        assertEquals(RiskLevel.RISK_AWARENESS, p.level)
    }
    @Test fun singleElevatedBpNeedsHigherMonitoringNotFollowUp() {
        val p = RiskProfileEngine.evaluate(healthy.copy(bpReadings = listOf(145 to 92)))
        assertEquals(RiskLevel.HIGHER_MONITORING, p.level)
    }
    @Test fun repeatedElevatedBpNeedsMedicalFollowUp() {
        val p = RiskProfileEngine.evaluate(healthy.copy(bpReadings = listOf(145 to 92, 150 to 95)))
        assertEquals(RiskLevel.MEDICAL_FOLLOW_UP, p.level)
        assertTrue(p.findings.any { it.id == "bp_repeated" })
    }
    @Test fun redFlagSymptomIsMedicalFollowUp() {
        assertEquals(RiskLevel.MEDICAL_FOLLOW_UP, RiskProfileEngine.evaluate(healthy.copy(redFlagSymptom = true)).level)
    }
    @Test fun profileLevelsAreOrdered() { assertTrue(RiskLevel.MEDICAL_FOLLOW_UP.rank > RiskLevel.HEALTHY_HABIT.rank) }
    @Test fun alwaysHasPlan() { assertTrue(RiskProfileEngine.evaluate(healthy).plan.isNotEmpty()) }
}

class FollowUpRulesTest {
    @Test fun normalNeedsNothing() { assertTrue(FollowUpRules.advise(118, 76, 100f, false, 180f).isEmpty()) }
    @Test fun firstElevatedBpIsRecheck() {
        val a = FollowUpRules.advise(145, 92, null, false, null).first()
        assertEquals(FollowUpType.REPEAT_MEASUREMENT, a.type)
    }
    @Test fun repeatedElevatedBpEscalatesToHomeVisit() {
        val a = FollowUpRules.advise(148, 94, null, false, null, previousBp = listOf(150 to 95)).first()
        assertEquals(FollowUpType.HOME_VISIT, a.type); assertEquals(3, a.priority)
    }
    @Test fun urgentBpGoesToPuskesmas() {
        assertEquals(FollowUpType.PUSKESMAS_EVALUATION, FollowUpRules.advise(190, 115, null, false, null).first().type)
    }
    @Test fun glucoseTiers() {
        assertEquals(FollowUpType.REPEAT_MEASUREMENT, FollowUpRules.advise(null, null, 168f, false, null).first().type)
        assertEquals(FollowUpType.PUSKESMAS_EVALUATION, FollowUpRules.advise(null, null, 230f, false, null).first().type)
    }
    @Test fun adviceSortedByPriority() {
        val a = FollowUpRules.advise(145, 92, 230f, false, 250f)
        assertEquals(a.sortedByDescending { it.priority }, a)
    }
}

class ValidationTest {
    @Test fun acceptsGoldenPathInput() {
        val i = MeasurementValidator.validate(MeasurementInput(145, 92, 80, 72f, 165f, 90f, 168f, null))
        assertFalse(MeasurementValidator.hasBlocking(i))
    }
    @Test fun rejectsSystolicBelowDiastolic() { assertTrue(MeasurementValidator.hasBlocking(MeasurementValidator.validate(MeasurementInput(80, 90)))) }
    @Test fun rejectsHalfBp() { assertTrue(MeasurementValidator.hasBlocking(MeasurementValidator.validate(MeasurementInput(systolic = 120)))) }
    @Test fun rejectsEmpty() { assertTrue(MeasurementValidator.hasBlocking(MeasurementValidator.validate(MeasurementInput()))) }
    @Test fun rejectsImpossibleValues() {
        assertTrue(MeasurementValidator.hasBlocking(MeasurementValidator.validate(MeasurementInput(weightKg = 900f))))
        assertTrue(MeasurementValidator.hasBlocking(MeasurementValidator.validate(MeasurementInput(glucose = 5f))))
    }
}

class IdentityTest {
    @Test fun idFormat() { assertEquals("HM-000127", SehatiId.format(127)); assertTrue(SehatiId.isValid("HM-000127")) }
    @Test fun normalizeLooseInput() { assertEquals("HM-000127", SehatiId.normalize(" hm-127 ")); assertEquals("HM-000127", SehatiId.normalize("127")) }
    @Test fun qrRoundTrip() {
        val token = QrPayload.newToken()
        val parsed = QrPayload.parse(QrPayload.build("HM-000127", token))
        assertEquals("HM-000127", parsed?.sehatiId); assertEquals(token, parsed?.token)
    }
    @Test fun qrRejectsGarbage() {
        assertNull(QrPayload.parse("https://evil.example/x")); assertNull(QrPayload.parse("sehati://citizen/HM-1/short"))
    }
    @Test fun qrNeverContainsSensitiveFields() {
        val q = QrPayload.build("HM-000127", QrPayload.newToken())
        assertFalse(q.contains("145")); assertTrue(q.startsWith("sehati://citizen/HM-000127/"))
    }
    @Test fun passwordHashing() {
        val h = PasswordHasher.hash("rahasia123".toCharArray(), iterations = 1000)
        assertTrue(PasswordHasher.verify("rahasia123".toCharArray(), h)); assertFalse(PasswordHasher.verify("salah".toCharArray(), h))
    }
    @Test fun ageFromBirthDate() { assertEquals(33, AgeCalc.age("1992-08-14", LocalDate.of(2026, 10, 2))) }
}

class DailyAndNutritionTest {
    @Test fun vehicleIsNotActiveMovement() {
        val t0 = 0L; val m = 60_000L
        val s = ActivityAggregator.summarize(listOf(
            MovementSegment(MovementKind.WALKING, t0, t0 + 20 * m, 1500f),
            MovementSegment(MovementKind.VEHICLE, t0, t0 + 30 * m, 9000f),
        ))
        assertEquals(20, s.activeMinutes); assertEquals(30, s.passiveMinutes)
        assertEquals(1.5f, s.activeDistanceKm, 0.001f)
    }
    @Test fun stepProgressClamped() { assertEquals(1f, ActivityAggregator.stepProgress(9000, 6000)); assertEquals(0f, ActivityAggregator.stepProgress(10, 0)) }
    @Test fun nutritionTotals() {
        val t = NutritionRules.total(listOf(NutritionTotals(kcal = 100, sodiumMg = 50f), NutritionTotals(kcal = 250, sodiumMg = 30f)))
        assertEquals(350, t.kcal); assertEquals(80f, t.sodiumMg)
    }
    @Test fun scalingPortion() {
        val item = FoodItem("x", "Nasi", "Karbo", "1", 130, 28f, 2.7f, 0.3f, 0f, 0.4f, 1f, false)
        assertEquals(195, NutritionRules.scale(item, 1.5f).kcal)
    }
    @Test fun energyEstimate() { assertTrue(NutritionRules.estimateDailyKcal(72f, 165f, 33, true, 2) in 2000..2400) }
    @Test fun foodQualityPenalisesSalt() {
        val good = NutritionRules.foodQuality(NutritionTotals(sodiumMg = 800f), 4, 3)
        val salty = NutritionRules.foodQuality(NutritionTotals(sodiumMg = 3000f), 4, 3)
        assertTrue(good > salty)
    }
    @Test fun streak() {
        val today = LocalDate.of(2026, 10, 2)
        assertEquals(3, Streaks.current(setOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(5)), today))
        assertEquals(0, Streaks.current(setOf(today.minusDays(3)), today))
    }
}

class CoachTest {
    private val ctx = CoachContext("Tariska", 2800, 6000, 5, 8, 7.5f, 0)
    @Test fun redFlagBypassesCoach() {
        val r = HealthCoach.reply("saya nyeri dada dan sesak", ctx)
        assertTrue(r.emergency); assertTrue(r.text.contains("pertolongan medis"))
    }
    @Test fun refusesMedicationAdvice() { assertTrue(HealthCoach.reply("berapa dosis obat tensi", ctx).text.contains("tidak dapat memberi saran obat")) }
    @Test fun tipMentionsSteps() { assertTrue(HealthCoach.dailyTip(ctx).contains("2800")) }
    @Test fun nonEmergencyIncludesDisclaimer() { assertTrue(HealthCoach.reply("tips garam", ctx).text.contains(HealthCoach.DISCLAIMER)) }
}

class CommunityAnalyticsTest {
    private fun citizens(rw: String, n: Int, start: Int) = (0 until n).map { CitizenRec("HM-${(start + it).toString().padStart(6, '0')}", rw, true) }

    @Test fun smallCellsAreInsufficient() {
        val c = citizens("01", 3, 1)
        val checks = c.map { CheckRec(it.id, 1L, 150, 95, null, null) }
        val s = CommunityAnalytics.calculate(c, checks, emptyList(), emptyList(), emptyList())
        assertEquals(MapState.INSUFFICIENT, s.map.single().state)
    }
    @Test fun coverageAndElevatedRatiosUseDenominators() {
        val c = citizens("01", 10, 1)
        val checks = c.take(6).mapIndexed { i, x -> CheckRec(x.id, 1L, if (i < 3) 150 else 115, if (i < 3) 95 else 75, null, null) }
        val s = CommunityAnalytics.calculate(c, checks, emptyList(), emptyList(), emptyList())
        assertEquals(Ratio(6, 10), s.screeningCoverage)
        assertEquals(Ratio(3, 6), s.elevatedBp)
    }
    @Test fun latestCheckWinsPerCitizen() {
        val c = citizens("01", 1, 1)
        val checks = listOf(CheckRec(c[0].id, 1L, 160, 100, null, null), CheckRec(c[0].id, 2L, 115, 75, null, null))
        assertEquals(0, CommunityAnalytics.calculate(c, checks, emptyList(), emptyList(), emptyList()).elevatedBp.numerator)
    }
    @Test fun higherNeedCellIsFlagged() {
        val c = citizens("03", 8, 1)
        val checks = c.map { CheckRec(it.id, 1L, 120, 80, null, null) }
        val profiles = c.take(4).map { ProfileRec(it.id, RiskLevel.HIGHER_MONITORING) }
        assertEquals(MapState.HIGHER, CommunityAnalytics.calculate(c, checks, emptyList(), emptyList(), profiles).map.single().state)
    }
    @Test fun followUpCoverage() {
        val fus = listOf(
            FollowUpRec("a", "u1", FollowUpStatus.OPEN, 2, "x", 0), FollowUpRec("b", "u2", FollowUpStatus.DONE, 2, "x", 0),
            FollowUpRec("c", "u3", FollowUpStatus.CANCELLED, 1, "x", 0),
        )
        val s = CommunityAnalytics.calculate(emptyList(), emptyList(), emptyList(), fus, emptyList())
        assertEquals(Ratio(1, 2), s.followUpCoverage); assertEquals(1, s.openFollowUps)
    }
    @Test fun mapNeverCarriesIndividualFields() {
        val fields = MapCell::class.java.declaredFields.map { it.name }
        assertTrue(fields.none { it in setOf("name", "nik", "phone", "address", "userId") })
    }
}

class MovementClassifierTest {
    @Test fun fastWalkIsReclassifiedAsVehicle() { assertEquals(MovementKind.VEHICLE, MovementClassifier.classify(MovementKind.WALKING, 32f)) }
    @Test fun normalWalkStaysWalking() { assertEquals(MovementKind.WALKING, MovementClassifier.classify(MovementKind.WALKING, 5f)) }
    @Test fun joggingBecomesRunning() { assertEquals(MovementKind.RUNNING, MovementClassifier.classify(MovementKind.WALKING, 10f)) }
    @Test fun cyclingAtCyclingSpeedIsKept() { assertEquals(MovementKind.CYCLING, MovementClassifier.classify(MovementKind.CYCLING, 22f)) }
    @Test fun pace() { assertEquals("6'00\"", MovementClassifier.paceMinPerKm(10f)) }
}

class RbacTest {
    @Test fun adminCannotReadIndividualHealthDetail() {
        assertFalse(id.sehati.app.domain.model.RbacPolicy.can(id.sehati.app.domain.model.Role.ADMIN, id.sehati.app.domain.model.Permission.CITIZEN_HEALTH_DETAIL))
        assertFalse(id.sehati.app.domain.model.RbacPolicy.can(id.sehati.app.domain.model.Role.ADMIN, id.sehati.app.domain.model.Permission.CITIZEN_LOOKUP))
    }
    @Test fun citizenCannotRecordVisits() {
        assertFalse(id.sehati.app.domain.model.RbacPolicy.can(id.sehati.app.domain.model.Role.WARGA, id.sehati.app.domain.model.Permission.VISIT_RECORD))
    }
    @Test fun cadreCanRecordVisitsButNotAssignOrManage() {
        val k = id.sehati.app.domain.model.Role.KADER
        assertTrue(id.sehati.app.domain.model.RbacPolicy.can(k, id.sehati.app.domain.model.Permission.VISIT_RECORD))
        assertFalse(id.sehati.app.domain.model.RbacPolicy.can(k, id.sehati.app.domain.model.Permission.MANAGE_CADRES))
        assertFalse(id.sehati.app.domain.model.RbacPolicy.can(k, id.sehati.app.domain.model.Permission.ANALYTICS_AGGREGATE))
    }
    @Test fun requireThrows() { assertFailsWith<SecurityException> { id.sehati.app.domain.model.RbacPolicy.require(id.sehati.app.domain.model.Role.WARGA, id.sehati.app.domain.model.Permission.VIEW_AUDIT) } }
}

class GamificationTest {
    @Test fun threeDayStreakBadge() {
        val today = LocalDate.of(2026, 10, 2)
        val steps = (0..2).associate { today.minusDays(it.toLong()) to 6500 }
        val p = Gamification.compute(steps, 6000, 0, 0, 0, today)
        assertEquals(3, p.streakDays); assertTrue(p.badges.first { it.id == "streak3" }.earned); assertFalse(p.badges.first { it.id == "streak7" }.earned)
    }
    @Test fun learningPoints() { assertEquals(2 * 3 + 10 * 1, Gamification.compute(emptyMap(), 6000, 3, 1, 0).learningPoints) }
}

class ReportTest {
    private val stats = CommunityAnalytics.calculate(
        (1..6).map { CitizenRec("HM-00010$it", "01", true) },
        (1..6).map { CheckRec("HM-00010$it", 1L, 150, 95, 170f, 27f) }, emptyList(), emptyList(), emptyList(),
    )
    @Test fun reportUsesScreeningTerminologyNotPrevalence() {
        val t = ReportBuilder.text(stats, "2 Okt 2026", "Desa Uji")
        assertTrue(t.contains("bukan prevalensi populasi")); assertFalse(t.contains("prevalensi:", true))
    }
    @Test fun reportHasNoIndividualIdentifiers() {
        val t = ReportBuilder.text(stats, "2 Okt 2026", "Desa Uji")
        assertFalse(t.contains("HM-0001")); assertFalse(ReportBuilder.csv(stats).contains("HM-0001"))
    }
    @Test fun csvHasHeaderAndRow() { assertEquals(2, ReportBuilder.csv(stats).trim().lines().size) }
}
