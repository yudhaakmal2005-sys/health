package id.sehati.app.domain.rules

import id.sehati.app.domain.model.RiskLevel

/** Laporan agregat untuk Puskesmas. Tidak memuat nama/NIK/HP/alamat/hasil individu. */
object ReportBuilder {
    fun text(s: CommunityStats, generatedAt: String, village: String): String = buildString {
        appendLine("LAPORAN SEHATI — $village")
        appendLine("Dibuat: $generatedAt")
        appendLine(CommunityAnalytics.TERMINOLOGY)
        appendLine()
        appendLine("Warga terdaftar: ${s.totalRegistered}")
        appendLine("Cakupan skrining: ${s.screeningCoverage.label} (${s.screeningCoverage.percent}%)")
        appendLine("Cakupan tindak lanjut: ${s.followUpCoverage.label} (${s.followUpCoverage.percent}%)")
        appendLine("Tindak lanjut terbuka: ${s.openFollowUps}")
        appendLine()
        appendLine("Distribusi hasil skrining peserta:")
        appendLine("- Tekanan darah pada rentang tinggi: ${s.elevatedBp.label} (${s.elevatedBp.percent}%)")
        appendLine("- Gula darah di atas rentang normal: ${s.elevatedGlucose.label} (${s.elevatedGlucose.percent}%)")
        appendLine("- Perokok aktif (asesmen): ${s.smoking.label} (${s.smoking.percent}%)")
        appendLine("- Indikator obesitas (IMT ≥ 25): ${s.obesityIndicator.label} (${s.obesityIndicator.percent}%)")
        appendLine("- Aktivitas di bawah anjuran: ${s.physicalInactivity.label} (${s.physicalInactivity.percent}%)")
        appendLine()
        appendLine("Profil SEHATI (bukan diagnosis):")
        RiskLevel.entries.forEach { appendLine("- ${it.label}: ${s.riskDistribution[it] ?: 0}") }
        appendLine()
        appendLine("Per RW (data agregat; sel < ${CommunityAnalytics.MIN_CELL} peserta ditandai data belum cukup):")
        s.map.forEach { appendLine("- RW ${it.rw}: terdaftar ${it.registered}, diskrining ${it.screened}, ${it.state.label}, tindak lanjut terbuka ${it.openFollowUps}") }
    }

    fun csv(s: CommunityStats): String = buildString {
        appendLine("rw,terdaftar,diskrining,kebutuhan_pemantauan_lebih_tinggi,tindak_lanjut_terbuka,status")
        s.map.forEach { appendLine("${it.rw},${it.registered},${it.screened},${it.higherNeed},${it.openFollowUps},${it.state.name}") }
    }
}
