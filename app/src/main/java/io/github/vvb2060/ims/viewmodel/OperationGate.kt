package io.github.vvb2060.ims.viewmodel

import java.util.concurrent.atomic.AtomicBoolean

/**
 * 防止多个特权 Instrumentation 操作并发执行。
 */
class OperationGate {
    private val occupied = AtomicBoolean(false)
    val isOccupied: Boolean get() = occupied.get()

    fun tryEnter(): Boolean = occupied.compareAndSet(false, true)

    fun leave() {
        occupied.set(false)
    }
}
