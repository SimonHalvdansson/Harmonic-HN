package com.simon.harmonichackernews.presentation

/** Generation token owner for async work keyed to the content being loaded. */
class KeyedRequestSession<K> {
    var generation: Int = 0
        private set

    private var key: K? = null

    fun begin(key: K): Int {
        generation++
        this.key = key
        return generation
    }

    fun invalidate() {
        generation++
        key = null
    }

    fun isCurrent(requestGeneration: Int, requestKey: K): Boolean =
        requestGeneration == generation && requestKey == key
}
