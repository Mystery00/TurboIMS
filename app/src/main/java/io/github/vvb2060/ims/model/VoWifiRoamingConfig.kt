package io.github.vvb2060.ims.model

import android.os.Bundle

object VoWifiRoamingConfig {
    // 该 CarrierConfig 键为隐藏 API，使用字符串保持 Android 版本兼容。
    const val KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_ENABLED_BOOL =
        "carrier_default_wfc_ims_roaming_enabled_bool"

    fun overrideValue(enabled: Boolean): Boolean? = if (enabled) true else null

    fun putOverride(bundle: Bundle, enabled: Boolean) {
        overrideValue(enabled)?.let {
            bundle.putBoolean(KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_ENABLED_BOOL, it)
        }
    }

    fun isEnabled(bundle: Bundle): Boolean =
        bundle.getBoolean(KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_ENABLED_BOOL, false)
}
