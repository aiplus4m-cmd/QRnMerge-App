package net.topvl.qrnmerge.merge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri

object PdfInspector {
    /** Page count, or null when the PDF cannot be opened by the platform renderer (e.g. encrypted). */
    fun pageCount(context: Context, uri: Uri): Int? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            PdfRenderer(fd).use { it.pageCount }
        }
    }.getOrNull()

    fun renderFirstPage(context: Context, uri: Uri, targetWidth: Int): Bitmap? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount == 0) null else renderer.openPage(0).use { page ->
                    val h = (targetWidth.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(targetWidth, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
    }.getOrNull()
}
