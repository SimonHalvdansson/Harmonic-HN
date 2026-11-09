package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.ui.common.Button
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import org.jetbrains.compose.resources.painterResource

private val preservedSettingsData = listOf(
    Res.drawable.ic_bookmark to "Bookmarks",
    Res.drawable.ic_history to "History",
    Res.drawable.ic_star to "Favorites",
    Res.drawable.ic_label to "User tags",
    Res.drawable.ic_person to "Account",
    Res.drawable.ic_cached to "Post cache",
)

@Composable
fun ResetSettingsConfirmationDialog(
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.size(44.dp)
                        .background(colors.primaryContainer, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_refresh),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = colors.onPrimaryContainer,
                    )
                }
                Box(Modifier.weight(1f)) {
                    SettingsDialogTitle("Reset all settings?")
                }
            }
        },
        scrollableContent = true,
        compactSpacing = true,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(colors.primaryContainer, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_settings),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = colors.onPrimaryContainer,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "Restored to defaults",
                            modifier = Modifier.semantics { heading() },
                            color = colors.onPrimaryContainer,
                            fontFamily = ProductSansFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                        )
                        Text(
                            "All app preferences",
                            color = colors.onPrimaryContainer,
                            fontFamily = ProductSansFontFamily,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(colors.surfaceContainerHighest, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_shield),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = colors.primary,
                        )
                        Text(
                            "Kept as they are",
                            modifier = Modifier.semantics { heading() },
                            color = colors.onSurface,
                            fontFamily = ProductSansFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                        )
                    }
                    BoxWithConstraints {
                        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
                        val columns = if (maxWidth >= 240.dp * fontScale) 2 else 1
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            preservedSettingsData.chunked(columns).forEach { items ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items.forEach { (icon, label) ->
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                                        ) {
                                            Icon(
                                                painterResource(icon),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = colors.onSurfaceVariant,
                                            )
                                            Text(
                                                label,
                                                modifier = Modifier.weight(1f),
                                                color = colors.onSurfaceVariant,
                                                fontFamily = ProductSansFontFamily,
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            SettingsDialogTextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                onClick = onReset,
                contentPadding = PaddingValues(horizontal = 22.dp),
            ) {
                Text("Reset settings")
            }
        },
    )
}
