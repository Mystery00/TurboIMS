package io.github.vvb2060.ims.ui

import io.github.vvb2060.ims.model.Feature
import io.github.vvb2060.ims.model.SimSelection

/**
 * SIM 列表刷新后按 subId 重新关联选择，避免继续操作已经失效的订阅。
 */
fun reconcileSelectedSim(
    current: SimSelection?,
    simList: List<SimSelection>,
): SimSelection? {
    current?.let { selected ->
        simList.firstOrNull { it.subId == selected.subId }?.let { return it }
    }
    return simList.firstOrNull { it.subId != -1 } ?: simList.firstOrNull()
}

/**
 * 批量配置不支持单卡字符串覆盖，因此在“所有 SIM”模式下隐藏相关输入项。
 */
fun visibleFeaturesForSelection(isAllSim: Boolean): List<Feature> {
    if (!isAllSim) return Feature.entries
    return Feature.entries.filterNot {
        it == Feature.CARRIER_NAME || it == Feature.IMS_USER_AGENT
    }
}
