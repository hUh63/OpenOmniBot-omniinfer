package cn.com.omnimind.baselib.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfficialVlmOperationRouteResolverTest {
    private val configured = OfficialVlmOperationConfig(
        enabled = true,
        apiBase = "https://chatgpt.example/v1",
        model = "gpt-5.6-sol",
        wireApi = OpenAiWireApi.RESPONSES,
    )

    @Test
    fun `bundled ChatGPT route requires explicit opt in`() {
        val route = OfficialVlmOperationRouteResolver.resolve(
            sceneId = SceneOperationConfigStore.SCENE_ID,
            hasExplicitRoute = false,
            hasEffectiveSceneBinding = false,
            sceneConfig = SceneOperationConfig(useOfficialService = true),
            officialConfig = configured
        )

        assertEquals(configured, route)
        assertEquals(OpenAiWireApi.RESPONSES, route?.wireApi)
    }

    @Test
    fun `official model is disabled by default`() {
        assertNull(
            OfficialVlmOperationRouteResolver.resolve(
                sceneId = SceneOperationConfigStore.SCENE_ID,
                hasExplicitRoute = false,
                hasEffectiveSceneBinding = false,
                sceneConfig = SceneOperationConfig(),
                officialConfig = configured
            )
        )
    }

    @Test
    fun `explicit provider binding wins over official default`() {
        assertNull(
            OfficialVlmOperationRouteResolver.resolve(
                sceneId = SceneOperationConfigStore.SCENE_ID,
                hasExplicitRoute = false,
                hasEffectiveSceneBinding = true,
                sceneConfig = SceneOperationConfig(useOfficialService = true),
                officialConfig = configured
            )
        )
    }

    @Test
    fun `stale scene binding does not block Gelab default`() {
        assertEquals(
            configured,
            OfficialVlmOperationRouteResolver.resolve(
                sceneId = SceneOperationConfigStore.SCENE_ID,
                hasExplicitRoute = false,
                hasEffectiveSceneBinding = false,
                sceneConfig = SceneOperationConfig(useOfficialService = true),
                officialConfig = configured
            )
        )
    }

    @Test
    fun `incomplete official config is never selected`() {
        assertNull(
            OfficialVlmOperationRouteResolver.resolve(
                sceneId = SceneOperationConfigStore.SCENE_ID,
                hasExplicitRoute = false,
                hasEffectiveSceneBinding = false,
                sceneConfig = SceneOperationConfig(useOfficialService = true),
                officialConfig = configured.copy(apiBase = "")
            )
        )
    }
}
