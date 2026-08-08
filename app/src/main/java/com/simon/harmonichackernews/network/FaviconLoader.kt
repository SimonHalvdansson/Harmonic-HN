package com.simon.harmonichackernews.network

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.core.content.ContextCompat
import coil3.Image
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.error
import coil3.request.fallback
import coil3.request.placeholder
import coil3.request.target
import coil3.target.ImageViewTarget
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.Utils

object FaviconLoader {
    @JvmOverloads
    fun loadFavicon(
        url: String?,
        into: ImageView,
        ctx: Context,
        faviconProvider: String?,
        fadeIn: Boolean = false
    ) {
        try {
            loadFaviconForHost(
                Utils.getDomainName(url ?: return), into, ctx, faviconProvider, fadeIn
            )
        } catch (ignored: Exception) {
        }
    }

    @JvmOverloads
    fun loadFavicon(
        story: Story,
        into: ImageView,
        ctx: Context,
        faviconProvider: String?,
        fadeIn: Boolean = false
    ) {
        try {
            val host = story.getDisplayDomain(true) ?: return
            loadFaviconForHost(host, into, ctx, faviconProvider, fadeIn)
        } catch (ignored: Exception) {
        }
    }

    private fun loadFaviconForHost(
        host: String,
        into: ImageView,
        ctx: Context,
        faviconProvider: String?,
        fadeIn: Boolean
    ) {
        val faviconUrl = getFaviconUrlForHost(host, faviconProvider)
        if (faviconUrl == into.getTag(R.id.favicon_request_url)) {
            return
        }
        val faviconSize = Utils.pxFromDpInt(ctx.resources, 17f)
        val webDrawable = checkNotNull(ContextCompat.getDrawable(ctx, R.drawable.ic_public))
        applyFaviconThumbnailShape(into)
        into.setTag(R.id.favicon_request_url, faviconUrl)

        val request = ImageRequest.Builder(ctx)
            .data(faviconUrl)
            .size(
                faviconSize,
                faviconSize
            ) // Metadata shared-element snapshots draw the favicon and text together onto
            // a software bitmap. A crossfade around a hardware bitmap makes that capture
            // fail and causes the entire metadata row to be omitted from the transition.
            .allowHardware(false)
            .placeholder(webDrawable)
            .error(webDrawable)
            .fallback(webDrawable)
            .crossfade(fadeIn)
            .target(object : ImageViewTarget(into) {
                override fun onStart(placeholder: Image?) {
                    if (faviconUrl == into.getTag(R.id.favicon_request_url)) {
                        super.onStart(placeholder)
                    }
                }

                override fun onError(error: Image?) {
                    if (faviconUrl == into.getTag(R.id.favicon_request_url)) {
                        into.setTag(R.id.favicon_request_url, null)
                        super.onError(error)
                    }
                }

                override fun onSuccess(result: Image) {
                    if (faviconUrl == into.getTag(R.id.favicon_request_url)) {
                        super.onSuccess(result)
                    }
                }
            })
            .build()

        ctx.imageLoader.enqueue(request)
    }

    private fun applyFaviconThumbnailShape(into: ImageView) {
        if (into.background == null) {
            into.setBackgroundResource(R.drawable.favicon_thumbnail_background)
        }
        into.clipToOutline = true
    }

    @Throws(Exception::class)
    fun getFaviconUrl(url: String?, faviconProvider: String?): String {
        return getFaviconUrlForHost(Utils.getDomainName(url ?: throw IllegalArgumentException("Missing URL")), faviconProvider)
    }

    fun getFaviconUrlSchema(faviconProvider: String?): String {
        return getFaviconUrlForHost("{host}", faviconProvider)
    }

    private fun getFaviconUrlForHost(host: String, faviconProvider: String?): String {
        return when (SettingsUtils.sanitizeFaviconProvider(faviconProvider)) {
            SettingsUtils.FAVICON_PROVIDER_TWENTY -> "https://twenty-icons.com/$host"
            SettingsUtils.FAVICON_PROVIDER_DUCKDUCKGO -> "https://icons.duckduckgo.com/ip3/$host.ico"
            else -> "https://www.google.com/s2/favicons?domain=$host&sz=128"
        }
    }
}
