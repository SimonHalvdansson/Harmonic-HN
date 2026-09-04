package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.Comment
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Golden outputs captured from 8586f059, before the parsing optimizations. */
class AlgoliaFixtureParityTest {
    @Test
    fun allFixturesMatchOriginalParserAndSummary() = runBlocking {
        for (fixture in fixtures) {
            val filename = "comments_benchmark_fixture${fixture.suffix}.json"
            val payload = Files.readString(Path.of("../app/src/benchmark/assets/$filename"))
            val parser = AlgoliaCommentsParser()
            val initial = parser.parse(payload)
            assertEquals(fixture.count, initial.comments.size, filename)
            val ids = initial.comments.filter { it.depth == 0 }.map { it.id }.reversed()
            val filters = listOf(emptySet(), setOf(initial.comments.first().by.orEmpty()))
            for ((index, filteredUsers) in filters.withIndex()) {
                val parsed = parser.parse(payload, ids, filteredUsers)
                assertEquals(fixture.comments[index], digest(parsed.comments.map(::snapshot).toString()), filename)
                val reused = Json.parseToJsonElement(assertNotNull(parsed.cacheSummary).encode(1))
                val standalone = Json.parseToJsonElement(assertNotNull(JSONParser.compactAlgoliaStoryResponse(payload, 1)))
                assertEquals(reused, standalone, filename)
                assertEquals(fixture.summary, digest(standalone.toString()), filename)
            }
        }
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun snapshot(comment: Comment): List<Any?> = listOf(
        comment.id, comment.parent, comment.by, comment.text, comment.time,
        comment.depth, comment.children, comment.expanded, comment.totalReplies,
        comment.sortOrder, comment.kidsIds?.toList(), comment.childComments.size,
    )

    private data class Fixture(
        val suffix: String,
        val count: Int,
        val comments: List<String>,
        val summary: String,
    )

    private val fixtures = listOf(
        Fixture("", 28, listOf(
            "819ef6719a13f01cfbf02a2e1fdf9fdc3de949d2d8177781e54b654ba748c893",
            "044726fb365de2d52f01b31f19384bbb58d32eb70961f2068aea50503b25bba5",
        ), "4ab833e84b51be2c90fc6e1023a951ef0dbada314df4725398bd728fcab5f9f6"),
        Fixture("_medium", 699, listOf(
            "4915bb6be17ec3742efbd93e7917b8d17936c06c3a01c1a852a98eff90f25cf5",
            "d670f0e14a939e71fbb0d846f2b4ca724bf4272d17a01e1a55e51a96d71d1f5a",
        ), "21862d181d20b3dd2cb27ddd9637b3cc08991afd98de0fb0505eb25b09fd6e1a"),
        Fixture("_large", 3767, listOf(
            "8609e90a9d72f23972fed29da8b9419ca82a210b8ff13fc9bd18fcceb8c14102",
            "854fcbb754d4476332e85cf91fadf78c948bab3b4e80d1558ab08f43f1387b9b",
        ), "7a3502e6d105d938102dc9f7e8490f497dc2e4ffa9591d632f9ec096a5ebf604"),
    )
}
