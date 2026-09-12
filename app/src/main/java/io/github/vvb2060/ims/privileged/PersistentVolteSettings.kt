package io.github.vvb2060.ims.privileged

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.SubscriptionManager

internal data class VolteOriginalValues(val optIn: Int, val userSetting: Int) {
    init {
        require(optIn in -1..1 && userSetting in -1..1) { "Unexpected subscription values" }
    }
}

/**
 * 通过系统管理类的反射入口调用隐藏 API，由系统自身解析 Binder 事务号。
 * 对照 SDK 37.2 的同名接口；Android 13+ 上先解析全部方法，缺失时在写入前失败。
 * 不能仅保存用户开关的布尔回读值：原始 -1 表示跟随运营商默认配置。
 */
internal class PersistentVolteSettings(private val context: Context, private val subId: Int) {
    private val intType = Int::class.javaPrimitiveType!!
    private val provisioningClass = Class.forName("android.telephony.ims.ProvisioningManager")
    private val mmTelClass = Class.forName("android.telephony.ims.ImsMmTelManager")
    private val provisioning = provisioningClass.getMethod("createForSubscriptionId", intType)
        .invoke(null, subId)
    private val mmTel = mmTelClass.getMethod("createForSubscriptionId", intType).invoke(null, subId)
    private val getProvisioning = provisioningClass.getMethod("getProvisioningIntValue", intType)
    private val setProvisioning = provisioningClass.getMethod("setProvisioningIntValue", intType, intType)
    private val getUser = mmTelClass.getMethod("isAdvancedCallingSettingEnabled")
    private val setUser = mmTelClass.getMethod("setAdvancedCallingSettingEnabled", Boolean::class.javaPrimitiveType)
    private val getProperty = SubscriptionManager::class.java.getMethod(
        "getIntegerSubscriptionProperty", intType, String::class.java, intType, Context::class.java,
    )
    private val setProperty = SubscriptionManager::class.java.getMethod(
        "setSubscriptionProperty", intType, String::class.java, String::class.java,
    )
    private val optInKey = provisioningClass.getField("KEY_VOIMS_OPT_IN_STATUS").getInt(null)
    private val optInColumn = SubscriptionManager::class.java.getField("VOIMS_OPT_IN_STATUS").get(null) as String
    private val userColumn = SubscriptionManager::class.java.getField("ENHANCED_4G_MODE_ENABLED").get(null) as String

    // shell permission delegation 提供读取活动订阅和 SIM 标识所需权限。
    @SuppressLint("MissingPermission")
    fun simIdentity(): String {
        val info = context.getSystemService(SubscriptionManager::class.java)
            .getActiveSubscriptionInfo(subId) ?: error("Subscription is not active")
        return info.iccId.takeIf { it.isNotBlank() } ?: error("SIM identity is unavailable")
    }

    fun readOriginal() = VolteOriginalValues(readProperty(optInColumn), readProperty(userColumn))

    fun readOptIn(): Boolean {
        val value = getProvisioning.invoke(provisioning, optInKey) as Int
        check(value == 0 || value == 1) { "Provisioning read failed: $value" }
        return value == 1
    }

    fun readUserEnabled(): Boolean = getUser.invoke(mmTel) as Boolean

    fun enable() {
        // opt-in 会关闭默认值回退，因此必须随后显式开启用户开关，失败由调用方恢复。
        writeOptIn(1)
        setUser.invoke(mmTel, true)
        check(readOriginal() == VolteOriginalValues(1, 1) && readOptIn() && readUserEnabled()) {
            "Persistent VoLTE verification failed"
        }
    }

    fun restore(values: VolteOriginalValues) {
        // 原始用户值先还原，再通过 provisioning 触发能力重算；即使一个步骤失败也尝试其余步骤。
        var failure: Throwable? = null
        fun attempt(block: () -> Unit) {
            try {
                block()
            } catch (t: Throwable) {
                val previous = failure
                if (previous == null) failure = t else previous.addSuppressed(t)
            }
        }
        attempt { writeProperty(userColumn, values.userSetting) }
        attempt { writeOptIn(if (values.optIn == 1) 1 else 0) }
        // provisioning 仅接受 0/1，原始 -1 在重算后恢复，布尔语义仍然是关闭。
        if (values.optIn == -1) attempt { writeProperty(optInColumn, -1) }
        attempt {
            check(readOriginal() == values && readOptIn() == (values.optIn == 1)) {
                "Original VoLTE settings verification failed"
            }
        }
        failure?.let { throw it }
    }

    private fun writeOptIn(value: Int) {
        val result = setProvisioning.invoke(provisioning, optInKey, value) as Int
        // ImsConfigImplBase.CONFIG_RESULT_SUCCESS 为 0；无异常不代表配置成功。
        check(result == 0) { "Provisioning write failed: $result" }
    }

    private fun readProperty(column: String): Int {
        // 使用不可与合法值混淆的缺省值，避免系统吞掉 RemoteException 后被视为未初始化。
        val value = getProperty.invoke(null, subId, column, Int.MIN_VALUE, context) as Int
        check(value in -1..1) { "Subscription property read failed: $column" }
        return value
    }

    private fun writeProperty(column: String, value: Int) {
        setProperty.invoke(null, subId, column, value.toString())
        check(readProperty(column) == value) { "Subscription property write failed: $column" }
    }
}
