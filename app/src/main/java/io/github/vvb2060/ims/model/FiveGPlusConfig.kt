package io.github.vvb2060.ims.model

import android.os.Bundle

/**
 * Pixel Telephony 用于判定 NR Advanced（5GA/5G+）状态的隐藏 CarrierConfig 配置。
 *
 * 这些键在不同 Android 版本中未必公开为 SDK 常量，因此集中使用字符串定义，
 * 避免隐藏 API 兼容逻辑分散到配置读取和写入路径。
 */
object FiveGPlusConfig {
    const val KEY_NR_ADVANCED_THRESHOLD_BANDWIDTH_KHZ =
        "nr_advanced_threshold_bandwidth_khz_int"
    const val KEY_INCLUDE_LTE_FOR_NR_ADVANCED_THRESHOLD_BANDWIDTH =
        "include_lte_for_nr_advanced_threshold_bandwidth_bool"
    const val KEY_ADDITIONAL_NR_ADVANCED_BANDS =
        "additional_nr_advanced_bands_int_array"
    const val KEY_5G_ICON_CONFIGURATION = "5g_icon_configuration_string"
    const val KEY_NR_ADVANCED_CAPABLE_PCO_ID = "nr_advanced_capable_pco_id_int"

    const val NR_ADVANCED_THRESHOLD_BANDWIDTH_KHZ = 110_000
    const val NR_ICON_CONFIGURATION =
        "connected_mmwave:5G_Plus,connected:5G,connected_rrc_idle:5G," +
            "not_restricted_rrc_idle:5G,not_restricted_rrc_con:5G"

    val additionalNrAdvancedBands = intArrayOf(1, 3, 8, 28, 41, 78, 79)

    val readKeys: List<String> = listOf(
        KEY_NR_ADVANCED_THRESHOLD_BANDWIDTH_KHZ,
        KEY_INCLUDE_LTE_FOR_NR_ADVANCED_THRESHOLD_BANDWIDTH,
        KEY_ADDITIONAL_NR_ADVANCED_BANDS,
        KEY_5G_ICON_CONFIGURATION,
        KEY_NR_ADVANCED_CAPABLE_PCO_ID,
    )

    fun putOverrides(bundle: Bundle) {
        bundle.putInt(
            KEY_NR_ADVANCED_THRESHOLD_BANDWIDTH_KHZ,
            NR_ADVANCED_THRESHOLD_BANDWIDTH_KHZ
        )
        bundle.putBoolean(KEY_INCLUDE_LTE_FOR_NR_ADVANCED_THRESHOLD_BANDWIDTH, false)
        bundle.putIntArray(KEY_ADDITIONAL_NR_ADVANCED_BANDS, additionalNrAdvancedBands)
        bundle.putString(KEY_5G_ICON_CONFIGURATION, NR_ICON_CONFIGURATION)
        // 取消运营商 PCO gate，避免其阻止系统进入 NR Advanced 图标状态。
        bundle.putInt(KEY_NR_ADVANCED_CAPABLE_PCO_ID, 0)
    }

    fun matchesOverrides(bundle: Bundle): Boolean {
        return bundle.getInt(
            KEY_NR_ADVANCED_THRESHOLD_BANDWIDTH_KHZ,
            Int.MIN_VALUE
        ) == NR_ADVANCED_THRESHOLD_BANDWIDTH_KHZ &&
            !bundle.getBoolean(
                KEY_INCLUDE_LTE_FOR_NR_ADVANCED_THRESHOLD_BANDWIDTH,
                true
            ) &&
            bundle.getIntArray(KEY_ADDITIONAL_NR_ADVANCED_BANDS)
                ?.contentEquals(additionalNrAdvancedBands) == true &&
            bundle.getString(KEY_5G_ICON_CONFIGURATION) == NR_ICON_CONFIGURATION &&
            bundle.getInt(KEY_NR_ADVANCED_CAPABLE_PCO_ID, Int.MIN_VALUE) == 0
    }
}
