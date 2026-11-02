package com.simon.harmonichackernews.ui.content

/**
 * Releases recomputable, process-owned UI cache entries under memory pressure.
 *
 * Hosts call this on the UI owner thread. Active text/artwork/image references remain valid;
 * no bitmap is recycled and no screen, reading position, draft, or persisted content is changed.
 * The resource-image cache may suspend until an in-progress decode finishes.
 */
suspend fun trimHarmonicUiMemoryCaches() {
    clearCommentContentCaches()
    clearAvatarArtworkCache()
    clearResourcePreviewCache()
}
