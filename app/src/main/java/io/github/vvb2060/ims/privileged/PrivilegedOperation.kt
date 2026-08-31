package io.github.vvb2060.ims.privileged

import android.app.IActivityManager
import android.app.Instrumentation
import android.content.Context
import android.os.ServiceManager
import android.system.Os
import android.util.Log
import rikka.shizuku.ShizukuBinderWrapper

/**
 * 在统一错误边界内执行需要 shell permission delegation 的特权操作。
 *
 * 返回 null 表示业务和身份清理均成功；否则返回最先发生的异常，清理异常会作为
 * suppressed exception 附加，避免覆盖原始业务失败原因。
 */
fun Instrumentation.runWithShellPermissionDelegation(
    tag: String,
    block: () -> Unit,
): Throwable? {
    var activityManager: IActivityManager? = null
    var delegationStarted = false
    var failure: Throwable? = null
    try {
        val binder = ServiceManager.getService(Context.ACTIVITY_SERVICE)
            ?: error("activity service unavailable")
        activityManager = IActivityManager.Stub.asInterface(ShizukuBinderWrapper(binder))
        Log.i(tag, "starting shell permission delegation")
        activityManager.startDelegateShellPermissionIdentity(Os.getuid(), null)
        delegationStarted = true
        block()
    } catch (t: Throwable) {
        failure = t
    } finally {
        if (delegationStarted) {
            try {
                activityManager?.stopDelegateShellPermissionIdentityCompat()
                Log.i(tag, "stopped shell permission delegation")
            } catch (cleanup: Throwable) {
                Log.e(tag, "failed to stop shell permission delegation", cleanup)
                if (failure == null) {
                    failure = cleanup
                } else {
                    failure.addSuppressed(cleanup)
                }
            }
        }
    }
    return failure
}
