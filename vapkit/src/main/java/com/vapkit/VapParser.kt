package com.vapkit

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

object VapParser {
    private val vapcType = byteArrayOf(0x76, 0x61, 0x70, 0x63)

    fun parseJson(text: String): VapManifest {
        val root = try {
            JSONObject(text)
        } catch (_: Exception) {
            throw VapError.InvalidManifest
        }
        val infoObject = root.optJSONObject("info") ?: throw VapError.InvalidManifest
        return VapManifest(info = parseInfo(infoObject))
    }

    fun parseJson(bytes: ByteArray): VapManifest = parseJson(bytes.toString(Charsets.UTF_8))

    fun parseMp4(bytes: ByteArray): VapManifest {
        val payload = extractVapcPayload(bytes) ?: throw VapError.InvalidVideo
        return parseJson(payload)
    }

    fun extractVapcPayload(mp4: ByteArray): ByteArray? {
        var offset = 0
        val count = mp4.size
        while (offset + 8 <= count) {
            val size = readUInt32BE(mp4, offset)
            if (size < 8) throw VapError.InvalidVideo
            val end = offset + size
            if (end > count) throw VapError.InvalidVideo
            if (mp4[offset + 4] == vapcType[0] &&
                mp4[offset + 5] == vapcType[1] &&
                mp4[offset + 6] == vapcType[2] &&
                mp4[offset + 7] == vapcType[3]
            ) {
                return mp4.copyOfRange(offset + 8, end)
            }
            offset = end
        }
        return null
    }

    private fun parseInfo(obj: JSONObject): VapInfo {
        val version = obj.intValue("v") ?: throw VapError.InvalidManifest
        if (version != 2) throw VapError.UnsupportedVersion(version)

        val frameCount = obj.intValue("f")
        val width = obj.intValue("w")
        val height = obj.intValue("h")
        val fps = obj.intValue("fps")
        val videoWidth = obj.intValue("videoW")
        val videoHeight = obj.intValue("videoH")
        if (frameCount == null || width == null || height == null || fps == null ||
            videoWidth == null || videoHeight == null ||
            frameCount <= 0 || fps <= 0 || width <= 0 || height <= 0 ||
            videoWidth <= 0 || videoHeight <= 0
        ) {
            throw VapError.InvalidManifest
        }

        val alphaFrame = parseRect(obj.opt("aFrame"))
        val rgbFrame = parseRect(obj.opt("rgbFrame"))
        if (!alphaFrame.isContained(videoWidth, videoHeight) ||
            !rgbFrame.isContained(videoWidth, videoHeight)
        ) {
            throw VapError.InvalidFrame
        }
        if (rgbFrame.width != width || rgbFrame.height != height) {
            throw VapError.InvalidFrame
        }

        return VapInfo(
            version = version,
            frameCount = frameCount,
            width = width,
            height = height,
            framesPerSecond = fps,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            alphaFrame = alphaFrame,
            rgbFrame = rgbFrame,
            isFusion = obj.intValue("isVapx") == 1,
            orientation = VapOrientation.from(obj.intValue("orien") ?: 0),
            codeTags = parseCodeTags(obj.opt("codeTag")),
        )
    }

    private fun parseRect(raw: Any?): VapRect {
        val values = raw as? JSONArray ?: throw VapError.InvalidFrame
        if (values.length() != 4) throw VapError.InvalidFrame
        val x = values.intValue(0) ?: throw VapError.InvalidFrame
        val y = values.intValue(1) ?: throw VapError.InvalidFrame
        val width = values.intValue(2) ?: throw VapError.InvalidFrame
        val height = values.intValue(3) ?: throw VapError.InvalidFrame
        val rect = VapRect(x, y, width, height)
        if (rect.isEmpty) throw VapError.InvalidFrame
        return rect
    }

    private fun parseCodeTags(raw: Any?): List<String> {
        return when (raw) {
            is JSONArray -> (0 until raw.length()).mapNotNull { raw.optString(it).takeIf { s -> s.isNotEmpty() } }
            is String -> if (raw.isEmpty()) emptyList() else listOf(raw)
            else -> emptyList()
        }
    }

    private fun readUInt32BE(data: ByteArray, offset: Int): Int {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.BIG_ENDIAN).int
    }

    private fun JSONObject.intValue(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return when (val value = get(key)) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private fun JSONArray.intValue(index: Int): Int? {
        return when (val value = opt(index)) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }
}
