package id.sehati.app.data.demo

import androidx.room.withTransaction
import id.sehati.app.core.util.Clock
import id.sehati.app.core.util.Ids
import id.sehati.app.core.util.TimeUtils
import id.sehati.app.data.local.*
import id.sehati.app.data.prefs.SettingsStore
import id.sehati.app.data.repository.HealthRepository
import id.sehati.app.data.repository.toCheck
import id.sehati.app.data.sync.SyncTables
import id.sehati.app.domain.content.FoodCatalog
import id.sehati.app.domain.model.*
import id.sehati.app.domain.rules.*
import java.time.LocalDate
import java.util.Random

/**
 * Dataset SINTETIS untuk demo/KKN (BUKAN data kesehatan nyata). Hanya dijalankan pada build DEMO_MODE.
 * Semua baris diberi isDemo/SYNCED agar tidak tampak sebagai data nyata yang belum terkirim.
 */
class DemoSeeder(
    private val db: SehatiDatabase,
    private val health: HealthRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    companion object {
        const val DEMO_PASSWORD = "demo1234"
        const val CADRE_ID = "KD-000001"
        const val ADMIN_ID = "AD-000001"
        const val GOLDEN_ID = "HM-000127"
        private val first = listOf("Ahmad", "Budi", "Citra", "Dewi", "Eko", "Fitri", "Gita", "Hadi", "Indah", "Joko", "Kartini", "Lukman", "Mega", "Nur", "Okta", "Putri", "Rahmat", "Sari", "Teguh", "Umi", "Vina", "Wawan", "Yani", "Zainal")
        private val last = listOf("Santoso", "Wijaya", "Lestari", "Hidayat", "Putra", "Rahayu", "Saputra", "Kusuma", "Nugroho", "Wulandari", "Pratama", "Utami", "Setiawan", "Handayani")
        private val rwList = listOf("01", "02", "03", "04", "05", "06")
    }

    suspend fun seedIfNeeded() {
        if (settings.current().demoSeeded || db.userDao().count() > 0) return
        seed()
        settings.setDemoSeeded(true)
    }

    private suspend fun seed() {
        val now = clock.now()
        val rnd = Random(20261002)
        val users = db.userDao(); val posy = db.posyanduDao(); val hd = db.healthDao(); val dd = db.dailyDao()
        val village = "Desa Mirigambar"

        db.withTransaction {
            posy.upsertFacility(FacilityEntity("fac-melati", "Posyandu Melati", "POSYANDU", village, "01-03"))
            posy.upsertFacility(FacilityEntity("fac-mawar", "Posyandu Mawar", "POSYANDU", village, "04-06"))
            posy.upsertFacility(FacilityEntity("fac-pkm", "Puskesmas Mirigambar", "PUSKESMAS", village))
            listOf(
                LogisticsItemEntity("log-1", "Strip gula darah", "strip", 38, 20, "fac-melati", now),
                LogisticsItemEntity("log-2", "Lancet", "buah", 12, 25, "fac-melati", now),
                LogisticsItemEntity("log-3", "Timbangan digital (baterai)", "set", 4, 2, "fac-mawar", now),
                LogisticsItemEntity("log-4", "Pita ukur lingkar perut", "buah", 6, 3, "fac-mawar", now),
                LogisticsItemEntity("log-5", "Leaflet edukasi PTM", "lembar", 140, 50, "fac-pkm", now),
            ).forEach { posy.upsertLogistics(it) }

            val h = PasswordHasher.hash(DEMO_PASSWORD.toCharArray())
            staffUser(users, CADRE_ID, "Siti Rahma", Role.KADER, "01", now, h)
            staffUser(users, "KD-000002", "Dewi Lestari", Role.KADER, "04", now, h)
            staffUser(users, "KD-000003", "Ratna Sari", Role.KADER, "06", now, h)
            staffUser(users, ADMIN_ID, "Dr. Hendra (Koordinator Puskesmas)", Role.ADMIN, "", now, h)
            posy.upsertCadre(CadreEntity(CADRE_ID, "fac-melati", "01-03", true, now))
            posy.upsertCadre(CadreEntity("KD-000002", "fac-mawar", "04-06", true, now))
            posy.upsertCadre(CadreEntity("KD-000003", "fac-mawar", "04-06", false, now))
        }

        // Warga demo: HM-000100 … HM-000199. HM-000127 sengaja BARU (belum asesmen) untuk golden path.
        val hash = PasswordHasher.hash(DEMO_PASSWORD.toCharArray())
        val twoYears = 365L * 86_400_000L
        for (n in 100..199) {
            val id = SehatiId.format(n)
            val male = if (id == GOLDEN_ID) false else rnd.nextBoolean()
            val name = when (id) {
                GOLDEN_ID -> "Tariska Amelia"
                "HM-000128" -> "Budi Santoso"
                "HM-000129" -> "Sri Wahyuni"
                else -> "${first[rnd.nextInt(first.size)]} ${last[rnd.nextInt(last.size)]}"
            }
            val age = when (id) { GOLDEN_ID -> 34; "HM-000128" -> 45; "HM-000129" -> 58; else -> 22 + rnd.nextInt(55) }
            val rw = when (id) { GOLDEN_ID, "HM-000128" -> "01"; "HM-000129" -> "03"; else -> rwList[rnd.nextInt(rwList.size)] }
            val hasAccount = id in setOf(GOLDEN_ID, "HM-000128", "HM-000129", "HM-000130")
            val birth = LocalDate.now().minusYears(age.toLong()).minusDays(rnd.nextInt(300).toLong()).toString()
            val u = UserEntity(
                sehatiId = id, fullName = name, birthDate = birth, sex = (if (male) Sex.MALE else Sex.FEMALE).name,
                village = village, rw = rw, rt = "0${1 + rnd.nextInt(4)}", phone = null, role = Role.WARGA.name,
                qrToken = QrPayload.newToken(), goals = "Memantau kesehatan", consentLocal = true, consentServerSync = true,
                consentHealthConnect = id == GOLDEN_ID, consentAt = now - rnd.nextInt(200) * 86_400_000L,
                onboardingDone = true, assessmentDone = id != GOLDEN_ID, hasAccount = hasAccount, isDemo = true,
                createdAt = now - twoYears / 2, updatedAt = now, syncStatus = "SYNCED",
            )
            db.withTransaction {
                users.upsert(u)
                if (hasAccount) users.upsertCredential(CredentialEntity(id, hash.salt, hash.hash, hash.iterations))
            }
            if (id == GOLDEN_ID) continue
            seedHealth(rnd, u, male, age, now, hd, id)
        }

        seedGoldenNeighbours(now, dd)
        seedVisitsAndFollowUps(rnd, now)

        // Profil + penandaan SYNCED (data demo bukan data yang menunggu dikirim)
        for (n in 100..199) health.recomputeProfile(SehatiId.format(n))
        db.withTransaction {
            val w = db.openHelper.writableDatabase
            w.execSQL("DELETE FROM sync_queue")
            SyncTables.map.values.forEach { w.execSQL("UPDATE ${it.table} SET syncStatus = 'SYNCED'") }
        }
    }

    private suspend fun staffUser(users: UserDao, id: String, name: String, role: Role, rw: String, now: Long, h: PasswordHasher.Hash) {
        users.upsert(
            UserEntity(
                sehatiId = id, fullName = name, birthDate = "1985-03-10", sex = Sex.FEMALE.name, village = "Desa Mirigambar", rw = rw,
                role = role.name, qrToken = QrPayload.newToken(), consentLocal = true, consentServerSync = true, consentAt = now,
                onboardingDone = true, assessmentDone = true, isDemo = true, createdAt = now, updatedAt = now, syncStatus = "SYNCED",
            ),
        )
        users.upsertCredential(CredentialEntity(id, h.salt, h.hash, h.iterations))
    }

    private suspend fun seedHealth(rnd: Random, u: UserEntity, male: Boolean, age: Int, now: Long, hd: HealthDao, id: String) {
        // Persona deterministik untuk akun sorotan; selebihnya acak
        val special = id == "HM-000129"
        val smoker = special.not() && male && rnd.nextInt(100) < 38
        val height = (if (male) 163f else 152f) + rnd.nextInt(16)
        val weight = height - 100f + rnd.nextInt(24) - 4 + (age / 10)
        val waist = (if (male) 78f else 70f) + (weight - (height - 100f)) * 1.1f + rnd.nextInt(8)
        val activeDays = if (id == "HM-000128") 2 else rnd.nextInt(6)
        val activeMin = if (activeDays == 0) 0 else 15 + rnd.nextInt(40)
        val takenAt = now - (20 + rnd.nextInt(150)) * 86_400_000L
        val hyp = special || (age > 50 && rnd.nextInt(100) < 30)
        val a = HealthAssessmentEntity(
            id = "asm-$id", userId = id, takenAt = takenAt, heightCm = height, weightKg = weight, waistCm = waist,
            knownHypertension = hyp && rnd.nextBoolean(), knownDiabetes = age > 45 && rnd.nextInt(100) < 12, knownDyslipidemia = false,
            knownHeartDisease = false, knownKidneyDisease = false, otherConditions = "",
            familyHypertension = special || rnd.nextInt(100) < 35, familyDiabetes = rnd.nextInt(100) < 20, familyCardio = rnd.nextInt(100) < 10,
            smokingStatus = if (smoker) "CURRENT" else if (male && rnd.nextInt(100) < 15) "FORMER" else "NEVER",
            smokingProduct = if (smoker) "Rokok kretek" else "", cigarettesPerDay = if (smoker) 4 + rnd.nextInt(14) else 0,
            vegetableDays = rnd.nextInt(8), fruitDays = rnd.nextInt(8), saltyFrequent = rnd.nextInt(100) < 45,
            sugaryFrequent = rnd.nextInt(100) < 40, fattyFrequent = rnd.nextInt(100) < 45,
            activeDays = activeDays, activeMinutes = activeMin, activityIntensity = "Sedang", sedentaryHours = 3 + rnd.nextInt(8),
            sleepHours = 5f + rnd.nextInt(5), sleepQuality = "Cukup", stressLevel = 1 + rnd.nextInt(5),
            createdAt = takenAt, updatedAt = takenAt, syncStatus = "SYNCED",
        )
        hd.upsertAssessment(a)

        // 1–4 pemeriksaan Posyandu historis; tekanan darah cenderung naik pada persona berisiko
        val count = if (id == "HM-000128" || special) 3 else rnd.nextInt(5)
        val baseSys = if (special) 138 else 108 + rnd.nextInt(28) + (age - 30) / 3
        val baseDia = if (special) 88 else 68 + rnd.nextInt(16) + (age - 30) / 6
        for (i in 0 until count) {
            val daysAgo = (count - i) * (28 + rnd.nextInt(20)) + 3
            val at = now - daysAgo * 86_400_000L
            val drift = if (special) i * 5 else rnd.nextInt(7) - 3
            val sys = baseSys + drift; val dia = baseDia + drift / 2
            val glucose = if (rnd.nextInt(100) < 70) 85f + rnd.nextInt(60) else 130f + rnd.nextInt(100)
            val mid = "chk-$id-$i"
            val facility = if (u.rw in listOf("01", "02", "03")) "fac-melati" else "fac-mawar"
            hd.upsertHeader(HealthMeasurementEntity(mid, id, at, DataSource.POSYANDU.name, if (u.rw in listOf("01", "02", "03")) CADRE_ID else "KD-000002", facility,
                null, "Pemeriksaan rutin Posyandu", VerificationStatus.VERIFIED.name, at, at, "SYNCED"))
            hd.upsertAnthropometry(AnthropometryEntity("an-$mid", mid, id, weight + i * 0.2f, height, waist, AnthropometryRules.bmi(height, weight + i * 0.2f)))
            hd.upsertBloodPressure(BloodPressureEntity("bp-$mid", mid, id, sys, dia, 66 + rnd.nextInt(24)))
            hd.upsertGlucose(BloodGlucoseEntity("gl-$mid", mid, id, glucose, false))
            if (rnd.nextInt(100) < 35) hd.upsertLipid(LipidMeasurementEntity("li-$mid", mid, id, 160f + rnd.nextInt(110)))
        }
    }

    /** Riwayat harian realistis untuk HM-000128 agar Beranda warga tidak kosong. */
    private suspend fun seedGoldenNeighbours(now: Long, dd: DailyDao) {
        val id = "HM-000128"
        val today = LocalDate.now()
        for (d in 0..6) {
            val date = today.minusDays(d.toLong())
            val iso = date.toString()
            dd.upsertHabit(HabitLogEntity("$id|$iso", id, iso, waterGlasses = 4 + (d % 4), steps = 3200 + d * 700, stepsSource = "SELF", updatedAt = now, syncStatus = "SYNCED"))
            dd.upsertSmoking(SmokingRecordEntity("$id|$iso", id, iso, if (d % 3 == 0) 2 else 0, now, syncStatus = "SYNCED"))
        }
        val items = listOf("nasi_putih" to MealCategory.BREAKFAST, "telur_rebus" to MealCategory.BREAKFAST, "tempe_goreng" to MealCategory.LUNCH, "bayam_bening" to MealCategory.LUNCH)
        items.forEachIndexed { i, (fid, meal) ->
            val f = FoodCatalog.byId(fid)!!
            val n = NutritionRules.scale(f, 1f)
            dd.upsertFood(FoodEntryEntity(Ids.uuid(), id, today.toString(), now - (6 - i) * 3_600_000L, meal.name, f.id, f.name, 1f, f.portion, n.kcal, n.carbs, n.protein, n.fat, n.sugar, n.fiber, n.sodiumMg, f.heartFriendly, now, now, "SYNCED"))
        }
    }

    /** Register Posyandu hari ini + tindak lanjut dari hasil nyata (memakai aturan, bukan angka karangan). */
    private suspend fun seedVisitsAndFollowUps(rnd: Random, now: Long) {
        val hd = db.healthDao(); val posy = db.posyanduDao()
        val today = TimeUtils.dateIso(now)
        val dayStart = TimeUtils.startOfDay(now)
        val picks = listOf("HM-000128", "HM-000129", "HM-000131", "HM-000132", "HM-000133", "HM-000134")
        picks.forEachIndexed { i, uid ->
            val checks = hd.checks(uid)
            val at = dayStart + (8 * 60 + 10 + i * 25) * 60_000L
            val latest = checks.firstOrNull()
            val vid = "visit-today-$uid"
            posy.upsertVisit(PosyanduVisitEntity(vid, uid, CADRE_ID, "fac-melati", today, at, VisitStep.VALIDATION.name, "COMPLETED",
                latest?.header?.id, "", "", at + 600_000, at, at, "SYNCED"))
        }
        for (n in 100..199) {
            val uid = SehatiId.format(n)
            val checks = hd.checks(uid).map { it.toCheck() }
            val latest = checks.firstOrNull() ?: continue
            val prev = checks.drop(1).mapNotNull { it.bloodPressure }.take(3)
            val advice = FollowUpRules.advise(latest.systolic, latest.diastolic, latest.glucose, false, latest.cholesterol, prev).firstOrNull() ?: continue
            val fuStatus = when (rnd.nextInt(4)) { 0 -> FollowUpStatus.DONE; 1 -> FollowUpStatus.SCHEDULED; else -> FollowUpStatus.OPEN }
            val status = if (uid == "HM-000129") FollowUpStatus.OPEN else fuStatus
            posy.upsertFollowUp(
                FollowUpEntity("fu-$uid", uid, null, advice.type.name, advice.reasonCode, advice.reason, advice.priority, status.name,
                    if (status == FollowUpStatus.SCHEDULED) CADRE_ID else null, now + advice.dueInDays * 86_400_000L, CADRE_ID,
                    if (status == FollowUpStatus.DONE) now else null, "", latest.measuredAt, now, "SYNCED"),
            )
        }
    }
}
