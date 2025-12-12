package io.github.vvb2060.ims.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.android.internal.telephony.ISub
import io.github.vvb2060.ims.Feature
import io.github.vvb2060.ims.FeatureValueType
import io.github.vvb2060.ims.ImsModifier
import io.github.vvb2060.ims.ShizukuProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

private const val PREFS_NAME = "ims_config"

enum class ShizukuStatus {
    CHECKING,
    NOT_RUNNING,
    NO_PERMISSION,
    READY,
    NEED_UPDATE,
}

data class SimSelection(
    val subId: Int,
    val displayName: String,
    val carrierName: String,
    val simSlotIndex: Int,
    val showTitle: String = buildString {
        append("SIM ")
        append(simSlotIndex + 1)
        append(": ")
        append(displayName)
        append(" (${carrierName})")
    }
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MainViewModel"
    }

    private val _androidVersion = MutableStateFlow("")
    val androidVersion: StateFlow<String> = _androidVersion.asStateFlow()

    private val _shizukuStatus = MutableStateFlow(ShizukuStatus.CHECKING)
    val shizukuStatus: StateFlow<ShizukuStatus> = _shizukuStatus.asStateFlow()

    private val _allSimList = MutableStateFlow<List<SimSelection>>(emptyList())
    val allSimList: StateFlow<List<SimSelection>> = _allSimList.asStateFlow()

    private val _featureSwitches = MutableStateFlow<Map<Feature, Any>>(emptyMap())
    val featureSwitches: StateFlow<Map<Feature, Any>> = _featureSwitches.asStateFlow()

    private val prefs: SharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val binderListener = Shizuku.OnBinderReceivedListener { updateShizukuStatus() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { updateShizukuStatus() }

    init {
        loadSimList()
        loadPreferences()
        updateAndroidVersionInfo()
        updateShizukuStatus()
        Shizuku.addBinderReceivedListener(binderListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    override fun onCleared() {
        super.onCleared()
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
    }

    fun updateShizukuStatus() {
        viewModelScope.launch {
            if (Shizuku.isPreV11()) {
                _shizukuStatus.value = ShizukuStatus.NEED_UPDATE
            }
            val status = when {
                !Shizuku.pingBinder() -> ShizukuStatus.NOT_RUNNING
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShizukuStatus.NO_PERMISSION
                else -> ShizukuStatus.READY
            }
            _shizukuStatus.value = status
        }
    }

    fun requestShizukuPermission(requestCode: Int) {
        viewModelScope.launch {
            if (Shizuku.isPreV11()) {
                _shizukuStatus.value = ShizukuStatus.NEED_UPDATE
            } else {
                Shizuku.requestPermission(requestCode)
            }
        }
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            val featureSwitches = linkedMapOf<Feature, Any>()
            for (feature in Feature.entries) {
                when (feature.valueType) {
                    FeatureValueType.STRING -> {
                        featureSwitches.put(feature, prefs.getString(feature.key, "")!!)
                    }

                    FeatureValueType.BOOLEAN -> {
                        featureSwitches.put(feature, prefs.getBoolean(feature.key, true))
                    }
                }
            }
            _featureSwitches.value = featureSwitches
        }
    }

    fun loadSimList() {
        viewModelScope.launch {
            val simInfoList = ShizukuProvider.readSimInfoList(application)
            val resultList = simInfoList.map {
                SimSelection(
                    it.subId,
                    it.displayName,
                    it.carrierName,
                    it.simSlotIndex,
                )
            }.toMutableList()
            resultList.add(0, SimSelection(-1, "", "", -1, "所有SIM"))
            _allSimList.value = resultList
        }
    }

    private fun updateAndroidVersionInfo() {
        viewModelScope.launch {
            val version = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            _androidVersion.value = version
        }
    }

    fun onFeatureSwitchChange(feature: Feature, value: Any) {
        Log.d(TAG, "onFeatureSwitchChange: $feature, $value")
        viewModelScope.launch {
            val updatedSwitches = _featureSwitches.value.toMutableMap()
            updatedSwitches[feature] = value
            _featureSwitches.value = updatedSwitches
        }
    }

    fun onApplyConfiguration(selectedSim: SimSelection) {
        viewModelScope.launch {
            val map = _featureSwitches.value
            val selectedSubId = selectedSim.subId
            val carrierName = map[Feature.CARRIER_NAME] as String?
            val enableVoLTE = map.getOrDefault(Feature.VOLTE, true) as Boolean
            val enableVoWiFi = map.getOrDefault(Feature.VOWIFI, true) as Boolean
            val enableVT = map.getOrDefault(Feature.VT, true) as Boolean
            val enableVoNR = map.getOrDefault(Feature.VONR, true) as Boolean
            val enableCrossSIM = map.getOrDefault(Feature.CROSS_SIM, true) as Boolean
            val enableUT = map.getOrDefault(Feature.UT, true) as Boolean
            val enable5GNR = map.getOrDefault(Feature.FIVE_G_NR, true) as Boolean
            val enable5GThreshold = map.getOrDefault(Feature.FIVE_G_THRESHOLDS, true) as Boolean

            val bundle = ImsModifier.buildBundle(
                carrierName,
                enableVoLTE,
                enableVoWiFi,
                enableVT,
                enableVoNR,
                enableCrossSIM,
                enableUT,
                enable5GNR,
                enable5GThreshold
            )
            bundle.putInt(ImsModifier.BUNDLE_SELECT_SIM_ID, selectedSubId)

            val result = ShizukuProvider.overrideImsConfig(application, bundle)
        }
    }

    fun onResetConfiguration(context: Context) {
        ShizukuProvider.startInstrument(context, true)
    }
}
