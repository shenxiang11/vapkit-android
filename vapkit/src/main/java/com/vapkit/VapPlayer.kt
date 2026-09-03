package com.vapkit

import android.content.res.AssetFileDescriptor
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class VapPlayer {
    var loop: Boolean = false
    @Volatile
    var state: VapPlaybackState = VapPlaybackState.Idle
        private set
    var manifest: VapManifest? = null
        private set
    var onStateChanged: ((VapPlaybackState) -> Unit)? = null

    private val renderer = VapGlRenderer()
    private val thread = HandlerThread("vapkit-player").apply { start() }
    private val handler = Handler(thread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bufferInfo = MediaCodec.BufferInfo()

    @Volatile
    private var playing = false
    private var source: File? = null
    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var inputDone = false
    private var viewWidth = 1
    private var viewHeight = 1
    private var playStartElapsed = 0L
    private var firstPtsUs = 0L
    private var generation = 0

    suspend fun load(file: File) {
        setState(VapPlaybackState.Loading)
        val parsed = withContext(Dispatchers.IO) {
            VapParser.parseMp4(file.readBytes())
        }
        source = file
        manifest = parsed
        setState(VapPlaybackState.Ready)
    }

    suspend fun load(afd: AssetFileDescriptor, cacheDir: File, name: String) {
        val file = File(cacheDir, name)
        withContext(Dispatchers.IO) {
            if (!file.exists() || file.length() != afd.length) {
                afd.createInputStream().use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        load(file)
    }

    fun attachOutput(surface: Surface, width: Int, height: Int) {
        viewWidth = width.coerceAtLeast(1)
        viewHeight = height.coerceAtLeast(1)
        handler.post {
            renderer.attachWindow(surface)
            val file = source
            if (playing && codec == null && file != null) {
                startDecoder(file)
            }
        }
    }

    fun detachOutput() {
        handler.post {
            playing = false
            generation += 1
            stopInternal()
            renderer.release()
        }
    }

    fun play() {
        val file = source ?: return
        playing = true
        setState(VapPlaybackState.Playing)
        handler.post { startDecoder(file) }
    }

    fun pause() {
        if (state != VapPlaybackState.Playing) return
        playing = false
        setState(VapPlaybackState.Paused)
    }

    fun stop() {
        playing = false
        handler.post {
            generation += 1
            stopInternal()
        }
        setState(VapPlaybackState.Stopped)
    }

    fun release() {
        playing = false
        handler.post {
            generation += 1
            stopInternal()
            renderer.release()
            thread.quitSafely()
        }
    }

    private fun startDecoder(file: File) {
        stopInternal()
        val info = manifest?.info ?: return
        val decoderSurface = renderer.decoderSurface ?: return
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        val track = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
        } ?: run {
            setState(VapPlaybackState.Failed)
            return
        }
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: run {
            setState(VapPlaybackState.Failed)
            return
        }
        renderer.setDecoderBufferSize(
            format.getInteger(MediaFormat.KEY_WIDTH),
            format.getInteger(MediaFormat.KEY_HEIGHT),
        )
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, decoderSurface, null, 0)
        codec.start()
        this.extractor = extractor
        this.codec = codec
        inputDone = false
        playStartElapsed = 0L
        firstPtsUs = 0L
        generation += 1
        pumpOnce(info, generation)
    }

    private fun pumpOnce(info: VapInfo, session: Int) {
        if (!playing || session != generation) return
        val extractor = extractor ?: return
        val codec = codec ?: return

        if (!inputDone) {
            val inputIndex = codec.dequeueInputBuffer(0)
            if (inputIndex >= 0) {
                val buffer = codec.getInputBuffer(inputIndex)
                val sampleSize = if (buffer != null) extractor.readSampleData(buffer, 0) else -1
                if (sampleSize < 0) {
                    codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputDone = true
                } else {
                    codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }
        }

        val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
        if (outputIndex >= 0) {
            if (playStartElapsed == 0L) {
                playStartElapsed = SystemClock.elapsedRealtime()
                firstPtsUs = bufferInfo.presentationTimeUs
            }
            val target = playStartElapsed + (bufferInfo.presentationTimeUs - firstPtsUs) / 1000
            val delay = (target - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            handler.postDelayed({
                if (!playing || session != generation) {
                    runCatching { codec.releaseOutputBuffer(outputIndex, false) }
                    return@postDelayed
                }
                val render = bufferInfo.size > 0
                codec.releaseOutputBuffer(outputIndex, render)
                if (render) {
                    renderLatest()
                }
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    if (loop && playing) {
                        source?.let(::startDecoder)
                    } else {
                        playing = false
                        stopInternal()
                        setState(VapPlaybackState.Stopped)
                    }
                    return@postDelayed
                }
                pumpOnce(info, session)
            }, delay)
            return
        }

        handler.postDelayed({ pumpOnce(info, session) }, 8)
    }

    private fun renderLatest() {
        val info = manifest?.info ?: return
        val texture = renderer.decoderSurfaceTexture ?: return
        try {
            texture.updateTexImage()
            texture.getTransformMatrix(renderer.texMatrix)
            renderer.draw(info, viewWidth, viewHeight)
        } catch (_: Exception) {
            // Surface gone during teardown.
        }
    }

    private fun stopInternal() {
        try {
            codec?.stop()
        } catch (_: Exception) {
        }
        codec?.release()
        codec = null
        extractor?.release()
        extractor = null
        inputDone = false
    }

    private fun setState(next: VapPlaybackState) {
        state = next
        val callback = onStateChanged ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback(next)
        } else {
            mainHandler.post { callback(next) }
        }
    }
}
