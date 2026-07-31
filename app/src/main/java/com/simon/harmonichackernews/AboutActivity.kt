package com.simon.harmonichackernews

import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simon.harmonichackernews.ui.about.AboutScreen
import com.simon.harmonichackernews.ui.common.HarmonicComposeActivity
import com.simon.harmonichackernews.utils.Changelog
import com.simon.harmonichackernews.utils.Utils

class AboutActivity : HarmonicComposeActivity() {

    @Composable
    override fun HarmonicContent() {
        AboutScreen(
            onBack = onBackPressedDispatcher::onBackPressed,
            onOpenGithub = ::openGithub,
            onOpenChangelog = ::openChangelog,
            onOpenLicenses = ::openLicenses,
            onOpenPrivacy = ::openPrivacy,
        )
    }

    private fun openGithub() {
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                "https://github.com/SimonHalvdansson/Harmonic-HN".toUri(),
            ),
        )
    }

    private fun openChangelog() {
        val dialog: AlertDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Changelog")
            .setMessage(Changelog.getFormatted(this))
            .setNegativeButton("Done", null)
            .create()

        dialog.show()
    }

    private fun openLicenses() {
        startActivity(Intent(this, LicensesActivity::class.java))
    }

    private fun openPrivacy() {
        Utils.launchCustomTab(this, "https://simonhalvdansson.github.io/harmonic_privacy.html")
    }
}
