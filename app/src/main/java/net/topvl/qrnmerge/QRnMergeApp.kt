package net.topvl.qrnmerge

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class QRnMergeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
