package io.github.vvb2060.ims

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.edit
import io.github.vvb2060.ims.model.Feature
import io.github.vvb2060.ims.model.FeatureValue
import io.github.vvb2060.ims.model.FeatureValueType
import io.github.vvb2060.ims.privileged.ImsModifier
import java.io.File

/** 保留原有界面历史，另外记录操作顺序、重置意图和本次开机的恢复进度。 */
class ConfigurationRepository(private val context: Context) {
    private val state = context.getSharedPreferences("auto_restore", Context.MODE_PRIVATE)
    private fun history(subId: Int) =
        context.getSharedPreferences("sim_config_$subId", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = state.getBoolean("enabled", false)
        set(value) { state.edit { putBoolean("enabled", value) } }

    // BOOT_COUNT 自 API 24 起提供，避免用墙上时间判断重启而受用户校时影响。
    fun bootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    fun beginOptIn() {
        state.edit {
            remove("permission_boot")
            state.all.keys.filter { it.startsWith("applied_") }.forEach(::remove)
        }
    }

    fun claimPermissionRequest(boot: Int): Boolean {
        if (boot < 0 || state.getInt("permission_boot", -1) == boot) return false
        // 请求前同步保存，避免进程在弹窗期间被回收后再次请求。
        return state.edit().putInt("permission_boot", boot).commit()
    }

    fun load(subId: Int): Map<Feature, FeatureValue>? {
        val prefs = history(subId)
        if (Feature.entries.none { prefs.contains(it.name) }) return null
        return Feature.entries.associateWith { feature ->
            val data = when (feature.valueType) {
                FeatureValueType.BOOLEAN -> prefs.getBoolean(feature.name, feature.defaultValue as Boolean)
                FeatureValueType.STRING -> prefs.getString(feature.name, feature.defaultValue as String) ?: ""
            }
            FeatureValue(data, feature.valueType)
        }
    }

    fun save(subId: Int, config: Map<Feature, FeatureValue>) {
        val revision = nextRevision()
        history(subId).edit {
            clear()
            config.forEach { (feature, value) ->
                when (value.valueType) {
                    FeatureValueType.BOOLEAN -> putBoolean(feature.name, value.data as Boolean)
                    FeatureValueType.STRING -> putString(feature.name, value.data as String)
                }
            }
            putLong(REVISION, revision)
            putBoolean(RESET, false)
        }
    }

    fun recordReset(subId: Int) {
        val revision = nextRevision()
        history(subId).edit {
            putLong(REVISION, revision)
            putBoolean(RESET, true)
        }
    }

    private fun nextRevision(): Long {
        val revision = state.getLong("revision", 0) + 1
        state.edit { putLong("revision", revision) }
        return revision
    }

    data class RestoreTarget(
        val sourceSubId: Int,
        val revision: Long,
        val config: Map<Feature, FeatureValue>,
    )

    fun restoreTarget(subId: Int): RestoreTarget? {
        val single = history(subId)
        val all = history(-1)
        // 老版本历史没有顺序信息；同序号时优先单卡，新增操作则严格采用最后成功的操作。
        val source = if (single.all.isNotEmpty() &&
            single.getLong(REVISION, 0) >= all.getLong(REVISION, 0)) subId else -1
        val prefs = history(source)
        if (prefs.getBoolean(RESET, false)) return null
        return RestoreTarget(source, prefs.getLong(REVISION, 0), load(source) ?: return null)
    }

    fun hasRestorableHistory(): Boolean {
        val ids = File(context.applicationInfo.dataDir, "shared_prefs").listFiles()
            .orEmpty().mapNotNull { file ->
                file.name.takeIf { it.startsWith("sim_config_") && it.endsWith(".xml") }
                    ?.removePrefix("sim_config_")?.removeSuffix(".xml")?.toIntOrNull()
            }.toMutableSet()
        ids.add(-1)
        return ids.any { restoreTarget(it) != null }
    }

    private fun token(boot: Int, target: RestoreTarget) = "$boot:${target.sourceSubId}:${target.revision}"

    fun wasApplied(subId: Int, boot: Int, target: RestoreTarget): Boolean =
        state.getString("applied_$subId", null) == token(boot, target)

    fun markApplied(subId: Int, boot: Int, target: RestoreTarget) {
        if (boot >= 0) state.edit { putString("applied_$subId", token(boot, target)) }
    }

    fun markManualApply(subIds: List<Int>) {
        val boot = bootCount()
        subIds.forEach { subId -> restoreTarget(subId)?.let { markApplied(subId, boot, it) } }
    }

    companion object {
        private const val REVISION = "_restore_revision"
        private const val RESET = "_restore_reset"

        fun buildBundle(subId: Int, config: Map<Feature, FeatureValue>): Bundle {
            fun boolean(feature: Feature) = (config[feature]?.data ?: feature.defaultValue) as Boolean
            fun string(feature: Feature) = config[feature]?.data as? String
            return ImsModifier.buildBundle(
                if (subId == -1) null else string(Feature.CARRIER_NAME),
                if (subId == -1) null else string(Feature.IMS_USER_AGENT),
                boolean(Feature.VOLTE), boolean(Feature.VOWIFI), boolean(Feature.VOWIFI_ROAMING),
                boolean(Feature.VT), boolean(Feature.VONR), boolean(Feature.CROSS_SIM),
                boolean(Feature.UT), boolean(Feature.FIVE_G_NR), boolean(Feature.FIVE_G_THRESHOLDS),
                boolean(Feature.FIVE_G_PLUS_ICON), boolean(Feature.SHOW_4G_FOR_LTE),
            ).apply { putInt(ImsModifier.BUNDLE_SELECT_SIM_ID, subId) }
        }
    }
}
