package cn.com.omnimind.baselib.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptCacheKeyStoreTest {
    private val installScope = "0123456789abcdef0123"

    @Test
    fun `conversation key is stable scoped and bounded`() {
        val first = PromptCacheKeyStore.buildConversationKey(installScope, 42L)
        val repeated = PromptCacheKeyStore.buildConversationKey(installScope, 42L)
        val otherConversation = PromptCacheKeyStore.buildConversationKey(installScope, 43L)

        assertEquals(first, repeated)
        assertNotEquals(first, otherConversation)
        assertEquals("omnibot:v1:$installScope:conversation:42", first)
        assertTrue(first.length <= 64)
    }

    @Test
    fun `largest conversation id still fits identifier limit`() {
        val key = PromptCacheKeyStore.buildConversationKey(installScope, Long.MAX_VALUE)

        assertEquals(64, key.length)
    }
}
