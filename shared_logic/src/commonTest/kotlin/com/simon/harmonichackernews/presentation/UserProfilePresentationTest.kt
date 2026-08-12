package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.network.dto.HackerNewsUserDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserProfilePresentationTest {
    @Test
    fun formatsProfileWithoutJvmDateOrHtmlApis() {
        val result = UserProfilePresenter.present(
            HackerNewsUserDto(
                id = "pg",
                created = 1_169_856_000L,
                karma = 12_345,
                about = "<p>Hello <b>HN</b></p>",
                submitted = listOf(1),
            ),
            MONTHS,
        )

        assertEquals("pg", result.id)
        assertEquals("12,345 karma since January 27, 2007", result.meta)
        assertEquals("Hello HN", result.about)
        assertTrue(result.hasSubmissions)
    }

    private companion object {
        val MONTHS = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        )
    }
}
