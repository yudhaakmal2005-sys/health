package id.sehati.app.data.repository

import id.sehati.app.domain.rules.*
import androidx.room.withTransaction
import id.sehati.app.core.util.Clock
import id.sehati.app.core.util.Ids
import id.sehati.app.core.util.TimeUtils
import id.sehati.app.data.local.*
import id.sehati.app.data.sync.SyncRecorder
import id.sehati.app.domain.model.MealCategory
import id.sehati.app.domain.model.MovementKind
import id.sehati.app.domain.rules.ActivityAggregator
import id.sehati.app.domain.rules.FoodItem
import id.sehati.app.domain.rules.NutritionRules
import kotlinx.coroutines.flow.Flow

/** Data harian warga: air, langkah, makanan, aktivitas, tidur, rokok, edukasi. Semua ditulis ke Room + antrean sync. */
class DailyRepository(
    private val db: SehatiDatabase,
    private val sync: SyncRecorder,
    private val clock: Clock,
) {
    private val dao get() = db.dailyDao()

    // --- Habit (air, langkah, rencana) ---
    fun observeHabit(userId: String, dateIso: String): Flow<HabitLogEntity?> = dao.observeHabit(userId, dateIso)
    suspend fun observeHabitOnce(userId: String, dateIso: String): HabitLogEntity? = dao.habit("$userId|$dateIso")
    fun observeRecentHabits(userId: String, days: Int) = dao.observeRecentHabits(userId, days)

    private suspend fun habitOrNew(userId: String, dateIso: String) =
        dao.habit("$userId|$dateIso") ?: HabitLogEntity("$userId|$dateIso", userId, dateIso, updatedAt = clock.now())

    private suspend fun saveHabit(h: HabitLogEntity) {
        val next = h.copy(updatedAt = clock.now(), version = h.version + 1, syncStatus = "LOCAL_ONLY")
        db.withTransaction { dao.upsertHabit(next); sync.record("habit", next.id, next.userId, next.version, HabitLogEntity.serializer(), next) }
    }

    suspend fun addWater(userId: String, dateIso: String, delta: Int) {
        val h = habitOrNew(userId, dateIso)
        saveHabit(h.copy(waterGlasses = (h.waterGlasses + delta).coerceIn(0, 30)))
    }

    suspend fun setSteps(userId: String, dateIso: String, steps: Int, source: String) {
        val h = habitOrNew(userId, dateIso)
        if (h.steps == steps && h.stepsSource == source) return
        saveHabit(h.copy(steps = steps.coerceAtLeast(0), stepsSource = source))
    }

    suspend fun togglePlanItem(userId: String, dateIso: String, itemId: String) {
        val h = habitOrNew(userId, dateIso)
        val set = h.completedPlanItems.split(',').filter { it.isNotBlank() }.toMutableSet()
        if (!set.add(itemId)) set.remove(itemId)
        saveHabit(h.copy(completedPlanItems = set.joinToString(",")))
    }

    // --- Rokok ---
    fun observeSmoking(userId: String, dateIso: String) = dao.observeSmoking(userId, dateIso)

    suspend fun setCigarettes(userId: String, dateIso: String, count: Int) {
        val id = "$userId|$dateIso"
        val cur = dao.smoking(id)
        val next = SmokingRecordEntity(id, userId, dateIso, count.coerceIn(0, 100), clock.now(), version = (cur?.version ?: 0) + 1)
        db.withTransaction { dao.upsertSmoking(next); sync.record("smoking", id, userId, next.version, SmokingRecordEntity.serializer(), next) }
    }

    // --- Makanan ---
    fun observeFood(userId: String, dateIso: String) = dao.observeFood(userId, dateIso)

    suspend fun addFood(userId: String, meal: MealCategory, item: FoodItem, portions: Float, at: Long = clock.now()): FoodEntryEntity {
        val n = NutritionRules.scale(item, portions)
        val now = clock.now()
        val e = FoodEntryEntity(
            id = Ids.uuid(), userId = userId, dateIso = TimeUtils.dateIso(at), loggedAt = at, meal = meal.name, foodId = item.id,
            name = item.name, portions = portions, portionLabel = item.portion, kcal = n.kcal, carbs = n.carbs, protein = n.protein,
            fat = n.fat, sugar = n.sugar, fiber = n.fiber, sodiumMg = n.sodiumMg, heartFriendly = item.heartFriendly,
            createdAt = now, updatedAt = now,
        )
        db.withTransaction { dao.upsertFood(e); sync.record("food", e.id, userId, e.version, FoodEntryEntity.serializer(), e) }
        return e
    }

    suspend fun deleteFood(id: String) {
        val e = dao.food(id) ?: return
        db.withTransaction { dao.deleteFood(id); sync.record("food", id, e.userId, e.version + 1, FoodEntryEntity.serializer(), e, op = "DELETE") }
    }

    // --- Aktivitas ---
    fun observeActivity(userId: String, dateIso: String): Flow<List<ActivitySessionEntity>> {
        val d = TimeUtils.fromIso(dateIso)
        return dao.observeActivity(userId, TimeUtils.startOfDay(d), TimeUtils.startOfDay(d.plusDays(1)))
    }
    fun observeRecentActivity(userId: String, limit: Int = 20) = dao.observeRecentActivity(userId, limit)

    suspend fun saveActivity(
        userId: String, kind: MovementKind, startAt: Long, endAt: Long, distanceMeters: Float, weightKg: Float,
        source: String, usedLocation: Boolean, steps: Int = 0,
    ): ActivitySessionEntity {
        val minutes = ((endAt - startAt) / 60000L).toInt().coerceAtLeast(0)
        val now = clock.now()
        val est = if (steps > 0) steps else if (kind.isActive) (distanceMeters / 0.75f).toInt() else 0
        val e = ActivitySessionEntity(
            id = Ids.uuid(), userId = userId, kind = kind.name, startAt = startAt, endAt = endAt, distanceMeters = distanceMeters,
            steps = est, kcal = ActivityAggregator.estimateKcal(kind, minutes, weightKg), source = source, usedLocation = usedLocation,
            createdAt = now, updatedAt = now,
        )
        db.withTransaction { dao.upsertActivity(e); sync.record("activity", e.id, userId, e.version, ActivitySessionEntity.serializer(), e) }
        return e
    }

    /** Impor dari sumber luar dengan id tetap → tidak menggandakan data. */
    suspend fun importActivity(e: ActivitySessionEntity) {
        db.withTransaction { dao.upsertActivity(e); sync.record("activity", e.id, e.userId, e.version, ActivitySessionEntity.serializer(), e) }
    }

    suspend fun deleteActivity(id: String) = dao.deleteActivity(id)

    // --- Tidur ---
    fun observeSleep(userId: String, dateIso: String) = dao.observeSleep(userId, dateIso)

    suspend fun saveSleep(userId: String, startAt: Long, endAt: Long, quality: Int?, source: String, fixedId: String? = null) {
        val minutes = ((endAt - startAt) / 60000L).toInt()
        if (minutes <= 0) return
        val now = clock.now()
        val e = SleepRecordEntity(
            id = fixedId ?: Ids.uuid(), userId = userId, dateIso = TimeUtils.dateIso(endAt), startAt = startAt, endAt = endAt,
            minutes = minutes, quality = quality, source = source, createdAt = now, updatedAt = now,
        )
        db.withTransaction { dao.upsertSleep(e); sync.record("sleep", e.id, userId, e.version, SleepRecordEntity.serializer(), e) }
    }

    // --- Edukasi ---
    fun observeEducation(userId: String) = dao.observeEducation(userId)

    suspend fun markRead(userId: String, moduleId: String) {
        val id = "$userId|$moduleId"
        val cur = dao.education(id)
        val now = clock.now()
        val next = (cur ?: EducationProgressEntity(id, userId, moduleId, now, null, null, null, now))
            .let { it.copy(readAt = it.readAt ?: now, updatedAt = now, version = it.version + 1, syncStatus = "LOCAL_ONLY") }
        db.withTransaction { dao.upsertEducation(next); sync.record("education", id, userId, next.version, EducationProgressEntity.serializer(), next) }
    }

    suspend fun saveQuiz(userId: String, moduleId: String, score: Int, total: Int) {
        val id = "$userId|$moduleId"
        val cur = dao.education(id)
        val now = clock.now()
        val best = maxOf(score, cur?.quizScore ?: 0)
        val next = (cur ?: EducationProgressEntity(id, userId, moduleId, now, null, null, null, now)).copy(
            readAt = cur?.readAt ?: now, quizScore = best, quizTotal = total,
            completedAt = if (score * 100 >= total * 60) (cur?.completedAt ?: now) else cur?.completedAt,
            updatedAt = now, version = (cur?.version ?: 0) + 1, syncStatus = "LOCAL_ONLY",
        )
        db.withTransaction { dao.upsertEducation(next); sync.record("education", id, userId, next.version, EducationProgressEntity.serializer(), next) }
    }

    // --- Tantangan kebiasaan ---
    fun observeChallenges(userId: String) = dao.observeChallenges(userId)

    suspend fun startChallenge(userId: String, challengeId: String, today: java.time.LocalDate) {
        requireNotNull(ChallengeCatalog.byId(challengeId)) { "Tantangan tidak dikenal" }
        val id = "$userId|$challengeId"
        val cur = dao.challenge(id)
        val now = clock.now()
        val next = ChallengeEntity(id, userId, challengeId, today.toString(), "", null, now, version = (cur?.version ?: 0) + 1)
        db.withTransaction { dao.upsertChallenge(next); sync.record("challenge", id, userId, next.version, ChallengeEntity.serializer(), next) }
    }

    /** Check-in manual hari ini; tantangan selesai bila target tercapai di dalam jendela waktunya. */
    suspend fun checkIn(userId: String, challengeId: String, today: java.time.LocalDate, autoDays: Set<java.time.LocalDate> = emptySet()) {
        val def = requireNotNull(ChallengeCatalog.byId(challengeId))
        val id = "$userId|$challengeId"
        val cur = dao.challenge(id) ?: return
        val days = Challenges.decode(cur.checkIns) + today
        val now = clock.now()
        val progress = Challenges.evaluate(def, java.time.LocalDate.parse(cur.startDate), days + autoDays, today)
        val next = cur.copy(
            checkIns = Challenges.encode(days), updatedAt = now, version = cur.version + 1, syncStatus = "LOCAL_ONLY",
            completedAt = cur.completedAt ?: if (progress.status == ChallengeStatus.COMPLETED) now else null,
        )
        db.withTransaction { dao.upsertChallenge(next); sync.record("challenge", id, userId, next.version, ChallengeEntity.serializer(), next) }
    }

    /** Menyimpan penyelesaian tantangan otomatis (langkah/air) tanpa check-in manual. */
    suspend fun markCompletedIfDone(userId: String, challengeId: String, autoDays: Set<java.time.LocalDate>, today: java.time.LocalDate) {
        val def = ChallengeCatalog.byId(challengeId) ?: return
        val cur = dao.challenge("$userId|$challengeId") ?: return
        if (cur.completedAt != null) return
        val p = Challenges.evaluate(def, java.time.LocalDate.parse(cur.startDate), Challenges.decode(cur.checkIns) + autoDays, today)
        if (p.status != ChallengeStatus.COMPLETED) return
        val next = cur.copy(completedAt = clock.now(), updatedAt = clock.now(), version = cur.version + 1, syncStatus = "LOCAL_ONLY")
        db.withTransaction { dao.upsertChallenge(next); sync.record("challenge", next.id, userId, next.version, ChallengeEntity.serializer(), next) }
    }

    suspend fun answerFact(userId: String, day: java.time.LocalDate) {
        val id = "$userId|fact"
        val cur = dao.challenge(id)
        val now = clock.now()
        val days = Challenges.decode(cur?.checkIns.orEmpty()) + day
        val next = (cur ?: ChallengeEntity(id, userId, "fact", day.toString(), "", null, now)).copy(
            checkIns = Challenges.encode(days), updatedAt = now, version = (cur?.version ?: 0) + 1, syncStatus = "LOCAL_ONLY",
        )
        db.withTransaction { dao.upsertChallenge(next); sync.record("challenge", id, userId, next.version, ChallengeEntity.serializer(), next) }
    }
}
