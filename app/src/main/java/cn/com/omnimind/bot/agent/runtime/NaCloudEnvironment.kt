package cn.com.omnimind.bot.agent.runtime

import cn.com.omnimind.bot.BuildConfig

/** The product environment, not the debug/release build type, owns test-server access. */
internal object NaCloudEnvironment {
    val developmentAvailable: Boolean
        get() = BuildConfig.FLAVOR_version == "develop"
}
