package com.simon.harmonichackernews.network

import kotlin.test.Test
import kotlin.test.assertEquals

class StableHashTest {
    @Test
    fun sha256MatchesStandardVectors() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            StableHash.sha256Hex(""),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            StableHash.sha256Hex("abc"),
        )
    }
}
