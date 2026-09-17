package com.simon.harmonichackernews.resources

/** Large generated payloads shared by Android, iOS and desktop hosts. */
object BundledHarmonicResources {
    const val AD_BLOCKLIST = "files/adblock/adblockserverlist.bin"

    suspend fun adBlocklist(): ByteArray = Res.readBytes(AD_BLOCKLIST)
}
