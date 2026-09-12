package cn.com.omnimind.bot.omniflow

import cn.com.omnimind.baselib.runlog.State
import java.nio.file.Files
import java.util.Base64
import java.util.zip.InflaterInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OmniFlowStateHostTest {
    @Test
    fun `host observation includes screenshot bytes only when requested`() {
        val screenshot = Files.createTempFile("omniflow-state", ".png").toFile()
        screenshot.writeBytes(byteArrayOf(1, 2, 3, 4))
        val state = State.create(
            packageName = "com.example",
            activityName = "ExampleActivity",
            displayWidth = 1080,
            displayHeight = 2400,
            xml = "<hierarchy />",
            screenshotPath = screenshot.absolutePath,
        )

        assertEquals(
            Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4)),
            state.asHostMap(includeImage = true)["image_base64"],
        )
        assertFalse(state.asHostMap(includeImage = false).containsKey("image_base64"))
        screenshot.delete()
    }

    @Test
    fun `visual rgb payload is lossless zlib rgb`() {
        val payload = encodeVisualRgb(
            width = 2,
            height = 1,
            argbPixels = intArrayOf(0x00112233, 0x00A0B0C0),
        )
        val compressed = Base64.getDecoder().decode(payload["data_base64"].toString())
        val rgb = InflaterInputStream(compressed.inputStream()).readBytes()

        assertEquals(2, payload["width"])
        assertEquals(1, payload["height"])
        assertEquals("zlib", payload["compression"])
        assertEquals(
            listOf(0x11, 0x22, 0x33, 0xA0, 0xB0, 0xC0),
            rgb.map(Byte::toInt).map { it and 0xFF },
        )
    }
}
