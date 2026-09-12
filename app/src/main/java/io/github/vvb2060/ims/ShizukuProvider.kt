package io.github.vvb2060.ims

import android.app.IActivityManager
import android.app.Activity
import android.app.IInstrumentationWatcher
import android.app.UiAutomationConnection
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.ServiceManager
import android.telephony.SubscriptionInfo
import android.util.Log
import io.github.vvb2060.ims.model.ImsCapabilityStatus
import io.github.vvb2060.ims.model.PersistentVolteState
import io.github.vvb2060.ims.model.SimSelection
import io.github.vvb2060.ims.privileged.BrokerInstrumentation
import io.github.vvb2060.ims.privileged.isCarrierConfigPermissionError
import io.github.vvb2060.ims.privileged.ImsCapabilityReader
import io.github.vvb2060.ims.privileged.ImsModifier
import io.github.vvb2060.ims.privileged.ImsResetter
import io.github.vvb2060.ims.privileged.SimReader
import io.github.vvb2060.ims.privileged.PersistentVolteModifier
import io.github.vvb2060.ims.privileged.toPrivilegedErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.lsposed.hiddenapibypass.LSPass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.ShizukuProvider

class ShizukuProvider : ShizukuProvider() {
    override fun onCreate(): Boolean {
        LSPass.setHiddenApiExemptions("")
        // 自动恢复由 Application 中的协调器按用户开关调度，此处仅初始化隐藏 API 访问。
        return super.onCreate()
    }

    companion object {
        private const val TAG = "ShizukuProvider"
        private const val INSTRUMENTATION_TIMEOUT_MS = 15_000L
        private val instrumentationMutex = Mutex()
        private var activeInstrumentation: CompletableDeferred<Bundle?>? = null

        suspend fun persistentVolte(context: Context, subId: Int, action: String): PersistentVolteState {
            try {
                check(rikka.shizuku.Shizuku.pingBinder()) { "Shizuku binder is unavailable" }
                check(!rikka.shizuku.Shizuku.isPreV11()) { "Shizuku update required" }
                check(rikka.shizuku.Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    "Shizuku permission is not granted"
                }
                val args = Bundle().apply {
                    putInt(PersistentVolteModifier.SUB_ID, subId)
                    putString(PersistentVolteModifier.ACTION, action)
                }
                val result = startInstrumentation(context, PersistentVolteModifier::class.java, args, true)
                    ?: return PersistentVolteState(subId, error = "No result; refresh state before retrying")
                fun readBoolean(key: String): Boolean? =
                    if (result.containsKey(key)) result.getBoolean(key) else null
                return PersistentVolteState(
                    subId = subId,
                    optIn = readBoolean(PersistentVolteModifier.OPT_IN),
                    userEnabled = readBoolean(PersistentVolteModifier.USER_ENABLED),
                    imsRegistered = readBoolean(PersistentVolteModifier.IMS_REGISTERED),
                    canRestore = result.getBoolean(PersistentVolteModifier.CAN_RESTORE),
                    unsupported = result.getBoolean(PersistentVolteModifier.UNSUPPORTED),
                    error = result.getString(PersistentVolteModifier.ERROR)
                        ?: if (!result.getBoolean(PersistentVolteModifier.COMPLETED)) "Incomplete instrumentation result" else null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                Log.e(TAG, "Persistent VoLTE request failed", t)
                return PersistentVolteState(subId, error = t.toPrivilegedErrorMessage())
            }
        }

        suspend fun overrideImsConfig(
            context: Context,
            data: Bundle,
            canStart: () -> Boolean = { true },
        ): String? {
            val primaryArgs = Bundle(data)
            val result = startInstrumentation(context, ImsModifier::class.java, primaryArgs, true, canStart)
            if (result == null) {
                if (!canStart()) return "Automatic restore disabled before execution"
                Log.w(TAG, "overrideImsConfig: failed with empty result")
                return tryOverrideWithBroker(context, data, "failed with empty result", canStart)
            }
            if (result.getBoolean(ImsModifier.BUNDLE_RESULT)) {
                return null
            }
            val msg = result.getString(ImsModifier.BUNDLE_RESULT_MSG) ?: "unknown error"
            // 权限受限或结果为空时，通过 Broker 重试临时配置覆盖。
            return tryOverrideWithBroker(context, data, msg, canStart)
        }

        private suspend fun tryOverrideWithBroker(
            context: Context,
            data: Bundle,
            msg: String,
            canStart: () -> Boolean,
        ): String? {
            if (!canStart()) return "Automatic restore disabled before fallback"
            if (!shouldRetryWithBroker(msg)) {
                return msg
            }
            val brokerArgs = Bundle(data)
            val brokerResult =
                startInstrumentation(context, BrokerInstrumentation::class.java, brokerArgs, true, canStart)
            if (brokerResult == null) {
                return "$msg\n\nBroker: failed with empty result"
            }
            if (brokerResult.getBoolean(ImsModifier.BUNDLE_RESULT)) {
                return null
            }
            val brokerMsg =
                brokerResult.getString(ImsModifier.BUNDLE_RESULT_MSG) ?: "unknown error"
            return "$msg\n\nBroker: $brokerMsg"
        }

        private fun shouldRetryWithBroker(msg: String): Boolean {
            return isCarrierConfigPermissionError(msg) ||
                    msg.contains("failed with empty result", ignoreCase = true)
        }

        suspend fun readImsCapabilities(context: Context, subId: Int): ImsCapabilityStatus? {
            val args = Bundle().apply {
                putInt(ImsCapabilityReader.BUNDLE_SELECT_SIM_ID, subId)
            }
            val result = startInstrumentation(context, ImsCapabilityReader::class.java, args, true)
            if (result == null) {
                Log.w(TAG, "readImsCapabilities: failed with empty result")
                return null
            }
            if (result.getString(ImsCapabilityReader.BUNDLE_RESULT_MSG) != null) {
                Log.w(TAG, "readImsCapabilities: ${result.getString(ImsCapabilityReader.BUNDLE_RESULT_MSG)}")
                return null
            }
            val requiredKeys = listOf(
                ImsCapabilityReader.BUNDLE_IMS_REGISTERED, ImsCapabilityReader.BUNDLE_VOLTE,
                ImsCapabilityReader.BUNDLE_VOWIFI, ImsCapabilityReader.BUNDLE_VONR,
                ImsCapabilityReader.BUNDLE_VT, ImsCapabilityReader.BUNDLE_NR_NSA,
                ImsCapabilityReader.BUNDLE_NR_SA,
            )
            if (!requiredKeys.all(result::containsKey)) {
                Log.w(TAG, "readImsCapabilities: incomplete result")
                return null
            }
            return ImsCapabilityStatus(
                isRegistered = result.getBoolean(ImsCapabilityReader.BUNDLE_IMS_REGISTERED),
                isVolteAvailable = result.getBoolean(ImsCapabilityReader.BUNDLE_VOLTE),
                isVoWifiAvailable = result.getBoolean(ImsCapabilityReader.BUNDLE_VOWIFI),
                isVoNrAvailable = result.getBoolean(ImsCapabilityReader.BUNDLE_VONR),
                isVtAvailable = result.getBoolean(ImsCapabilityReader.BUNDLE_VT),
                isNrNsaAvailable = result.getBoolean(ImsCapabilityReader.BUNDLE_NR_NSA),
                isNrSaAvailable = result.getBoolean(ImsCapabilityReader.BUNDLE_NR_SA),
            )
        }

        suspend fun resetIms(context: Context, subId: Int): String? {
            val args = Bundle().apply {
                putInt(ImsResetter.BUNDLE_SELECT_SIM_ID, subId)
            }
            val result = startInstrumentation(context, ImsResetter::class.java, args, true)
            if (result == null) return "No instrumentation result"
            if (!result.getBoolean(ImsResetter.BUNDLE_RESULT)) {
                return result.getString(ImsResetter.BUNDLE_RESULT_MSG) ?: "Incomplete IMS reset result"
            }
            return null
        }

        suspend fun readSimInfoList(context: Context): List<SimSelection> {
            val result = startInstrumentation(context, SimReader::class.java, null, true)
            if (result == null) {
                Log.w(TAG, "readSimInfoList: failed with empty result")
                return emptyList()
            }
            val subList =
                result.getParcelableArrayList(SimReader.BUNDLE_RESULT, SubscriptionInfo::class.java)
            val resultList = subList?.map {
                SimSelection(
                    it.subscriptionId,
                    it.displayName.toString(),
                    it.carrierName.toString(),
                    it.simSlotIndex,
                )
            } ?: emptyList()
            return resultList
        }

        private suspend fun startInstrumentation(
            context: Context,
            cls: Class<*>,
            args: Bundle?,
            receiveResult: Boolean,
            canStart: () -> Boolean = { true },
        ): Bundle? = instrumentationMutex.withLock {
            // 新入口使用工作线程，所有 Instrumentation 必须串行，避免 UID 权限委托互相清理。
            // 超时或协程取消不代表设备端操作结束；旧 watcher 返回前不启动下一次操作。
            val previous = activeInstrumentation
            if (previous != null && !previous.isCompleted) {
                val finished = withTimeoutOrNull(INSTRUMENTATION_TIMEOUT_MS) {
                    previous.await()
                    true
                } ?: false
                if (!finished) {
                    Log.w(TAG, "Previous instrumentation is still running")
                    return@withLock null
                }
            }
            // 等待锁或上次调用期间用户可能关闭自动恢复；主路径和 Broker 启动前都要重查。
            if (!canStart()) return@withLock null
            startInstrumentationLocked(context, cls, args, receiveResult)
        }

        private suspend fun startInstrumentationLocked(
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
                        // 系统取消或启动异常不属于业务成功，不能把空 Bundle 当作全部能力关闭。
                        if (resultCode != Activity.RESULT_OK) {
                            Log.w(TAG, "Instrumentation finished with resultCode=$resultCode: $name")
                            deferredResult.complete(null)
                        } else {
                            deferredResult.complete(results)
                        }
                    }
                }
            }

            try {
                // 读卡等入口也会在 Shizuku 未启动时被调用；初始化和隐藏 API 链接均纳入错误边界。
                check(Shizuku.pingBinder()) { "Shizuku binder is unavailable" }
                check(!Shizuku.isPreV11()) { "Shizuku update required" }
                check(Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    "Shizuku permission is not granted"
                }
                val binder = ServiceManager.getService(Context.ACTIVITY_SERVICE)
                    ?: error("Activity service unavailable")
                val am = IActivityManager.Stub.asInterface(ShizukuBinderWrapper(binder))
                val name = ComponentName(context, cls)
                val flags = 8 // 使用 INSTR_FLAG_NO_RESTART，保留主应用进程。
                val connection = UiAutomationConnection()
                Log.d(TAG, "startInstrumentation: call with component: $name")
                val started = am.startInstrumentation(
                    name,
                    null,
                    flags,
                    args,
                    watcher,
                    connection,
                    0,
                    null
                )
                if (!started) {
                    Log.e(TAG, "instrumentation start rejected for component: $name")
                    return null
                }
                Log.i(TAG, "instrumentation started successfully")
                if (receiveResult) {
                    activeInstrumentation = deferredResult
                    val result = withTimeoutOrNull(INSTRUMENTATION_TIMEOUT_MS) {
                        deferredResult.await()
                    }
                    if (result == null) {
                        Log.e(TAG, "instrumentation result timeout for component: $name")
                    }
                    return result
                }
                return null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "failed to start instrumentation", e)
                return null
            }
        }
    }
}
