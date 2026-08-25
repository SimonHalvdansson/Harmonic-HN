package com.simon.harmonichackernews.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReplyNotificationPresentationTest {
    @Test
    fun presentsOneReplyWithoutGroupSummary() {
        val batch = ReplyNotificationPresentation.present(
            listOf(HackerNewsReply(12, 10, "alice", "Hello")),
        )

        assertEquals(1, batch.notifications.size)
        assertEquals("New reply from alice", batch.notifications.single().title)
        assertEquals("https://news.ycombinator.com/item?id=10#12", batch.notifications.single().deepLink)
        assertNull(batch.summary)
    }

    @Test
    fun deduplicatesOrdersAndUsesLatestReplyForSummaryLink() {
        val batch = ReplyNotificationPresentation.present(
            listOf(
                HackerNewsReply(22, 20, "bob", "Later"),
                HackerNewsReply(11, 9, "alice", "Earlier"),
                HackerNewsReply(22, 20, "bob", "Duplicate"),
            ),
        )

        assertEquals(listOf(11, 22), batch.notifications.map(ReplyNotificationPayload::id))
        assertEquals("2 new replies", batch.summary?.title)
        assertEquals("https://news.ycombinator.com/item?id=20#22", batch.summary?.deepLink)
    }
}
