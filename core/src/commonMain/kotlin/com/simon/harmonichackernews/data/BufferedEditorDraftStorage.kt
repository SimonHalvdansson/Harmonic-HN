package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.platform.EditorDraftField
import com.simon.harmonichackernews.platform.EditorDraftStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI-owned memory with a single background writer. The host retains this object across activity
 * recreation, and its scope outlives the editor so a final save can finish after backgrounding.
 */
class BufferedEditorDraftStorage(
    private val files: EditorDraftStorage,
    scope: CoroutineScope,
    private val inlineLimit: Int,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : EditorDraftStorage {
    private val values = mutableMapOf<EditorDraftField, String>()
    private val pending = linkedMapOf<EditorDraftField, String>()
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val initialized = CompletableDeferred<Unit>()
    private var cleared = false
    private val mutableFailures = MutableStateFlow(0)
    val failures = mutableFailures.asStateFlow()

    init {
        scope.launch {
            try {
                val restored = withContext(dispatcher) {
                    EditorDraftField.entries.associateWith(files::read)
                }
                if (!cleared) restored.forEach { (field, text) ->
                    if (text != null && field !in values) values[field] = text
                }
            } finally {
                initialized.complete(Unit)
            }
            for (request in requests) {
                if (cleared) {
                    withContext(dispatcher) { files.clear() }
                    break
                }
                val batch = pending.toMap()
                pending.clear()
                val failed = withContext(dispatcher) {
                    mutableMapOf<EditorDraftField, String>().apply {
                        batch.forEach { (field, text) ->
                            if (!files.write(field, text)) put(field, text)
                        }
                    }
                }
                if (failed.isNotEmpty()) {
                    // Keep the current text in memory and retry on the next edit/save.
                    failed.forEach { (field, text) ->
                        if (values[field] == text && field !in pending) pending[field] = text
                    }
                    mutableFailures.value++
                }
            }
            requests.close()
        }
    }

    suspend fun awaitRestored() = initialized.await()

    override fun stage(field: EditorDraftField, text: String) {
        if (cleared) return
        if (values[field] == text) {
            if (pending.isNotEmpty()) requests.trySend(Unit)
            return
        }
        values[field] = text
        if (text.length > inlineLimit) pending[field] = text else pending.remove(field)
        if (pending.isNotEmpty()) requests.trySend(Unit)
    }

    override fun write(field: EditorDraftField, text: String): Boolean {
        if (cleared) return false
        stage(field, text)
        return true
    }

    override fun read(field: EditorDraftField): String? = values[field]

    override fun clear() {
        if (cleared) return
        cleared = true
        values.clear()
        pending.clear()
        requests.trySend(Unit)
    }
}
