package net.topvl.qrnmerge.merge

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.topvl.qrnmerge.R
import net.topvl.qrnmerge.util.FileNames
import java.util.concurrent.atomic.AtomicLong

data class MergeItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val isPdf: Boolean,
    val sizeBytes: Long,
    val pageCount: Int?,
) {
    fun toSource() = MergeSource(uri, name, isPdf)
}

sealed interface MergeStatus {
    data object Idle : MergeStatus
    data class Working(val done: Int, val total: Int) : MergeStatus
    data class Done(val result: MergeResult) : MergeStatus
    data class Failed(val message: String) : MergeStatus
}

class MergeViewModel(private val app: Application) : AndroidViewModel(app) {
    private val ids = AtomicLong(1)

    var items by mutableStateOf<List<MergeItem>>(emptyList())
        private set
    var outputName by mutableStateOf(FileNames.defaultPdfName())
    var pageSize by mutableStateOf(PageSizeMode.A4)
    var status by mutableStateOf<MergeStatus>(MergeStatus.Idle)
        private set
    var loading by mutableStateOf(false)
        private set

    val totalPages: Int get() = items.sumOf { if (it.isPdf) it.pageCount ?: 0 else 1 }

    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        loading = true
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) { uris.mapNotNull { describe(it) } }
            items = items + added
            loading = false
        }
    }

    /** Synchronous variant used by tests and the scan tab (already on a background thread). */
    fun addUrisBlocking(uris: List<Uri>) {
        val added = uris.mapNotNull { describe(it) }
        items = items + added
    }

    private fun describe(uri: Uri): MergeItem? {
        val resolver = app.contentResolver
        var name: String? = null
        var size = -1L
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                    ?.use { c ->
                        if (c.moveToFirst()) {
                            name = c.getString(0)
                            if (!c.isNull(1)) size = c.getLong(1)
                        }
                    }
            }
        } else if (uri.scheme == ContentResolver.SCHEME_FILE) {
            uri.path?.let { size = java.io.File(it).length() }
        }
        val displayName = name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        val mime = resolver.getType(uri)
        val isPdf = mime == "application/pdf" || displayName.endsWith(".pdf", ignoreCase = true)
        val isImage = mime?.startsWith("image/") == true ||
            displayName.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif")
        if (!isPdf && !isImage) return null
        val pages = if (isPdf) PdfInspector.pageCount(app, uri) else 1
        return MergeItem(ids.getAndIncrement(), uri, displayName, isPdf, size, pages)
    }

    fun move(fromId: Long, toId: Long) {
        val from = items.indexOfFirst { it.id == fromId }
        val to = items.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0 || from == to) return
        items = items.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun moveBy(id: Long, delta: Int) {
        val from = items.indexOfFirst { it.id == id }
        val to = (from + delta).coerceIn(0, items.lastIndex)
        if (from < 0 || from == to) return
        items = items.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun remove(id: Long) {
        items = items.filterNot { it.id == id }
    }

    fun clear() {
        items = emptyList()
        status = MergeStatus.Idle
    }

    fun dismissStatus() {
        status = MergeStatus.Idle
    }

    fun merge() {
        val snapshot = items
        if (snapshot.isEmpty() || status is MergeStatus.Working) return
        status = MergeStatus.Working(0, snapshot.size)
        val name = outputName
        val size = pageSize
        viewModelScope.launch {
            status = try {
                val result = withContext(Dispatchers.IO) {
                    MergeEngine.mergeAndSave(app, snapshot.map { it.toSource() }, name, size) { d, t ->
                        viewModelScope.launch { if (status is MergeStatus.Working) status = MergeStatus.Working(d, t) }
                    }
                }
                outputName = FileNames.defaultPdfName()
                MergeStatus.Done(result)
            } catch (e: MergeException) {
                MergeStatus.Failed(errorMessage(e))
            } catch (e: OutOfMemoryError) {
                MergeStatus.Failed(app.getString(R.string.err_memory))
            } catch (e: Exception) {
                MergeStatus.Failed(app.getString(R.string.err_generic, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun errorMessage(e: MergeException): String = when (e.reason) {
        MergeException.Reason.PASSWORD -> app.getString(R.string.err_password, e.fileName ?: "")
        MergeException.Reason.CORRUPT -> app.getString(R.string.err_corrupt, e.fileName ?: "")
        MergeException.Reason.IMAGE_DECODE -> app.getString(R.string.err_image, e.fileName ?: "")
        MergeException.Reason.EMPTY -> app.getString(R.string.err_empty)
        MergeException.Reason.IO -> app.getString(R.string.err_generic, e.cause?.message ?: e.fileName ?: "")
    }
}
