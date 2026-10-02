package id.sehati.app.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

fun qrBitmap(content: String, px: Int = 640): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
    val m = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, px, px, hints)
    val pixels = IntArray(px * px) { i -> if (m[i % px, i / px]) AColor.BLACK else AColor.WHITE }
    return Bitmap.createBitmap(pixels, px, px, Bitmap.Config.ARGB_8888)
}

/** QR identitas SEHATI. Isi hanya SEHATI ID + token opak; tidak ada NIK/alamat/data kesehatan. */
@Composable
fun QrImage(content: String, modifier: Modifier = Modifier, size: Dp = 240.dp) {
    val bmp = remember(content) { qrBitmap(content).asImageBitmap() }
    Image(
        bmp, contentDescription = null,
        modifier = modifier.size(size).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(10.dp)
            .semantics { contentDescription = "Kode QR identitas SEHATI" },
    )
}
