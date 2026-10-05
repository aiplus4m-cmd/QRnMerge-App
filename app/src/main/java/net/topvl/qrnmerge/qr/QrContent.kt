package net.topvl.qrnmerge.qr

/** Parsed representation of a scanned QR/barcode payload (pure Kotlin, unit tested). */
sealed class QrContent(open val raw: String) {
    data class Url(override val raw: String, val url: String) : QrContent(raw)
    data class Wifi(override val raw: String, val ssid: String, val password: String, val security: String) : QrContent(raw)
    data class Email(override val raw: String, val address: String) : QrContent(raw)
    data class Phone(override val raw: String, val number: String) : QrContent(raw)
    data class Sms(override val raw: String, val number: String, val body: String) : QrContent(raw)
    data class Geo(override val raw: String, val uri: String) : QrContent(raw)
    data class Text(override val raw: String) : QrContent(raw)

    companion object {
        private val schemeUrl = Regex("^(https?|ftp)://\\S+$", RegexOption.IGNORE_CASE)
        private val bareDomain = Regex("^(www\\.)?[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}(/\\S*)?$", RegexOption.IGNORE_CASE)

        fun parse(input: String): QrContent {
            val raw = input.trim()
            val upper = raw.uppercase()
            return when {
                schemeUrl.matches(raw) -> Url(raw, raw)
                upper.startsWith("WIFI:") -> parseWifi(raw)
                upper.startsWith("MAILTO:") -> Email(raw, raw.substring(7).substringBefore('?'))
                upper.startsWith("MATMSG:") -> Email(raw, field(raw.substring(7), "TO"))
                upper.startsWith("TEL:") -> Phone(raw, raw.substring(4))
                upper.startsWith("SMSTO:") || upper.startsWith("SMS:") -> {
                    val rest = raw.substringAfter(':')
                    Sms(raw, rest.substringBefore(':'), rest.substringAfter(':', ""))
                }
                upper.startsWith("GEO:") -> Geo(raw, raw)
                !raw.contains(' ') && bareDomain.matches(raw) -> Url(raw, "https://$raw")
                else -> Text(raw)
            }
        }

        private fun parseWifi(raw: String): Wifi {
            val body = raw.substring(5)
            return Wifi(raw, field(body, "S"), field(body, "P"), field(body, "T").ifEmpty { "nopass" })
        }

        /** Reads `KEY:value;` from MECARD-like payloads, honouring backslash escapes. */
        private fun field(body: String, key: String): String {
            var i = 0
            while (i < body.length) {
                val keyEnd = body.indexOf(':', i)
                if (keyEnd < 0) return ""
                val k = body.substring(i, keyEnd)
                val sb = StringBuilder()
                var j = keyEnd + 1
                while (j < body.length && body[j] != ';') {
                    if (body[j] == '\\' && j + 1 < body.length) j++
                    sb.append(body[j]); j++
                }
                if (k.equals(key, ignoreCase = true)) return sb.toString()
                i = j + 1
            }
            return ""
        }
    }
}
