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

    @Test
    fun sha256MatchesPaddingAndBlockBoundaryVectors() {
        // Independently generated with Python's hashlib.sha256 for ASCII 'a' repeated N times.
        val vectors = listOf(
            55 to "9f4390f8d30c2dd92ec9f095b65e2b9ae9b0a925a5258e241c9f1e910f734318",
            56 to "b35439a4ac6f0948b6d6f9e3c6af0f5f590ce20f1bde7090ef7970686ec6738a",
            63 to "7d3e74a05d7db15bce4ad9ec0658ea98e3f06eeecf16b4c6fff2da457ddc2f34",
            64 to "ffe054fe7ae0cb6dc65c3af9b61d5209f439851db43d0ba5997337df154668eb",
            65 to "635361c48bb9eab14198e76ea8ab7f1a41685d6ad62aa9146d301d4f17eb0ae0",
            119 to "31eba51c313a5c08226adf18d4a359cfdfd8d2e816b13f4af952f7ea6584dcfb",
            120 to "2f3d335432c70b580af0e8e1b3674a7c020d683aa5f73aaaedfdc55af904c21c",
            127 to "c57e9278af78fa3cab38667bef4ce29d783787a2f731d4e12200270f0c32320a",
            128 to "6836cf13bac400e9105071cd6af47084dfacad4e5e302c94bfed24e013afb73e",
            129 to "c12cb024a2e5551cca0e08fce8f1c5e314555cc3fef6329ee994a3db752166ae",
        )
        for ((length, expected) in vectors) {
            assertEquals(expected, StableHash.sha256Hex("a".repeat(length)), "Length $length")
        }
    }

    @Test
    fun sha256MatchesUtf8AndLargeInputVectors() {
        assertEquals(
            "97810f544a9f2317750c7c2911bf2b86249405734d6c323c6a2e1d1b22153f8c",
            StableHash.sha256Hex("æ漢😀".repeat(40)),
        )
        assertEquals(
            "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
            StableHash.sha256Hex("a".repeat(1_000_000)),
        )
    }

    @Test
    fun sha256PreservesPlatformMalformedUtf8Replacement() {
        val malformed = listOf("\uD800", "\uDC00", "\uD800\uD800", "\uDC00\uD800", "\uD800x\uDC00")
        for (prefixLength in listOf(0, 55, 63, 4092, 4093, 4095, 4096, 8191)) {
            for (suffix in malformed) {
                val value = "a".repeat(prefixLength) + suffix + "😀漢"
                assertEquals(
                    StableHash.sha256Hex(value.encodeToByteArray().decodeToString()),
                    StableHash.sha256Hex(value),
                    "Malformed input after $prefixLength characters",
                )
            }
        }
    }

    @Test
    fun sha256MatchesUtf8AcrossWorkspaceBoundaries() {
        // Independent Python hashlib vectors: multibyte characters cross both hash blocks and
        // workspace boundaries, including UTF-16 surrogate pairs representing supplementary text.
        val vectors = listOf(
            54 to "7876f6dad14aec0d679a71637cc295a2ccee070a958f7308bbb71f5b714b68aa",
            55 to "1b57afae9a99866ffa217c9f0fa9ec95b13ea8af749444dad4b986716f56e3a1",
            56 to "42169d0967a89f8f15dfcdbcc7de67586c73c5caf1ffcfbe3a0cc90bafc2f73a",
            60 to "fd06cf3ef0a2c38d8a3748ed790de72f4194a053a955331d07fc4e176f2699a9",
            61 to "1930decf75c9f4e49d167745392d2fc4bbfc0104511ed2579a8d8b8c1e75ef07",
            62 to "d30faa12f4af40a19a825ec3b074585b6c52c1e914d0cf85b41d8286082f6ebd",
            63 to "068c3f44bd07afbf94edd80e907396839ac22c5942e6eb78ef6c505a4dd98a4c",
            64 to "7239af4e7e8535244b5e29833b65506f160004bc3e81f7758aa853f17020583e",
            4090 to "5cf35cc6f9b566100218c26868e79e432d088699185c09e2f59b2f7d6a20da89",
            4091 to "a24bf14c9acdf6f5971371eab40a0a9935118fea32c0a26de7a4a9f4f8ba1fd2",
            4092 to "55a62b5aa1ce51b9c0da2fe1180fd2c6b93a35950d63b2e5b2e3baeef91e4d7a",
            4093 to "2333e5ae0c7ab288fb20d13754ff53839d38c87ea3db45b6cd6f7446c56e98a4",
            4094 to "685cb9edd1b4d70a048f7f93ec188b9f87690510546507d8c3203dbf7999affc",
            4095 to "8eb4cf735755fd1b28c68ac02340846469c35004e17f5bf18e1515d036eee0b9",
            4096 to "122cb2d0205b33daebbc6a271d7bc3553370d6eff001570bd09ef929c83f58a7",
            4097 to "af77e5f4bf6c3b9e0f197d48ff60e220b03bf7e4c8914f29199df1e7cc9de1aa",
            8189 to "9bc876096af7ec0ed53fda5279dcf4f0784a35d22c91fa3ee34b965758706e66",
            8192 to "88c9c42f268e38aad3f6f966814057e74b9159fe42bd54d6f6b7219283fa6059",
        )
        for ((length, expected) in vectors) {
            val value = "a".repeat(length) + "\u0000\u007f\u0080\u07ff\u0800漢😀\uDBFF\uDFFF" + "z".repeat(129)
            assertEquals(expected, StableHash.sha256Hex(value), "Prefix $length")
        }
    }

    @Test
    fun sha256MatchesMultibyteTextAcrossMultipleWorkspaces() {
        // Independent hashlib vectors; every workspace contains multibyte UTF-8 sequences.
        val vectors = listOf(
            511 to "23d15d138e54815c3a63e22ff1291af8cad0f7cd9e78b3ea526d99fd42d03b37",
            512 to "4cf28b2fc9210cc57ec1d539772c720fabf7bc601b7bbf329612c6ff1a855266",
            513 to "fc60c5931a0fb031933057fc5312142afdef779afb12753ffd980d63ce21909d",
            4096 to "a332a7ae7ea1782623e3bb351b99ab3ca26ea895f4145308fbc0c80385fe2b4c",
        )
        for ((repetitions, expected) in vectors) {
            assertEquals(expected, StableHash.sha256Hex("æ漢😀".repeat(repetitions)))
        }
    }
}
