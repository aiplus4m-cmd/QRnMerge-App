package net.topvl.qrnmerge.scan

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.topvl.qrnmerge.R
import net.topvl.qrnmerge.merge.MergeEngine
import net.topvl.qrnmerge.merge.MergeSource
import net.topvl.qrnmerge.merge.PageSizeMode
import net.topvl.qrnmerge.util.BitmapLoader
import net.topvl.qrnmerge.util.FileNames
import net.topvl.qrnmerge.util.MediaSaver
import net.topvl.qrnmerge.util.SavedFile
import java.io.File

data class ScanPage(val id: Long, val file: File, val rotation: Int = 0)

/** Renders scan pages with the current enhancement settings (cached source bitmaps). */
object PageRenderer {
    private val sourceCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun render(context: Context, page: ScanPage, settings: EnhanceSettings, maxSide: Int): Bitmap? {
        val key = "${page.file.path}#${page.rotation}#$maxSide"
        val source = sourceCache.get(key) ?: BitmapLoader.decode(
            context, Uri.fromFile(page.file), maxSide, page.rotation,
        )?.also { sourceCache.put(key, it) } ?: return null
        val px = BitmapLoader.pixels(source)
        val out = ImageEnhancer.process(px, source.width, source.height, settings)
        return BitmapLoader.fromPixels(out, source.width, source.height)
    }

    /** Full-resolution render written as JPEG (not cached, to keep memory low). */
    fun renderToJpeg(context: Context, page: ScanPage, settings: EnhanceSettings, target: File) {
        val src = BitmapLoader.decode(context, Uri.fromFile(page.file), EXPORT_MAX_SIDE, page.rotation)
            ?: throw java.io.IOException("Cannot decode ${page.file.name}")
        val px = BitmapLoader.pixels(src)
        val w = src.width
        val h = src.height
        src.recycle()
        val out = BitmapLoader.fromPixels(ImageEnhancer.process(px, w, h, settings), w, h)
        BitmapLoader.writeJpeg(out, target, 92)
        out.recycle()
    }

    fun evict(page: ScanPage) {
        sourceCache.snapshot().keys.filter { it.startsWith(page.file.path + "#") }.forEach { sourceCache.remove(it) }
    }

    const val EXPORT_MAX_SIDE = 3000
}

class ScanViewModel(private val app: Application) : AndroidViewModel(app) {
    private val scanDir = File(app.filesDir, "scans").apply { mkdirs() }
    private val exportDir = File(app.cacheDir, "scan_export")

    var pages by mutableStateOf(loadExisting())
        private set
    var settings by mutableStateOf(EnhanceSettings())
    var busyMessage by mutableStateOf<String?>(null)
        private set
    /** One-shot message (snackbar) */
    var message by mutableStateOf<String?>(null)
    var lastSaved by mutableStateOf<List<SavedFile>>(emptyList())
        private set

    private fun loadExisting(): List<ScanPage> =
        scanDir.listFiles { f -> f.extension == "jpg" }
            ?.sortedBy { it.name }
            ?.map { ScanPage(it.nameWithoutExtension.toLongOrNull() ?: it.lastModified(), it) }
            ?: emptyList()

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        busyMessage = app.getString(R.string.scan_importing)
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) {
                var next = System.currentTimeMillis()
                uris.mapNotNull { uri ->
                    val id = next++
                    val target = File(scanDir, "%016d.jpg".format(id))
                    runCatching {
                        // Normalise: decode (EXIF aware, max 3000px) and store as JPEG.
                        val bmp = BitmapLoader.decode(app, uri, PageRenderer.EXPORT_MAX_SIDE) ?: return@runCatching null
                        BitmapLoader.writeJpeg(bmp, target, 95)
                        bmp.recycle()
                        ScanPage(id, target)
                    }.getOrNull()
                }
            }
            pages = pages + added
            busyMessage = null
            if (added.size < uris.size) message = app.getString(R.string.scan_import_failed)
        }
    }

    fun rotate(page: ScanPage) {
        PageRenderer.evict(page)
        pages = pages.map { if (it.id == page.id) it.copy(rotation = (it.rotation + 90) % 360) else it }
    }

    fun remove(page: ScanPage) {
        PageRenderer.evict(page)
        page.file.delete()
        pages = pages.filterNot { it.id == page.id }
    }

    fun clear() {
        pages.forEach { PageRenderer.evict(it); it.file.delete() }
        pages = emptyList()
    }

    private fun exportJpegs(): List<File> {
        exportDir.deleteRecursively()
        exportDir.mkdirs()
        val stamp = FileNames.timestamp()
        return pages.mapIndexed { i, page ->
            val f = File(exportDir, "Scan_${stamp}_${i + 1}.jpg")
            PageRenderer.renderToJpeg(app, page, settings, f)
            f
        }
    }

    fun saveImages() = runBusy(R.string.scan_saving) {
        val saved = exportJpegs().map { MediaSaver.saveJpeg(app, it, it.name) }
        lastSaved = saved
        app.getString(R.string.scan_saved_images, saved.size, "Pictures/${MediaSaver.FOLDER}")
    }

    fun savePdf(pageSize: PageSizeMode = PageSizeMode.A4) = runBusy(R.string.scan_saving) {
        val files = exportJpegs()
        val result = MergeEngine.mergeAndSave(
            app,
            files.map { MergeSource(Uri.fromFile(it), it.name, isPdf = false) },
            FileNames.defaultPdfName("Scan"),
            pageSize,
        )
        lastSaved = listOf(result.saved)
        app.getString(R.string.scan_saved_pdf, result.saved.displayName, result.saved.folder)
    }

    /** Renders enhanced pages into files the Merger tab can consume. */
    fun exportForMerge(onReady: (List<Uri>) -> Unit) {
        if (pages.isEmpty()) return
        busyMessage = app.getString(R.string.scan_preparing)
        viewModelScope.launch {
            val uris = runCatching {
                withContext(Dispatchers.IO) {
                    val dir = File(app.filesDir, "merge_inputs").apply { mkdirs() }
                    val old = System.currentTimeMillis() - 3L * 24 * 3600 * 1000
                    dir.listFiles()?.filter { it.lastModified() < old }?.forEach { it.delete() }
                    val stamp = FileNames.timestamp()
                    pages.mapIndexed { i, page ->
                        val f = File(dir, "Scan_${stamp}_${i + 1}.jpg")
                        PageRenderer.renderToJpeg(app, page, settings, f)
                        Uri.fromFile(f)
                    }
                }
            }
            busyMessage = null
            uris.onSuccess(onReady).onFailure {
                message = app.getString(R.string.err_generic, it.message ?: "")
            }
        }
    }

    private fun runBusy(label: Int, block: () -> String) {
        if (pages.isEmpty() || busyMessage != null) return
        lastSaved = emptyList()
        busyMessage = app.getString(label)
        viewModelScope.launch {
            message = try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: OutOfMemoryError) {
                app.getString(R.string.err_memory)
            } catch (e: Exception) {
                app.getString(R.string.err_generic, e.message ?: e.javaClass.simpleName)
            }
            busyMessage = null
        }
    }
}
