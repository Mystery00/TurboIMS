package io.github.vvb2060.ims.privileged

import android.app.Activity
import android.app.IActivityManager
import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import android.os.ServiceManager
import android.system.Os
import android.telephony.AccessNetworkConstants
import android.telephony.NetworkRegistrationInfo
import android.telephony.TelephonyManager
import android.util.Log
import com.android.internal.telephony.ITelephony
import rikka.shizuku.ShizukuBinderWrapper

class ImsCapabilityReader : Instrumentation() {
    companion object {
        private const val TAG = "ImsCapabilityReader"
        const val BUNDLE_SELECT_SIM_ID = "select_sim_id"
        const val BUNDLE_IMS_REGISTERED = "ims_registered"
        const val BUNDLE_VOLTE = "volte"
        const val BUNDLE_VOWIFI = "vowifi"
        const val BUNDLE_VONR = "vonr"
        const val BUNDLE_VT = "vt"
        const val BUNDLE_NR_NSA = "nr_nsa"
        const val BUNDLE_NR_SA = "nr_sa"
        const val BUNDLE_RESULT_MSG = "result_msg"

        // IMS 注册技术常量（来自 ImsRegistrationImplBase，值稳定）
        private const val REGISTRATION_TECH_LTE = 0
        private const val REGISTRATION_TECH_IWLAN = 1
        private const val REGISTRATION_TECH_NR = 2

        // MmTelCapabilities 能力类型常量（来自 MmTelFeature.MmTelCapabilities，值稳定）
        private const val CAPABILITY_TYPE_VOICE = 1
        private const val CAPABILITY_TYPE_VIDEO = 2
    }

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        if (arguments == null) {
            finish(Activity.RESULT_CANCELED, Bundle())
            return
        }

        val result = Bundle()
        val binder = ServiceManager.getService(Context.ACTIVITY_SERVICE)
        val am = IActivityManager.Stub.asInterface(ShizukuBinderWrapper(binder))
        am.startDelegateShellPermissionIdentity(Os.getuid(), null)
        try {
            val subId = arguments.getInt(BUNDLE_SELECT_SIM_ID, -1)
            if (subId < 0) {
                result.putString(BUNDLE_RESULT_MSG, "invalid subId")
                finish(Activity.RESULT_OK, result)
                return
            }

            // 1. 整体 IMS 注册状态（隐藏 API，通过已有 stub 调用）
            val telephony = ITelephony.Stub.asInterface(
                ShizukuBinderWrapper(ServiceManager.getService("phone"))
            )
            result.putBoolean(BUNDLE_IMS_REGISTERED, telephony.isImsRegistered(subId))

            // 2. 逐项 IMS 能力（@SystemApi ImsMmTelManager，通过反射调用，与 *#*#4636#*#* 相同）
            val mmTelClass = Class.forName("android.telephony.ims.ImsMmTelManager")
            // createForSubscriptionId 是 @SystemApi，公开 SDK 不暴露，使用反射调用
            val createMethod = mmTelClass.getMethod("createForSubscriptionId", Int::class.javaPrimitiveType)
            val mmTelManager = createMethod.invoke(null, subId)
            // isAvailable 是 @SystemApi，公开 SDK 不暴露，使用反射调用
            val isAvailableMethod = mmTelClass.getMethod(
                "isAvailable",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            result.putBoolean(
                BUNDLE_VOLTE,
                isAvailableMethod.invoke(mmTelManager, CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_LTE) as Boolean
            )
            result.putBoolean(
                BUNDLE_VOWIFI,
                isAvailableMethod.invoke(mmTelManager, CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_IWLAN) as Boolean
            )
            result.putBoolean(
                BUNDLE_VONR,
                isAvailableMethod.invoke(mmTelManager, CAPABILITY_TYPE_VOICE, REGISTRATION_TECH_NR) as Boolean
            )
            result.putBoolean(
                BUNDLE_VT,
                isAvailableMethod.invoke(mmTelManager, CAPABILITY_TYPE_VIDEO, REGISTRATION_TECH_LTE) as Boolean
            )

            // 3. NR NSA/SA 可用性（来自 ServiceState，API 30+ 公开 API）
            val tm = context.getSystemService(TelephonyManager::class.java)
                .createForSubscriptionId(subId)
            val ss = tm.serviceState
            val nrRegInfo = ss?.networkRegistrationInfoList?.firstOrNull {
                it.transportType == AccessNetworkConstants.TRANSPORT_TYPE_WWAN &&
                        (it.domain and NetworkRegistrationInfo.DOMAIN_PS) != 0
            }
            // getNrState 是 @hide API，使用反射调用
            val nrState = if (nrRegInfo != null) {
                val getNrStateMethod = nrRegInfo.javaClass.getMethod("getNrState")
                getNrStateMethod.invoke(nrRegInfo) as Int
            } else {
                NetworkRegistrationInfo.NR_STATE_NONE
            }
            result.putBoolean(
                BUNDLE_NR_NSA,
                nrState == NetworkRegistrationInfo.NR_STATE_CONNECTED ||
                        nrState == NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED
            )
            result.putBoolean(
                BUNDLE_NR_SA,
                nrRegInfo?.accessNetworkTechnology == TelephonyManager.NETWORK_TYPE_NR
            )
        } catch (t: Throwable) {
            Log.e(TAG, "read IMS capabilities failed", t)
            result.putString(BUNDLE_RESULT_MSG, t.message ?: t.javaClass.simpleName)
        } finally {
            am.stopDelegateShellPermissionIdentity()
        }

        finish(Activity.RESULT_OK, result)
    }
}
