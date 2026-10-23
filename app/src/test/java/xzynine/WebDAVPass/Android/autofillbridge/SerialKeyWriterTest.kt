/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 偏好落库顺序保证回归测试（PR #13 评审第 7 条）：
 * 同一设置键的连续更新必须按调用顺序落库，最终值必然是最后一次设置的值。
 */

package xzynine.WebDAVPass.Android.autofillbridge

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Collections

class SerialKeyWriterTest {
    private val recorded = Collections.synchronizedList(mutableListOf<String>())

    @Test
    fun `同一键的连续写入按调用顺序落库`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val writer = SerialKeyWriter(scope)
            try {
                val values = (1..8).map { "v$it" }
                values.forEachIndexed { index, value ->
                    writer.enqueue(KEY) {
                        // 故意让先入队的写入最慢：缺少顺序保证时，旧的写入会后到并覆盖新值
                        delay((values.size - index) * 20L)
                        recorded.add(value)
                    }
                }
                writer.awaitKeyIdle(KEY)
                // 落库顺序 == 调用顺序 ⇒ 最后一次设置的值最终生效
                assertEquals(values, recorded.toList())
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun `不同键之间不互相阻塞`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val writer = SerialKeyWriter(scope)
            try {
                val slowWriteStarted = CompletableDeferred<Unit>()
                // 键 A 的第一次写入很慢；若实现退化为全局串行，键 B 的写入会被拖到它后面
                writer.enqueue("key_a") {
                    slowWriteStarted.complete(Unit)
                    delay(SLOW_WRITE_MILLIS)
                    recorded.add("a1")
                }
                writer.enqueue("key_b") { recorded.add("b1") }

                slowWriteStarted.await()
                val otherKeyDone = withTimeoutOrNull(SLOW_WRITE_MILLIS / 2) { writer.awaitKeyIdle("key_b") }
                assertNotNull("不同键不应被串行化：key_b 的写入被 key_a 阻塞", otherKeyDone)
            } finally {
                scope.cancel()
            }
        }

    /** 等待指定键排队的写入全部结束（探针任务能执行即代表该键的链条已排空）。 */
    private suspend fun SerialKeyWriter.awaitKeyIdle(key: String) {
        val probe = CompletableDeferred<Unit>()
        enqueue(key) { probe.complete(Unit) }
        withTimeout(IDLE_TIMEOUT_MILLIS) { probe.await() }
    }

    private companion object {
        const val KEY = "autofill_enabled"
        const val IDLE_TIMEOUT_MILLIS = 10_000L
        const val SLOW_WRITE_MILLIS = 800L
    }
}
