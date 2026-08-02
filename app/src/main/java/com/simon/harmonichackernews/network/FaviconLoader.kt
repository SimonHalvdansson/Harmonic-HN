package com.simon.harmonichackernews.network

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.core.content.ContextCompat
import coil.Coil
import coil.Coil.imageLoader
import coil.request.ImageRequest
import coil.target.ImageViewTarget
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.Utils
import java.util.Objects

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
            FaviconLoader.loadFaviconForHost(
                story.getDisplayDomain(true)!!, into, ctx, faviconProvider, fadeIn
            )
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
        val faviconSize = Utils.pxFromDpInt(ctx.getResources(), 17f)
        val webDrawable = Objects.requireNonNull<Drawable?>(
            ContextCompat.getDrawable(ctx, R.drawable.ic_public)
        )
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
                override fun onStart(placeholder: Drawable?) {
                    if (faviconUrl == into.getTag(R.id.favicon_request_url)) {
                        super.onStart(placeholder)
                    }
                }

                override fun onError(error: Drawable?) {
                    if (faviconUrl == into.getTag(R.id.favicon_request_url)) {
                        into.setTag(R.id.favicon_request_url, null)
                        super.onError(error)
                    }
                }

                override fun onSuccess(result: Drawable) {
                    if (faviconUrl == into.getTag(R.id.favicon_request_url)) {
                        super.onSuccess(result)
                    }
                }
            })
            .build()

        imageLoader(ctx).enqueue(request)
    }

    private fun applyFaviconThumbnailShape(into: ImageView) {
        if (into.getBackground() == null) {
            into.setBackgroundResource(R.drawable.favicon_thumbnail_background)
        }
        into.setClipToOutline(true)
    }

    @Throws(Exception::class)
    fun getFaviconUrl(url: String?, faviconProvider: String?): String {
        return getFaviconUrlForHost(Utils.getDomainName(url ?: throw IllegalArgumentException("Missing URL")), faviconProvider)
    }

    fun getFaviconUrlSchema(faviconProvider: String?): String {
        return getFaviconUrlForHost("{host}", faviconProvider)
    }

    private fun getFaviconUrlForHost(host: String, faviconProvider: String?): String {
        when (SettingsUtils.sanitizeFaviconProvider(faviconProvider)) {
            SettingsUtils.FAVICON_PROVIDER_TWENTY -> return "https://twenty-icons.com/" + host
            SettingsUtils.FAVICON_PROVIDER_GOOGLE -> return "https://www.google.com/s2/favicons?domain=" + host + "&sz=128"
            SettingsUtils.FAVICON_PROVIDER_DUCKDUCKGO -> return "https://icons.duckduckgo.com/ip3/" + host + ".ico"
            else -> return "https://www.google.com/s2/favicons?domain=" + host + "&sz=128"
        }
    }
}
