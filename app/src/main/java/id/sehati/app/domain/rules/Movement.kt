package id.sehati.app.domain.rules

import id.sehati.app.domain.model.MovementKind

object MovementClassifier {
    const val VEHICLE_KMH = 25f

    /**
     * Mengoreksi jenis gerak yang dipilih pengguna berdasarkan kecepatan rata-rata.
     * Kecepatan seperti kendaraan TIDAK dihitung sebagai aktivitas fisik.
     */
    fun classify(declared: MovementKind, avgSpeedKmh: Float): MovementKind = when {
        avgSpeedKmh >= VEHICLE_KMH && declared != MovementKind.CYCLING -> MovementKind.VEHICLE
        avgSpeedKmh >= 40f -> MovementKind.VEHICLE
        declared == MovementKind.WALKING && avgSpeedKmh > 8f -> MovementKind.RUNNING
        declared == MovementKind.RUNNING && avgSpeedKmh < 3f -> MovementKind.WALKING
        declared == MovementKind.CYCLING && avgSpeedKmh < 4f -> MovementKind.WALKING
        else -> declared
    }

    fun paceMinPerKm(avgSpeedKmh: Float): String {
        if (avgSpeedKmh < 0.5f) return "-"
        val minPerKm = 60f / avgSpeedKmh
        val m = minPerKm.toInt()
        val s = ((minPerKm - m) * 60).toInt()
        return "$m'${s.toString().padStart(2, '0')}\""
    }
}
