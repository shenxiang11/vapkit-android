package com.vapkit

data class VapRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    val maxX: Int get() = x + width
    val maxY: Int get() = y + height
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    fun isContained(videoWidth: Int, videoHeight: Int): Boolean {
        return x >= 0 && y >= 0 && width > 0 && height > 0 &&
            maxX <= videoWidth && maxY <= videoHeight
    }
}

enum class VapOrientation(val rawValue: Int) {
    Unspecified(0),
    Unknown(-1),
    ;

    companion object {
        fun from(raw: Int): VapOrientation =
            entries.firstOrNull { it.rawValue == raw } ?: Unknown
    }
}

data class VapInfo(
    val version: Int,
    val frameCount: Int,
    val width: Int,
    val height: Int,
    val framesPerSecond: Int,
    val videoWidth: Int,
    val videoHeight: Int,
    val alphaFrame: VapRect,
    val rgbFrame: VapRect,
    val isFusion: Boolean,
    val orientation: VapOrientation,
    val codeTags: List<String> = emptyList(),
) {
    val duration: Double
        get() = if (framesPerSecond > 0) frameCount.toDouble() / framesPerSecond else 0.0
}

data class VapManifest(
    val info: VapInfo,
)

enum class VapPlaybackState {
    Idle,
    Loading,
    Ready,
    Playing,
    Paused,
    Stopped,
    Failed,
}
