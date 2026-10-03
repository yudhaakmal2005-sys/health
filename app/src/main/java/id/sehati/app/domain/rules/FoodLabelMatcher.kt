package id.sehati.app.domain.rules

import id.sehati.app.domain.content.FoodCatalog

data class FoodSuggestion(val item: FoodItem, val score: Float)

/**
 * Mencocokkan label hasil pengenalan gambar (bahasa Inggris, dari model di perangkat) dengan katalog makanan lokal.
 * Hasilnya hanya saran: pengguna selalu memilih/mengoreksi sendiri.
 */
object FoodLabelMatcher {
    private val keywords: Map<String, List<String>> = mapOf(
        "nasi_putih" to listOf("rice", "white rice", "steamed rice", "cooked rice"),
        "nasi_merah" to listOf("brown rice"),
        "singkong" to listOf("cassava", "yuca"),
        "kentang" to listOf("potato"),
        "jagung" to listOf("corn", "maize", "sweet corn"),
        "oatmeal" to listOf("oatmeal", "oat", "porridge", "cereal"),
        "mie_instan" to listOf("noodle", "instant noodle", "ramen", "pasta", "spaghetti"),
        "roti_tawar" to listOf("bread", "toast", "sandwich", "loaf"),
        "bubur_ayam" to listOf("congee", "porridge"),
        "telur_rebus" to listOf("egg", "boiled egg", "hard-boiled egg"),
        "telur_dadar" to listOf("omelette", "omelet", "fried egg", "scrambled eggs"),
        "ayam_bakar" to listOf("roast chicken", "grilled chicken", "barbecue chicken", "chicken"),
        "ayam_goreng" to listOf("fried chicken", "chicken wing", "chicken nugget"),
        "ikan_kembung" to listOf("fish", "mackerel", "seafood"),
        "tempe_goreng" to listOf("tempeh", "fried tofu"),
        "tahu_rebus" to listOf("tofu", "bean curd"),
        "rendang" to listOf("beef", "meat", "curry", "stew"),
        "bayam_bening" to listOf("spinach", "soup", "leaf vegetable", "leaf"),
        "gado_gado" to listOf("salad", "vegetable", "peanut"),
    )

    private val generic = setOf("food", "dish", "cuisine", "meal", "ingredient", "recipe", "tableware", "plate", "produce", "staple food")

    fun suggest(labels: List<Pair<String, Float>>, limit: Int = 5): List<FoodSuggestion> {
        val scores = mutableMapOf<String, Float>()
        for ((raw, conf) in labels) {
            val label = raw.trim().lowercase()
            if (label in generic) continue
            for ((foodId, words) in keywords) {
                val exact = words.any { it == label }
                val partial = !exact && words.any { label.contains(it) || it.contains(label) }
                if (exact || partial) {
                    val s = conf * if (exact) 1f else 0.6f
                    scores[foodId] = maxOf(scores[foodId] ?: 0f, s)
                }
            }
        }
        val catalog = FoodCatalog.items.associateBy { it.id }
        return scores.entries.sortedByDescending { it.value }.mapNotNull { e -> catalog[e.key]?.let { FoodSuggestion(it, e.value) } }.take(limit)
    }
}
