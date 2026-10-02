package id.sehati.app.data.repository

import id.sehati.app.core.util.Clock
import id.sehati.app.core.util.Ids
import id.sehati.app.data.local.AuditLogEntity
import id.sehati.app.data.local.SystemDao
import id.sehati.app.data.session.SessionManager

/** Audit log: aksi sensitif (akses data warga, pencatatan, penugasan). Tidak memuat nilai kesehatan. */
class AuditLogger(private val dao: SystemDao, private val session: SessionManager, private val clock: Clock) {
    suspend fun log(action: String, subjectId: String? = null, detail: String = "") {
        val s = session.session.value
        dao.audit(
            AuditLogEntity(Ids.uuid(), s?.sehatiId ?: "anonymous", s?.role?.name ?: "NONE", action, subjectId, detail, clock.now()),
        )
    }
}

class AccessDenied(message: String) : Exception(message)
