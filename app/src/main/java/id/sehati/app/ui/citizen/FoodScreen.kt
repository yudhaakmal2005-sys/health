package id.sehati.app.ui.citizen

import android.Manifest
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import id.sehati.app.data.vision.FoodPhotoRecognizer
import id.sehati.app.domain.rules.FoodSuggestion
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.sehati.app.core.util.NumberFmt
import id.sehati.app.domain.content.FoodCatalog
import id.sehati.app.domain.content.Recipes
import id.sehati.app.domain.model.MealCategory
import id.sehati.app.domain.rules.FoodItem
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodScreen(vm: FoodViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var addFor by remember { mutableStateOf<MealCategory?>(null) }

    ScreenColumn(Modifier.testTag("food_screen")) {
        ScreenHeader("Makanan", "Heart Food · catatan makan hari ini")
        SehatiCard(Modifier.staggerIn(0)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(if (s.kcalNeed == 0) 0f else s.totals.kcal.toFloat() / s.kcalNeed, size = 100.dp, stroke = 8.dp, color = Wellness, track = SurfaceMuted) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedNumber(s.totals.kcal, MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), format = { NumberFmt.thousands(it) })
                        Text("kkal", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Energi hari ini", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    if (s.kcalNeed > 0) Text("Perkiraan kebutuhan ±${NumberFmt.thousands(s.kcalNeed)} kkal", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    Text("Kualitas makanan ${if (s.entries.isEmpty()) "–" else "${s.quality}/100"}", style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = TextSecondary, modifier = Modifier.testTag("food_quality_label"))
                }
            }
            BarRow("Karbohidrat", s.totals.carbs.roundToInt(), maxOf(s.totals.carbs.roundToInt(), 1), TextSecondary, valueLabel = "${s.totals.carbs.roundToInt()} g")
            BarRow("Protein", s.totals.protein.roundToInt(), maxOf(s.totals.protein.roundToInt(), 1), TextSecondary, valueLabel = "${s.totals.protein.roundToInt()} g")
            BarRow("Lemak", s.totals.fat.roundToInt(), maxOf(s.totals.fat.roundToInt(), 1), TextSecondary, valueLabel = "${s.totals.fat.roundToInt()} g")
            BarRow("Natrium", s.totals.sodiumMg.roundToInt(), s.targets.sodiumMgLimit, if (s.totals.sodiumMg > s.targets.sodiumMgLimit) RiskRed else Wellness, valueLabel = "${s.totals.sodiumMg.roundToInt()} mg")
            BarRow("Gula", s.totals.sugar.roundToInt(), s.targets.sugarGramsLimit, if (s.totals.sugar > s.targets.sugarGramsLimit) RiskRed else Wellness, valueLabel = "${s.totals.sugar.roundToInt()} g")
            Text("Batas natrium ${NumberFmt.thousands(s.targets.sodiumMgLimit)} mg dan gula ${s.targets.sugarGramsLimit} g adalah target edukasi yang dapat diubah di Profil, bukan diagnosis.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }

        SectionTitle("Catatan makan")
        SehatiCard(Modifier.staggerIn(1), contentPadding = 0) {
        Column {
        MealCategory.entries.forEachIndexed { i, meal ->
            val list = s.entries.filter { it.meal == meal.name }
            if (i > 0) HorizontalDivider(color = SurfaceMuted, modifier = Modifier.padding(horizontal = 14.dp))
            Column(Modifier.fillMaxWidth().testTag("meal_${meal.name}").padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(meal.label, style = MaterialTheme.typography.titleSmall, color = TextPrimary, modifier = Modifier.weight(1f))
                    Text("${list.sumOf { it.kcal }} kkal", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = TextMuted)
                    IconButton({ addFor = meal }, Modifier.size(48.dp).testTag("add_food_${meal.name}")) { Icon(Icons.Outlined.Add, "Tambah makanan ${meal.label}", tint = PrimaryDark, modifier = Modifier.size(22.dp)) }
                }
                if (list.isEmpty()) Text("Belum ada catatan", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                list.forEach { e ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text("${trim(e.portions)}× ${e.portionLabel} · ${e.kcal} kkal · Na ${e.sodiumMg.roundToInt()} mg", style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), color = TextMuted)
                        }
                        IconButton({ vm.delete(e) }, Modifier.size(48.dp)) { Icon(Icons.Outlined.DeleteOutline, "Hapus ${e.name}", tint = TextMuted, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        }
        }
        }

        InfoNote(FoodCatalog.ESTIMATE_NOTE, icon = Icons.Outlined.Info, color = TextSecondary, bg = SurfaceMuted)
        SectionTitle("Ide menu sehat")
        if (Recipes.list.isNotEmpty()) SehatiCard(contentPadding = 0) {
            Column {
                Recipes.list.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider(color = SurfaceMuted, modifier = Modifier.padding(horizontal = 14.dp))
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(r.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(r.items.mapNotNull { FoodCatalog.byId(it)?.name }.joinToString(" + "), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text(r.note, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    addFor?.let { meal ->
        ModalBottomSheet(onDismissRequest = { addFor = null }, containerColor = CardWhite, modifier = Modifier.testTag("add_food_sheet")) {
            AddFoodSheet(meal, onAdd = { item, p -> vm.add(meal, item, p); addFor = null })
        }
    }
}

private fun trim(f: Float): String = if (f % 1f == 0f) f.toInt().toString() else f.toString()

@Composable
private fun AddFoodSheet(meal: MealCategory, onAdd: (FoodItem, Float) -> Unit) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<FoodItem?>(null) }
    var portions by remember { mutableFloatStateOf(1f) }
    val results = remember(query, category) { FoodCatalog.search(query, category) }

    Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tambah ${meal.label.lowercase()}", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        val p = picked
        if (p == null) {
            PhotoScanRow(onPick = { picked = it; portions = 1f })
            SehatiTextField(query, { query = it }, "Cari makanan", tag = "food_search_field")
            ChoiceChips(listOf<String?>(null) + FoodCatalog.categories, category, { category = it }, { it ?: "Semua" }, tagPrefix = "foodcat")
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(results, key = { it.id }) { f ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { picked = f; portions = 1f }.testTag("food_item_${f.id}"), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                            Text("${f.portion} · ${f.kcal} kkal", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                        if (f.heartFriendly) StatusPill("Ramah jantung", RiskGreenText, RiskGreenBg, Icons.Outlined.Favorite)
                    }
                }
            }
        } else {
            Text(p.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(p.portion, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            NumberStepper("Porsi", (portions * 2).toInt(), { portions = it / 2f }, 1..12, tag = "portion", display = { trim(it / 2f) + " porsi" })
            Text("≈ ${(p.kcal * portions).roundToInt()} kkal · natrium ${(p.sodiumMg * portions).roundToInt()} mg · gula ${"%.1f".format(p.sugar * portions)} g", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            if (!p.heartFriendly) {
                val alt = FoodCatalog.healthierAlternatives(p)
                if (alt.isNotEmpty()) InfoNote("Alternatif lebih ramah jantung: ${alt.joinToString { it.name }}", icon = Icons.Outlined.Lightbulb, color = WellnessDark, bg = WellnessLight)
            }
            InfoNote(FoodCatalog.ESTIMATE_NOTE, color = TextSecondary, bg = SurfaceMuted)
            PrimaryButton("Tambahkan", { onAdd(p, portions) }, tag = "confirm_add_food_button")
            TextButton({ picked = null }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Pilih makanan lain", color = TextSecondary) }
        }
    }
}

/** Pindai foto makanan: ambil foto atau pilih dari galeri, lalu pilih saran yang cocok. Foto diproses di perangkat saja. */
@Composable
private fun PhotoScanRow(onPick: (FoodItem) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<FoodSuggestion>?>(null) }

    fun analyze(bmp: Bitmap?) {
        if (bmp == null) return
        busy = true
        scope.launch { suggestions = FoodPhotoRecognizer.suggest(bmp); busy = false }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { analyze(it) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) camera.launch(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let {
            analyze(runCatching { ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, it)) { d, _, _ -> d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE; d.setTargetSampleSize(2) } }.getOrNull())
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Foto makanan", {
                val granted = androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (granted) camera.launch(null) else permission.launch(Manifest.permission.CAMERA)
            }, Modifier.weight(1f), icon = Icons.Outlined.PhotoCamera, tag = "scan_food_camera")
            SecondaryButton("Dari galeri", { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f), icon = Icons.Outlined.Image, tag = "scan_food_gallery")
        }
        if (busy) Text("Mengenali makanan…", style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.shimmer())
        suggestions?.let { list ->
            if (list.isEmpty()) InfoNote("Makanan belum dikenali. Coba foto lebih dekat atau cari manual di bawah.", icon = Icons.Outlined.SearchOff, color = TextSecondary, bg = SurfaceMuted)
            else {
                Text("Mungkin ini makananmu (ketuk untuk memilih):", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                list.forEach { sg -> AssistChip({ onPick(sg.item) }, { Text(sg.item.name, color = TextPrimary) }, Modifier.heightIn(min = 48.dp).testTag("scan_suggestion_${sg.item.id}"), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Hairline)) }
            }
        }
        Text("Foto diproses di ponselmu, tidak disimpan dan tidak dikirim. Hasil hanya saran, silakan periksa.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}
