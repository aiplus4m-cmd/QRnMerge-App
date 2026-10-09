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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ScanPage(
    val id: Long,
    val file: File,
    val rotation: Int = 0,
    /** Already exported as JPG / included in a saved PDF – shown as badges to avoid duplicates. */
    val savedAsImage: Boolean = false,
    val savedInPdf: Boolean = false,
)

enum class SaveFormat { IMAGES, PDF }

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
    private val manifest = File(scanDir, "pages.json")
    private val exportDir = File(app.cacheDir, "scan_export")
    private val prefs = app.getSharedPreferences("scan_prefs", Context.MODE_PRIVATE)

    var pages by mutableStateOf(loadPages())
        private set
    var selected by mutableStateOf<Set<Long>>(emptySet())
        private set
    var settings by mutableStateOf(loadSettings())
        private set
    var busyMessage by mutableStateOf<String?>(null)
        private set
    /** One-shot message (snackbar) */
    var message by mutableStateOf<String?>(null)
    var lastSaved by mutableStateOf<List<SavedFile>>(emptyList())
        private set

    val selectedPages: List<ScanPage> get() = pages.filter { it.id in selected }

    // ---- persistence -------------------------------------------------------------------------

    private fun loadSettings(): EnhanceSettings {
        val mode = runCatching { EnhanceMode.valueOf(prefs.getString("mode", null) ?: "") }.getOrDefault(EnhanceMode.AUTO)
        return EnhanceSettings(mode, prefs.getFloat("sharpness", 0.6f), prefs.getFloat("contrast", 0.5f))
    }

    fun updateSettings(value: EnhanceSettings) {
        settings = value
        prefs.edit().putString("mode", value.mode.name).putFloat("sharpness", value.sharpness)
            .putFloat("contrast", value.contrast).apply()
    }

    private fun loadPages(): List<ScanPage> {
        val files = scanDir.listFiles { f -> f.extension == "jpg" }?.associateBy { it.name } ?: emptyMap()
        val fromManifest = runCatching {
            val arr = JSONArray(manifest.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                files[o.getString("file")]?.let { f ->
                    ScanPage(
                        id = o.getLong("id"), file = f, rotation = o.optInt("rotation", 0),
                        savedAsImage = o.optBoolean("savedImage", false), savedInPdf = o.optBoolean("savedPdf", false),
                    )
                }
            }.filterNotNull()
        }.getOrDefault(emptyList())
        val known = fromManifest.map { it.file.name }.toSet()
        val orphans = files.values.filter { it.name !in known }.sortedByDescending { it.name }
            .map { ScanPage(it.nameWithoutExtension.toLongOrNull() ?: it.lastModified(), it) }
        return orphans + fromManifest
    }

    private fun commitPages(value: List<ScanPage>) {
        pages = value
        selected = selected.intersect(value.map { it.id }.toSet())
        runCatching {
            val arr = JSONArray()
            value.forEach { p ->
                arr.put(
                    JSONObject().put("id", p.id).put("file", p.file.name).put("rotation", p.rotation)
                        .put("savedImage", p.savedAsImage).put("savedPdf", p.savedInPdf),
                )
            }
            val tmp = File(scanDir, "pages.json.tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(manifest)
        }
    }

    // ---- page management ---------------------------------------------------------------------

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        busyMessage = app.getString(R.string.scan_importing)
        viewModelScope.launch {
            val failed = importImages(uris)
            busyMessage = null
            if (failed > 0) message = app.getString(R.string.scan_import_failed)
        }
    }

    /** Copies images into app storage as new pages (selected by default). Returns the number of failures. */
    suspend fun importImages(uris: List<Uri>): Int {
        val added = withContext(Dispatchers.IO) {
            var next = maxOf(System.currentTimeMillis(), (pages.maxOfOrNull { it.id } ?: 0) + 1)
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
        // Newest scan batch first; pages inside one batch keep their scan order.
        commitPages(added + pages)
        selected = selected + added.map { it.id }
        return uris.size - added.size
    }

    fun rotate(page: ScanPage) {
        PageRenderer.evict(page)
        commitPages(pages.map { if (it.id == page.id) it.copy(rotation = (it.rotation + 90) % 360) else it })
    }

    fun move(page: ScanPage, delta: Int) {
        val from = pages.indexOfFirst { it.id == page.id }
        val to = (from + delta).coerceIn(0, pages.lastIndex)
        if (from < 0 || from == to) return
        commitPages(pages.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun remove(page: ScanPage) = removeAll(setOf(page.id))

    fun removeSelected() = removeAll(selected)

    private fun removeAll(ids: Set<Long>) {
        val (gone, keep) = pages.partition { it.id in ids }
        gone.forEach { PageRenderer.evict(it); it.file.delete() }
        commitPages(keep)
    }

    fun clear() = removeAll(pages.map { it.id }.toSet())

    fun toggleSelected(page: ScanPage) {
        selected = if (page.id in selected) selected - page.id else selected + page.id
    }

    fun selectAll(all: Boolean) {
        selected = if (all) pages.map { it.id }.toSet() else emptySet()
    }

    // ---- export ------------------------------------------------------------------------------

    private fun exportJpegs(targets: List<ScanPage>): List<File> {
        exportDir.deleteRecursively()
        exportDir.mkdirs()
        val stamp = FileNames.timestamp()
        return targets.mapIndexed { i, page ->
            val f = File(exportDir, "Scan_${stamp}_${i + 1}.jpg")
            PageRenderer.renderToJpeg(app, page, settings, f)
            f
        }
    }

    /** Saves the selected pages; afterwards they are marked as saved and deselected. */
    fun saveSelected(format: SaveFormat, pdfName: String, pageSize: PageSizeMode = PageSizeMode.A4) {
        if (selected.isEmpty() || busyMessage != null) return
        lastSaved = emptyList()
        busyMessage = app.getString(R.string.scan_saving)
        viewModelScope.launch {
            message = try {
                val saved = save(format, pdfName, pageSize)
                if (format == SaveFormat.IMAGES) app.getString(R.string.scan_saved_images, saved.size, saved.first().folder)
                else app.getString(R.string.scan_saved_pdf, saved.first().displayName, saved.first().folder)
            } catch (e: OutOfMemoryError) {
                app.getString(R.string.err_memory)
            } catch (e: Exception) {
                app.getString(R.string.err_generic, e.message ?: e.javaClass.simpleName)
            }
            busyMessage = null
        }
    }

    suspend fun save(format: SaveFormat, pdfName: String, pageSize: PageSizeMode = PageSizeMode.A4): List<SavedFile> {
        val targets = selectedPages
        require(targets.isNotEmpty()) { "no pages selected" }
        val saved = withContext(Dispatchers.IO) {
            val files = exportJpegs(targets)
            when (format) {
                SaveFormat.IMAGES -> files.map { MediaSaver.saveJpeg(app, it, it.name) }
                SaveFormat.PDF -> listOf(
                    MergeEngine.mergeAndSave(
                        app,
                        files.map { MergeSource(Uri.fromFile(it), it.name, isPdf = false) },
                        pdfName.ifBlank { FileNames.defaultPdfName("Scan") },
                        pageSize,
                    ).saved,
                )
            }
        }
        val ids = targets.map { it.id }.toSet()
        commitPages(pages.map {
            if (it.id !in ids) it
            else if (format == SaveFormat.IMAGES) it.copy(savedAsImage = true) else it.copy(savedInPdf = true)
        })
        selected = emptySet()
        lastSaved = saved
        return saved
    }

    /** Renders the selected pages into files the Merger tab can consume. */
    fun exportForMerge(onReady: (List<Uri>) -> Unit) {
        val targets = selectedPages
        if (targets.isEmpty() || busyMessage != null) return
        busyMessage = app.getString(R.string.scan_preparing)
        viewModelScope.launch {
            val uris = runCatching {
                withContext(Dispatchers.IO) {
                    val dir = File(app.filesDir, "merge_inputs").apply { mkdirs() }
                    val old = System.currentTimeMillis() - 3L * 24 * 3600 * 1000
                    dir.listFiles()?.filter { it.lastModified() < old }?.forEach { it.delete() }
                    val stamp = FileNames.timestamp()
                    targets.mapIndexed { i, page ->
                        val f = File(dir, "Scan_${stamp}_${i + 1}.jpg")
                        PageRenderer.renderToJpeg(app, page, settings, f)
                        Uri.fromFile(f)
                    }
                }
            }
            busyMessage = null
            uris.onSuccess {
                selected = emptySet()
                onReady(it)
            }.onFailure {
                message = app.getString(R.string.err_generic, it.message ?: "")
            }
        }
    }
}
