package io.github.vvb2060.ims

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 手动操作与自动恢复共用业务锁，保证系统写入与本地记录作为一个整体串行完成。 */
object ConfigurationOperations {
    private val mutex = Mutex()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock {
        _busy.value = true
        try {
            block()
        } finally {
            _busy.value = false
        }
    }
}
