package net.topvl.qrnmerge.merge

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import net.topvl.qrnmerge.util.BitmapLoader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.min

/** One input of the merge: a PDF document or an image that becomes one page. */
data class MergeSource(val uri: Uri, val name: String, val isPdf: Boolean)

enum class PageSizeMode { A4, FIT_IMAGE }

class MergeException(val fileName: String?, val reason: Reason, cause: Throwable? = null) :
    IOException("${reason.name}: ${fileName ?: ""} ${cause?.message ?: ""}".trim(), cause) {
    enum class Reason { PASSWORD, CORRUPT, IMAGE_DECODE, EMPTY, IO }
}

object PdfMerger {
    /** Longest side used when embedding photos (≈ A4 at 300 dpi). */
    const val IMAGE_MAX_SIDE = 3000
    private const val A4_W = 595.28f
    private const val A4_H = 841.89f

    /**
     * Merges [sources] in order into [output]. Returns the number of pages written.
     * PDFs keep their vector content (pages are copied, not rasterised).
     */
    fun merge(
        context: Context,
        sources: List<MergeSource>,
        output: File,
        pageSize: PageSizeMode = PageSizeMode.A4,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Int {
        if (sources.isEmpty()) throw MergeException(null, MergeException.Reason.EMPTY)
        PDFBoxResourceLoader.init(context.applicationContext)
        val opened = mutableListOf<PDDocument>()
        val dest = PDDocument(MemoryUsageSetting.setupTempFileOnly())
        try {
            val util = PDFMergerUtility()
            sources.forEachIndexed { index, src ->
                if (src.isPdf) {
                    val doc = openPdf(context, src)
                    opened += doc
                    try {
                        util.appendDocument(dest, doc)
                    } catch (e: IOException) {
                        throw MergeException(src.name, MergeException.Reason.CORRUPT, e)
                    }
                } else {
                    addImagePage(context, dest, src, pageSize)
                }
                onProgress(index + 1, sources.size)
            }
            val pages = dest.numberOfPages
            if (pages == 0) throw MergeException(null, MergeException.Reason.EMPTY)
            output.parentFile?.mkdirs()
            val tmp = File(output.parentFile, output.name + ".part")
            try {
                dest.save(tmp)
            } catch (e: IOException) {
                tmp.delete()
                throw MergeException(output.name, MergeException.Reason.IO, e)
            }
            if (output.exists()) output.delete()
            if (!tmp.renameTo(output)) {
                tmp.copyTo(output, overwrite = true)
                tmp.delete()
            }
            return pages
        } finally {
            runCatching { dest.close() }
            opened.forEach { runCatching { it.close() } }
        }
    }

    private fun openPdf(context: Context, src: MergeSource): PDDocument {
        val input = try {
            context.contentResolver.openInputStream(src.uri)
        } catch (e: Exception) {
            throw MergeException(src.name, MergeException.Reason.IO, e)
        } ?: throw MergeException(src.name, MergeException.Reason.IO)
        val doc = try {
            input.use { PDDocument.load(it, "", MemoryUsageSetting.setupTempFileOnly()) }
        } catch (e: InvalidPasswordException) {
            throw MergeException(src.name, MergeException.Reason.PASSWORD, e)
        } catch (e: IOException) {
            throw MergeException(src.name, MergeException.Reason.CORRUPT, e)
        }
        if (doc.isEncrypted) doc.setAllSecurityToBeRemoved(true)
        if (doc.numberOfPages == 0) {
            doc.close()
            throw MergeException(src.name, MergeException.Reason.CORRUPT)
        }
        return doc
    }

    private fun addImagePage(context: Context, dest: PDDocument, src: MergeSource, mode: PageSizeMode) {
        val bitmap = BitmapLoader.decode(context, src.uri, IMAGE_MAX_SIDE)
            ?: throw MergeException(src.name, MergeException.Reason.IMAGE_DECODE)
        val jpeg = ByteArrayOutputStream()
        val opaque = BitmapLoader.flattenOnWhite(bitmap)
        opaque.compress(Bitmap.CompressFormat.JPEG, 90, jpeg)
        val imgW = opaque.width.toFloat()
        val imgH = opaque.height.toFloat()
        if (opaque !== bitmap) opaque.recycle()
        bitmap.recycle()

        val image = JPEGFactory.createFromByteArray(dest, jpeg.toByteArray())
        val landscape = imgW > imgH
        val box = when (mode) {
            PageSizeMode.A4 -> if (landscape) PDRectangle(A4_H, A4_W) else PDRectangle(A4_W, A4_H)
            PageSizeMode.FIT_IMAGE -> {
                // Keep the image aspect ratio, longest side = A4 long side.
                val f = A4_H / maxOf(imgW, imgH)
                PDRectangle(imgW * f, imgH * f)
            }
        }
        val page = PDPage(box)
        dest.addPage(page)
        val scale = min(box.width / imgW, box.height / imgH)
        val w = imgW * scale
        val h = imgH * scale
        PDPageContentStream(dest, page).use { cs ->
            cs.drawImage(image, (box.width - w) / 2f, (box.height - h) / 2f, w, h)
        }
    }
}
