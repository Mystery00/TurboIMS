package io.github.vvb2060.ims.privileged

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.util.Log

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
    override fun onStart() {
        // start() 只负责启动 Instrumentation 工作线程，读卡在 onStart 中执行。
        val result = Bundle()
        val failure = runWithShellPermissionDelegation(TAG) {
            Log.d(TAG, "start read sim info list")
            val subManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val subList = subManager.activeSubscriptionInfoList
            val resultList = subList ?: emptyList()
            Log.i(TAG, "read sim info list size: ${resultList.size}")
            result.putParcelableArrayList(BUNDLE_RESULT, ArrayList(resultList))
        }
        if (failure == null) {
            finish(Activity.RESULT_OK, result)
        } else {
            Log.e(TAG, "failed to read sim info list", failure)
            finish(Activity.RESULT_CANCELED, Bundle())
        }
    }
}
