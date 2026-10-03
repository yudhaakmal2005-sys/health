package id.sehati.app.domain.rules

import java.util.Locale

object RedFlag {
    const val EMERGENCY_TITLE = "PERHATIAN"
    const val EMERGENCY_MESSAGE =
        "Nyeri/tekanan dada, sesak berat, pingsan, atau gejala akut lainnya memerlukan pertolongan medis segera. " +
            "Segera hubungi 119 atau 112, atau ke IGD terdekat. Jangan menunggu respons aplikasi."

    private val triggers = listOf(
        "nyeri dada", "sakit dada", "dada tertekan", "dada ditekan", "dada terasa berat", "dada panas",
        "sesak berat", "sesak napas berat", "sesak nafas berat", "susah napas", "susah bernapas", "sesak napas",
        "menjalar ke lengan", "menjalar ke rahang", "menjalar ke leher",
        "pingsan", "tidak sadar", "hilang kesadaran", "mulut mencong", "bicara pelo", "cadel mendadak",
        "lemah sebelah", "mati rasa sebelah", "kejang", "muntah darah", "sakit kepala hebat",
    )

    fun detect(text: String): Boolean {
        val q = text.lowercase(Locale.ROOT)
        return triggers.any { q.contains(it) }
    }
}
