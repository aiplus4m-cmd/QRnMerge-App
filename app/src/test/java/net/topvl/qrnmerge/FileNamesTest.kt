package net.topvl.qrnmerge

import net.topvl.qrnmerge.util.FileNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FileNamesTest {
    @Test fun addsExtension() = assertEquals("report.pdf", FileNames.sanitize("report", ".pdf"))
    @Test fun keepsExistingExtension() = assertEquals("report.pdf", FileNames.sanitize("report.PDF", ".pdf"))
    @Test fun replacesIllegalCharacters() = assertEquals("a_b_c_d.pdf", FileNames.sanitize("a/b:c*d", ".pdf"))
    @Test fun keepsVietnamese() = assertEquals("Hợp đồng 2026.pdf", FileNames.sanitize("  Hợp đồng 2026 ", ".pdf"))
    @Test fun emptyFallsBack() = assertEquals("fallback.pdf", FileNames.sanitize("  ..  ", ".pdf", "fallback"))
    @Test fun longNamesAreTrimmed() = assertTrue(FileNames.sanitize("x".repeat(300), ".pdf").length <= 124)
    @Test fun numbered() {
        assertEquals("doc (2).pdf", FileNames.numbered("doc.pdf", 2))
        assertEquals("doc (1)", FileNames.numbered("doc", 1))
    }
    @Test fun defaultName() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 5, 9, 7, 3) }
        assertEquals("Scan2PDF_20261005_090703", FileNames.defaultPdfName(date = cal.time))
    }
    @Test fun humanSize() {
        assertEquals("512 B", FileNames.humanSize(512))
        assertEquals("2 KB", FileNames.humanSize(2048))
        assertEquals("1.5 MB", FileNames.humanSize(1572864))
    }
}
