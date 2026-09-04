package cn.com.omnimind.baselib.util.exception

/**
 * 隐私限制异常
 * 当应用因隐私设置（黑名单）被阻止启动时抛出
 * 此异常应导致任务直接终止
 */
class PrivacyBlockedException(message: String) : Exception(message)
