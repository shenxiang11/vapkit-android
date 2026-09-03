package com.vapkit

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView

class VapTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {

    var player: VapPlayer? = null
        set(value) {
            field = value
            bindSurfaceIfReady()
        }

    private var output: Surface? = null

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        output?.release()
        output = Surface(surface)
        bindSurfaceIfReady()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        player?.attachOutput(output ?: return, width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        player?.detachOutput()
        output?.release()
        output = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    private fun bindSurfaceIfReady() {
        val surface = output ?: return
        val player = player ?: return
        player.attachOutput(surface, width.coerceAtLeast(1), height.coerceAtLeast(1))
    }
}
