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
import com.google.android.filament.EntityManager
import com.google.android.filament.LightManager
import com.google.android.filament.Skybox
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
    private var keyLight = 0
    private var fillLight = 0
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
        keyLight = EntityManager.get().create()
        fillLight = EntityManager.get().create()
        modelViewer.scene.skybox = Skybox.Builder()
            .color(0.08f, 0.08f, 0.09f, 1.0f)
            .build(modelViewer.engine)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1.0f, 0.95f, 0.86f)
            .intensity(120_000.0f)
            .direction(0.25f, -0.65f, -1.0f)
            .castShadows(false)
            .build(modelViewer.engine, keyLight)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(0.66f, 0.76f, 1.0f)
            .intensity(45_000.0f)
            .direction(-0.8f, 0.15f, -0.55f)
            .castShadows(false)
            .build(modelViewer.engine, fillLight)
        modelViewer.scene.addEntity(keyLight)
        modelViewer.scene.addEntity(fillLight)
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
        runCatching { modelViewer.destroyModel() }
        runCatching { modelViewer.scene.removeEntity(keyLight) }
        runCatching { modelViewer.scene.removeEntity(fillLight) }
        runCatching { modelViewer.engine.destroyEntity(keyLight) }
        runCatching { modelViewer.engine.destroyEntity(fillLight) }
        runCatching { EntityManager.get().destroy(keyLight) }
        runCatching { EntityManager.get().destroy(fillLight) }
    }

    private fun postFrameCallback() {
        if (!frameCallbackPosted && !released) {
            frameCallbackPosted = true
            choreographer.postFrameCallback(this)
        }
    }
}
