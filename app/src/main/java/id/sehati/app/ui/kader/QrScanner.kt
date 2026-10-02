package id.sehati.app.ui.kader

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import id.sehati.app.ui.components.*
import id.sehati.app.ui.theme.*
import java.util.concurrent.Executors

/** Pemindai QR offline: CameraX + zxing (tanpa Google Play Services). */
@Composable
fun QrScannerPanel(onResult: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    if (!granted) {
        Column(modifier.fillMaxWidth().testTag("scanner_permission"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoNote("Izin kamera diperlukan untuk memindai QR warga. Kamera hanya dipakai saat layar ini terbuka. Kamu juga dapat mencari dengan SEHATI ID.", icon = Icons.Rounded.CameraAlt)
            PrimaryButton("Izinkan kamera", { launcher.launch(Manifest.permission.CAMERA) }, tag = "grant_camera_button")
        }
        return
    }

    val lifecycle = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var done by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    AndroidView(
        modifier = modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(20.dp)).testTag("scanner_preview"),
        factory = { c ->
            val view = PreviewView(c)
            val providerFuture = ProcessCameraProvider.getInstance(c)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val reader = MultiFormatReader().apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))) }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { proxy -> decode(proxy, reader)?.let { text -> if (!done) { done = true; view.post { onResult(text); done = false } } } }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            }, ContextCompat.getMainExecutor(c))
            view
        },
    )
}

private fun decode(proxy: ImageProxy, reader: MultiFormatReader): String? = try {
    val plane = proxy.planes[0]
    val buffer = plane.buffer
    val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val rowStride = plane.rowStride
    val src = PlanarYUVLuminanceSource(data, rowStride, proxy.height, 0, 0, proxy.width.coerceAtMost(rowStride), proxy.height, false)
    reader.decodeWithState(BinaryBitmap(HybridBinarizer(src))).text
} catch (e: Exception) {
    null
} finally {
    reader.reset()
    proxy.close()
}
