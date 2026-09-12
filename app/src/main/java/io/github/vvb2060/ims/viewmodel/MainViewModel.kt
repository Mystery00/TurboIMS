package io.github.vvb2060.ims.viewmodel

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.vvb2060.ims.BuildConfig
import io.github.vvb2060.ims.R
import io.github.vvb2060.ims.ShizukuProvider
import io.github.vvb2060.ims.model.Feature
import io.github.vvb2060.ims.model.ImsCapabilityStatus
import io.github.vvb2060.ims.model.PersistentVolteState
import io.github.vvb2060.ims.model.FeatureValue
import io.github.vvb2060.ims.model.FeatureValueType
import io.github.vvb2060.ims.model.ShizukuStatus
import io.github.vvb2060.ims.model.SimSelection
import io.github.vvb2060.ims.model.SystemInfo
import io.github.vvb2060.ims.privileged.ImsModifier
import io.github.vvb2060.ims.privileged.PersistentVolteModifier
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

    private val _isOperationInProgress = MutableStateFlow(false)
    val isOperationInProgress: StateFlow<Boolean> = _isOperationInProgress.asStateFlow()

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
        if (_isOperationInProgress.value) {
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

    init {
        loadSimList()
        loadSystemInfo()
        updateShizukuStatus()
        Shizuku.addBinderReceivedListener(binderListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    override fun onCleared() {
        super.onCleared()
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
    }

    /**
     * 更新 Shizuku 的当前状态。
     * 检查服务是否运行、是否需要更新以及权限授予情况。
     */
    fun updateShizukuStatus() {
        viewModelScope.launch {
            if (Shizuku.isPreV11()) {
                _shizukuStatus.value = ShizukuStatus.NEED_UPDATE
                return@launch
            }
            _shizukuStatus.value = when {
                !Shizuku.pingBinder() -> ShizukuStatus.NOT_RUNNING
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShizukuStatus.NO_PERMISSION
                else -> ShizukuStatus.READY
            }
        }
    }

    /**
     * 请求 Shizuku 授权。
     */
    fun requestShizukuPermission(requestCode: Int) {
        viewModelScope.launch {
            if (Shizuku.isPreV11()) {
                _shizukuStatus.value = ShizukuStatus.NEED_UPDATE
            } else {
                Shizuku.requestPermission(requestCode)
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
        launchExclusiveOperation {
            // 在首次挂起前固定本次应用内容，避免操作期间的 UI 编辑污染成功历史。
            val appliedConfig = map.toMap()

            // 构建传递给底层 ImsModifier 的配置 Bundle
            val carrierName =
                if (selectedSim.subId == -1) null else appliedConfig[Feature.CARRIER_NAME]?.data as String?
            val imsUserAgent =
                if (selectedSim.subId == -1) null else appliedConfig[Feature.IMS_USER_AGENT]?.data as String?
            val enableVoLTE = (appliedConfig[Feature.VOLTE]?.data ?: true) as Boolean
            val enableVoWiFi = (appliedConfig[Feature.VOWIFI]?.data ?: true) as Boolean
            val enableVoWifiRoaming =
                (appliedConfig[Feature.VOWIFI_ROAMING]?.data ?: false) as Boolean
            val enableVT = (appliedConfig[Feature.VT]?.data ?: true) as Boolean
            val enableVoNR = (appliedConfig[Feature.VONR]?.data ?: true) as Boolean
            val enableCrossSIM = (appliedConfig[Feature.CROSS_SIM]?.data ?: true) as Boolean
            val enableUT = (appliedConfig[Feature.UT]?.data ?: true) as Boolean
            val enable5GNR = (appliedConfig[Feature.FIVE_G_NR]?.data ?: true) as Boolean
            val enable5GThreshold =
                (appliedConfig[Feature.FIVE_G_THRESHOLDS]?.data ?: true) as Boolean
            val enable5GPlusIcon =
                (appliedConfig[Feature.FIVE_G_PLUS_ICON]?.data ?: true) as Boolean
            val enableShow4GForLTE =
                (appliedConfig[Feature.SHOW_4G_FOR_LTE]?.data ?: false) as Boolean

            val bundle = ImsModifier.buildBundle(
                carrierName,
                imsUserAgent,
                enableVoLTE,
                enableVoWiFi,
                enableVoWifiRoaming,
                enableVT,
                enableVoNR,
                enableCrossSIM,
                enableUT,
                enable5GNR,
                enable5GThreshold,
                enable5GPlusIcon,
                enableShow4GForLTE
            )
            bundle.putInt(ImsModifier.BUNDLE_SELECT_SIM_ID, selectedSim.subId)

            // 调用 Shizuku 服务进行实际修改
            val resultMsg = ShizukuProvider.overrideImsConfig(application, bundle)
            if (resultMsg == null) {
                // 仅在系统配置成功后保存历史，避免失败尝试覆盖上次有效配置。
                saveConfiguration(selectedSim.subId, appliedConfig)
                toast(application.getString(R.string.config_success_message))
            } else {
                toast(application.getString(R.string.config_failed, resultMsg), false)
            }
        }
    }

    /**
     * 将配置保存到 SharedPreferences 中以便下次加载。
     */
    private fun saveConfiguration(subId: Int, map: Map<Feature, FeatureValue>) {
        application.getSharedPreferences("sim_config_$subId", Context.MODE_PRIVATE).edit {
            clear() // 清除旧配置
            map.forEach { (feature, value) ->
                when (value.valueType) {
                    FeatureValueType.BOOLEAN -> putBoolean(feature.name, value.data as Boolean)
                    FeatureValueType.STRING -> putString(feature.name, value.data as String)
                }
            }
        }
    }

    /**
     * 加载指定 subId 的配置。如果不存在则返回 null。
     */
    fun loadConfiguration(subId: Int): Map<Feature, FeatureValue>? {
        val prefs = application.getSharedPreferences("sim_config_$subId", Context.MODE_PRIVATE)
        if (prefs.all.isEmpty()) return null

        val map = linkedMapOf<Feature, FeatureValue>()
        Feature.entries.forEach { feature ->
            if (prefs.contains(feature.name)) {
                when (feature.valueType) {
                    FeatureValueType.BOOLEAN -> {
                        val data = prefs.getBoolean(feature.name, feature.defaultValue as Boolean)
                        map[feature] = FeatureValue(data, feature.valueType)
                    }

                    FeatureValueType.STRING -> {
                        val data =
                            prefs.getString(feature.name, feature.defaultValue as String) ?: ""
                        map[feature] = FeatureValue(data, feature.valueType)
                    }
                }
            } else {
                map[feature] = FeatureValue(feature.defaultValue, feature.valueType)
            }
        }
        return map
    }

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
        _isOperationInProgress.value = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                _isOperationInProgress.value = false
                operationGate.leave()
                if (pendingPersistentRefresh) {
                    pendingPersistentRefresh = false
                    refreshPersistentVolte()
                }
            }
        }
    }

    private fun toast(msg: String, short: Boolean = true) {
        toast?.cancel()
        toast =
            Toast.makeText(application, msg, if (short) Toast.LENGTH_SHORT else Toast.LENGTH_LONG)
        toast?.show()
    }
}
