package io.github.vvb2060.ims.privileged

import android.annotation.SuppressLint
import android.app.Activity
import android.app.IActivityManager
import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import android.os.ServiceManager
import android.system.Os
import android.telephony.SubscriptionManager
import android.util.Log
import rikka.shizuku.ShizukuBinderWrapper

class SimReader : Instrumentation() {
    companion object {
        private const val TAG = "SimReader"
        const val BUNDLE_RESULT = "sim_list"
    }

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    @SuppressLint("MissingPermission")
    override fun start() {
        super.start()
        val binder = ServiceManager.getService(Context.ACTIVITY_SERVICE)
        val am = IActivityManager.Stub.asInterface(ShizukuBinderWrapper(binder))
        Log.i(TAG, "starting shell permission delegation")
        am.startDelegateShellPermissionIdentity(Os.getuid(), null)
        try {
            Log.d(TAG, "start read sim info list")
            val subManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val subList = subManager.activeSubscriptionInfoList
            val resultList = subList ?: emptyList()
            Log.i(TAG, "read sim info list size: ${resultList.size}")
            val bundle = Bundle()
            bundle.putParcelableArrayList(BUNDLE_RESULT, ArrayList(resultList))
            finish(Activity.RESULT_OK, bundle)
        } catch (e: Exception) {
            Log.e(TAG, "failed to read sim info list", e)
            finish(Activity.RESULT_CANCELED, Bundle())
        } finally {
            am.stopDelegateShellPermissionIdentityCompat()
            Log.i(TAG, "stopped shell permission delegation")
        }
    }
}
