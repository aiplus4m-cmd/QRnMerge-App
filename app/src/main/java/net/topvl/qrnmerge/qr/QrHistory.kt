package net.topvl.qrnmerge.qr

import android.content.Context
import org.json.JSONArray

/** Small recent-scans list kept in SharedPreferences. */
class QrHistory(context: Context) {
    private val prefs = context.getSharedPreferences("qr_history", Context.MODE_PRIVATE)

    fun load(): List<String> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        List(arr.length()) { arr.getString(it) }
    }.getOrDefault(emptyList())

    fun add(value: String): List<String> {
        val list = (listOf(value) + load().filter { it != value }).take(MAX)
        prefs.edit().putString(KEY, JSONArray(list).toString()).apply()
        return list
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "items"
        const val MAX = 20
    }
}
