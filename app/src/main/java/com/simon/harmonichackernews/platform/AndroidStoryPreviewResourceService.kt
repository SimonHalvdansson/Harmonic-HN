package com.simon.harmonichackernews.platform

import android.content.Context
import com.simon.harmonichackernews.network.CachedStoryPreviewResource
import com.simon.harmonichackernews.network.PreviewContent
import com.simon.harmonichackernews.network.StoryPreviewImageLoader.getCachedLinkSummary
import com.simon.harmonichackernews.network.StoryPreviewImageLoader.getCachedPreviewImageUrl
import com.simon.harmonichackernews.network.StoryPreviewImageLoader.isCachedPreviewImageUrlLoaded
import com.simon.harmonichackernews.network.StoryPreviewImageLoader.loadPreviewContent
import com.simon.harmonichackernews.network.StoryPreviewResourceRequest
import com.simon.harmonichackernews.network.StoryPreviewResourceService
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Android cache/network adapter for the platform-neutral preview resource runtime. */
internal class AndroidStoryPreviewResourceService(
    private val context: () -> Context?,
) : StoryPreviewResourceService {
    override suspend fun readCached(
        request: StoryPreviewResourceRequest,
    ): CachedStoryPreviewResource {
        val currentContext = context()
        return CachedStoryPreviewResource(
            imageUrlResolved = request.loadImage && isCachedPreviewImageUrlLoaded(
                currentContext,
                request.storyId,
                request.pageUrl,
            ),
            imageUrl = getCachedPreviewImageUrl(
                currentContext,
                request.storyId,
                request.pageUrl,
            ),
            summary = if (request.loadSummary) {
                getCachedLinkSummary(currentContext, request.pageUrl)
            } else {
                null
            },
        )
    }

    override suspend fun load(request: StoryPreviewResourceRequest): PreviewContent =
        suspendCancellableCoroutine { continuation ->
            val previewRequest = loadPreviewContent(
                context(),
                request.storyId,
                request.pageUrl,
                request.loadSummary,
            ) { imageUrl, summary ->
                if (continuation.isActive) {
                    continuation.resume(PreviewContent(imageUrl, summary))
                }
            }
            continuation.invokeOnCancellation { previewRequest.cancel() }
        }
}
