package id.sehati.app.data.repository

import androidx.room.withTransaction
import id.sehati.app.core.util.Clock
import id.sehati.app.core.util.Ids
import id.sehati.app.core.util.TimeUtils
import id.sehati.app.data.local.*
import id.sehati.app.data.session.SessionManager
import id.sehati.app.data.sync.SyncRecorder
import id.sehati.app.domain.model.*
import id.sehati.app.domain.rules.*
import kotlinx.coroutines.flow.Flow

data class VisitResult(
    val visit: PosyanduVisitEntity,
    val check: HealthCheck,
    val followUps: List<FollowUpEntity>,
    val profile: HealthProfile,
)

/**
 * Alur Posyandu ILP 5 langkah: Pendaftaran → Pengukuran → Pencatatan → Penyuluhan → Validasi & Sinkronisasi.
 * Setiap langkah ditulis ke Room sehingga alur tetap berjalan tanpa internet.
 */
class PosyanduRepository(
    private val db: SehatiDatabase,
    private val health: HealthRepository,
    private val sync: SyncRecorder,
    private val session: SessionManager,
    private val audit: AuditLogger,
    private val clock: Clock,
) {
    private val dao get() = db.posyanduDao()
    private val users get() = db.userDao()
    private val sys get() = db.systemDao()

    private fun requireRole(p: Permission): Actor {
        val s = session.validate() ?: throw AccessDenied("Sesi berakhir. Silakan masuk kembali.")
        if (!RbacPolicy.can(s.role, p)) throw AccessDenied("Peran ${s.role.name} tidak berwenang untuk aksi ini.")
        return Actor(s.sehatiId, s.role)
    }
    private data class Actor(val id: String, val role: Role)

    // ---------- Pendaftaran ----------
    fun observeVisitsOn(dateIso: String) = dao.observeVisitsOn(dateIso)
    fun observeAllVisits() = dao.observeAllVisits()
    fun observeVisitsOf(userId: String) = dao.observeVisitsOf(userId)
    fun observeVisit(id: String) = dao.observeVisit(id)

    /** Langkah 1. Mendaftarkan warga ke Posyandu hari ini (atau melanjutkan kunjungan terbuka). */
    suspend fun registerVisit(citizenId: String, facilityId: String?): PosyanduVisitEntity {
        val s = requireRole(Permission.VISIT_RECORD)
        val today = TimeUtils.dateIso(clock.now())
        dao.openVisit(citizenId, today)?.let { return it }
        users.get(citizenId) ?: throw IllegalArgumentException("Warga tidak ditemukan.")
        val now = clock.now()
        val v = PosyanduVisitEntity(
            id = Ids.uuid(), userId = citizenId, cadreId = s.id, facilityId = facilityId, dateIso = today, registeredAt = now,
            step = VisitStep.MEASUREMENT.name, status = "IN_PROGRESS", createdAt = now, updatedAt = now,
        )
        db.withTransaction { dao.upsertVisit(v); sync.record("visit", v.id, citizenId, v.version, PosyanduVisitEntity.serializer(), v) }
        audit.log("visit_register", citizenId)
        return v
    }

    /** Hanya dalam kunjungan aktif kader boleh melihat riwayat kesehatan individu. */
    suspend fun historyForVisit(visitId: String): List<HealthCheck> {
        requireRole(Permission.CITIZEN_HEALTH_DETAIL)
        val v = dao.visit(visitId) ?: throw IllegalArgumentException("Kunjungan tidak ditemukan.")
        audit.log("view_history", v.userId, "visit=$visitId")
        return health.checks(v.userId)
    }

    // ---------- Langkah 2–3: Pengukuran & Pencatatan ----------
    suspend fun recordMeasurement(visitId: String, input: MeasurementInput, fasting: Boolean, quality: QualityCheck?): HealthCheck {
        val s = requireRole(Permission.VISIT_RECORD)
        val v = dao.visit(visitId) ?: throw IllegalArgumentException("Kunjungan tidak ditemukan.")
        check(v.status == "IN_PROGRESS") { "Kunjungan sudah selesai." }
        val saved = health.saveMeasurement(
            NewMeasurement(
                userId = v.userId, source = DataSource.POSYANDU, input = input, glucoseFasting = fasting, operatorId = s.id,
                facilityId = v.facilityId, visitId = v.id, notes = quality?.toNote().orEmpty(), verified = true,
            ),
        )
        val next = v.copy(
            measurementId = saved.id, step = VisitStep.RECORDING.name, updatedAt = clock.now(), version = v.version + 1, syncStatus = "LOCAL_ONLY",
        )
        db.withTransaction { dao.upsertVisit(next); sync.record("visit", next.id, next.userId, next.version, PosyanduVisitEntity.serializer(), next) }
        audit.log("measurement_record", v.userId, "visit=$visitId")
        return saved
    }

    // ---------- Langkah 4: Penyuluhan ----------
    suspend fun recordEducation(visitId: String, moduleIds: List<String>) {
        requireRole(Permission.VISIT_RECORD)
        val v = dao.visit(visitId) ?: return
        val next = v.copy(
            educationModules = moduleIds.joinToString(","), step = VisitStep.EDUCATION.name,
            updatedAt = clock.now(), version = v.version + 1, syncStatus = "LOCAL_ONLY",
        )
        db.withTransaction { dao.upsertVisit(next); sync.record("visit", next.id, next.userId, next.version, PosyanduVisitEntity.serializer(), next) }
    }

    // ---------- Langkah 5: Validasi & sinkronisasi ----------
    suspend fun completeVisit(visitId: String, notes: String = ""): VisitResult {
        val s = requireRole(Permission.VISIT_RECORD)
        val v = dao.visit(visitId) ?: throw IllegalArgumentException("Kunjungan tidak ditemukan.")
        val mid = v.measurementId ?: throw IllegalStateException("Belum ada pengukuran yang dicatat.")
        val chk = db.healthDao().check(mid)?.toCheck() ?: throw IllegalStateException("Data pengukuran tidak ditemukan.")
        return db.withTransaction {
            val now = clock.now()
            val previousBp = health.checks(v.userId).filter { it.id != mid }.mapNotNull { it.bloodPressure }.take(3)
            val advice = FollowUpRules.advise(
                chk.systolic, chk.diastolic, chk.glucose, chk.glucoseFasting, chk.cholesterol, previousBp,
            )
            val followUps = advice.mapNotNull { createFollowUpIfNeeded(v.userId, v.id, it, s.id, now) }
            val done = v.copy(
                status = "COMPLETED", step = VisitStep.VALIDATION.name, validatedAt = now, notes = notes,
                updatedAt = now, version = v.version + 1, syncStatus = "LOCAL_ONLY",
            )
            dao.upsertVisit(done)
            sync.record("visit", done.id, done.userId, done.version, PosyanduVisitEntity.serializer(), done)
            val profile = health.recomputeProfile(v.userId)
            audit.log("visit_complete", v.userId, "followups=${followUps.size}")
            VisitResult(done, chk, followUps, profile)
        }
    }

    // ---------- Tindak lanjut ----------
    fun observeFollowUps() = dao.observeFollowUps()
    fun observeFollowUpsOf(userId: String) = dao.observeFollowUpsOf(userId)

    private suspend fun createFollowUpIfNeeded(userId: String, visitId: String?, a: FollowUpAdvice, by: String, now: Long): FollowUpEntity? {
        // dedupe: satu tindak lanjut terbuka per alasan per warga
        dao.openFollowUp(userId, a.reasonCode)?.let { existing ->
            if (existing.priority >= a.priority) return null
            val up = existing.copy(priority = a.priority, type = a.type.name, reason = a.reason, updatedAt = now, version = existing.version + 1, syncStatus = "LOCAL_ONLY")
            dao.upsertFollowUp(up); sync.record("followup", up.id, userId, up.version, FollowUpEntity.serializer(), up)
            return up
        }
        val f = FollowUpEntity(
            id = Ids.uuid(), userId = userId, visitId = visitId, type = a.type.name, reasonCode = a.reasonCode, reason = a.reason,
            priority = a.priority, status = FollowUpStatus.OPEN.name, dueAt = now + a.dueInDays * 86_400_000L, createdBy = by,
            createdAt = now, updatedAt = now,
        )
        dao.upsertFollowUp(f)
        sync.record("followup", f.id, userId, f.version, FollowUpEntity.serializer(), f)
        val n = NotificationEntity(Ids.uuid(), userId, "FOLLOW_UP", "Tindak lanjut kesehatan", a.reason + ". Hubungi kader atau datang ke Posyandu.", now)
        sys.upsertNotification(n)
        return f
    }

    suspend fun assignCadre(followUpId: String, cadreId: String?) {
        val s = requireRole(Permission.FOLLOW_UP_ASSIGN)
        val f = dao.followUp(followUpId) ?: return
        saveFollowUp(f.copy(assignedCadreId = cadreId, status = if (cadreId != null && f.status == "OPEN") FollowUpStatus.SCHEDULED.name else f.status))
        audit.log("followup_assign", f.userId, "cadre=$cadreId by=${s.id}")
    }

    suspend fun scheduleRecheck(followUpId: String, dueAt: Long) {
        requireRole(Permission.FOLLOW_UP_ASSIGN)
        val f = dao.followUp(followUpId) ?: return
        saveFollowUp(f.copy(dueAt = dueAt, status = FollowUpStatus.SCHEDULED.name))
        audit.log("followup_schedule", f.userId)
    }

    suspend fun updateFollowUpStatus(followUpId: String, status: FollowUpStatus, notes: String = "") {
        requireRole(Permission.FOLLOW_UP_WORK)
        val f = dao.followUp(followUpId) ?: return
        val closed = status == FollowUpStatus.DONE || status == FollowUpStatus.CANCELLED
        saveFollowUp(f.copy(status = status.name, notes = notes.ifBlank { f.notes }, closedAt = if (closed) clock.now() else null))
        audit.log("followup_status", f.userId, status.name)
    }

    private suspend fun saveFollowUp(f: FollowUpEntity) {
        val next = f.copy(updatedAt = clock.now(), version = f.version + 1, syncStatus = "LOCAL_ONLY")
        db.withTransaction { dao.upsertFollowUp(next); sync.record("followup", next.id, next.userId, next.version, FollowUpEntity.serializer(), next) }
    }

    // ---------- Rujukan (administratif, bukan diagnosis) ----------
    fun observeReferrals() = dao.observeReferrals()
    fun observeReferralsOf(u: String) = dao.observeReferralsOf(u)

    /** Rujukan hanya dibuat setelah kader/tenaga kesehatan memverifikasi hasil skrining. */
    suspend fun requestReferral(followUpId: String, facilityId: String?, reason: String): ReferralEntity {
        val s = requireRole(Permission.FOLLOW_UP_WORK)
        val f = dao.followUp(followUpId) ?: throw IllegalArgumentException("Tindak lanjut tidak ditemukan.")
        val now = clock.now()
        val r = ReferralEntity(Ids.uuid(), followUpId, f.userId, facilityId, reason, "REQUESTED", s.id, createdAt = now, updatedAt = now)
        db.withTransaction {
            dao.upsertReferral(r); sync.record("referral", r.id, r.userId, r.version, ReferralEntity.serializer(), r)
            saveFollowUp(f.copy(type = FollowUpType.PUSKESMAS_EVALUATION.name, status = FollowUpStatus.IN_PROGRESS.name))
        }
        audit.log("referral_request", f.userId)
        return r
    }

    // ---------- Kunjungan rumah ----------
    fun observeHomeVisits() = dao.observeHomeVisits()

    suspend fun scheduleHomeVisit(followUpId: String, cadreId: String, at: Long): HomeVisitEntity {
        requireRole(Permission.FOLLOW_UP_WORK)
        val f = dao.followUp(followUpId) ?: throw IllegalArgumentException("Tindak lanjut tidak ditemukan.")
        val now = clock.now()
        val h = HomeVisitEntity(Ids.uuid(), followUpId, f.userId, cadreId, at, status = HomeVisitStatus.PLANNED.name, createdAt = now, updatedAt = now)
        db.withTransaction {
            dao.upsertHomeVisit(h); sync.record("homevisit", h.id, h.userId, h.version, HomeVisitEntity.serializer(), h)
            saveFollowUp(f.copy(type = FollowUpType.HOME_VISIT.name, status = FollowUpStatus.SCHEDULED.name, assignedCadreId = cadreId, dueAt = at))
        }
        return h
    }

    private suspend fun saveHomeVisit(h: HomeVisitEntity) {
        val next = h.copy(updatedAt = clock.now(), version = h.version + 1, syncStatus = "LOCAL_ONLY")
        db.withTransaction { dao.upsertHomeVisit(next); sync.record("homevisit", next.id, next.userId, next.version, HomeVisitEntity.serializer(), next) }
    }

    suspend fun arriveHomeVisit(id: String) {
        requireRole(Permission.FOLLOW_UP_WORK)
        val h = dao.homeVisit(id) ?: return
        saveHomeVisit(h.copy(arrivedAt = clock.now(), status = HomeVisitStatus.ARRIVED.name))
    }

    suspend fun verifyCitizenAtHome(id: String, verified: Boolean) {
        requireRole(Permission.FOLLOW_UP_WORK)
        val h = dao.homeVisit(id) ?: return
        saveHomeVisit(h.copy(citizenVerified = verified, status = HomeVisitStatus.IN_PROGRESS.name))
        audit.log("homevisit_verify", h.userId, verified.toString())
    }

    suspend fun recordHomeVisitMeasurement(id: String, input: MeasurementInput): HealthCheck {
        val s = requireRole(Permission.FOLLOW_UP_WORK)
        val h = dao.homeVisit(id) ?: throw IllegalArgumentException("Kunjungan rumah tidak ditemukan.")
        check(h.citizenVerified) { "Verifikasi identitas warga terlebih dahulu." }
        val saved = health.saveMeasurement(
            NewMeasurement(h.userId, DataSource.KADER, input, operatorId = s.id, notes = "Kunjungan rumah", verified = true),
        )
        saveHomeVisit(h.copy(measurementId = saved.id))
        return saved
    }

    suspend fun closeHomeVisit(id: String, assessmentNote: String, educationNote: String, actionNote: String, resolved: Boolean) {
        requireRole(Permission.FOLLOW_UP_WORK)
        val h = dao.homeVisit(id) ?: return
        check(h.citizenVerified) { "Verifikasi identitas warga terlebih dahulu." }
        db.withTransaction {
            saveHomeVisit(h.copy(assessmentNote = assessmentNote, educationNote = educationNote, actionNote = actionNote, status = HomeVisitStatus.CLOSED.name, closedAt = clock.now()))
            dao.followUp(h.followUpId)?.let {
                saveFollowUp(it.copy(status = if (resolved) FollowUpStatus.DONE.name else FollowUpStatus.IN_PROGRESS.name, closedAt = if (resolved) clock.now() else null, notes = actionNote.ifBlank { it.notes }))
            }
            health.recomputeProfile(h.userId)
        }
        audit.log("homevisit_close", h.userId)
    }

    // ---------- Kader, fasilitas, logistik ----------
    fun observeCadres() = dao.observeCadres()
    fun observeFacilities() = dao.observeFacilities()
    fun observeLogistics() = dao.observeLogistics()

    suspend fun setCadreActive(id: String, active: Boolean) {
        requireRole(Permission.MANAGE_CADRES)
        val c = dao.cadre(id) ?: return
        val next = c.copy(active = active, updatedAt = clock.now(), version = c.version + 1, syncStatus = "LOCAL_ONLY")
        db.withTransaction { dao.upsertCadre(next); sync.record("cadre", id, null, next.version, CadreEntity.serializer(), next) }
        audit.log("cadre_active", id, active.toString())
    }

    suspend fun adjustStock(id: String, delta: Int) {
        requireRole(Permission.MANAGE_LOGISTICS)
        val l = dao.logistics(id) ?: return
        val next = l.copy(stock = (l.stock + delta).coerceAtLeast(0), updatedAt = clock.now(), version = l.version + 1, syncStatus = "LOCAL_ONLY")
        db.withTransaction { dao.upsertLogistics(next); sync.record("logistics", id, null, next.version, LogisticsItemEntity.serializer(), next) }
        audit.log("logistics_adjust", null, "$id $delta")
    }

    fun observeAudit(limit: Int = 100): Flow<List<AuditLogEntity>> = sys.observeAudit(limit)
}
