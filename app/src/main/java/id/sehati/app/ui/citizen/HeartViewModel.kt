package id.sehati.app.ui.citizen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sehati.app.core.util.Clock
import id.sehati.app.core.util.TimeUtils
import id.sehati.app.data.local.*
import id.sehati.app.data.prefs.AppSettings
import id.sehati.app.data.prefs.SettingsStore
import id.sehati.app.data.repository.DailyRepository
import id.sehati.app.data.repository.HealthCheck
import id.sehati.app.data.repository.HealthRepository
import id.sehati.app.domain.content.DailyFact
import id.sehati.app.domain.content.DailyFacts
import id.sehati.app.domain.model.MovementKind
import id.sehati.app.domain.rules.*
import id.sehati.app.ui.app.CurrentUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class HeartUiState(
    val loading: Boolean = true,
    val report: HeartRiskReport? = null,
    val pillars: List<Pillar> = emptyList(),
    val started: List<ChallengeProgress> = emptyList(),
    val available: List<ChallengeDef> = emptyList(),
    val fact: DailyFact = DailyFacts.all.first(),
    val factAnswered: Boolean = false,
    val factsAnswered: Int = 0,
    val progress: Progress = Progress(0, 0, emptyList()),
    val weekSteps: Int = 0,
    val weekActiveDays: Int = 0,
)

/** Pusat fitur "Jantung Sehat": faktor risiko, pilar harian, tantangan, fakta harian, lencana. Semua dihitung di perangkat. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HeartViewModel @Inject constructor(
    private val current: CurrentUser,
    private val health: HealthRepository,
    private val daily: DailyRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
) : ViewModel() {
    private val today: LocalDate get() = TimeUtils.toLocalDate(clock.now())

    private data class A(val profile: HealthProfileEntity?, val settings: AppSettings, val habit: HabitLogEntity?, val smoking: SmokingRecordEntity?, val sleep: List<SleepRecordEntity>)
    private data class B(val food: List<FoodEntryEntity>, val checks: List<HealthCheck>, val habits: List<HabitLogEntity>, val challenges: List<ChallengeEntity>, val edu: List<EducationProgressEntity>, val activity: List<ActivitySessionEntity>)

    val state: StateFlow<HeartUiState> = current.user.filterNotNull().flatMapLatest { u ->
        val id = u.sehatiId
        val iso = today.toString()
        val a = combine(health.observeProfile(id), settings.settings, daily.observeHabit(id, iso), daily.observeSmoking(id, iso), daily.observeSleep(id, iso), ::A)
        val b = combine(daily.observeFood(id, iso), health.observeChecks(id), daily.observeRecentHabits(id, 30), daily.observeChallenges(id), daily.observeEducation(id), daily.observeRecentActivity(id, 300), ::B)
        combine(a, b) { x, y -> x to y }.mapLatest { (x, y) -> build(id, x, y) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HeartUiState())

    private suspend fun build(id: String, a: A, b: B): HeartUiState {
        val day = today
        val s = a.settings
        val report = health.heartRisk(id, day)
        val food = b.food
        val sodium = food.sumOf { it.sodiumMg.toDouble() }.toFloat()
        val lastBp = b.checks.firstOrNull { it.bloodPressure != null }
        val daysSinceBp = lastBp?.let { ChronoUnit.DAYS.between(TimeUtils.toLocalDate(it.measuredAt), day).toInt() }
        val pillars = HeartPillars.evaluate(
            a.habit?.steps ?: 0, s.targets.steps, a.smoking?.cigarettes ?: 0, food.size, sodium, s.targets.sodiumMgLimit,
            a.sleep.maxOfOrNull { it.minutes }?.div(60f), s.targets.sleepHours, daysSinceBp,
        )
        val stepsByDay = b.habits.associate { LocalDate.parse(it.dateIso) to it.steps }
        val waterByDay = b.habits.associate { LocalDate.parse(it.dateIso) to it.waterGlasses }
        val started = b.challenges.filter { it.challengeId != "fact" }.mapNotNull { e ->
            val def = ChallengeCatalog.byId(e.challengeId) ?: return@mapNotNull null
            val auto = Challenges.autoDays(def, stepsByDay, s.targets.steps, waterByDay, s.targets.waterGlasses)
            val p = Challenges.evaluate(def, LocalDate.parse(e.startDate), Challenges.decode(e.checkIns) + auto, day)
            if (p.status == ChallengeStatus.COMPLETED && e.completedAt == null) daily.markCompletedIfDone(id, def.id, auto, day)
            p
        }.sortedWith(compareBy({ it.status != ChallengeStatus.ACTIVE }, { it.def.title }))
        val startedActive = started.filter { it.status == ChallengeStatus.ACTIVE }.map { it.def.id }.toSet()
        val available = ChallengeCatalog.all.filter { it.id !in startedActive }
        val answeredDays = Challenges.decode(b.challenges.firstOrNull { it.challengeId == "fact" }?.checkIns.orEmpty())
        val weekStart = day.minusDays(6)
        val weekSegments = b.activity.filter { !TimeUtils.toLocalDate(it.startAt).isBefore(weekStart) }
            .map { MovementSegment(MovementKind.parse(it.kind), it.startAt, it.endAt, it.distanceMeters) }
        val weekActiveMin = ActivityAggregator.summarize(weekSegments).activeMinutes
        val progress = Gamification.compute(
            stepsByDay, s.targets.steps, b.edu.count { it.readAt != null }, b.edu.count { it.completedAt != null }, weekActiveMin, day,
            challengesCompleted = started.count { it.status == ChallengeStatus.COMPLETED }, factsAnswered = answeredDays.size,
        )
        val week = (0..6).map { day.minusDays(it.toLong()) }
        return HeartUiState(
            loading = false, report = report, pillars = pillars, started = started, available = available,
            fact = DailyFacts.forDay(day), factAnswered = day in answeredDays, factsAnswered = answeredDays.size, progress = progress,
            weekSteps = week.sumOf { stepsByDay[it] ?: 0 }, weekActiveDays = week.count { (stepsByDay[it] ?: 0) >= s.targets.steps },
        )
    }

    fun startChallenge(id: String) = launchUser { u -> daily.startChallenge(u, id, today) }
    fun checkIn(id: String) = launchUser { u -> daily.checkIn(u, id, today) }
    fun answerFact() = launchUser { u -> daily.answerFact(u, today) }

    private fun launchUser(block: suspend (String) -> Unit) { val id = current.id ?: return; viewModelScope.launch { block(id) } }
}
