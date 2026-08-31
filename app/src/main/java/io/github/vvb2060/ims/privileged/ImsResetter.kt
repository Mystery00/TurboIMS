package io.github.vvb2060.ims.privileged

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.telephony.TelephonyFrameworkInitializer
import android.util.Log
import com.android.internal.telephony.ISub
import com.android.internal.telephony.ITelephony
import rikka.shizuku.ShizukuBinderWrapper

class ImsResetter : Instrumentation() {
    companion object {
        private const val TAG = "ImsResetter"
        const val BUNDLE_SELECT_SIM_ID = "select_sim_id"
        const val BUNDLE_RESULT = "result"
        const val BUNDLE_RESULT_MSG = "result_msg"
    }

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        if (arguments == null) {
            finish(Activity.RESULT_CANCELED, Bundle())
            return
        }

        val result = Bundle()
        val failure = runWithShellPermissionDelegation(TAG) {
            val subId = arguments.getInt(BUNDLE_SELECT_SIM_ID, -1)
            val sm = context.getSystemService(SubscriptionManager::class.java)
            val subIds: IntArray = if (subId == -1) {
                sm.javaClass.getMethod("getActiveSubscriptionIdList").invoke(sm) as IntArray
            } else {
                intArrayOf(subId)
            }

            val telephony = ITelephony.Stub.asInterface(
                ShizukuBinderWrapper(
                    TelephonyFrameworkInitializer
                        .getTelephonyServiceManager()
                        .getTelephonyServiceRegisterer()
                        .get()!!
                )
            )
            val sub = ISub.Stub.asInterface(
                ShizukuBinderWrapper(
                    TelephonyFrameworkInitializer
                        .getTelephonyServiceManager()
                        .getSubscriptionServiceRegisterer()
                        .get()!!
                )
            )

            for (id in subIds) {
                val slotIndex = sub.getSlotIndex(id)
                Log.i(TAG, "resetIms for subId $id slot $slotIndex")
                telephony.resetIms(slotIndex)
            }
        }
        if (failure == null) {
            result.putBoolean(BUNDLE_RESULT, true)
        } else {
            Log.e(TAG, "reset ims failed", failure)
            result.putBoolean(BUNDLE_RESULT, false)
            result.putString(BUNDLE_RESULT_MSG, failure.toPrivilegedErrorMessage())
        }
        finish(Activity.RESULT_OK, result)
    }
}
