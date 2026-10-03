package id.sehati.app.data.repository

import id.sehati.app.data.local.SehatiDatabase
import id.sehati.app.data.prefs.SettingsStore
import id.sehati.app.data.session.SessionManager
import id.sehati.app.domain.model.Permission
import id.sehati.app.domain.model.RbacPolicy
import id.sehati.app.domain.rules.ClinicalConfig
import id.sehati.app.domain.rules.ClinicalThresholds
import kotlinx.serialization.json.Json

/**
 * Ambang aturan yang dapat diatur admin (mis. mengikuti pedoman Puskesmas setempat). Perubahan divalidasi,
 * dicatat di audit log, disimpan lokal, lalu semua profil dihitung ulang. Nilai asli tidak pernah dilabeli sebagai standar WHO.
 */
class ThresholdService(
    private val db: SehatiDatabase,
    private val settings: SettingsStore,
    private val health: HealthRepository,
    private val session: SessionManager,
    private val audit: AuditLogger,
    private val json: Json,
) {
    /** Dipanggil saat aplikasi mulai: memuat ambang kustom bila ada. */
    suspend fun restore() {
        val raw = settings.thresholdsJson() ?: return
        val t = runCatching { json.decodeFromString(ClinicalThresholds.serializer(), raw) }.getOrNull() ?: return
        if (t.validate() == null) ClinicalConfig.current = t
    }

    /** Mengembalikan pesan kesalahan, atau null bila berhasil. */
    suspend fun save(t: ClinicalThresholds): String? {
        val s = session.validate() ?: return "Sesi berakhir. Silakan masuk kembali."
        if (!RbacPolicy.can(s.role, Permission.CONFIGURE_RULES)) return "Peran ini tidak berwenang mengubah ambang."
        t.validate()?.let { return it }
        ClinicalConfig.current = t
        settings.setThresholdsJson(json.encodeToString(ClinicalThresholds.serializer(), t))
        audit.log("RULES_CHANGED", null, "ruleset ${ClinicalConfig.rulesetVersion}")
        recomputeAll()
        return null
    }

    suspend fun reset(): String? = save(ClinicalThresholds()).also { if (it == null) settings.setThresholdsJson(null) }

    private suspend fun recomputeAll() {
        db.userDao().citizenIds().forEach { health.recomputeProfile(it) }
    }
}
