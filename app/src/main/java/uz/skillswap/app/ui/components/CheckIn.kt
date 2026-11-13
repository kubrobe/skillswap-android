package uz.skillswap.app.ui.components

import uz.skillswap.app.ui.i18n.LocalStrings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.common.MlKitException
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import androidx.compose.runtime.LaunchedEffect
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import uz.skillswap.app.util.checkinQrText
import uz.skillswap.app.util.parseCheckinQr

/** A QR code drawn square by square on a Canvas (no bitmap needed). */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 1, 1) }
    Canvas(modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color.White)) {
        val n = matrix.width
        val cell = size.width / n
        for (y in 0 until n) {
            for (x in 0 until n) {
                if (matrix.get(x, y)) {
                    drawRect(Color.Black, topLeft = Offset(x * cell, y * cell), size = Size(cell + 0.6f, cell + 0.6f))
                }
            }
        }
    }
}

/** TEACHER: shows the check-in QR and the 4-digit code for one booked lesson. */
@Composable
fun TeacherCheckInDialog(bookingId: Long, code: String, skillName: String, learnerName: String, onDismiss: () -> Unit) {
    val tr = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr.checkinTitle(skillName)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()) {
                QrCode(checkinQrText(bookingId, code), Modifier.width(220.dp))
                Text(code.toCharArray().joinToString(" "), fontSize = 34.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                Text(tr.showThisTo(learnerName.substringBefore(' ')),
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr.close) } }
    )
}

/**
 * LEARNER: finish the lesson by scanning the teacher's QR (Google code scanner) or typing the code.
 * onSubmit(code, done): done(null) = success, done("message") = show the error.
 */
@Composable
fun LearnerCheckInDialog(
    bookingId: Long, skillName: String, onDismiss: () -> Unit,
    onSubmit: (String, (String?) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val tr = LocalStrings.current

    fun send(value: String) {
        busy = true
        error = null
        onSubmit(value) { err ->
            busy = false
            error = err
        }
    }

    // Google's scanner is not inside our app: Google Play services downloads it the first time it is needed.
    // For an app not installed from the Play Store nobody asked for it, so the first "Scan" failed and the
    // camera never opened. Ask for the download as soon as this dialog opens, so it is ready by the tap.
    val scanner = remember {
        GmsBarcodeScanning.getClient(context, GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }
    fun requestScannerDownload() {
        ModuleInstall.getClient(context).installModules(ModuleInstallRequest.newBuilder().addApi(scanner).build())
    }
    LaunchedEffect(Unit) { requestScannerDownload() }

    fun scan() {
        error = null
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val found = parseCheckinQr(barcode.rawValue ?: "", bookingId)
                if (found != null) {
                    code = found
                    send(found)
                } else {
                    error = tr.notThisQr
                }
            }
            .addOnFailureListener { e ->
                if (e is MlKitException && e.errorCode == MlKitException.UNAVAILABLE) {
                    // still downloading: ask again and tell the learner to wait a moment
                    requestScannerDownload()
                    error = tr.scannerDownloading
                } else {
                    error = tr.scannerUnavailable + (e.message?.let { " ($it)" } ?: "")
                }
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr.checkinTitle(skillName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr.askTeacherQr,
                    style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { scan() }, enabled = !busy, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)) { IconText(tr.scanQr) }
                Text(tr.orTypeCode, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
                OutlinedTextField(
                    value = code,
                    onValueChange = { v -> code = v.filter { it.isDigit() }.take(4) },
                    label = { Text(tr.code4) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { send(code) }, enabled = code.length == 4 && !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(tr.confirm)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr.close) } }
    )
}
