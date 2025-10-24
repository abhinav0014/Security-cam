package com.onnet.securitycam.ui

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * CameraPreview composable that provides a TextureView and exposes its Surface when ready.
 * onSurfaceReady will be invoked with a non-null Surface once the TextureView is available.
 */
@Composable
fun CameraPreview(modifier: Modifier = Modifier, onSurfaceReady: (Surface?) -> Unit) {
    val context = LocalContext.current
    val surfaceRef = remember { mutableStateOf<Surface?>(null) }

    AndroidView(factory = { ctx ->
        TextureView(ctx).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                    val s = Surface(st)
                    surfaceRef.value = s
                    onSurfaceReady(s)
                }

                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                    surfaceRef.value?.release()
                    surfaceRef.value = null
                    onSurfaceReady(null)
                    return true
                }

                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
            }
        }
    }, modifier = modifier)

    DisposableEffect(Unit) {
        onDispose {
            surfaceRef.value?.release()
            onSurfaceReady(null)
        }
    }
}
