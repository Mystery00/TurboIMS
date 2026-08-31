package io.github.vvb2060.ims.privileged

import java.lang.reflect.InvocationTargetException

/**
 * 展开反射和调用链包装，取得最接近系统服务实际失败原因的异常。
 */
fun Throwable.privilegedRootCause(): Throwable {
    var current = this
    val visited = mutableSetOf<Throwable>()
    while (visited.add(current)) {
        current = when (current) {
            is InvocationTargetException -> current.targetException ?: return current
            else -> current.cause ?: return current
        }
    }
    return current
}

/**
 * 生成可供主进程判断和展示的稳定错误文本。
 */
fun Throwable.toPrivilegedErrorMessage(): String {
    val root = privilegedRootCause()
    val name = root.javaClass.simpleName.ifBlank { root.javaClass.name }
    return root.message?.takeIf { it.isNotBlank() }?.let { "$name: $it" } ?: name
}

/**
 * 判断 CarrierConfig 写入失败是否属于可由 Broker 路径重试的权限错误。
 */
fun isCarrierConfigPermissionError(message: String): Boolean {
    return message.contains("SecurityException", ignoreCase = true) ||
        message.contains("android.permission.MODIFY_PHONE_STATE", ignoreCase = true) ||
        message.contains("No permission to write to carrier config", ignoreCase = true)
}
