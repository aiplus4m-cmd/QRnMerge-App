package net.topvl.qrnmerge.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/** A file written to shared storage, visible to the user in Files / Gallery. */
data class SavedFile(
    val uri: Uri,
    val displayName: String,
    /** Human readable folder, e.g. "Download/Scan2PDF". */
    val folder: String,
    val mimeType: String,
    val sizeBytes: Long,
)

object MediaSaver {
    const val FOLDER = "Scan2PDF"
    const val MIME_PDF = "application/pdf"
    const val MIME_JPEG = "image/jpeg"

    /** Android 9 and older need WRITE_EXTERNAL_STORAGE to write into public folders. */
    fun needsLegacyPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    fun savePdf(context: Context, source: File, displayName: String): SavedFile =
        save(context, source, displayName, MIME_PDF, Environment.DIRECTORY_DOWNLOADS)

    fun saveJpeg(context: Context, source: File, displayName: String): SavedFile =
        save(context, source, displayName, MIME_JPEG, Environment.DIRECTORY_PICTURES)

    private fun save(context: Context, source: File, displayName: String, mime: String, dirType: String): SavedFile {
        if (!source.exists() || source.length() == 0L) throw IOException("Source file is empty: ${source.name}")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveScoped(context, source, displayName, mime, dirType)
        } else {
            saveLegacy(context, source, displayName, mime, dirType)
        }
    }

    @Suppress("NewApi")
    private fun saveScoped(context: Context, source: File, displayName: String, mime: String, dirType: String): SavedFile {
        val resolver = context.contentResolver
        val collection = if (dirType == Environment.DIRECTORY_DOWNLOADS) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val relativePath = "$dirType/$FOLDER"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore refused to create $displayName")
        try {
            val out = resolver.openOutputStream(uri, "w") ?: throw IOException("Cannot open $uri")
            out.use { o -> source.inputStream().use { it.copyTo(o) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw if (e is IOException) e else IOException(e)
        }
        // MediaStore may have renamed the file ("name (1).pdf") if the name was taken.
        var finalName = displayName
        var size = source.length()
        resolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE),
            null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { finalName = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        return SavedFile(uri, finalName, relativePath, mime, size)
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, source: File, displayName: String, mime: String, dirType: String): SavedFile {
        val dir = File(Environment.getExternalStoragePublicDirectory(dirType), FOLDER)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Cannot create ${dir.path}")
        var target = File(dir, displayName)
        var i = 1
        while (target.exists()) target = File(dir, FileNames.numbered(displayName, i++))
        source.copyTo(target)
        MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(mime), null)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        return SavedFile(uri, target.name, "$dirType/$FOLDER", mime, target.length())
    }

    /** Copies a file to a user-chosen SAF location (ACTION_CREATE_DOCUMENT result). */
    fun copyToUri(context: Context, source: File, target: Uri) {
        val out = context.contentResolver.openOutputStream(target, "wt")
            ?: throw IOException("Cannot open $target")
        out.use { o -> source.inputStream().use { it.copyTo(o) } }
    }

    fun viewIntent(file: SavedFile): Intent =
        Intent(Intent.ACTION_VIEW).setDataAndType(file.uri, file.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    fun shareIntent(uris: List<Uri>, mime: String): Intent {
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.type = mime
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
