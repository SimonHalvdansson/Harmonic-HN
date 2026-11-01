package com.simon.harmonichackernews.summary.local

import java.util.concurrent.Future
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/** Legacy Nano APIs return futures; don't keep their request alive after memory cancellation. */
internal suspend fun <T> awaitLocalAiFuture(future: Future<T>): T = try {
    runInterruptible(Dispatchers.IO) { future.get() }
} catch (cancelled: CancellationException) {
    future.cancel(true)
    throw cancelled
}
