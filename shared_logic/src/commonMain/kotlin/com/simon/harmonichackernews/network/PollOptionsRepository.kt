package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.PollOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface PollOptionsLoader {
    suspend fun findOptionIds(storyId: Int): IntArray
    fun placeholders(optionIds: IntArray): List<PollOption>
    fun loadOptions(optionIds: IntArray): Flow<PollOption>
}

/** Resolves poll metadata and streams options as each network item finishes loading. */
class PollOptionsRepository(
    private val api: HackerNewsApi,
) : PollOptionsLoader {
    override suspend fun findOptionIds(storyId: Int): IntArray =
        api.getItem(storyId)?.parts.orEmpty().toIntArray()

    override fun placeholders(optionIds: IntArray): List<PollOption> = optionIds.map { optionId ->
        PollOption().apply { id = optionId }
    }

    override fun loadOptions(optionIds: IntArray): Flow<PollOption> = flow {
        for (optionId in optionIds) {
            val option = PollOption().apply { id = optionId }
            try {
                val item = api.getItem(optionId)
                val normalizedText = JSONParser.preprocessHtml(item?.text)
                if (item == null || normalizedText.isNullOrBlank()) {
                    throw IllegalStateException("Poll option response was invalid")
                }
                option.points = item.score
                option.text = normalizedText
                option.loaded = true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                option.loadFailed = true
            }
            emit(option)
        }
    }
}
