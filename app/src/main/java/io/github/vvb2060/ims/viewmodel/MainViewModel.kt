package io.github.vvb2060.ims.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.vvb2060.ims.BuildConfig
import io.github.vvb2060.ims.ConfigurationOperations
import io.github.vvb2060.ims.ConfigurationRepository
import io.github.vvb2060.ims.R
import io.github.vvb2060.ims.ShizukuProvider
import io.github.vvb2060.ims.model.Feature
import io.github.vvb2060.ims.model.ImsCapabilityStatus
import io.github.vvb2060.ims.model.PersistentVolteState
import io.github.vvb2060.ims.model.FeatureValue
import io.github.vvb2060.ims.model.ShizukuStatus
import io.github.vvb2060.ims.model.SimSelection
import io.github.vvb2060.ims.model.SystemInfo
import io.github.vvb2060.ims.privileged.ImsModifier
import io.github.vvb2060.ims.privileged.PersistentVolteModifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/**
 * 主界面的 ViewModel，负责管理 UI 状态和业务逻辑。
 * 包括 Shizuku 状态监听、系统信息加载、SIM 卡信息加载以及 IMS 配置的读写。
 */
class MainViewModel(private val application: Application) : AndroidViewModel(application) {
    private var toast: Toast? = null
    private val operationGate = OperationGate()

    private val autoRestore = (application as io.github.vvb2060.ims.Application).autoRestore
    private val configurations = autoRestore.repository
    val autoRestoreEnabled = autoRestore.enabled
    val isOperationInProgress = ConfigurationOperations.busy

    fun setAutoRestoreEnabled(enabled: Boolean) = autoRestore.setEnabled(enabled)

    private val _persistentVolteState = MutableStateFlow<PersistentVolteState?>(null)
    val persistentVolteState = _persistentVolteState.asStateFlow()
    private var persistentVolteSubId: Int? = null
    private var pendingPersistentRefresh = false

    fun selectPersistentVolteSim(subId: Int?) {
        persistentVolteSubId = subId?.takeIf { it >= 0 }
        _persistentVolteState.value = persistentVolteSubId?.let { PersistentVolteState(it) }
        refreshPersistentVolte()
    }

    fun refreshPersistentVolte() {
        val subId = persistentVolteSubId ?: return
        if (_shizukuStatus.value != ShizukuStatus.READY) return
        if (isOperationInProgress.value) {
            pendingPersistentRefresh = true
            return
        }
        launchExclusiveOperation {
            publishPersistentVolte(ShizukuProvider.persistentVolte(application, subId, PersistentVolteModifier.QUERY))
        }
    }

    fun onPersistentVolteChange(subId: Int, restore: Boolean) {
        if (subId < 0 || subId != persistentVolteSubId || _shizukuStatus.value != ShizukuStatus.READY) return
        launchExclusiveOperation {
            val result = ShizukuProvider.persistentVolte(
                application, subId,
                if (restore) PersistentVolteModifier.RESTORE else PersistentVolteModifier.ENABLE,
            )
            publishPersistentVolte(result)
            if (result.error == null) {
                toast(application.getString(if (restore) R.string.persistent_volte_restored else R.string.persistent_volte_applied))
            } else {
                toast(application.getString(R.string.config_failed, result.error), false)
            }
        }
    }

    private fun publishPersistentVolte(state: PersistentVolteState) {
        // 异步结果只更新对应的当前 SIM；Shizuku 断开后不再展示旧快照。
        if (state.subId == persistentVolteSubId && _shizukuStatus.value == ShizukuStatus.READY) {
            _persistentVolteState.value = state
        }
    }

    // 系统信息状态流
    private val _systemInfo = MutableStateFlow(SystemInfo())
    val systemInfo: StateFlow<SystemInfo> = _systemInfo.asStateFlow()

    // Shizuku 运行状态流
    private val _shizukuStatus = MutableStateFlow(ShizukuStatus.CHECKING)
    val shizukuStatus: StateFlow<ShizukuStatus> = _shizukuStatus.asStateFlow()

    // 所有可用 SIM 卡列表流
    private val _allSimList = MutableStateFlow<List<SimSelection>>(emptyList())
    val allSimList: StateFlow<List<SimSelection>> = _allSimList.asStateFlow()

    // Shizuku Binder 接收监听器（服务连接/授权后触发）
    private val binderListener = Shizuku.OnBinderReceivedListener { updateShizukuStatus() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { updateShizukuStatus() }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        updateShizukuStatus()
        // 回调与服务断开可能相邻发生，读卡入口统一处理 Binder 失效和权限错误。
        loadSimList()
    }

    init {
        viewModelScope.launch {
            ConfigurationOperations.busy.collect { busy ->
                if (!busy) drainPendingPersistentRefresh()
            }
        }
        Shizuku.addRequestPermissionResultListener(permissionListener)
        autoRestore.schedule()
        loadSimList()
        loadSystemInfo()
        updateShizukuStatus()
        Shizuku.addBinderReceivedListener(binderListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    override fun onCleared() {
        super.onCleared()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
    }

    /**
     * 更新 Shizuku 的当前状态。
     * 检查服务是否运行、是否需要更新以及权限授予情况。
     */
    fun updateShizukuStatus() {
        viewModelScope.launch {
            _shizukuStatus.value = try {
                when {
                    !Shizuku.pingBinder() -> ShizukuStatus.NOT_RUNNING
                    Shizuku.isPreV11() -> ShizukuStatus.NEED_UPDATE
                    Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShizukuStatus.NO_PERMISSION
                    else -> ShizukuStatus.READY
                }
            } catch (e: Exception) {
                Log.w("MainViewModel", "Failed to check Shizuku status", e)
                ShizukuStatus.NOT_RUNNING
            }
        }
    }

    /**
     * 请求 Shizuku 授权。
     */
    fun requestShizukuPermission(requestCode: Int) {
        viewModelScope.launch {
            try {
                when {
                    !Shizuku.pingBinder() -> _shizukuStatus.value = ShizukuStatus.NOT_RUNNING
                    Shizuku.isPreV11() -> _shizukuStatus.value = ShizukuStatus.NEED_UPDATE
                    else -> autoRestore.requestPermission(requestCode)
                }
            } catch (e: Exception) {
                Log.w("MainViewModel", "Failed to request Shizuku permission", e)
                updateShizukuStatus()
            }
        }
    }

    /**
     * 加载默认的功能配置。
     * 当没有保存的配置时使用此默认值。
     */
    fun loadDefaultPreferences(): Map<Feature, FeatureValue> {
        val featureSwitches = linkedMapOf<Feature, FeatureValue>()
        for (feature in Feature.entries) {
            featureSwitches.put(feature, FeatureValue(feature.defaultValue, feature.valueType))
        }
        return featureSwitches
    }

    /**
     * 通过 Shizuku 读取设备上的 SIM 卡信息。
     * 并在列表头部添加“所有 SIM 卡”选项。
     */
    fun loadSimList() {
        viewModelScope.launch {
            val simInfoList = ShizukuProvider.readSimInfoList(application)
            val resultList = simInfoList.toMutableList()
            // 添加默认的 "所有 SIM 卡" 选项 (subId = -1)
            val title = application.getString(R.string.all_sim)
            resultList.add(0, SimSelection(-1, "", "", -1, title))
            _allSimList.value = resultList
        }
    }

    /**
     * 加载当前应用和系统的基本信息。
     */
    private fun loadSystemInfo() {
        viewModelScope.launch {
            _systemInfo.value = SystemInfo(
                appVersionName = BuildConfig.VERSION_NAME,
                androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                systemVersion = Build.DISPLAY,
                securityPatchVersion = Build.VERSION.SECURITY_PATCH,
            )
        }
    }

    /**
     * 应用 IMS 配置到选定的 SIM 卡。
     * 此操作会调用 ShizukuProvider 进行特权操作，并保存当前配置到本地。
     */
    fun onApplyConfiguration(selectedSim: SimSelection, map: Map<Feature, FeatureValue>) {
        // 排队等待自动恢复前就固定本次应用内容，避免等待期间的 UI 编辑污染成功历史。
        val appliedConfig = map.toMap()
        launchExclusiveOperation {
            val bundle = ConfigurationRepository.buildBundle(selectedSim.subId, appliedConfig)

            // 调用 Shizuku 服务进行实际修改
            val resultMsg = ShizukuProvider.overrideImsConfig(application, bundle)
            if (resultMsg == null) {
                // 仅在系统配置成功后保存历史，避免失败尝试覆盖上次有效配置。
                configurations.save(selectedSim.subId, appliedConfig)
                val appliedIds = if (selectedSim.subId == -1) {
                    ShizukuProvider.readSimInfoList(application).map { it.subId }
                } else listOf(selectedSim.subId)
                configurations.markManualApply(appliedIds)
                toast(application.getString(R.string.config_success_message))
            } else {
                toast(application.getString(R.string.config_failed, resultMsg), false)
            }
        }
    }

    /** 加载界面历史；重置只取消自动恢复资格，不删除历史内容。 */
    fun loadConfiguration(subId: Int): Map<Feature, FeatureValue>? = configurations.load(subId)

    /**
     * 通过 Shizuku 读取系统当前实时 IMS 能力状态。
     */
    suspend fun loadRealSystemConfig(subId: Int): ImsCapabilityStatus? {
        return ShizukuProvider.readImsCapabilities(application, subId)
    }

    /**
     * 重置选中 SIM 卡的配置到运营商默认状态。
     */
    fun onResetConfiguration(selectedSim: SimSelection) {
        launchExclusiveOperation {
            val restored = ShizukuProvider.persistentVolte(
                application, selectedSim.subId, PersistentVolteModifier.RESTORE_FOR_RESET,
            )
            pendingPersistentRefresh = true
            if (restored.error != null) {
                toast(application.getString(R.string.config_failed, restored.error), false)
                return@launchExclusiveOperation
            }
            val bundle = ImsModifier.buildResetBundle()
            bundle.putInt(ImsModifier.BUNDLE_SELECT_SIM_ID, selectedSim.subId)
            val resultMsg = ShizukuProvider.overrideImsConfig(application, bundle)
            if (resultMsg == null) {
                configurations.recordReset(selectedSim.subId)
                toast(application.getString(R.string.config_success_reset_message))
            } else {
                toast(application.getString(R.string.config_failed, resultMsg), false)
            }
        }
    }

    fun onResetIms(simSelection: SimSelection) {
        launchExclusiveOperation {
            try {
                val error = ShizukuProvider.resetIms(application, simSelection.subId)
                if (error == null) {
                    toast(application.getString(R.string.restart_ims_success))
                } else {
                    toast(application.getString(R.string.restart_ims_failed, error), false)
                }
            } catch (e: Exception) {
                toast(application.getString(R.string.restart_ims_failed, e.localizedMessage), false)
            }
        }
    }

    private fun launchExclusiveOperation(block: suspend () -> Unit) {
        if (!operationGate.tryEnter()) return
        viewModelScope.launch {
            try {
                ConfigurationOperations.run { block() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e("MainViewModel", "Configuration operation failed", e)
                toast(application.getString(R.string.config_failed, e.localizedMessage), false)
            } finally {
                operationGate.leave()
                drainPendingPersistentRefresh()
            }
        }
    }

    private fun drainPendingPersistentRefresh() {
        // 自动恢复释放业务锁也要刷新；手动操作尚未离开本地门闩时留给 finally 处理。
        if (!pendingPersistentRefresh || isOperationInProgress.value || operationGate.isOccupied) return
        pendingPersistentRefresh = false
        refreshPersistentVolte()
    }

    private fun toast(msg: String, short: Boolean = true) {
        toast?.cancel()
        toast =
            Toast.makeText(application, msg, if (short) Toast.LENGTH_SHORT else Toast.LENGTH_LONG)
        toast?.show()
    }
}
