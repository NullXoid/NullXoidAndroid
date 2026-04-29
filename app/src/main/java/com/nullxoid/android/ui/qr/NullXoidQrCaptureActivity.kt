package com.nullxoid.android.ui.qr

import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.nullxoid.android.R

class NullXoidQrCaptureActivity : CaptureActivity() {
    override fun initializeContent(): DecoratedBarcodeView {
        setContentView(R.layout.activity_nullxoid_qr_capture)
        return findViewById(R.id.zxing_barcode_scanner)
    }
}
