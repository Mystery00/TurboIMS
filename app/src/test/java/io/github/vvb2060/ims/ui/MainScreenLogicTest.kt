package io.github.vvb2060.ims.ui

import io.github.vvb2060.ims.model.Feature
import io.github.vvb2060.ims.model.SimSelection
import io.github.vvb2060.ims.viewmodel.OperationGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenLogicTest {
    private val allSim = SimSelection(-1, "", "", -1, "所有 SIM 卡")
    private val sim1 = SimSelection(1, "SIM 1", "Carrier 1", 0)
    private val sim2 = SimSelection(2, "SIM 2", "Carrier 2", 1)

    @Test
    fun `SIM 列表刷新后使用列表中的最新对象保留当前订阅`() {
        val refreshedSim = sim1.copy(displayName = "Updated")

        val result = reconcileSelectedSim(sim1, listOf(allSim, refreshedSim, sim2))

        assertSame(refreshedSim, result)
    }

    @Test
    fun `当前订阅失效后选择第一个真实 SIM`() {
        val result = reconcileSelectedSim(sim1, listOf(allSim, sim2))

        assertSame(sim2, result)
    }

    @Test
    fun `没有真实 SIM 时回退到所有 SIM`() {
        assertSame(allSim, reconcileSelectedSim(sim1, listOf(allSim)))
        assertNull(reconcileSelectedSim(sim1, emptyList()))
    }

    @Test
    fun `所有 SIM 模式隐藏单卡字符串配置`() {
        val features = visibleFeaturesForSelection(isAllSim = true)

        assertFalse(features.contains(Feature.CARRIER_NAME))
        assertFalse(features.contains(Feature.IMS_USER_AGENT))
        assertEquals(Feature.entries.size - 2, features.size)
    }

    @Test
    fun `单卡模式显示全部配置`() {
        assertEquals(Feature.entries, visibleFeaturesForSelection(isAllSim = false))
    }

    @Test
    fun `特权操作门同一时间只允许一个操作`() {
        val gate = OperationGate()

        assertTrue(gate.tryEnter())
        assertFalse(gate.tryEnter())
        gate.leave()
        assertTrue(gate.tryEnter())
    }
}

