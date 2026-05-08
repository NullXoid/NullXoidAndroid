package com.nullxoid.android

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nullxoid.android.ui.store.InteractiveGlbViewer
import java.io.File
import java.io.FileOutputStream
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Model3DViewerDeviceSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersStagedVehicleGlbAndCapturesDeviceScreenshot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testContext = instrumentation.context
        val bytes = runCatching {
            testContext.assets.open("mobile-test-vehicle.glb").use { it.readBytes() }
        }.getOrNull()
        assumeTrue("Stage mobile-test-vehicle.glb under androidTest/assets for this local smoke.", bytes != null)

        compose.setContent {
            InteractiveGlbViewer(
                artifactId = "mobile-test-vehicle",
                bytes = bytes!!,
                modifier = Modifier.fillMaxSize()
            )
        }
        compose.onNodeWithTag("store-model3d-viewer").assertIsDisplayed()
        compose.waitForIdle()
        Thread.sleep(3_000)

        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        assumeTrue("Device screenshot capture returned null.", screenshot != null)
        val out = File(context.filesDir, "mobile_vehicle_viewer.png")
        FileOutputStream(out).use { stream ->
            screenshot!!.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "mobile_vehicle_viewer.png")
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/EchoLabs")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        assumeTrue("Could not create MediaStore screenshot output.", uri != null)
        resolver.openOutputStream(uri!!).use { stream ->
            assumeTrue("Could not open MediaStore screenshot output.", stream != null)
            screenshot!!.compress(Bitmap.CompressFormat.PNG, 100, stream!!)
        }
        screenshot!!.recycle()
    }
}
