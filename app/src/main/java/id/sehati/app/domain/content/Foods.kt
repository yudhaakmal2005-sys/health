package id.sehati.app.domain.content

import id.sehati.app.domain.rules.FoodItem

/** Basis data makanan lokal (nilai estimasi per porsi; sumber nilai gizi perkiraan umum, dapat berbeda pada produk nyata). */
object FoodCatalog {
    const val ESTIMATE_NOTE = "Estimasi berdasarkan database umum. Nilai sebenarnya dapat berbeda."

    val items: List<FoodItem> = listOf(
        FoodItem("nasi_putih", "Nasi putih", "Karbohidrat", "1 centong (100 g)", 130, 28f, 2.7f, 0.3f, 0f, 0.4f, 1f, false),
        FoodItem("nasi_merah", "Nasi merah", "Karbohidrat", "1 centong (100 g)", 111, 23.5f, 2.6f, 0.9f, 0f, 1.8f, 1f, true),
        FoodItem("singkong", "Singkong rebus", "Karbohidrat", "1 potong (100 g)", 160, 38f, 1.4f, 0.3f, 1.7f, 1.8f, 14f, true),
        FoodItem("kentang", "Kentang rebus", "Karbohidrat", "1 butir (150 g)", 130, 29.5f, 2.9f, 0.2f, 1.2f, 2.1f, 6f, true),
        FoodItem("jagung", "Jagung rebus", "Karbohidrat", "1 mangkuk kecil (100 g)", 96, 21f, 3.4f, 1.5f, 4f, 2.4f, 15f, true),
        FoodItem("oatmeal", "Oatmeal", "Karbohidrat", "4 sdm (40 g)", 150, 27f, 5f, 2.5f, 1f, 4f, 2f, true),
        FoodItem("mie_instan", "Mi instan + bumbu", "Karbohidrat", "1 bungkus (75 g)", 350, 50f, 8f, 14f, 3f, 2f, 1700f, false),
        FoodItem("roti_tawar", "Roti tawar", "Karbohidrat", "2 lembar (50 g)", 130, 24f, 4f, 1.5f, 3f, 1.5f, 260f, false),
        FoodItem("bubur_ayam", "Bubur ayam", "Karbohidrat", "1 mangkuk (250 g)", 250, 35f, 10f, 8f, 2f, 1f, 650f, false),
        FoodItem("telur_rebus", "Telur rebus", "Protein", "1 butir (50 g)", 74, 0.4f, 6.3f, 5f, 0.2f, 0f, 70f, true),
        FoodItem("telur_dadar", "Telur dadar", "Protein", "1 butir (60 g)", 110, 0.6f, 7f, 9f, 0.3f, 0f, 180f, false),
        FoodItem("ayam_bakar", "Ayam bakar tanpa kulit", "Protein", "1 potong (100 g)", 165, 0f, 31f, 3.6f, 0f, 0f, 74f, true),
        FoodItem("ayam_goreng", "Ayam goreng", "Protein", "1 potong (90 g)", 245, 7.8f, 18f, 15.6f, 0f, 0.2f, 320f, false),
        FoodItem("ikan_kembung", "Ikan kembung panggang", "Protein", "1 ekor (80 g)", 135, 0f, 17f, 6.8f, 0f, 0f, 65f, true),
        FoodItem("ikan_asin", "Ikan asin goreng", "Protein", "1 potong (30 g)", 100, 0f, 15f, 4f, 0f, 0f, 1500f, false),
        FoodItem("tempe_bacem", "Tempe bacem", "Protein", "1 potong (50 g)", 118, 12f, 7.5f, 4.2f, 6f, 2.8f, 180f, true),
        FoodItem("tempe_goreng", "Tempe goreng", "Protein", "1 potong (50 g)", 145, 9.5f, 5.5f, 9.8f, 0.5f, 1.5f, 220f, false),
        FoodItem("tahu_rebus", "Tahu rebus", "Protein", "1 potong (100 g)", 76, 1.9f, 8.1f, 4.8f, 0.5f, 0.9f, 7f, true),
        FoodItem("tahu_goreng", "Tahu goreng", "Protein", "1 potong (60 g)", 135, 6.2f, 6f, 9.5f, 0.5f, 0.8f, 210f, false),
        FoodItem("rendang", "Rendang daging", "Protein", "1 potong (60 g)", 195, 3f, 17f, 13f, 1f, 0.5f, 420f, false),
        FoodItem("bayam_bening", "Sayur bayam bening", "Sayuran", "1 mangkuk (150 g)", 36, 6.5f, 2.8f, 0.5f, 1f, 2.2f, 120f, true),
        FoodItem("sayur_asem", "Sayur asem", "Sayuran", "1 mangkuk (200 g)", 70, 14f, 2.5f, 1f, 4f, 3.1f, 310f, true),
        FoodItem("tumis_kangkung", "Tumis kangkung", "Sayuran", "1 piring kecil (100 g)", 85, 4.2f, 2.5f, 6.5f, 1f, 2f, 240f, true),
        FoodItem("sop_sayur", "Sop sayur", "Sayuran", "1 mangkuk (180 g)", 60, 11f, 2.8f, 0.8f, 3f, 3.5f, 290f, true),
        FoodItem("gado_gado", "Gado-gado", "Sayuran", "1 porsi (250 g)", 295, 32f, 12f, 13f, 8f, 6.5f, 380f, true),
        FoodItem("lalapan", "Lalapan segar", "Sayuran", "1 piring kecil (80 g)", 20, 4f, 1f, 0.2f, 2f, 2f, 10f, true),
        FoodItem("pisang", "Pisang", "Buah", "1 buah (100 g)", 89, 22.8f, 1.1f, 0.3f, 12f, 2.6f, 1f, true),
        FoodItem("pepaya", "Pepaya", "Buah", "1 potong (150 g)", 65, 16f, 0.9f, 0.4f, 10f, 2.7f, 5f, true),
        FoodItem("semangka", "Semangka", "Buah", "2 potong (200 g)", 60, 15f, 1.2f, 0.3f, 12f, 0.8f, 2f, true),
        FoodItem("jeruk", "Jeruk manis", "Buah", "1 buah (120 g)", 62, 15.4f, 1.2f, 0.2f, 12f, 3.1f, 0f, true),
        FoodItem("apel", "Apel", "Buah", "1 buah (180 g)", 95, 25f, 0.5f, 0.3f, 19f, 4.4f, 2f, true),
        FoodItem("air_putih", "Air putih", "Minuman & camilan", "1 gelas (250 ml)", 0, 0f, 0f, 0f, 0f, 0f, 2f, true),
        FoodItem("kopi_hitam", "Kopi hitam tanpa gula", "Minuman & camilan", "1 cangkir (150 ml)", 2, 0f, 0.3f, 0f, 0f, 0f, 5f, true),
        FoodItem("teh_manis", "Teh manis", "Minuman & camilan", "1 gelas (200 ml)", 90, 22.5f, 0f, 0f, 22f, 0f, 10f, false),
        FoodItem("kopi_sachet", "Kopi sachet manis", "Minuman & camilan", "1 gelas (200 ml)", 120, 20f, 1.5f, 3.5f, 17f, 0f, 60f, false),
        FoodItem("minuman_kemasan", "Minuman kemasan manis", "Minuman & camilan", "1 botol (350 ml)", 150, 37f, 0f, 0f, 35f, 0f, 30f, false),
        FoodItem("kerupuk", "Kerupuk", "Minuman & camilan", "1 keping (15 g)", 75, 9.5f, 0.8f, 3.8f, 0.2f, 0.1f, 140f, false),
        FoodItem("gorengan", "Gorengan (bakwan)", "Minuman & camilan", "1 buah (50 g)", 140, 14f, 2f, 8f, 1f, 1f, 230f, false),
        FoodItem("kacang_rebus", "Kacang tanah rebus", "Minuman & camilan", "1 genggam (30 g)", 100, 5f, 4.5f, 7f, 1f, 2f, 3f, true),
    )

    val categories: List<String> = items.map { it.category }.distinct()

    fun byId(id: String?) = items.firstOrNull { it.id == id }

    fun search(query: String, category: String? = null): List<FoodItem> =
        items.filter { (category == null || it.category == category) && (query.isBlank() || it.name.contains(query.trim(), ignoreCase = true)) }

    /** Saran pengganti yang lebih ramah jantung dari kategori yang sama. */
    fun healthierAlternatives(item: FoodItem): List<FoodItem> =
        items.filter { it.category == item.category && it.heartFriendly && it.sodiumMg < item.sodiumMg }.take(3)
}

data class Recipe(val title: String, val items: List<String>, val note: String)

object Recipes {
    /** Ide menu sederhana & ramah jantung dari bahan lokal (saran umum). */
    val list = listOf(
        Recipe("Pepes ikan + nasi merah", listOf("ikan_kembung", "nasi_merah", "bayam_bening"), "Dikukus tanpa minyak; tambahkan banyak sayur."),
        Recipe("Sarapan telur & oat", listOf("oatmeal", "telur_rebus", "pisang"), "Tinggi serat, rendah garam."),
        Recipe("Pecel sayur tempe", listOf("gado_gado", "tempe_bacem", "nasi_merah"), "Kurangi bumbu kacang dan garam."),
        Recipe("Sop ayam bening", listOf("sop_sayur", "ayam_bakar", "nasi_putih"), "Pakai sedikit garam; kuah bening."),
    )
}
