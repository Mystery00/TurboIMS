package io.github.vvb2060.ims.privileged

import android.app.IActivityManager
import android.os.Build
import android.system.Os
import android.util.Log

private const val TAG = "ShellPermission"
private const val ANDROID_17_SDK = 37

internal fun IActivityManager.stopDelegateShellPermissionIdentityCompat(uid: Int = Os.getuid()) {
    if (Build.VERSION.SDK_INT >= ANDROID_17_SDK) {
        try {
            stopDelegateShellPermissionIdentity(uid)
            return
        } catch (e: LinkageError) {
            Log.w(TAG, "stopDelegateShellPermissionIdentity(uid) is unavailable, fallback", e)
        }
        stopDelegateShellPermissionIdentity()
        return
    }

    try {
        stopDelegateShellPermissionIdentity()
    } catch (e: LinkageError) {
        Log.w(TAG, "stopDelegateShellPermissionIdentity() is unavailable, fallback", e)
        stopDelegateShellPermissionIdentity(uid)
    }
}
