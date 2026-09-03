package com.vapkit

sealed class VapError(message: String) : Exception(message) {
    data object InvalidManifest : VapError("invalid manifest")
    data class UnsupportedVersion(val version: Int) : VapError("unsupported version $version")
    data object InvalidFrame : VapError("invalid frame")
    data object InvalidVideo : VapError("invalid video")
    data object DecoderInitializationFailed : VapError("decoder initialization failed")
    data object DecodingFailed : VapError("decoding failed")
    data object RendererInitializationFailed : VapError("renderer initialization failed")
}
