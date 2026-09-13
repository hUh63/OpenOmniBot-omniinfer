package cn.com.omnimind.assists.openclaw

import cn.com.omnimind.baselib.util.OmniLog
import com.tencent.mmkv.MMKV

/**
 * OpenClaw DeviceToken 持久化存储
 *
 * Gateway 在 hello-ok 响应中可能返回 deviceToken，
 * 客户端必须持久化保存并在后续连接中使用此 token 进行认证。
 */
object OpenClawTokenStore {
    private const val TAG = "OpenClawTokenStore"
    private const val KEY_DEVICE_TOKEN = "openclaw_device_token"
    private const val KEY_DEVICE_ROLE = "openclaw_device_role"
    private const val KEY_DEVICE_SCOPES = "openclaw_device_scopes"

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    /**
     * 保存 Gateway 颁发的 deviceToken（来自 hello-ok 响应）
     */
    fun saveDeviceToken(token: String) {
        if (token.isBlank()) return
        mmkv.encode(KEY_DEVICE_TOKEN, token)
        OmniLog.i(TAG, "saved deviceToken (${token.length} chars)")
    }

    /**
     * 获取已保存的 deviceToken，如果没有则返回 null
     */
    fun getDeviceToken(): String? {
        val token = mmkv.decodeString(KEY_DEVICE_TOKEN)
        return if (token.isNullOrBlank()) null else token
    }

    /**
     * 保存 Gateway 返回的角色和 scope 信息
     */
    fun saveAuthInfo(role: String?, scopes: List<String>?) {
        if (!role.isNullOrBlank()) {
            mmkv.encode(KEY_DEVICE_ROLE, role)
        }
        if (!scopes.isNullOrEmpty()) {
            mmkv.encode(KEY_DEVICE_SCOPES, scopes.joinToString(","))
        }
    }

    /**
     * 获取已保存的角色
     */
    fun getRole(): String? {
        return mmkv.decodeString(KEY_DEVICE_ROLE)
    }

    /**
     * 获取已保存的 scopes
     */
    fun getScopes(): List<String> {
        val raw = mmkv.decodeString(KEY_DEVICE_SCOPES)
        return if (raw.isNullOrBlank()) emptyList()
        else raw.split(",").filter { it.isNotBlank() }
    }

    /**
     * 清除所有存储的 token 信息（用于重置设备配对）
     */
    fun clear() {
        mmkv.removeValueForKey(KEY_DEVICE_TOKEN)
        mmkv.removeValueForKey(KEY_DEVICE_ROLE)
        mmkv.removeValueForKey(KEY_DEVICE_SCOPES)
        OmniLog.i(TAG, "cleared all stored tokens")
    }

    /**
     * 获取用于认证的 token：
     * 优先使用 deviceToken（后续连接），退回到 gateway token（首次连接）
     */
    fun getAuthToken(gatewayToken: String?): String {
        val deviceToken = getDeviceToken()
        if (!deviceToken.isNullOrBlank()) {
            OmniLog.i(TAG, "using stored deviceToken for auth")
            return deviceToken
        }
        OmniLog.i(TAG, "using gateway token for auth (first connect or no deviceToken)")
        return gatewayToken?.trim().orEmpty()
    }
}
