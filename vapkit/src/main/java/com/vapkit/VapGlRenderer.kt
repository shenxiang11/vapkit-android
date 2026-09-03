package com.vapkit

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

internal class VapGlRenderer {
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var windowSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var oesTexture = 0
    private var vertexBuffer: FloatBuffer? = null
    private var window: Surface? = null

    val texMatrix = FloatArray(16)
    var decoderSurfaceTexture: SurfaceTexture? = null
        private set
    var decoderSurface: Surface? = null
        private set

    fun attachWindow(surface: Surface) {
        window = surface
        ensureEgl()
    }

    fun setDecoderBufferSize(width: Int, height: Int) {
        decoderSurfaceTexture?.setDefaultBufferSize(width.coerceAtLeast(1), height.coerceAtLeast(1))
    }

    fun release() {
        decoderSurface?.release()
        decoderSurface = null
        decoderSurfaceTexture?.release()
        decoderSurfaceTexture = null
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (windowSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(display, windowSurface)
            }
            if (context != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(display, context)
            }
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        windowSurface = EGL14.EGL_NO_SURFACE
        program = 0
        oesTexture = 0
        window = null
    }

    fun draw(info: VapInfo, viewWidth: Int, viewHeight: Int) {
        if (display == EGL14.EGL_NO_DISPLAY) return
        GLES20.glViewport(0, 0, viewWidth, viewHeight)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        updateVertices(info)
        GLES20.glUseProgram(program)
        val pos = GLES20.glGetAttribLocation(program, "aPosition")
        val rgb = GLES20.glGetAttribLocation(program, "aRgbUV")
        val alpha = GLES20.glGetAttribLocation(program, "aAlphaUV")
        val tex = GLES20.glGetUniformLocation(program, "uTexture")
        val matrix = GLES20.glGetUniformLocation(program, "uTexMatrix")

        val buffer = vertexBuffer ?: return
        val stride = 6 * 4
        buffer.position(0)
        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, stride, buffer)
        buffer.position(2)
        GLES20.glEnableVertexAttribArray(rgb)
        GLES20.glVertexAttribPointer(rgb, 2, GLES20.GL_FLOAT, false, stride, buffer)
        buffer.position(4)
        GLES20.glEnableVertexAttribArray(alpha)
        GLES20.glVertexAttribPointer(alpha, 2, GLES20.GL_FLOAT, false, stride, buffer)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
        GLES20.glUniform1i(tex, 0)
        GLES20.glUniformMatrix4fv(matrix, 1, false, texMatrix, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        EGL14.eglSwapBuffers(display, windowSurface)
    }

    private fun ensureEgl() {
        val surface = window ?: return
        if (display == EGL14.EGL_NO_DISPLAY) {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val versions = IntArray(2)
            EGL14.eglInitialize(display, versions, 0, versions, 1)
            val attribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val num = IntArray(1)
            EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0)
            val config = configs[0] ?: throw VapError.RendererInitializationFailed
            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            windowSurface = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
            EGL14.eglMakeCurrent(display, windowSurface, windowSurface, context)
            program = buildProgram()
            oesTexture = createOesTexture()
            val st = SurfaceTexture(oesTexture)
            decoderSurfaceTexture = st
            decoderSurface = Surface(st)
        }
    }

    private fun createOesTexture(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return ids[0]
    }

    private fun updateVertices(info: VapInfo) {
        val rgb = uvRect(info.rgbFrame, info.videoWidth, info.videoHeight)
        val alpha = uvRect(info.alphaFrame, info.videoWidth, info.videoHeight)
        val data = floatArrayOf(
            // BL, TL, BR, TR — image Y maps to v (top-left origin)
            -1f, -1f, rgb[0], rgb[3], alpha[0], alpha[3],
            -1f, 1f, rgb[0], rgb[1], alpha[0], alpha[1],
            1f, -1f, rgb[2], rgb[3], alpha[2], alpha[3],
            1f, 1f, rgb[2], rgb[1], alpha[2], alpha[1],
        )
        vertexBuffer = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(data)
        vertexBuffer?.position(0)
    }

    private fun uvRect(rect: VapRect, videoWidth: Int, videoHeight: Int): FloatArray {
        val width = videoWidth.coerceAtLeast(1).toFloat()
        val height = videoHeight.coerceAtLeast(1).toFloat()
        return floatArrayOf(
            rect.x / width,
            rect.y / height,
            rect.maxX / width,
            rect.maxY / height,
        )
    }

    private fun buildProgram(): Int {
        val vertex = """
            attribute vec4 aPosition;
            attribute vec2 aRgbUV;
            attribute vec2 aAlphaUV;
            varying vec2 vRgbUV;
            varying vec2 vAlphaUV;
            void main() {
                gl_Position = aPosition;
                vRgbUV = aRgbUV;
                vAlphaUV = aAlphaUV;
            }
        """.trimIndent()
        val fragment = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTexture;
            uniform mat4 uTexMatrix;
            varying vec2 vRgbUV;
            varying vec2 vAlphaUV;
            vec2 mapUv(vec2 uv) {
                vec2 flipped = vec2(uv.x, 1.0 - uv.y);
                return (uTexMatrix * vec4(flipped, 0.0, 1.0)).xy;
            }
            void main() {
                vec4 rgb = texture2D(uTexture, mapUv(vRgbUV));
                vec4 alpha = texture2D(uTexture, mapUv(vAlphaUV));
                gl_FragColor = vec4(rgb.rgb, alpha.r);
            }
        """.trimIndent()
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        return program
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }
}
