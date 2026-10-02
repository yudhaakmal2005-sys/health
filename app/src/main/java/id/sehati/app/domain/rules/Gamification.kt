package id.sehati.app.domain.rules

import java.time.LocalDate

data class Badge(val id: String, val title: String, val description: String, val earned: Boolean)

data class Progress(val streakDays: Int, val learningPoints: Int, val badges: List<Badge>)

/** Gamifikasi ringan: kebiasaan sehat dan belajar (bukan permainan finansial). */
object Gamification {
    fun compute(
        stepsByDay: Map<LocalDate, Int>, stepTarget: Int, modulesRead: Int, modulesCompleted: Int,
        activeMinutesThisWeek: Int, today: LocalDate = LocalDate.now(),
    ): Progress {
        val achieved = stepsByDay.filterValues { it >= stepTarget && stepTarget > 0 }.keys
        val streak = Streaks.current(achieved, today)
        val points = modulesRead * 2 + modulesCompleted * 10
        val badges = listOf(
            Badge("streak3", "Healthy Streak 3 hari", "3 hari berturut-turut mencapai target aktivitas.", streak >= 3),
            Badge("streak7", "Healthy Streak 7 hari", "7 hari berturut-turut mencapai target aktivitas.", streak >= 7),
            Badge("active150", "Activity Badge", "150 menit aktif dalam seminggu.", activeMinutesThisWeek >= 150),
            Badge("edu1", "Education Badge", "Menuntaskan satu materi dengan kuis.", modulesCompleted >= 1),
            Badge("edu5", "Pembelajar Sehat", "Menuntaskan lima materi.", modulesCompleted >= 5),
        )
        return Progress(streak, points, badges)
    }
}
