package id.sehati.app.domain

import id.sehati.app.data.local.MedicationEntity
import id.sehati.app.data.prefs.ReminderPrefs
import id.sehati.app.data.reminders.ReminderKind
import id.sehati.app.data.reminders.ReminderPlanner
import id.sehati.app.data.remote.PosyanduSlot
import id.sehati.app.data.repository.MedicationRepository
import id.sehati.app.ui.citizen.markdownLite
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.*

class ReminderPlannerTest {
    private val zone = ZoneId.of("Asia/Jakarta")
    private val now = ZonedDateTime.of(2026, 10, 5, 10, 30, 0, 0, zone) // Senin 10.30
    private fun med(times: String, active: Boolean = true) = MedicationEntity("m1", "HM-1", "Amlodipin", "1 tablet pagi", times, active, 0, 0)

    @Test fun dailyTimesRollToTomorrowWhenPassed() {
        val plan = ReminderPlanner.plan(now, ReminderPrefs(), emptyList(), emptyList(), "02")
        val water = plan.filter { it.kind == ReminderKind.WATER }.associate { it.key to it.at }
        val at08 = ZonedDateTime.of(2026, 10, 6, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val at11 = ZonedDateTime.of(2026, 10, 5, 11, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(at08, water["water_0"])
        assertEquals(at11, water["water_1"])
    }

    @Test fun disabledKindsAreNotPlanned() {
        val plan = ReminderPlanner.plan(now, ReminderPrefs(water = false, walk = false, bpCheck = false, fact = false, challenge = false, medication = false, posyandu = false), listOf(med("08:00")), emptyList(), "02")
        assertTrue(plan.isEmpty())
    }

    @Test fun weeklyBpOnChosenDay() {
        val p = ReminderPlanner.plan(now, ReminderPrefs(bpDay = 7, bpTime = "08:00"), emptyList(), emptyList(), null).single { it.kind == ReminderKind.BP_CHECK }
        assertEquals(ZonedDateTime.of(2026, 10, 11, 8, 0, 0, 0, zone).toInstant().toEpochMilli(), p.at)
    }

    @Test fun medicationPerTimeOnlyWhenActive() {
        val plan = ReminderPlanner.plan(now, ReminderPrefs(), listOf(med("07:00,19:00")), emptyList(), null).filter { it.kind == ReminderKind.MEDICATION }
        assertEquals(setOf("07:00", "19:00"), plan.map { it.doseTime }.toSet())
        assertTrue(ReminderPlanner.plan(now, ReminderPrefs(), listOf(med("07:00", active = false)), emptyList(), null).none { it.kind == ReminderKind.MEDICATION })
    }

    @Test fun posyanduOnlyForOwnRwAndFuture() {
        val slots = listOf(PosyanduSlot("a", "02", "2026-10-07", "08:00", "11:00", "Balai RW 02"), PosyanduSlot("b", "03", "2026-10-07"), PosyanduSlot("c", "02", "2026-10-01"))
        val plan = ReminderPlanner.plan(now, ReminderPrefs(), emptyList(), slots, "02").filter { it.kind == ReminderKind.POSYANDU }
        assertEquals(setOf("posy_a_eve", "posy_a_day"), plan.map { it.key }.toSet())
        assertTrue(plan.first { it.key == "posy_a_eve" }.body.contains("Balai RW 02"))
    }
}

class MedicationLogicTest {
    @Test fun normalizeTime() {
        assertEquals("07:05", MedicationRepository.normalizeTime("7.05"))
        assertNull(MedicationRepository.normalizeTime("25:00"))
        assertNull(MedicationRepository.normalizeTime("abc"))
    }

    @Test fun dosesAndAdherence() {
        val m = MedicationEntity("m1", "HM-1", "Obat", "", "07:00,19:00", true, 0, 0)
        val today = LocalDate.of(2026, 10, 5)
        val logs = listOf(id.sehati.app.data.local.MedicationLogEntity("m1|2026-10-05|07:00", "HM-1", "m1", "2026-10-05", "07:00", 1, 1))
        val d = MedicationRepository.doses(listOf(m), logs, today)
        assertEquals(listOf(true, false), d.map { it.taken })
        assertEquals(100, MedicationRepository.adherence(listOf(m), logs, today, "12:00", days = 1))
        assertEquals(50, MedicationRepository.adherence(listOf(m), logs, today, "20:00", days = 1))
    }
}

class MarkdownLiteTest {
    @Test fun boldAndBullets() {
        val a = markdownLite("**Penting**\n- satu\n2. dua")
        assertEquals("Penting\n•  satu\n2.  dua", a.text)
        assertTrue(a.spanStyles.isNotEmpty())
    }
}
