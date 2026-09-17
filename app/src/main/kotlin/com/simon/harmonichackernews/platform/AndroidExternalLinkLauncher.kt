package com.simon.harmonichackernews.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.simon.harmonichackernews.utils.ThemeUtils
import com.simon.harmonichackernews.utils.defaultBrowserPackageName
import com.simon.harmonichackernews.utils.isInvalidViewHandlerPackage

/** Android browser/custom-tab facility behind the shared external-link contract. */
object AndroidExternalLinkLauncher {
    fun openCustomTab(context: Context, request: ExternalLinkRequest): Boolean {
        if (!isCustomTabSupported(context)) return openExternalBrowser(context, request)
        val customTabsIntent = createCustomTabsIntent(context, request.shareable)
        val opened = ExternalLinkPolicy.openCandidates(request.url).any { candidate ->
            try {
                customTabsIntent.launchUrl(context, candidate.toUri())
                true
            } catch (_: Exception) {
                false
            }
        }
        return opened || openExternalBrowser(context, request)
    }

    fun openExternalBrowser(context: Context, request: ExternalLinkRequest): Boolean =
        ExternalLinkPolicy.openCandidates(request.url).any { candidate ->
            try {
                openExternalUrl(context, candidate)
                true
            } catch (_: Exception) {
                false
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
            .apply { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

    private fun openExternalUrl(context: Context, url: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
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
