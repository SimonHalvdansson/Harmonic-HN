package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.network.ReplyCheckResult
import com.simon.harmonichackernews.network.ReplyNotificationRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** A cancellable handle: Swift cancels it when iOS expires a background refresh. */
class IosReplyCheckTask internal constructor(private val job: Job) {
    fun cancel() = job.cancel()
}

/** Keeps account/checkpoint ownership on the same dispatcher as the shared settings screen. */
class IosReplyNotificationChecks internal constructor(
    private val runtime: ReplyNotificationRuntime,
    private val awaitAccount: suspend () -> Unit,
    private val accountUsername: () -> String?,
    private val persist: suspend () -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    fun start(checkForReplies: Boolean, wifiAvailable: Boolean, timeoutMillis: Long = 25_000, completion: (Boolean) -> Unit): IosReplyCheckTask {
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var success = false
            try {
                withTimeout(timeoutMillis.coerceIn(1_000, 240_000)) {
                    awaitAccount()
                    if (runtime.isEnabled && runtime.accountScoped && runtime.configuredUsername != accountUsername()) {
                        runtime.disable()
                    }
                    // Enqueue the next wakeup before scanning; cancellation/failed networking must
                    // not silently stop future checks. The runtime re-evaluates local quiet hours.
                    runtime.rescheduleChecks()
                    val wifiOnly = runtime.activity.snapshot(runtime.configuredUsername).alerts.wifiOnly
                    val result = if (checkForReplies && (!wifiOnly || wifiAvailable)) runtime.checkInBackground()
                        else ReplyCheckResult.Disabled
                    success = result !is ReplyCheckResult.Failed && result != ReplyCheckResult.UserNotFound
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                success = false
            } finally {
                // Flush the checkpoint/inbox before telling iOS it may suspend this process.
                try {
                    withContext(NonCancellable) { withTimeout(1_000) { persist() } }
                } catch (_: Exception) {
                    success = false
                }
                completion(success)
            }
        }
        return IosReplyCheckTask(job)
    }

    internal fun close() = scope.cancel()
}
