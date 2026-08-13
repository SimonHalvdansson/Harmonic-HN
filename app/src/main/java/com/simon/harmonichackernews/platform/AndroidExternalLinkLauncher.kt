package com.simon.harmonichackernews.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.webkit.URLUtil
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION
import androidx.core.content.ContextCompat
import com.simon.harmonichackernews.harmonicAppComposition
import com.simon.harmonichackernews.utils.ExternalUrlPolicy
import com.simon.harmonichackernews.utils.ThemeUtils
import com.simon.harmonichackernews.utils.defaultBrowserPackageName
import com.simon.harmonichackernews.utils.isInvalidViewHandlerPackage

/** Android browser/custom-tab facility behind the shared external-link contract. */
object AndroidExternalLinkLauncher {
    fun openCustomTab(context: Context, url: String?, shareable: Boolean = true): Boolean {
        val originalUrl = url ?: return false
        if (context.harmonicAppComposition.userSettings.reading.externalBrowser ||
            !isCustomTabSupported(context)
        ) {
            return openExternalBrowser(context, originalUrl)
        }

        return try {
            createCustomTabsIntent(context, shareable).launchUrl(context, Uri.parse(originalUrl))
            true
        } catch (_: Exception) {
            try {
                createCustomTabsIntent(context, shareable).launchUrl(
                    context,
                    Uri.parse(URLUtil.guessUrl(originalUrl)),
                )
                true
            } catch (_: Exception) {
                val fallbackUrl = ExternalUrlPolicy.ensureHttpScheme(originalUrl)
                try {
                    createCustomTabsIntent(context, shareable).launchUrl(
                        context,
                        Uri.parse(fallbackUrl),
                    )
                    true
                } catch (_: Exception) {
                    openExternalBrowser(context, fallbackUrl)
                }
            }
        }
    }

    fun openExternalBrowser(context: Context, url: String): Boolean =
        try {
            openExternalUrl(context, url)
            true
        } catch (_: Exception) {
            try {
                openExternalUrl(context, URLUtil.guessUrl(url))
                true
            } catch (_: Exception) {
                val fallbackUrl = ExternalUrlPolicy.ensureHttpScheme(url)
                try {
                    openExternalUrl(context, fallbackUrl)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }

    private fun createCustomTabsIntent(
        context: Context,
        shareable: Boolean,
    ): CustomTabsIntent {
        val colorScheme = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(
                ContextCompat.getColor(
                    context,
                    ThemeUtils.getBackgroundColorResource(context),
                ),
            )
            .build()
        return CustomTabsIntent.Builder()
            .setShareState(
                if (shareable) CustomTabsIntent.SHARE_STATE_ON
                else CustomTabsIntent.SHARE_STATE_OFF,
            )
            .setDefaultColorSchemeParams(colorScheme)
            .build()
    }

    private fun openExternalUrl(context: Context, url: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        packageForExternalUrl(context, browserIntent)?.let(browserIntent::setPackage)
        context.startActivity(browserIntent)
    }

    private fun packageForExternalUrl(context: Context, browserIntent: Intent): String? {
        val defaultBrowserPackageName = context.defaultBrowserPackageName() ?: return null
        val resolvedPackageName = context.packageManager
            .resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
        return defaultBrowserPackageName.takeIf {
            context.packageName == resolvedPackageName ||
                context.isInvalidViewHandlerPackage(resolvedPackageName)
        }
    }

    private fun isCustomTabSupported(context: Context): Boolean =
        customTabsPackages(context).isNotEmpty()

    private fun customTabsPackages(context: Context): List<ResolveInfo> {
        val packageManager = context.packageManager
        val activityIntent = Intent()
            .setAction(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.fromParts("http", "", null))
        return packageManager.queryIntentActivities(activityIntent, 0).filter { info ->
            val serviceIntent = Intent().apply {
                action = ACTION_CUSTOM_TABS_CONNECTION
                setPackage(info.activityInfo.packageName)
            }
            packageManager.resolveService(serviceIntent, 0) != null
        }
    }
}
