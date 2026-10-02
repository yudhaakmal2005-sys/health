package id.sehati.app.domain.rules

import id.sehati.app.domain.model.MovementKind
import java.time.LocalDate
import kotlin.math.roundToInt

/** Target harian dapat dikonfigurasi; bukan angka universal untuk seluruh populasi. */
data class DailyTargets(
    val steps: Int = 6000,
    val waterGlasses: Int = 8,
    val sleepHours: Float = 8f,
    val sodiumMgLimit: Int = 2000,
    val sugarGramsLimit: Int = 50,
    val smokingLimit: Int = 0,
)

data class MovementSegment(val kind: MovementKind, val startMillis: Long, val endMillis: Long, val distanceMeters: Float = 0f) {
    val minutes: Int get() = ((endMillis - startMillis) / 60000L).toInt().coerceAtLeast(0)
}

data class MovementSummary(val activeMinutes: Int, val passiveMinutes: Int, val activeDistanceKm: Float, val passiveDistanceKm: Float)

object ActivityAggregator {
    /** Kendaraan bukan aktivitas fisik, meski bergerak. */
    fun summarize(segments: List<MovementSegment>): MovementSummary {
        val active = segments.filter { it.kind.isActive }
        val transport = segments.filter { it.kind == MovementKind.VEHICLE }
        return MovementSummary(
            activeMinutes = active.sumOf { it.minutes },
            passiveMinutes = transport.sumOf { it.minutes },
            activeDistanceKm = active.sumOf { it.distanceMeters.toDouble() }.toFloat() / 1000f,
            passiveDistanceKm = transport.sumOf { it.distanceMeters.toDouble() }.toFloat() / 1000f,
        )
    }

    fun stepProgress(steps: Int, target: Int): Float = if (target <= 0) 0f else (steps.toFloat() / target).coerceIn(0f, 1f)

    /** Perkiraan kalori aktivitas (MET sederhana). Estimasi, bukan pengukuran. */
    fun estimateKcal(kind: MovementKind, minutes: Int, weightKg: Float): Int {
        val met = when (kind) {
            MovementKind.WALKING -> 3.5f; MovementKind.RUNNING -> 8.5f; MovementKind.CYCLING -> 6.0f
            MovementKind.EXERCISE -> 5.0f; else -> 0f
        }
        return (met * weightKg * (minutes / 60f)).roundToInt()
    }
}

data class FoodItem(
    val id: String, val name: String, val category: String, val portion: String,
    val kcal: Int, val carbs: Float, val protein: Float, val fat: Float, val sugar: Float,
    val fiber: Float, val sodiumMg: Float, val heartFriendly: Boolean,
)

data class NutritionTotals(
    val kcal: Int = 0, val carbs: Float = 0f, val protein: Float = 0f, val fat: Float = 0f,
    val sugar: Float = 0f, val fiber: Float = 0f, val sodiumMg: Float = 0f,
)

object NutritionRules {
    fun total(entries: List<NutritionTotals>): NutritionTotals = NutritionTotals(
        kcal = entries.sumOf { it.kcal }, carbs = entries.sumOf { it.carbs.toDouble() }.toFloat(),
        protein = entries.sumOf { it.protein.toDouble() }.toFloat(), fat = entries.sumOf { it.fat.toDouble() }.toFloat(),
        sugar = entries.sumOf { it.sugar.toDouble() }.toFloat(), fiber = entries.sumOf { it.fiber.toDouble() }.toFloat(),
        sodiumMg = entries.sumOf { it.sodiumMg.toDouble() }.toFloat(),
    )

    fun scale(item: FoodItem, portions: Float): NutritionTotals = NutritionTotals(
        kcal = (item.kcal * portions).roundToInt(), carbs = item.carbs * portions, protein = item.protein * portions,
        fat = item.fat * portions, sugar = item.sugar * portions, fiber = item.fiber * portions, sodiumMg = item.sodiumMg * portions,
    )

    /** Estimasi kebutuhan energi (Mifflin-St Jeor). Estimasi populasi, bukan resep diet. */
    fun estimateDailyKcal(weightKg: Float, heightCm: Float, age: Int, male: Boolean, activeDays: Int): Int {
        if (weightKg <= 0 || heightCm <= 0) return 0
        val bmr = 10f * weightKg + 6.25f * heightCm - 5f * age + if (male) 5f else -161f
        val factor = when { activeDays >= 5 -> 1.55f; activeDays >= 3 -> 1.375f; activeDays >= 1 -> 1.25f; else -> 1.2f }
        return (bmr.coerceAtLeast(1000f) * factor).roundToInt()
    }

    /** Skor kualitas makanan 0..100 dari porsi harian; menilai pola, bukan kalori semata. */
    fun foodQuality(totals: NutritionTotals, items: Int, heartFriendlyItems: Int, targets: DailyTargets = DailyTargets()): Int {
        if (items == 0) return 0
        var score = 50
        score += (heartFriendlyItems * 30 / items)
        if (totals.sodiumMg > targets.sodiumMgLimit) score -= 15
        if (totals.sugar > targets.sugarGramsLimit) score -= 10
        if (totals.fiber >= 25f) score += 10 else if (totals.fiber >= 12f) score += 5
        return score.coerceIn(0, 100)
    }
}

object Streaks {
    /** Hari berturut-turut (berakhir hari ini atau kemarin) di mana target tercapai. */
    fun current(achievedDays: Set<LocalDate>, today: LocalDate = LocalDate.now()): Int {
        var d = if (today in achievedDays) today else today.minusDays(1)
        var n = 0
        while (d in achievedDays) { n++; d = d.minusDays(1) }
        return n
    }
}
