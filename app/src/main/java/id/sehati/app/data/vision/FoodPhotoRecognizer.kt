package id.sehati.app.data.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import id.sehati.app.domain.rules.FoodLabelMatcher
import id.sehati.app.domain.rules.FoodSuggestion
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Pengenalan foto makanan 100% di perangkat (model ML Kit bawaan). Foto tidak disimpan dan tidak dikirim ke server.
 * Hasilnya hanya saran; pengguna selalu memilih dan mengoreksi sendiri.
 */
object FoodPhotoRecognizer {
    suspend fun suggest(bitmap: Bitmap): List<FoodSuggestion> = suspendCancellableCoroutine { cont ->
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.4f).build())
        labeler.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { labels ->
                labeler.close()
                if (cont.isActive) cont.resume(FoodLabelMatcher.suggest(labels.map { it.text to it.confidence }))
            }
            .addOnFailureListener {
                labeler.close()
                if (cont.isActive) cont.resume(emptyList())
            }
        cont.invokeOnCancellation { runCatching { labeler.close() } }
    }
}
