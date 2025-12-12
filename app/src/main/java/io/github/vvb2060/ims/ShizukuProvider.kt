package io.github.vvb2060.ims

import android.app.IActivityManager
import android.app.IInstrumentationWatcher
import android.app.UiAutomationConnection
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.ServiceManager
import android.util.Log
import io.github.vvb2060.ims.privileged.SimInfo
import io.github.vvb2060.ims.privileged.SimReader
import kotlinx.coroutines.CompletableDeferred
import org.lsposed.hiddenapibypass.LSPass
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.ShizukuProvider

class ShizukuProvider : ShizukuProvider() {
    override fun onCreate(): Boolean {
        LSPass.setHiddenApiExemptions("")
        // 不再自动触发，只在用户手动点击"应用配置"时才执行
        return super.onCreate()
    }

    companion object {
        private const val TAG = "ShizukuProvider"

        suspend fun overrideImsConfig(context: Context, data: Bundle): Boolean {
            val result = startInstrumentation(context, ImsModifier::class.java, data, true)
            if (result == null) {
                Log.w(TAG, "overrideImsConfig: failed with empty result")
                return false
            }
            return result.getBoolean(ImsModifier.BUNDLE_RESULT)
        }

        suspend fun readSimInfoList(context: Context): List<SimInfo> {
            val result = startInstrumentation(context, SimReader::class.java, null, true)
            if (result == null) {
                Log.w(TAG, "readSimInfoList: failed with empty result")
                return emptyList()
            }
            val resultList =
                result.getParcelableArrayList(SimReader.BUNDLE_RESULT, SimInfo::class.java)
            return resultList!!
        }

        private suspend fun startInstrumentation(
            context: Context,
            cls: Class<*>,
            args: Bundle?,
            receiveResult: Boolean,
        ): Bundle? {
            val deferredResult = CompletableDeferred<Bundle?>()
            var watcher: IInstrumentationWatcher.Stub? = null
            if (receiveResult) {
                watcher = object : IInstrumentationWatcher.Stub() {
                    override fun instrumentationStatus(
                        name: ComponentName?,
                        resultCode: Int,
                        results: Bundle?
                    ) {
                    }

                    override fun instrumentationFinished(
                        name: ComponentName?,
                        resultCode: Int,
                        results: Bundle?
                    ) {
                        deferredResult.complete(results)
                    }
                }
            }

            val binder = ServiceManager.getService(Context.ACTIVITY_SERVICE)
            val am = IActivityManager.Stub.asInterface(ShizukuBinderWrapper(binder))
            val name = ComponentName(context, cls)
            val flags = 8 // ActivityManager.INSTR_FLAG_NO_RESTART
            val connection = UiAutomationConnection()
            try {
                Log.d(TAG, "startInstrumentation: call with component: $name")
                am.startInstrumentation(name, null, flags, args, watcher, connection, 0, null)
                Log.i(TAG, "instrumentation started successfully")
                if (receiveResult) {
                    return deferredResult.await()
                } else {
                    return null
                }
            } catch (e: Exception) {
                Log.e(TAG, "failed to start instrumentation", e)
                return null
            }
        }
    }
}
