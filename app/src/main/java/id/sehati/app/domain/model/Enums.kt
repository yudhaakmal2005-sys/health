package id.sehati.app.domain.model

enum class Role { WARGA, KADER, ADMIN;
    companion object { fun parse(v: String?): Role = entries.firstOrNull { it.name == v } ?: WARGA }
}

enum class Sex(val label: String) { MALE("Laki-laki"), FEMALE("Perempuan");
    companion object { fun parse(v: String?): Sex = entries.firstOrNull { it.name == v } ?: MALE }
}

/** Status sinkronisasi setiap data yang dapat dikirim ke server. */
enum class SyncStatus(val label: String) {
    LOCAL_ONLY("Tersimpan di perangkat"),
    SYNCING("Sedang disinkronkan"),
    SYNCED("Tersinkron"),
    SYNC_FAILED("Sinkron gagal");
    companion object { fun parse(v: String?): SyncStatus = entries.firstOrNull { it.name == v } ?: LOCAL_ONLY }
}

/** Asal sebuah data pengukuran (data provenance). */
enum class DataSource(val label: String) {
    SELF("Mandiri"),
    KADER("Kader"),
    POSYANDU("Posyandu"),
    PUSKESMAS("Puskesmas"),
    HEALTH_CONNECT("Health Connect");
    companion object { fun parse(v: String?): DataSource = entries.firstOrNull { it.name == v } ?: SELF }
}

enum class VerificationStatus(val label: String) {
    UNVERIFIED("Belum diverifikasi"),
    VERIFIED("Terverifikasi kader");
    companion object { fun parse(v: String?): VerificationStatus = entries.firstOrNull { it.name == v } ?: UNVERIFIED }
}

/**
 * Profil SEHATI. Ini BUKAN diagnosis: hanya kategori pemantauan berbasis data yang diinput.
 */
enum class RiskLevel(val rank: Int, val label: String, val summary: String) {
    HEALTHY_HABIT(0, "Kebiasaan Sehat",
        "Tidak ditemukan indikator risiko yang perlu ditindaklanjuti secara khusus berdasarkan data yang tersedia."),
    RISK_AWARENESS(1, "Waspada Faktor Risiko",
        "Ada faktor gaya hidup atau indikator yang perlu diperbaiki dan dipantau."),
    HIGHER_MONITORING(2, "Perlu Pemantauan Lebih",
        "Terdapat beberapa indikator yang memerlukan pemantauan lebih teratur dan/atau evaluasi tenaga kesehatan."),
    MEDICAL_FOLLOW_UP(3, "Perlu Tindak Lanjut Medis",
        "Terdapat temuan yang perlu mendapatkan evaluasi tenaga kesehatan sesuai protokol pelayanan.");
    companion object { fun parse(v: String?): RiskLevel = entries.firstOrNull { it.name == v } ?: HEALTHY_HABIT }
}

const val PROFILE_DISCLAIMER =
    "Profil ini merupakan hasil pemantauan berbasis data yang dimasukkan ke SEHATI dan bukan diagnosis medis."

enum class FollowUpStatus(val label: String) {
    OPEN("Perlu tindak lanjut"), SCHEDULED("Terjadwal"), IN_PROGRESS("Sedang berjalan"), DONE("Selesai"), CANCELLED("Dibatalkan");
    companion object { fun parse(v: String?): FollowUpStatus = entries.firstOrNull { it.name == v } ?: OPEN }
}

enum class FollowUpType(val label: String) {
    REPEAT_MEASUREMENT("Ukur ulang"), EDUCATION("Edukasi kesehatan"),
    HOME_VISIT("Kunjungan rumah"), PUSKESMAS_EVALUATION("Evaluasi Puskesmas");
    companion object { fun parse(v: String?): FollowUpType = entries.firstOrNull { it.name == v } ?: REPEAT_MEASUREMENT }
}

enum class HomeVisitStatus(val label: String) {
    PLANNED("Direncanakan"), ARRIVED("Tiba di lokasi"), IN_PROGRESS("Sedang dikunjungi"), CLOSED("Selesai");
    companion object { fun parse(v: String?): HomeVisitStatus = entries.firstOrNull { it.name == v } ?: PLANNED }
}

enum class VisitStep(val number: Int, val title: String) {
    REGISTRATION(1, "Pendaftaran"),
    MEASUREMENT(2, "Pengukuran"),
    RECORDING(3, "Pencatatan"),
    EDUCATION(4, "Penyuluhan"),
    VALIDATION(5, "Validasi & Sinkronisasi");
    companion object { fun parse(v: String?): VisitStep = entries.firstOrNull { it.name == v } ?: REGISTRATION }
}

enum class MealCategory(val label: String) {
    BREAKFAST("Sarapan"), LUNCH("Makan siang"), DINNER("Makan malam"), SNACK("Camilan");
    companion object { fun parse(v: String?): MealCategory = entries.firstOrNull { it.name == v } ?: SNACK }
}

enum class MovementKind(val label: String, val isActive: Boolean) {
    WALKING("Jalan kaki", true), RUNNING("Lari", true), CYCLING("Bersepeda", true),
    EXERCISE("Olahraga", true), STATIONARY("Diam", false), VEHICLE("Berkendara", false);
    companion object { fun parse(v: String?): MovementKind = entries.firstOrNull { it.name == v } ?: WALKING }
}
