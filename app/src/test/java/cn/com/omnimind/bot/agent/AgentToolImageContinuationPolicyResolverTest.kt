package cn.com.omnimind.bot.agent

import cn.com.omnimind.assists.controller.http.HttpController
import org.junit.Assert.assertFalse
import org.junit.Test

class AgentToolImageContinuationPolicyResolverTest {
    @Test
    fun mimoRouteDisablesToolImageContinuation() {
        val routeInfo = HttpController.ChatCompletionRouteInfo(
            requestedModel = "scene.dispatch.model",
            resolvedModel = "mimo-v2.5",
            apiBase = "https://relay.example.com/v1",
            providerProfileId = "profile-1",
            providerProfileName = "Provider 1",
            routeTag = "custom_openai_compat",
            bindingApplied = false,
            bindingProfileMissing = false,
            overrideApplied = true,
            protocolType = "openai_compatible"
        )

        val policy = AgentToolImageContinuationPolicyResolver.resolve(routeInfo)

        assertFalse(policy.supportsToolImageContinuation)
    }
}
