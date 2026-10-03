package id.sehati.app.data.reminders

import id.sehati.app.data.local.MedicationEntity
import id.sehati.app.data.prefs.ReminderPrefs
import id.sehati.app.data.remote.PosyanduSlot
import id.sehati.app.data.repository.MedicationRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

enum class ReminderKind(val channel: String, val route: String) {
    WATER(Notifier.CH_DAILY, "home"), WALK(Notifier.CH_DAILY, "move"), BP_CHECK(Notifier.CH_DAILY, "health"),
    SLEEP(Notifier.CH_DAILY, "home"), FACT(Notifier.CH_DAILY, "home"), CHALLENGE(Notifier.CH_DAILY, "challenges"),
    MEDICATION(Notifier.CH_MEDS, "medications"), POSYANDU(Notifier.CH_POSYANDU, "health"),
}

data class PlannedReminder(
    val key: String,
    val kind: ReminderKind,
    val at: Long,
    val title: String,
    val body: String,
    val medicationId: String? = null,
    val doseTime: String? = null,
)

/** Menghitung waktu pengingat berikutnya (fungsi murni, mudah diuji). */
object ReminderPlanner {
    fun plan(
        now: ZonedDateTime, prefs: ReminderPrefs, meds: List<MedicationEntity>, posyandu: List<PosyanduSlot>, rw: String?,
    ): List<PlannedReminder> {
        val out = mutableListOf<PlannedReminder>()
        val dayIdx = now.toLocalDate().toEpochDay()
        fun pick(list: List<String>, salt: Int = 0) = list[Math.floorMod(dayIdx + salt, list.size.toLong()).toInt()]

        if (prefs.water) prefs.waterTimes.mapNotNull(::time).forEachIndexed { i, t ->
            out += PlannedReminder("water_$i", ReminderKind.WATER, nextDaily(now, t), "Waktunya minum air 💧", pick(WATER_LINES, i))
        }
        if (prefs.walk) time(prefs.walkTime)?.let {
            out += PlannedReminder("walk", ReminderKind.WALK, nextDaily(now, it), "Yuk jalan sehat 🚶", pick(WALK_LINES))
        }
        if (prefs.bpCheck) time(prefs.bpTime)?.let {
            out += PlannedReminder("bp", ReminderKind.BP_CHECK, nextWeekly(now, DayOfWeek.of(prefs.bpDay.coerceIn(1, 7)), it),
                "Cek tekanan darah minggu ini", "Ukur tensimu atau datang ke Posyandu. Tekanan darah tinggi sering tanpa gejala.")
        }
        if (prefs.sleep) time(prefs.sleepTime)?.let {
            out += PlannedReminder("sleep", ReminderKind.SLEEP, nextDaily(now, it), "Bersiap tidur 🌙", "Matikan layar 30 menit sebelum tidur. Tidur 7–8 jam membantu jantung pulih.")
        }
        if (prefs.fact) time(prefs.factTime)?.let {
            out += PlannedReminder("fact", ReminderKind.FACT, nextDaily(now, it), "Fakta atau mitos hari ini?", "Jawab satu pertanyaan jantung sehat dan kumpulkan poin.")
        }
        if (prefs.challenge) time(prefs.challengeTime)?.let {
            out += PlannedReminder("challenge", ReminderKind.CHALLENGE, nextDaily(now, it), "Tantanganmu hari ini", "Sudah berhasil menjalankan kebiasaan sehatmu? Catat sebelum tidur.")
        }
        if (prefs.medication) meds.filter { it.active }.forEach { m ->
            MedicationRepository.times(m).mapNotNull { t -> time(t)?.let { t to it } }.forEach { (raw, t) ->
                val body = if (m.instructions.isBlank()) "Minum sesuai resep dokter." else m.instructions
                out += PlannedReminder("med_${m.id}_$raw", ReminderKind.MEDICATION, nextDaily(now, t), "Waktunya minum ${m.name} 💊", body, m.id, raw)
            }
        }
        if (prefs.posyandu) posyandu.filter { rw == null || it.rw == rw }.forEach { s ->
            val date = runCatching { LocalDate.parse(s.date) }.getOrNull() ?: return@forEach
            val where = listOf(s.location, s.startTime.takeIf { it.isNotBlank() }?.let { "pukul $it" }).filterNotNull().filter { it.isNotBlank() }.joinToString(", ")
            val eve = date.minusDays(1).atTime(18, 0).atZone(now.zone)
            val day = date.atTime(time(s.startTime)?.minusHours(1) ?: LocalTime.of(7, 0)).atZone(now.zone)
            if (eve.isAfter(now)) out += PlannedReminder("posy_${s.id}_eve", ReminderKind.POSYANDU, eve.toInstant().toEpochMilli(),
                "Besok Posyandu 🩺", "Cek tensi, gula darah, dan berat badan gratis" + (if (where.isNotBlank()) " di $where" else "") + ". Bawa QR SEHATI.")
            if (day.isAfter(now)) out += PlannedReminder("posy_${s.id}_day", ReminderKind.POSYANDU, day.toInstant().toEpochMilli(),
                "Hari ini Posyandu", (if (where.isNotBlank()) "$where. " else "") + "Jangan lupa bawa QR SEHATI.")
        }
        return out
    }

    fun time(raw: String): LocalTime? = MedicationRepository.normalizeTime(raw)?.let { LocalTime.parse(it) }

    fun nextDaily(now: ZonedDateTime, t: LocalTime): Long {
        var at = now.toLocalDate().atTime(t).atZone(now.zone)
        if (!at.isAfter(now)) at = at.plusDays(1)
        return at.toInstant().toEpochMilli()
    }

    fun nextWeekly(now: ZonedDateTime, day: DayOfWeek, t: LocalTime): Long {
        var at = now.toLocalDate().with(TemporalAdjusters.nextOrSame(day)).atTime(t).atZone(now.zone)
        if (!at.isAfter(now)) at = at.plusWeeks(1)
        return at.toInstant().toEpochMilli()
    }

    private val WATER_LINES = listOf(
        "Segelas air putih membantu menjaga tubuh tetap segar.",
        "Ganti minuman manis dengan air putih ya.",
        "Sudah minum? Ketuk \"Tambah 1 gelas\" setelah minum.",
        "Air putih lebih baik untuk gula darah daripada minuman kemasan.",
    )
    private val WALK_LINES = listOf(
        "Jalan santai 15 menit sore ini baik untuk jantung.",
        "Ajak keluarga jalan bersama keliling kampung.",
        "Sedikit lagi menuju target langkahmu hari ini.",
        "Bergerak 30 menit sehari membantu tekanan darah tetap terkendali.",
    )

    val zone: ZoneId get() = ZoneId.systemDefault()
}
