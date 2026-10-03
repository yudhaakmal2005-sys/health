package id.sehati.app.domain.rules

import java.time.LocalDate

enum class AutoRule { NONE, STEPS, WATER }

/** Tantangan kebiasaan jantung sehat. Selesai bila [target] hari tercapai dalam [windowDays] hari sejak dimulai. */
data class ChallengeDef(
    val id: String,
    val title: String,
    val tagline: String,
    val howTo: String,
    val iconKey: String,
    val target: Int = 7,
    val windowDays: Int = 14,
    val auto: AutoRule = AutoRule.NONE,
    val moduleId: String? = null,
)

object ChallengeCatalog {
    val all = listOf(
        ChallengeDef("walk", "Jalan sehat 7 hari", "Jalan kaki sampai target langkahmu.", "Tercatat otomatis dari langkah harianmu.", "walk", auto = AutoRule.STEPS, moduleId = "aktivitas"),
        ChallengeDef("salt", "Kurangi garam", "Tidak menambah garam/kecap berlebih di meja makan.", "Centang setiap hari kamu berhasil mengurangi garam.", "salt", moduleId = "garam"),
        ChallengeDef("smoke", "Hari bebas rokok", "Tidak merokok seharian. Hindari asap rokok orang lain.", "Centang setiap hari tanpa rokok.", "smoke", moduleId = "rokok"),
        ChallengeDef("water", "Cukup minum air putih", "Ganti minuman manis dengan air putih.", "Tercatat otomatis dari catatan minummu.", "water", auto = AutoRule.WATER, moduleId = "diabetes"),
        ChallengeDef("veg", "Sayur & buah tiap hari", "Isi separuh piringmu dengan sayur dan buah.", "Centang setiap hari kamu makan sayur dan buah.", "veg", moduleId = "makanan"),
        ChallengeDef("sleep", "Tidur teratur", "Tidur dan bangun di jam yang sama.", "Centang setiap malam tidur cukup.", "sleep", moduleId = "tidur"),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}

enum class ChallengeStatus { ACTIVE, COMPLETED, EXPIRED }

data class ChallengeProgress(val def: ChallengeDef, val done: Int, val daysLeft: Int, val status: ChallengeStatus, val checkedToday: Boolean) {
    val fraction: Float get() = (done.toFloat() / def.target).coerceIn(0f, 1f)
}

object Challenges {
    fun evaluate(def: ChallengeDef, start: LocalDate, checkIns: Set<LocalDate>, today: LocalDate): ChallengeProgress {
        val end = start.plusDays(def.windowDays.toLong() - 1)
        val done = checkIns.count { !it.isBefore(start) && !it.isAfter(end) }
        val daysLeft = (java.time.temporal.ChronoUnit.DAYS.between(today, end).toInt() + 1).coerceAtLeast(0)
        val status = when {
            done >= def.target -> ChallengeStatus.COMPLETED
            today.isAfter(end) -> ChallengeStatus.EXPIRED
            else -> ChallengeStatus.ACTIVE
        }
        return ChallengeProgress(def, done.coerceAtMost(def.target), daysLeft, status, today in checkIns)
    }

    /** Hari yang otomatis dihitung dari data: langkah ≥ target atau air ≥ target. */
    fun autoDays(def: ChallengeDef, stepsByDay: Map<LocalDate, Int>, stepTarget: Int, waterByDay: Map<LocalDate, Int>, waterTarget: Int): Set<LocalDate> =
        when (def.auto) {
            AutoRule.STEPS -> stepsByDay.filterValues { stepTarget > 0 && it >= stepTarget }.keys
            AutoRule.WATER -> waterByDay.filterValues { waterTarget > 0 && it >= waterTarget }.keys
            AutoRule.NONE -> emptySet()
        }

    fun encode(days: Set<LocalDate>): String = days.sorted().joinToString(",")
    fun decode(s: String): Set<LocalDate> = s.split(',').filter { it.isNotBlank() }.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
}
