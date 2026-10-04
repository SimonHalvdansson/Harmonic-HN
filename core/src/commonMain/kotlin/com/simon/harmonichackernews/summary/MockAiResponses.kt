package com.simon.harmonichackernews.summary

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Shared debug backend: no credentials, extraction, downloads, or inference required. */
object MockAiResponses {
    fun summary(): Flow<StorySummaryEvent> = stream(
        "- **The main idea:** small improvements can make a familiar workflow easier to use.\n" +
            "- **The trade-off:** convenience still needs to be balanced with reliability and control.\n" +
            "- **What matters:** try it with a real task and check where the approach breaks down.\n\n" +
            "This is a sample summary for testing.",
    )

    fun discussion(question: String): Flow<StorySummaryEvent> {
        val introduction = when {
            "example" in question.lowercase() ->
                "Imagine a team building a small tool to automate a repetitive task. " +
                    "The tool may be simple, but deciding whether it is worth maintaining takes judgment."
            "term" in question.lowercase() ->
                "A useful way to understand technical language is to connect each term to the problem it solves. " +
                    "An **abstraction**, for example, hides details so you can focus on the task at hand."
            "summari" in question.lowercase() || "main point" in question.lowercase() ->
                "The main point is that making something easier to build does not remove every other kind of work."
            else -> "One way to read a comment like this is to separate its central claim from the assumptions behind it."
        }
        return stream(introduction + "\n\n" +
            "- **The claim:** a change makes a task easier or more practical.\n" +
            "- **The assumption:** the remaining costs are small enough to manage.\n" +
            "- **The open question:** does that still hold for more complex situations?\n\n" +
            "A concrete example can help reveal the distinction. A prototype might take an afternoon, " +
            "while testing, maintenance, and supporting other users still take time.\n\n" +
            "This is a sample answer for testing, not an analysis of the selected comment.")
    }

    private fun stream(text: String): Flow<StorySummaryEvent> = flow {
        delay(650)
        var end = 0
        while (end < text.length) {
            // Small batches with punctuation pauses resemble real streaming without flicker.
            end = (end + 18).coerceAtMost(text.length)
            emit(StorySummaryEvent.Progress(text.substring(0, end)))
            delay(if (text[end - 1] in ".!?\n") 180 else 80)
        }
        emit(StorySummaryEvent.Success(text))
    }
}
