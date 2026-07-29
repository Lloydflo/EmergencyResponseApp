package com.ers.emergencyresponseapp

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import android.view.View
import kotlin.math.max

/**
 * Small, dependency-free video surface for the entry screen.
 *
 * TextureView is used instead of VideoView/SurfaceView so Compose can apply the
 * rounded clipping used by the intro card. The bundled clip is muted and loops
 * continuously while this view is visible.
 */
class LoopingVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {

    private var videoResourceId: Int = 0
    private var mediaPlayer: MediaPlayer? = null
    private var playbackSurface: Surface? = null
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0

    init {
        surfaceTextureListener = this
        isOpaque = true
    }

    fun setVideoResource(resourceId: Int) {
        if (videoResourceId == resourceId && mediaPlayer != null) return
        videoResourceId = resourceId
        if (isAvailable) {
            createPlayer()
        }
    }

    override fun onSurfaceTextureAvailable(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int
    ) {
        playbackSurface?.release()
        playbackSurface = Surface(surfaceTexture)
        createPlayer()
    }

    override fun onSurfaceTextureSizeChanged(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int
    ) {
        updateCenterCropTransform()
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        releasePlayer()
        playbackSurface?.release()
        playbackSurface = null
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        val player = mediaPlayer ?: return
        runCatching {
            if (visibility == View.VISIBLE) {
                player.start()
            } else if (player.isPlaying) {
                player.pause()
            }
        }
    }

    override fun onDetachedFromWindow() {
        releasePlayer()
        playbackSurface?.release()
        playbackSurface = null
        super.onDetachedFromWindow()
    }

    private fun createPlayer() {
        if (videoResourceId == 0 || playbackSurface == null) return

        releasePlayer()

        val player = MediaPlayer.create(context, videoResourceId) ?: return
        mediaPlayer = player

        runCatching {
            player.setSurface(playbackSurface)
            player.isLooping = true
            player.setVolume(0f, 0f)
            player.setOnVideoSizeChangedListener { _, width, height ->
                videoWidth = width
                videoHeight = height
                updateCenterCropTransform()
            }
            player.setOnErrorListener { _, _, _ ->
                releasePlayer()
                true
            }

            videoWidth = player.videoWidth
            videoHeight = player.videoHeight
            updateCenterCropTransform()

            if (windowVisibility == View.VISIBLE) {
                player.start()
            }
        }.onFailure {
            releasePlayer()
        }
    }

    private fun updateCenterCropTransform() {
        if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val uniformScale = max(
            viewWidth / videoWidth.toFloat(),
            viewHeight / videoHeight.toFloat()
        )
        val scaledVideoWidth = videoWidth * uniformScale
        val scaledVideoHeight = videoHeight * uniformScale

        val matrix = Matrix().apply {
            setScale(
                scaledVideoWidth / viewWidth,
                scaledVideoHeight / viewHeight,
                viewWidth / 2f,
                viewHeight / 2f
            )
        }
        setTransform(matrix)
    }

    private fun releasePlayer() {
        val player = mediaPlayer ?: return
        mediaPlayer = null
        runCatching { player.stop() }
        runCatching { player.reset() }
        runCatching { player.release() }
    }
}
