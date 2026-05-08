package com.nullxoid.android.ui.store

import android.annotation.SuppressLint
import android.content.Context
import android.view.Choreographer
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.Renderer
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Composable
fun InteractiveGlbViewer(
    artifactId: String,
    bytes: ByteArray,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewer = remember(artifactId) { FilamentGlbView(context) }
    DisposableEffect(viewer) {
        onDispose { viewer.release() }
    }
    Box(
        modifier = modifier
            .background(Color.Black)
            .testTag("store-model3d-viewer")
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewer },
            update = { it.loadModel(artifactId, bytes) }
        )
    }
}

@SuppressLint("ClickableViewAccessibility")
private class FilamentGlbView(context: Context) : FrameLayout(context), Choreographer.FrameCallback {
    private val surfaceView = SurfaceView(context)
    private val choreographer = Choreographer.getInstance()
    private val modelViewer: ModelViewer
    private var currentArtifactId = ""
    private var released = false
    private var frameCallbackPosted = false

    init {
        Utils.init()
        surfaceView.layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        addView(surfaceView)
        modelViewer = ModelViewer(surfaceView)
        modelViewer.renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = floatArrayOf(0.08f, 0.08f, 0.09f, 1.0f)
        }
        val light = modelViewer.engine.lightManager
        val lightInstance = light.getInstance(modelViewer.light)
        if (lightInstance != 0) {
            light.setColor(lightInstance, 1.0f, 0.95f, 0.86f)
            light.setIntensity(lightInstance, 160_000.0f)
            light.setDirection(lightInstance, 0.25f, -0.65f, -1.0f)
            light.setShadowCaster(lightInstance, false)
        }
        surfaceView.setOnTouchListener { _, event ->
            modelViewer.onTouchEvent(event)
            true
        }
    }

    fun loadModel(artifactId: String, bytes: ByteArray) {
        if (released || bytes.isEmpty() || artifactId == currentArtifactId) return
        currentArtifactId = artifactId
        modelViewer.destroyModel()
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buffer.put(bytes)
        buffer.flip()
        modelViewer.loadModelGlb(buffer)
        modelViewer.transformToUnitCube()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postFrameCallback()
    }

    override fun onDetachedFromWindow() {
        choreographer.removeFrameCallback(this)
        frameCallbackPosted = false
        release()
        super.onDetachedFromWindow()
    }

    override fun doFrame(frameTimeNanos: Long) {
        frameCallbackPosted = false
        if (!released && isAttachedToWindow) {
            modelViewer.render(frameTimeNanos)
            postFrameCallback()
        }
    }

    fun release() {
        if (released) return
        released = true
        choreographer.removeFrameCallback(this)
        frameCallbackPosted = false
    }

    private fun postFrameCallback() {
        if (!frameCallbackPosted && !released) {
            frameCallbackPosted = true
            choreographer.postFrameCallback(this)
        }
    }
}
