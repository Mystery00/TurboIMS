package io.github.vvb2060.ims

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import io.github.vvb2060.ims.privileged.ImsModifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/** Shizuku 就绪后按用户选择恢复配置；不负责启动 Shizuku，也不恢复持久化 VoLTE。 */
class AutoRestoreController(private val context: Context) {
    val repository = ConfigurationRepository(context)
    private val notifier = AutoRestoreNotifier(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _enabled = MutableStateFlow(repository.enabled)
    val enabled = _enabled.asStateFlow()
    private var restoreJob: Job? = null
    private var started = false
    private var permissionPending = false

    private val binderReceived = Shizuku.OnBinderReceivedListener { schedule() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        permissionPending = false
        restoreJob?.cancel()
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, result ->
        permissionPending = false
        // 手动授予权限也可以继续等待中的恢复，但关闭开关后绝不恢复。
        if (result == PackageManager.PERMISSION_GRANTED) schedule()
    }

    fun start() {
        if (started) return
        started = true
        Shizuku.addRequestPermissionResultListener(permissionResult)
        Shizuku.addBinderDeadListener(binderDead)
        // Provider 可能早于 Application 收到 Binder，必须覆盖已经就绪的情形。
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
    }

    fun setEnabled(value: Boolean) {
        if (value == _enabled.value) return
        repository.enabled = value
        _enabled.value = value
        if (value) {
            repository.beginOptIn()
            schedule()
        }
        // 不取消正在执行的 Instrumentation：等待其返回并记录结果，下一次写入前再检查开关。
    }

    fun requestPermission(requestCode: Int) {
        if (permissionPending) return
        permissionPending = true
        try {
            Shizuku.requestPermission(requestCode)
        } catch (e: Exception) {
            permissionPending = false
            throw e
        }
    }

    fun schedule() {
        if (!_enabled.value || restoreJob?.isActive == true) return
        restoreJob = scope.launch {
            val results = mutableMapOf<Int, Boolean>()
            try {
                if (!repository.hasRestorableHistory()) return@launch
                val boot = repository.bootCount()
                if (boot < 0) {
                    Log.w(TAG, "Boot count unavailable; skipping automatic restore")
                    return@launch
                }
                repeat(MAX_ATTEMPTS) { attempt ->
                    if (!_enabled.value || !Shizuku.pingBinder()) return@launch
                    if (Shizuku.isPreV11()) {
                        Log.w(TAG, "Shizuku update required; skipping automatic restore")
                        return@launch
                    }
                    if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                        if (!permissionPending && !Shizuku.shouldShowRequestPermissionRationale() &&
                            repository.claimPermissionRequest(boot)) {
                            Log.i(TAG, "Requesting Shizuku permission for automatic restore")
                            requestPermission(PERMISSION_REQUEST_CODE)
                        }
                        return@launch
                    }
                    val complete = ConfigurationOperations.run {
                        if (!_enabled.value) return@run true
                        val sims = ShizukuProvider.readSimInfoList(context)
                        if (sims.isEmpty()) return@run false
                        var success = true
                        for (sim in sims) {
                            if (!_enabled.value || !Shizuku.pingBinder()) return@run true
                            val target = repository.restoreTarget(sim.subId) ?: continue
                            if (repository.wasApplied(sim.subId, boot, target)) continue
                            // 全卡历史中的名称字段仍遵循手动全卡应用规则，不得转为单卡名称覆盖。
                            val bundle = ConfigurationRepository.buildBundle(target.sourceSubId, target.config)
                                .apply { putInt(ImsModifier.BUNDLE_SELECT_SIM_ID, sim.subId) }
                            val error = ShizukuProvider.overrideImsConfig(context, bundle) { _enabled.value }
                            if (error == null) {
                                results[sim.subId] = true
                                repository.markApplied(sim.subId, boot, target)
                                Log.i(TAG, "Automatically restored config for subId=${sim.subId}")
                            } else {
                                // 用户关闭后未启动的调用不算应用失败，也不触发结果提示。
                                if (_enabled.value) results[sim.subId] = false
                                success = false
                                Log.w(TAG, "Automatic restore failed for subId=${sim.subId}: $error")
                            }
                        }
                        success
                    }
                    if (complete) return@launch
                    if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_DELAY_MS)
                }
                Log.w(TAG, "Automatic restore retry limit reached")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e(TAG, "Automatic restore failed", e)
            } finally {
                // 一轮有限重试只汇总一次，失败后重试成功的 SIM 不再计入失败。
                if (results.isNotEmpty()) {
                    notifier.showResult(results.count { it.value }, results.count { !it.value })
                }
            }
        }
    }

    fun close() {
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
        scope.cancel()
        notifier.close()
    }

    companion object {
        private const val TAG = "AutoRestore"
        private const val PERMISSION_REQUEST_CODE = 0x4152
        private const val MAX_ATTEMPTS = 6
        private const val RETRY_DELAY_MS = 10_000L
    }
}
