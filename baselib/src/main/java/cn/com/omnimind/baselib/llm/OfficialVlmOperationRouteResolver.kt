package cn.com.omnimind.baselib.llm

object OfficialVlmOperationRouteResolver {
    const val PROFILE_ID = "official-gelab-vlm"
    const val PROFILE_NAME = "小万官方内置模型"
    const val ROUTE_TAG = "official_gelab_vlm"

    fun resolve(
        sceneId: String?,
        hasExplicitRoute: Boolean,
        hasEffectiveSceneBinding: Boolean,
        sceneConfig: SceneOperationConfig,
        officialConfig: OfficialVlmOperationConfig
    ): OfficialVlmOperationConfig? {
        return officialConfig.takeIf {
            sceneId == SceneOperationConfigStore.SCENE_ID &&
                !hasExplicitRoute &&
                !hasEffectiveSceneBinding &&
                sceneConfig.useOfficialService &&
                it.isConfigured()
        }
    }
}
