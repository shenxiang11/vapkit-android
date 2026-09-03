package com.vapkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VapParserTest {
    @Test
    fun parseUserFixture() {
        val json = javaClass.classLoader!!.getResource("user_246106.vapc.json")!!.readText()
        val manifest = VapParser.parseJson(json)
        assertEquals(2, manifest.info.version)
        assertEquals(151, manifest.info.frameCount)
        assertEquals(750, manifest.info.width)
        assertEquals(1624, manifest.info.height)
        assertEquals(1136, manifest.info.videoWidth)
        assertEquals(1632, manifest.info.videoHeight)
        assertEquals(VapRect(0, 0, 750, 1624), manifest.info.rgbFrame)
        assertEquals(VapRect(754, 0, 375, 812), manifest.info.alphaFrame)
        assertEquals(listOf("17ae.com"), manifest.info.codeTags)
        assertTrue(manifest.info.duration > 5.0)
    }

    @Test(expected = VapError.UnsupportedVersion::class)
    fun rejectOldVersion() {
        VapParser.parseJson("""{"info":{"v":1,"f":1,"w":10,"h":10,"fps":30,"videoW":10,"videoH":10,"aFrame":[0,0,5,5],"rgbFrame":[0,0,10,10]}}""")
    }
}
