package com.simon.harmonichackernews.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.months
import org.jetbrains.compose.resources.stringArrayResource

@Composable
fun AndroidUserSettingsDialog(
    userName: String,
    onDismiss: () -> Unit,
    onTagChanged: () -> Unit,
) {
    val context = LocalContext.current
    val dependencies = LocalHarmonicUiDependencies.current
    UserProfileDialog(
        scene = dependencies.scene,
        settingsRepository = dependencies.settings,
        userTags = dependencies.userTags,
        userName = userName,
        onDismiss = onDismiss,
        onTagChanged = onTagChanged,
        monthNames = stringArrayResource(Res.array.months),
        onReportEmail = { username ->
            val intent = Intent(
                Intent.ACTION_SENDTO,
                ("mailto:hn@ycombinator.com?subject=" +
                    Uri.encode("Reporting user $username")).toUri(),
            )
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(Intent.createChooser(intent, "Send report via"))
            }
        },
    )
}
