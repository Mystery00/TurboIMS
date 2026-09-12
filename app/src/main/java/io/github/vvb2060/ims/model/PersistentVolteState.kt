package io.github.vvb2060.ims.model

/** 可空值表示尚未读取或读取失败，不能当作关闭。 */
data class PersistentVolteState(
    val subId: Int,
    val optIn: Boolean? = null,
    val userEnabled: Boolean? = null,
    val imsRegistered: Boolean? = null,
    val canRestore: Boolean = false,
    val unsupported: Boolean = false,
    val error: String? = null,
)
