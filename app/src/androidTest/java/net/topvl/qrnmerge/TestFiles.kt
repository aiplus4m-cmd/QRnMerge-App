package net.topvl.qrnmerge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import net.topvl.qrnmerge.util.SavedFile
import java.io.File
import java.io.FileOutputStream

/** Builds fixture PDFs / images in the app cache and exposes them as content:// URIs (like a picker would). */
class TestFiles(val context: Context) {
    val dir = File(context.cacheDir, "test_fixtures").apply { deleteRecursively(); mkdirs() }

    fun uri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun pdf(name: String, pages: Int, width: Int, height: Int): File {
        val doc = PdfDocument()
        val paint = Paint().apply { color = Color.BLACK; textSize = 24f }
        repeat(pages) { i ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, i + 1).create())
            page.canvas.drawText("$name page ${i + 1}", 20f, 40f, paint)
            page.canvas.drawRect(20f, 60f, width - 20f, 80f, paint)
            doc.finishPage(page)
        }
        val f = File(dir, name)
        FileOutputStream(f).use { doc.writeTo(it) }
        doc.close()
        return f
    }

    fun image(name: String, width: Int, height: Int, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, alpha: Boolean = false): File {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(if (alpha) Color.TRANSPARENT else Color.rgb(235, 232, 225))
        val p = Paint().apply { color = Color.rgb(20, 20, 20) }
        var y = height / 10f
        while (y < height * 0.9f) {
            c.drawRect(width * 0.1f, y, width * 0.9f, y + height / 60f, p)
            y += height / 25f
        }
        val f = File(dir, name)
        FileOutputStream(f).use { bmp.compress(format, 90, it) }
        bmp.recycle()
        return f
    }

    fun encryptedPdf(name: String, userPassword: String): File {
        PDFBoxResourceLoader.init(context)
        val f = File(dir, name)
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            doc.addPage(PDPage())
            val policy = StandardProtectionPolicy("owner-pass", userPassword, AccessPermission())
            policy.encryptionKeyLength = 128
            doc.protect(policy)
            doc.save(f)
        }
        return f
    }

    fun garbage(name: String): File = File(dir, name).apply { writeBytes(ByteArray(4096) { (it * 31 % 251).toByte() }) }

    companion object {
        fun grantLegacyStorage() {
            if (Build.VERSION.SDK_INT < 29) {
                val inst = InstrumentationRegistry.getInstrumentation()
                inst.uiAutomation.executeShellCommand(
                    "pm grant ${inst.targetContext.packageName} android.permission.WRITE_EXTERNAL_STORAGE",
                ).close()
                Thread.sleep(500)
            }
        }

        /** Page sizes (in PDF points) of a saved document, read back through the platform renderer. */
        fun pageSizes(context: Context, uri: Uri): List<Pair<Int, Int>> {
            val fd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")!!
            return fd.use {
                PdfRenderer(it).use { r ->
                    (0 until r.pageCount).map { i -> r.openPage(i).use { p -> p.width to p.height } }
                }
            }
        }

        fun readAll(context: Context, uri: Uri): ByteArray =
            context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }

        fun delete(context: Context, saved: SavedFile) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 29) context.contentResolver.delete(saved.uri, null, null)
                else {
                    @Suppress("DEPRECATION")
                    val dir = android.os.Environment.getExternalStoragePublicDirectory(saved.folder.substringBefore('/'))
                    File(File(dir, "QRnMerge"), saved.displayName).delete()
                }
            }
        }

        /** Looks the file up in MediaStore the way a file manager would (Android 10+). */
        fun existsInDownloads(context: Context, displayName: String): Boolean {
            if (Build.VERSION.SDK_INT < 29) {
                @Suppress("DEPRECATION")
                val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                return File(File(dir, "QRnMerge"), displayName).let { it.exists() && it.length() > 0 }
            }
            val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.IS_PENDING)
            context.contentResolver.query(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), projection,
                "${MediaStore.MediaColumns.DISPLAY_NAME}=?", arrayOf(displayName), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getString(1).trimEnd('/') == "Download/QRnMerge" && c.getInt(2) == 0) return true
                }
            }
            return false
        }
    }
}
