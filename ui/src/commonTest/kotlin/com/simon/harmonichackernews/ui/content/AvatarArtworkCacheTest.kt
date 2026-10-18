package com.simon.harmonichackernews.ui.content

import com.simon.harmonichackernews.settings.UserAvatarStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class AvatarArtworkCacheTest {
    @Test
    fun repeatedAuthorsShareArtworkButDifferentStylesRemainSeparate() {
        var creations = 0
        val cache = AvatarArtworkCache { _: String, _: UserAvatarStyle ->
            creations++
            Any()
        }
        val mosaic = cache.get("reader", UserAvatarStyle.MOSAIC)
        assertSame(mosaic, cache.get("reader", UserAvatarStyle.MOSAIC))
        val robot = cache.get("reader", UserAvatarStyle.ROBOT)
        assertNotSame(mosaic, robot)
        assertSame(robot, cache.get("reader", UserAvatarStyle.ROBOT))
        assertNotSame(mosaic, cache.get("another", UserAvatarStyle.MOSAIC))
        assertEquals(3, creations)
    }

    @Test
    fun cacheIsBoundedAndHitsRetainFrequentlySeenAuthors() {
        var creations = 0
        val cache = AvatarArtworkCache(capacity = 2) { _: String, _: UserAvatarStyle ->
            creations++
            Any()
        }
        val first = cache.get("first", UserAvatarStyle.MOSAIC)
        val second = cache.get("second", UserAvatarStyle.MOSAIC)
        assertSame(first, cache.get("first", UserAvatarStyle.MOSAIC))
        cache.get("third", UserAvatarStyle.MOSAIC)
        assertSame(first, cache.get("first", UserAvatarStyle.MOSAIC))
        assertNotSame(second, cache.get("second", UserAvatarStyle.MOSAIC))
        assertEquals(4, creations)
    }
}
