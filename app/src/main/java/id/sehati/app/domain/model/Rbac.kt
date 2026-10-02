package id.sehati.app.domain.model

enum class Permission {
    OWN_DATA_READ_WRITE,       // data pribadi warga
    CITIZEN_LOOKUP,            // cari/scan warga (ringkasan minimal)
    VISIT_RECORD,              // mencatat kunjungan/pemeriksaan Posyandu
    CITIZEN_HEALTH_DETAIL,     // riwayat kesehatan individu (hanya dalam kunjungan aktif)
    FOLLOW_UP_WORK,            // mengerjakan tindak lanjut & kunjungan rumah
    REGISTER_CITIZEN,          // mendaftarkan warga baru
    ANALYTICS_AGGREGATE,       // dashboard agregat
    FOLLOW_UP_ASSIGN,          // menugaskan kader/jadwal ulang
    MANAGE_CADRES,
    MANAGE_LOGISTICS,
    VIEW_AUDIT,
    EXPORT_REPORT,
}

/** RBAC: izin ditentukan role dari sesi terautentikasi, bukan dari tombol di UI. Admin tidak punya akses data individu. */
object RbacPolicy {
    private val matrix: Map<Role, Set<Permission>> = mapOf(
        Role.WARGA to setOf(Permission.OWN_DATA_READ_WRITE),
        Role.KADER to setOf(
            Permission.OWN_DATA_READ_WRITE, Permission.CITIZEN_LOOKUP, Permission.VISIT_RECORD,
            Permission.CITIZEN_HEALTH_DETAIL, Permission.FOLLOW_UP_WORK, Permission.REGISTER_CITIZEN,
        ),
        Role.ADMIN to setOf(
            Permission.ANALYTICS_AGGREGATE, Permission.FOLLOW_UP_ASSIGN, Permission.MANAGE_CADRES,
            Permission.MANAGE_LOGISTICS, Permission.VIEW_AUDIT, Permission.EXPORT_REPORT,
        ),
    )
    fun can(role: Role, p: Permission) = matrix[role]?.contains(p) == true
    fun require(role: Role, p: Permission) { if (!can(role, p)) throw SecurityException("Peran ${role.name} tidak berwenang: ${p.name}") }
}
