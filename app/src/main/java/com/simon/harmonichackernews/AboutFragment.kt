package com.simon.harmonichackernews

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simon.harmonichackernews.ui.about.AboutScreen
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.utils.Changelog
import com.simon.harmonichackernews.utils.Utils

class AboutFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val settingsActivity = activity as? SettingsActivity
        val isSettingsTwoPane = settingsActivity?.isTwoPane == true

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            setContent {
                HarmonicTheme {
                    AboutScreen(
                        onBack = {
                            activity?.onBackPressedDispatcher?.onBackPressed()
                        },
                        onOpenGithub = ::openGithub,
                        onOpenChangelog = ::openChangelog,
                        onOpenLicenses = ::openLicenses,
                        onOpenPrivacy = ::openPrivacy,
                        showNavigation = !isSettingsTwoPane,
                        singlePane = !isSettingsTwoPane,
                    )
                }
            }
        }
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
        val dialog: AlertDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Changelog")
            .setMessage(Changelog.getFormatted(requireContext()))
            .setNegativeButton("Done", null)
            .create()

        dialog.show()
    }

    private fun openLicenses() {
        val settingsActivity = activity as? SettingsActivity
        if (settingsActivity?.isTwoPane == true) {
            settingsActivity.showLicenses()
        } else {
            startActivity(Intent(requireContext(), LicensesActivity::class.java))
        }
    }

    private fun openPrivacy() {
        Utils.launchCustomTab(
            requireActivity(),
            "https://simonhalvdansson.github.io/harmonic_privacy.html",
        )
    }
}
