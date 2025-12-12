package io.github.vvb2060.ims.privileged

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import kotlinx.parcelize.Parcelize

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
        uiAutomation.adoptShellPermissionIdentity()
        try {
            Log.d(TAG, "start read sim info list")
            val subManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val subList = subManager.activeSubscriptionInfoList
            val resultList = subList?.map {
                SimInfo(
                    it.subscriptionId,
                    it.displayName.toString(),
                    it.carrierName.toString(),
                    it.simSlotIndex,
                )
            } ?: emptyList()
            val bundle = Bundle()
            bundle.putParcelableArrayList(BUNDLE_RESULT, ArrayList(resultList))
            finish(Activity.RESULT_OK, bundle)
        } catch (e: Exception) {
            Log.e(TAG, "failed to read sim info list", e)
            finish(Activity.RESULT_CANCELED, Bundle())
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }
}

@Parcelize
data class SimInfo(
    val subId: Int,
    val displayName: String,
    val carrierName: String,
    val simSlotIndex: Int,
) : Parcelable