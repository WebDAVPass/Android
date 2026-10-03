/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 偏好写入顺序保证：同一设置键的多次写入严格按调用顺序落库，不同键之间不互相阻塞。
 */

package xzynine.WebDAVPass.Android.autofillbridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap

/**
 * 按键串行的异步写入器。
 *
 * 背景：偏好更新（如开关连点、黑名单逐条增删）会为每次更新各起一个协程写库，
 * 较新的协程可能先写入、较旧的随后用 REPLACE 策略覆盖，
 * 导致「重启后读回过期值」。这里为每个键维护一条任务链，新写入先等待同键的上一次写入完成，
 * 从而保证**调用顺序 == 落库顺序**，最终值必然是最后一次设置的值。
 *
 * 不同键各自独立成链：写入慢的键（如较长的黑名单字符串）不会阻塞其他键。
 * 键的取值空间有限（见 [AppAutofillPreferences] 的键常量），无需回收已完成的任务引用。
 *
 * @param scope 写入任务所在作用域（需含 [kotlinx.coroutines.Dispatchers.IO] 等后台调度器）
 */
internal class SerialKeyWriter(
    private val scope: CoroutineScope,
) {
    /** 各键当前挂起的最后一个写入任务，作为下一次写入的前置依赖。 */
    private val tails = ConcurrentHashMap<String, Deferred<Unit>>()

    /**
     * 排队一次写入（非阻塞，立即返回）。
     *
     * @param key 串行化分组键（同一设置键的多次入队严格按入队顺序执行）
     * @param write 真正的写入动作，在同键前序写入全部完成后才执行
     */
    fun enqueue(
        key: String,
        write: suspend () -> Unit,
    ) {
        while (true) {
            val previous = tails[key]
            // LAZY 保证「登记为 tail」发生在任务真正开始之前，
            // 否则并发入队时新任务可能在登记前就跑完，导致链条断掉。
            val current =
                scope.async(start = CoroutineStart.LAZY) {
                    // join 不会因前序任务被取消而抛出，取消/异常都能正确向后传递顺序保证
                    previous?.join()
                    write()
                }
            if (tails.put(key, current) == previous) {
                current.start()
                return
            }
            // CAS 失败（tail 被其他线程抢先更新）：丢弃本次任务并重试，避免打断他人的链条
            current.cancel()
        }
    }
}
