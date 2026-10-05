package net.topvl.qrnmerge.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import net.topvl.qrnmerge.R

const val WEBSITE_URL = "https://topvl.net"
const val QR_TOOL_URL = "https://topvl.net/qr"

fun Context.safeStart(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        Toast.makeText(this, R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
    }
}

fun Context.openUrl(url: String) = safeStart(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
