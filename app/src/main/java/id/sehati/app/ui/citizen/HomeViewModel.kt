package id.sehati.app.ui.citizen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sehati.app.core.util.Clock
import id.sehati.app.core.util.TimeUtils
import id.sehati.app.data.local.*
import id.sehati.app.data.prefs.AppSettings
import id.sehati.app.data.prefs.SettingsStore
import id.sehati.app.data.repository.*
import id.sehati.app.domain.content.Academy
import id.sehati.app.domain.content.EducationModule
import id.sehati.app.domain.model.FollowUpStatus
import id.sehati.app.domain.model.MovementKind
import id.sehati.app.domain.model.RiskLevel
import id.sehati.app.domain.rules.*
import id.sehati.app.ui.app.CurrentUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val user: UserEntity? = null,
    val greeting: String = "",
    val level: RiskLevel = RiskLevel.HEALTHY_HABIT,
    val steps: Int = 0, val stepsSource: String = "SELF", val targets: DailyTargets = DailyTargets(),
    val water: Int = 0, val sleepMinutes: Int = 0, val cigarettes: Int = 0,
    val foodQuality: Int = 0, val kcal: Int = 0, val foodCount: Int = 0,
    val activeMinutes: Int = 0, val passiveMinutes: Int = 0,
    val latestCheck: HealthCheck? = null,
    val openFollowUps: Int = 0,
    val plan: List<PlanDto> = emptyList(), val donePlan: Set<String> = emptySet(),
    val tip: String = "", val modules: List<EducationModule> = emptyList(),
    val progress: Progress = Progress(0, 0, emptyList()),
    val unread: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val current: CurrentUser,
    private val health: HealthRepository,
    private val daily: DailyRepository,
    private val posyandu: PosyanduRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
) : ViewModel() {

    private val today get() = TimeUtils.dateIso(clock.now())

    private data class GroupA(val profile: HealthProfileEntity?, val settings: AppSettings, val habit: HabitLogEntity?, val smoking: SmokingRecordEntity?, val sleep: List<SleepRecordEntity>)
    private data class GroupB(val food: List<FoodEntryEntity>, val activity: List<ActivitySessionEntity>, val checks: List<HealthCheck>, val followUps: List<FollowUpEntity>, val habits: List<HabitLogEntity>)

    val state: StateFlow<HomeUiState> = current.user.filterNotNull().flatMapLatest { u ->
        val id = u.sehatiId
        val iso = today
        val a = combine(health.observeProfile(id), settings.settings, daily.observeHabit(id, iso), daily.observeSmoking(id, iso), daily.observeSleep(id, iso), ::GroupA)
        val b = combine(daily.observeFood(id, iso), daily.observeActivity(id, iso), health.observeChecks(id), posyandu.observeFollowUpsOf(id), daily.observeRecentHabits(id, 30), ::GroupB)
        combine(a, b, daily.observeEducation(id)) { x, y, edu -> build(u, x.profile, x.settings, x.habit, x.smoking, x.sleep, y.food, y.activity, y.checks, y.followUps, y.habits, edu) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    private fun build(
        u: UserEntity, p: HealthProfileEntity?, s: AppSettings, h: HabitLogEntity?, sm: SmokingRecordEntity?, sleep: List<SleepRecordEntity>,
        food: List<FoodEntryEntity>, act: List<ActivitySessionEntity>, checks: List<HealthCheck>, fus: List<FollowUpEntity>, habits: List<HabitLogEntity>, edu: List<EducationProgressEntity>,
    ): HomeUiState {
        val level = health.levelOf(p)
        val findings = health.decodeFindings(p)
        val totals = NutritionRules.total(food.map { NutritionTotals(it.kcal, it.carbs, it.protein, it.fat, it.sugar, it.fiber, it.sodiumMg) })
        val quality = NutritionRules.foodQuality(totals, food.size, food.count { it.heartFriendly }, s.targets)
        val seg = ActivityAggregator.summarize(act.map { MovementSegment(MovementKind.parse(it.kind), it.startAt, it.endAt, it.distanceMeters) })
        val steps = maxOf(h?.steps ?: 0, 0)
        val hour = TimeUtils.hourOf(clock.now())
        val coach = CoachContext(u.fullName.substringBefore(' '), steps, s.targets.steps, h?.waterGlasses ?: 0, s.targets.waterGlasses,
            sleep.maxOfOrNull { it.minutes }?.div(60f), sm?.cigarettes ?: 0, checks.firstOrNull()?.bloodPressure, level.label, fus.any { it.status == "OPEN" })
        val stepsByDay = habits.associate { LocalDate.parse(it.dateIso) to it.steps }
        val prog = Gamification.compute(stepsByDay, s.targets.steps, edu.count { it.readAt != null }, edu.count { it.completedAt != null }, seg.activeMinutes)
        return HomeUiState(
            loading = false, user = u, greeting = TimeUtils.greeting(hour), level = level,
            steps = steps, stepsSource = h?.stepsSource ?: "SELF", targets = s.targets, water = h?.waterGlasses ?: 0,
            sleepMinutes = sleep.maxOfOrNull { it.minutes } ?: 0, cigarettes = sm?.cigarettes ?: 0,
            foodQuality = quality, kcal = totals.kcal, foodCount = food.size, activeMinutes = seg.activeMinutes, passiveMinutes = seg.passiveMinutes,
            latestCheck = checks.firstOrNull(), openFollowUps = fus.count { it.status != "DONE" && it.status != "CANCELLED" },
            plan = health.decodePlan(p), donePlan = (h?.completedPlanItems ?: "").split(',').filter { it.isNotBlank() }.toSet(),
            tip = HealthCoach.dailyTip(coach), modules = Academy.recommendFor(findings.map { it.id }).take(3), progress = prog,
        )
    }

    fun addWater(d: Int) = launchUser { id -> daily.addWater(id, today, d) }
    fun setCigarettes(delta: Int) = launchUser { id -> daily.setCigarettes(id, today, ((state.value.cigarettes) + delta).coerceAtLeast(0)) }
    fun togglePlan(itemId: String) = launchUser { id -> daily.togglePlanItem(id, today, itemId) }

    private fun launchUser(block: suspend (String) -> Unit) { val id = current.id ?: return; viewModelScope.launch { block(id) } }
}

