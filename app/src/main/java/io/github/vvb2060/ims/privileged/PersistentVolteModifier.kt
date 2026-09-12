package io.github.vvb2060.ims.privileged

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.os.ServiceManager
import android.telephony.SubscriptionManager
import android.util.Log
import com.android.internal.telephony.ITelephony
import rikka.shizuku.ShizukuBinderWrapper

class PersistentVolteModifier : Instrumentation() {
    companion object {
        private const val TAG = "PersistentVolte"
        private val operationLock = Any()
        const val SUB_ID = "sub_id"
        const val ACTION = "action"
        const val QUERY = "query"
        const val ENABLE = "enable"
        const val RESTORE = "restore"
        const val RESTORE_FOR_RESET = "restore_for_reset"
        const val OPT_IN = "opt_in"
        const val USER_ENABLED = "user_enabled"
        const val IMS_REGISTERED = "ims_registered"
        const val CAN_RESTORE = "can_restore"
        const val ERROR = "error"
        const val UNSUPPORTED = "unsupported"
        const val COMPLETED = "completed"
    }

    private var arguments = Bundle()

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        this.arguments = arguments ?: Bundle()
        // 文件写入和系统服务调用放在 Instrumentation 工作线程，避免阻塞主界面。
        start()
    }

    @SuppressLint("MissingPermission")
    override fun onStart() {
        val result = Bundle()
        val subId = arguments.getInt(SUB_ID, -1)
        val action = arguments.getString(ACTION)
        synchronized(operationLock) {
            val failure = runWithShellPermissionDelegation(TAG) {
                require(action in setOf(QUERY, ENABLE, RESTORE, RESTORE_FOR_RESET)) { "Invalid persistent VoLTE action" }
                if (subId == -1 && action == RESTORE_FOR_RESET) {
                    val subscriptions = targetContext.getSystemService(SubscriptionManager::class.java)
                        .activeSubscriptionInfoList ?: error("Cannot read active subscriptions")
                    val failures = mutableListOf<String>()
                    for (sim in subscriptions) {
                        try {
                            restore(sim.subscriptionId)
                        } catch (t: Throwable) {
                            Log.e(TAG, "Restore failed for subId=${sim.subscriptionId}", t)
                            failures += "SIM ${sim.subscriptionId}: ${describeFailure(t)}"
                        }
                    }
                    check(failures.isEmpty()) { failures.joinToString("\n") }
                } else {
                    require(subId >= 0) { "Select a single active SIM" }
                    var operationFailure: Throwable? = null
                    try {
                        when (action) {
                            ENABLE -> enable(subId)
                            RESTORE, RESTORE_FOR_RESET -> restore(subId)
                        }
                    } catch (t: Throwable) {
                        operationFailure = t
                    }
                    // 操作失败后仍回读实际状态，但保留操作错误，不能把回读成功当作写入成功。
                    if (action != RESTORE_FOR_RESET) readState(subId, result)
                    operationFailure?.let { throw it }
                }
            }
            if (failure != null) {
                Log.e(TAG, "Persistent VoLTE operation failed", failure)
                result.putString(ERROR, describeFailure(failure))
                result.putBoolean(UNSUPPORTED, result.getBoolean(UNSUPPORTED) || isUnsupported(failure))
            }
            result.putBoolean(COMPLETED, failure == null && !result.containsKey(ERROR))
        }
        finish(Activity.RESULT_OK, result)
    }

    private fun enable(subId: Int) {
        val settings = PersistentVolteSettings(targetContext, subId)
        val identity = PersistentVolteBackup.identityDigest(settings.simIdentity())
        val backup = PersistentVolteBackup(targetContext, subId)
        val existing = backup.read()
        check(existing == null || existing.identity == identity) { "SIM identity does not match recovery record" }
        val before = settings.readOriginal()
        if (existing == null) backup.save(PersistentVolteBackup.Entry(identity, before))
        try {
            settings.enable()
            check(PersistentVolteBackup.identityDigest(settings.simIdentity()) == identity) { "SIM changed during operation" }
            Log.i(TAG, "Persistent VoLTE enabled for subId=$subId")
        } catch (t: Throwable) {
            try {
                check(PersistentVolteBackup.identityDigest(settings.simIdentity()) == identity) { "SIM changed; rollback deferred" }
                settings.restore(before)
                if (existing == null) backup.clear()
                Log.i(TAG, "Persistent VoLTE rollback completed for subId=$subId")
            } catch (rollback: Throwable) {
                t.addSuppressed(rollback)
                Log.e(TAG, "Persistent VoLTE rollback failed for subId=$subId", rollback)
            }
            throw t
        }
    }

    private fun restore(subId: Int) {
        val backup = PersistentVolteBackup(targetContext, subId)
        val entry = backup.read() ?: return
        val settings = PersistentVolteSettings(targetContext, subId)
        check(PersistentVolteBackup.identityDigest(settings.simIdentity()) == entry.identity) {
            "SIM identity does not match recovery record"
        }
        settings.restore(entry.values)
        check(PersistentVolteBackup.identityDigest(settings.simIdentity()) == entry.identity) {
            "SIM changed during restore; recovery record retained"
        }
        backup.clear()
        Log.i(TAG, "Original VoLTE settings restored for subId=$subId")
    }

    private fun readState(subId: Int, result: Bundle) {
        val errors = mutableListOf<String>()
        fun attempt(block: () -> Unit) {
            try {
                block()
            } catch (t: Throwable) {
                Log.w(TAG, "Persistent VoLTE state read failed for subId=$subId", t)
                errors += describeFailure(t)
                if (isUnsupported(t)) result.putBoolean(UNSUPPORTED, true)
            }
        }
        attempt { result.putBoolean(CAN_RESTORE, PersistentVolteBackup(targetContext, subId).read() != null) }
        attempt {
            val settings = PersistentVolteSettings(targetContext, subId)
            settings.simIdentity()
            attempt { result.putBoolean(OPT_IN, settings.readOptIn()) }
            attempt { result.putBoolean(USER_ENABLED, settings.readUserEnabled()) }
        }
        attempt {
            // 复用项目现有 ITelephony stub，仅查询注册快照，不据此判定持久化写入成功。
            val binder = ServiceManager.getService("phone") ?: error("Telephony service unavailable")
            val telephony = ITelephony.Stub.asInterface(ShizukuBinderWrapper(binder))
            result.putBoolean(IMS_REGISTERED, telephony.isImsRegistered(subId))
        }
        if (errors.isNotEmpty()) result.putString(ERROR, errors.joinToString("\n"))
    }

    private fun isUnsupported(t: Throwable): Boolean = when (t.privilegedRootCause()) {
        is ReflectiveOperationException, is LinkageError, is UnsupportedOperationException -> true
        else -> false
    }

    private fun describeFailure(t: Throwable): String = buildString {
        append(t.toPrivilegedErrorMessage())
        t.suppressed.forEach { append("\nRecovery: ").append(it.toPrivilegedErrorMessage()) }
    }
}
