package cn.com.omnimind.uikit.loader.cat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PetSpriteAtlasTest {
    @Test
    fun detectsCodexV1AndV2AtlasDimensions() {
        val v1 = PetSpriteAtlasSpec.detect(1536, 1872)
        val v2 = PetSpriteAtlasSpec.detect(1536, 2288)

        assertEquals(1, v1?.spriteVersionNumber)
        assertEquals(9, v1?.rows)
        assertEquals(2, v2?.spriteVersionNumber)
        assertEquals(11, v2?.rows)
        assertNull(PetSpriteAtlasSpec.detect(1024, 1024))
    }

    @Test
    fun mapsPublicActionAliasesToCodexRows() {
        assertEquals(
            PetAnimationAction.RUNNING_RIGHT,
            PetAnimationAction.fromWireName("move-right")
        )
        assertEquals(
            PetAnimationAction.RUNNING,
            PetAnimationAction.fromWireName("processing")
        )
        assertEquals(
            PetAnimationAction.RUNNING,
            PetAnimationAction.fromWireName("run")
        )
        assertEquals(
            PetAnimationAction.FAILED,
            PetAnimationAction.fromWireName("error")
        )
        assertEquals(4, PetAnimationAction.WAVING.frameCount)
        assertEquals(6, PetAnimationAction.REVIEW.frameCount)
        assertEquals(
            7,
            PetAnimationAction.entries.map { it.category }.toSet().size
        )
    }
}
