package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.network.OpenRouterProviderIcon
import com.simon.harmonichackernews.network.networkHeader
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily

@Composable
fun AiModelSelectorDialog(onDismiss: () -> Unit) {
    SharedAiModelSelectorRoute(
        onDismiss = onDismiss,
        providerIcon = { providerSlug -> AiModelProviderIcon(providerSlug) },
    )
}

@Composable
private fun AiModelProviderIcon(providerSlug: String) {
    val context = LocalContext.current
    val appComposition = LocalHarmonicUiDependencies.current
    var iconData by remember(providerSlug) { mutableStateOf<Any?>(null) }

    LaunchedEffect(providerSlug, appComposition) {
        iconData = runCatching {
            when (
                val icon = appComposition.network.openRouterProviderIconRepository
                    .resolve(providerSlug).icon
            ) {
                is OpenRouterProviderIcon.RemoteUrl -> icon.url
                is OpenRouterProviderIcon.SvgBytes -> icon.bytes
                null -> null
            }
        }.getOrNull()
    }

    Box(
        modifier = Modifier
            .size(24.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = providerSlug.take(1).uppercase().ifEmpty { "?" },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = ProductSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
        iconData?.let { resolvedIcon ->
            val request = remember(context, resolvedIcon) {
                ImageRequest.Builder(context)
                    .data(resolvedIcon)
                    .networkHeader("User-Agent", appComposition.network.userAgent)
                    .crossfade(100)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(22.dp).clip(CircleShape),
            )
        }
    }
}
