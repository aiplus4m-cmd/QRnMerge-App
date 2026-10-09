package net.topvl.qrnmerge.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileNames {
    private val illegal = Regex("[\\\\/:*?\"<>|\\x00-\\x1F]")

    fun timestamp(date: Date = Date()): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(date)

    fun defaultPdfName(prefix: String = "Scan2PDF", date: Date = Date()) = "${prefix}_${timestamp(date)}"

    /** Turns user input into a safe file name with the given extension (".pdf", ".jpg", ...). */
    fun sanitize(input: String, extension: String, fallback: String = defaultPdfName()): String {
        var base = input.trim()
        if (base.endsWith(extension, ignoreCase = true)) base = base.dropLast(extension.length)
        base = base.replace(illegal, "_").trim().trim('.').trim()
        if (base.isEmpty()) base = fallback
        if (base.length > 120) base = base.take(120)
        return base + extension
    }

    /** "name.pdf" -> "name (1).pdf" style alternative, used when the name already exists. */
    fun numbered(name: String, index: Int): String {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) "$name ($index)" else "${name.substring(0, dot)} ($index)${name.substring(dot)}"
    }

    fun humanSize(bytes: Long): String = when {
        bytes < 0 -> ""
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
