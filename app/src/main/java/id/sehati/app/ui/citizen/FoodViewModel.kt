package id.sehati.app.ui.citizen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.sehati.app.core.util.Clock
import id.sehati.app.core.util.TimeUtils
import id.sehati.app.data.local.FoodEntryEntity
import id.sehati.app.data.prefs.SettingsStore
import id.sehati.app.data.repository.DailyRepository
import id.sehati.app.data.repository.HealthRepository
import id.sehati.app.domain.model.MealCategory
import id.sehati.app.domain.rules.*
import id.sehati.app.ui.app.CurrentUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FoodUiState(
    val loading: Boolean = true,
    val entries: List<FoodEntryEntity> = emptyList(),
    val totals: NutritionTotals = NutritionTotals(),
    val quality: Int = 0,
    val targets: DailyTargets = DailyTargets(),
    val kcalNeed: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FoodViewModel @Inject constructor(
    private val current: CurrentUser,
    private val daily: DailyRepository,
    private val health: HealthRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<FoodUiState> = current.user.filterNotNull().flatMapLatest { u ->
        val iso = TimeUtils.dateIso(clock.now())
        combine(daily.observeFood(u.sehatiId, iso), settings.settings, health.observeChecks(u.sehatiId), health.observeLatestAssessment(u.sehatiId)) { food, s, checks, a ->
            val totals = NutritionRules.total(food.map { NutritionTotals(it.kcal, it.carbs, it.protein, it.fat, it.sugar, it.fiber, it.sodiumMg) })
            val weight = checks.firstNotNullOfOrNull { it.weightKg } ?: a?.weightKg ?: 0f
            val height = checks.firstNotNullOfOrNull { it.heightCm } ?: a?.heightCm ?: 0f
            val need = NutritionRules.estimateDailyKcal(weight, height, AgeCalc.age(u.birthDate), u.sex == "MALE", a?.activeDays ?: 0)
            FoodUiState(false, food, totals, NutritionRules.foodQuality(totals, food.size, food.count { it.heartFriendly }, s.targets), s.targets, need)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FoodUiState())

    fun add(meal: MealCategory, item: FoodItem, portions: Float) { val id = current.id ?: return; viewModelScope.launch { daily.addFood(id, meal, item, portions) } }
    fun delete(e: FoodEntryEntity) { viewModelScope.launch { daily.deleteFood(e.id) } }
}
