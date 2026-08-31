package io.github.vvb2060.ims.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTest {
    @Test
    fun `开启动态颜色时根据深浅主题选择动态方案`() {
        assertEquals(ColorSchemeSource.DYNAMIC_LIGHT, selectColorSchemeSource(false, true))
        assertEquals(ColorSchemeSource.DYNAMIC_DARK, selectColorSchemeSource(true, true))
    }

    @Test
    fun `关闭动态颜色时保留静态配色方案`() {
        assertEquals(ColorSchemeSource.STATIC_LIGHT, selectColorSchemeSource(false, false))
        assertEquals(ColorSchemeSource.STATIC_DARK, selectColorSchemeSource(true, false))
    }
}

