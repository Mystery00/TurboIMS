package io.github.vvb2060.ims.model

import androidx.compose.runtime.Immutable

@Immutable
data class ImsCapabilityStatus(
    val isRegistered: Boolean = false,
    val isVolteAvailable: Boolean = false,
    val isVoWifiAvailable: Boolean = false,
    val isVoNrAvailable: Boolean = false,
    val isVtAvailable: Boolean = false,
    val isNrNsaAvailable: Boolean = false,
    val isNrSaAvailable: Boolean = false,
)
