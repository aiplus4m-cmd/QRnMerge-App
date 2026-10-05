package net.topvl.qrnmerge.qr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.tasks.await

sealed interface QrScanOutcome {
    data class Found(val value: String) : QrScanOutcome
    data object Cancelled : QrScanOutcome
    data object NotFound : QrScanOutcome
    data class Error(val message: String) : QrScanOutcome
}

object QrScanner {
    /** Google code scanner: camera UI provided by Play services, no CAMERA permission needed. */
    suspend fun scanWithCamera(context: Context): QrScanOutcome = try {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .enableAutoZoom()
            .build()
        val barcode = GmsBarcodeScanning.getClient(context, options).startScan().await()
        barcode.rawValue?.let { QrScanOutcome.Found(it) } ?: QrScanOutcome.NotFound
    } catch (e: kotlinx.coroutines.CancellationException) {
        // Task cancelled by the user (back pressed) surfaces as CancellationException.
        QrScanOutcome.Cancelled
    } catch (e: Exception) {
        if (e.message?.contains("cancel", ignoreCase = true) == true) QrScanOutcome.Cancelled
        else QrScanOutcome.Error(e.message ?: e.javaClass.simpleName)
    }

    suspend fun scanImage(context: Context, uri: Uri): QrScanOutcome = try {
        val image = InputImage.fromFilePath(context, uri)
        val client = BarcodeScanning.getClient()
        val codes = client.process(image).await()
        client.close()
        codes.firstNotNullOfOrNull { it.rawValue }?.let { QrScanOutcome.Found(it) } ?: QrScanOutcome.NotFound
    } catch (e: Exception) {
        QrScanOutcome.Error(e.message ?: e.javaClass.simpleName)
    }
}
