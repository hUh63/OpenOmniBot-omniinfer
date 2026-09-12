package cn.com.omnimind.baselib.util

import com.tencent.mmkv.MMKV
import java.util.UUID

/**
 * 开源版本地匿名身份标识。
 * 用于替代账号体系中的 userId 作用域能力。
 */
object OssIdentity {
    private const val KEY_LOCAL_USER_ID = "oss_local_user_id"

    fun currentUserId(): String {
        val mmkv = MMKV.defaultMMKV()
        val existing = mmkv?.decodeString(KEY_LOCAL_USER_ID)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (existing != null) return existing

        val generated = UUID.randomUUID().toString()
        mmkv?.encode(KEY_LOCAL_USER_ID, generated)
        return generated
    }

    fun currentUserIdOrNull(): String? {
        return currentUserId().trim().takeIf { it.isNotEmpty() }
    }
}
