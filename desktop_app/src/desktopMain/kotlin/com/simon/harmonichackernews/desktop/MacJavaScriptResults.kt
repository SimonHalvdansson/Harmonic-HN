package com.simon.harmonichackernews.desktop

import com.sun.jna.Callback
import java.awt.EventQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal interface MacJavaScriptCallback : Callback {
    fun completed(id: Long, value: String?)
}

/** Keep the native callback rooted until process exit, including completions after view disposal. */
internal object MacJavaScriptResults {
    private data class Request(val owner: Any, val result: (String?) -> Unit)
    private val nextId = AtomicLong()
    private val pending = ConcurrentHashMap<Long, Request>()
    val callback = object : MacJavaScriptCallback {
        override fun completed(id: Long, value: String?) {
            EventQueue.invokeLater { pending.remove(id)?.result?.invoke(value) }
        }
    }

    fun register(owner: Any, result: (String?) -> Unit): Long = nextId.incrementAndGet().also {
        pending[it] = Request(owner, result)
    }

    fun cancel(owner: Any) {
        pending.entries.filter { it.value.owner === owner }.forEach { (id, _) ->
            pending.remove(id)?.let { request -> EventQueue.invokeLater { request.result(null) } }
        }
    }
}
