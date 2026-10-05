package net.topvl.qrnmerge

import net.topvl.qrnmerge.qr.QrContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrContentTest {
    @Test fun url() {
        val c = QrContent.parse("https://topvl.net/qr")
        assertTrue(c is QrContent.Url)
        assertEquals("https://topvl.net/qr", (c as QrContent.Url).url)
    }

    @Test fun bareDomainBecomesHttps() {
        val c = QrContent.parse("topvl.net/mobile-apps.html")
        assertEquals("https://topvl.net/mobile-apps.html", (c as QrContent.Url).url)
    }

    @Test fun wifiWithEscapes() {
        val c = QrContent.parse("WIFI:T:WPA;S:My\\;Net;P:pa\\:ss;;") as QrContent.Wifi
        assertEquals("My;Net", c.ssid)
        assertEquals("pa:ss", c.password)
        assertEquals("WPA", c.security)
    }

    @Test fun wifiOpenNetwork() {
        val c = QrContent.parse("WIFI:S:Cafe;;") as QrContent.Wifi
        assertEquals("Cafe", c.ssid)
        assertEquals("", c.password)
        assertEquals("nopass", c.security)
    }

    @Test fun emailPhoneSms() {
        assertEquals("a@b.com", (QrContent.parse("mailto:a@b.com?subject=hi") as QrContent.Email).address)
        assertEquals("+84901234567", (QrContent.parse("tel:+84901234567") as QrContent.Phone).number)
        val sms = QrContent.parse("SMSTO:0901:Xin chao") as QrContent.Sms
        assertEquals("0901", sms.number)
        assertEquals("Xin chao", sms.body)
    }

    @Test fun plainText() {
        assertTrue(QrContent.parse("Xin chào NhảmStudio") is QrContent.Text)
        assertTrue(QrContent.parse("1234567890123") is QrContent.Text)
    }

    @Test fun geo() {
        assertTrue(QrContent.parse("geo:21.0285,105.8542") is QrContent.Geo)
    }
}
