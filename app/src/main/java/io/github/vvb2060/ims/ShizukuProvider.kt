package io.github.vvb2060.ims

import android.app.IActivityManager
import android.app.IInstrumentationWatcher
import android.app.UiAutomationConnection
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.ServiceManager
import android.telephony.SubscriptionInfo
import android.util.Log
import io.github.vvb2060.ims.model.SimSelection
import io.github.vvb2060.ims.privileged.BrokerInstrumentation
import io.github.vvb2060.ims.privileged.ConfigReader
import io.github.vvb2060.ims.privileged.ImsModifier
import io.github.vvb2060.ims.privileged.ImsResetter
import io.github.vvb2060.ims.privileged.ImsStatusReader
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

        suspend fun overrideImsConfig(context: Context, data: Bundle): String? {
            val primaryArgs = Bundle(data)
            val result = startInstrumentation(context, ImsModifier::class.java, primaryArgs, true)
            if (result == null) {
                Log.w(TAG, "overrideImsConfig: failed with empty result")
                return tryOverrideWithBroker(context, data, "failed with empty result")
            }
            if (result.getBoolean(ImsModifier.BUNDLE_RESULT)) {
                return null
            }
            val msg = result.getString(ImsModifier.BUNDLE_RESULT_MSG) ?: "unknown error"
            // Retry via broker when persistent override is restricted or result is empty.
            return tryOverrideWithBroker(context, data, msg)
        }

        private suspend fun tryOverrideWithBroker(
            context: Context,
            data: Bundle,
            msg: String,
        ): String? {
            if (!shouldRetryWithBroker(msg)) {
                return msg
            }
            val brokerArgs = Bundle(data)
            val brokerResult =
                startInstrumentation(context, BrokerInstrumentation::class.java, brokerArgs, true)
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
            return msg.contains("SecurityException") ||
                    msg.contains("does not have android.permission.MODIFY_PHONE_STATE") ||
                    msg.contains("No permission to write to carrier config") ||
                    msg.contains("failed with empty result")
        }

        suspend fun readCarrierConfig(
            context: Context,
            subId: Int,
            keys: Array<String>
        ): Bundle? {
            val args = Bundle()
            args.putInt(ConfigReader.BUNDLE_SELECT_SIM_ID, subId)
            args.putStringArray(ConfigReader.BUNDLE_KEYS, keys)
            val result = startInstrumentation(context, ConfigReader::class.java, args, true)
            if (result == null) {
                Log.w(TAG, "readCarrierConfig: failed with empty result")
                return null
            }
            return result.getBundle(ConfigReader.BUNDLE_RESULT)
        }

        suspend fun readImsRegistrationStatus(context: Context, subId: Int): Boolean? {
            val args = Bundle().apply {
                putInt(ImsStatusReader.BUNDLE_SELECT_SIM_ID, subId)
            }
            val result = startInstrumentation(context, ImsStatusReader::class.java, args, true)
            if (result == null) return null
            if (result.getString(ImsStatusReader.BUNDLE_RESULT_MSG) != null) return null
            return result.getBoolean(ImsStatusReader.BUNDLE_RESULT)
        }

        suspend fun resetIms(context: Context, subId: Int): String? {
            val args = Bundle().apply {
                putInt(ImsResetter.BUNDLE_SELECT_SIM_ID, subId)
            }
            val result = startInstrumentation(context, ImsResetter::class.java, args, true)
            if (result == null) return "Unknown error"
            return result.getString(ImsResetter.BUNDLE_RESULT_MSG)
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
                }
                return null
            } catch (e: Exception) {
                Log.e(TAG, "failed to start instrumentation", e)
                return null
            }
        }
    }
}
