package cn.com.omnimind.bot.webchat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WebChatAvatarServiceTest {
    @Test
    fun `preset indexes outside the shared Flutter list fall back to the first avatar`() {
        assertEquals(0, WebChatAvatarService.normalizePresetIndex(-1))
        assertEquals(0, WebChatAvatarService.normalizePresetIndex(6))
        assertEquals(5, WebChatAvatarService.normalizePresetIndex(5))
    }

    @Test
    fun `custom avatar must be a direct child of the managed directory`() {
        val managedDirectory = File("build/test-avatar/files/agent_avatars")

        assertTrue(
            WebChatAvatarService.isManagedCustomAvatar(
                File(managedDirectory, "agent_avatar_1.png"),
                managedDirectory
            )
        )
        assertFalse(
            WebChatAvatarService.isManagedCustomAvatar(
                File(managedDirectory.parentFile, "outside.png"),
                managedDirectory
            )
        )
        assertFalse(
            WebChatAvatarService.isManagedCustomAvatar(
                File(managedDirectory, "nested/agent_avatar_2.png"),
                managedDirectory
            )
        )
    }
}
