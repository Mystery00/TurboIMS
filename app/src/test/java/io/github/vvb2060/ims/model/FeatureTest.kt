package io.github.vvb2060.ims.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureTest {
    @Test
    fun `漫游 VoWiFi 默认关闭`() {
        val feature = Feature.VOWIFI_ROAMING

        assertEquals(false, feature.defaultValue)
        assertEquals(FeatureValueType.BOOLEAN, feature.valueType)
    }

    @Test
    fun `开启漫游 VoWiFi 时生成 true 覆盖值`() {
        assertEquals(true, VoWifiRoamingConfig.overrideValue(true))
    }

    @Test
    fun `关闭漫游 VoWiFi 时不覆盖运营商默认值`() {
        assertEquals(null, VoWifiRoamingConfig.overrideValue(false))
    }
}

