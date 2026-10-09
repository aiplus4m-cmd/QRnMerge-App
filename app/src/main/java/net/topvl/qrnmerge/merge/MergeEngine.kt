package net.topvl.qrnmerge.merge

import android.content.Context
import net.topvl.qrnmerge.util.FileNames
import net.topvl.qrnmerge.util.MediaSaver
import net.topvl.qrnmerge.util.SavedFile
import java.io.File

data class MergeResult(val saved: SavedFile, val pageCount: Int, val localFile: File)

/** Merge + persist to the device (Download/Scan2PDF). Shared by the UI and the scan tab. */
object MergeEngine {

    fun outputDir(context: Context) = File(context.cacheDir, "merged")

    fun mergeAndSave(
        context: Context,
        sources: List<MergeSource>,
        requestedName: String,
        pageSize: PageSizeMode,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): MergeResult {
        val fileName = FileNames.sanitize(requestedName, ".pdf")
        val dir = outputDir(context)
        dir.mkdirs()
        // Keep only the latest few local copies (used for share / "save as").
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(4)?.forEach { it.delete() }
        val local = File(dir, fileName)
        val pages = PdfMerger.merge(context, sources, local, pageSize, onProgress)
        val saved = MediaSaver.savePdf(context, local, fileName)
        return MergeResult(saved, pages, local)
    }
}
