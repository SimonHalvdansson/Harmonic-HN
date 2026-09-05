package com.simon.harmonichackernews.presentation

import com.fleeksoft.ksoup.Ksoup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Builds a search index only while search is open, using immutable thread content on a worker. */
internal class CommentSearchSession(
    private val scope: CoroutineScope,
    private val thread: CommentThreadStore,
    private val dispatcher: CoroutineDispatcher,
    private val parseText: suspend (String) -> String = { Ksoup.parse(it).text().lowercase() },
) {
    private var job: Job? = null
    private var index: Map<Int, SearchableCommentText> = emptyMap()

    fun setActive(active: Boolean) {
        if (!active) {
            job?.cancel()
            job = null
            thread.setSearchPreparing(false)
            thread.setSearchQuery("")
            return
        }
        if (job?.isActive == true) return
        thread.setSearchPreparing(true)
        job = scope.launch {
            thread.state.map { it.allComments }.distinctUntilChanged { old, new -> old === new }.collectLatest { source ->
                thread.setSearchPreparing(true)
                val previous = index
                val prepared = withContext(dispatcher) {
                    buildMap {
                        for (item in source.drop(1)) {
                            coroutineContext.ensureActive()
                            val html = item.expandedAnchorText.orEmpty()
                            put(item.id, previous[item.id]?.takeIf { it.source == html }
                                ?: SearchableCommentText(html, parseText(html)))
                        }
                    }
                }
                index = prepared
                thread.installSearchIndex(source, prepared)
            }
        }
    }
}
