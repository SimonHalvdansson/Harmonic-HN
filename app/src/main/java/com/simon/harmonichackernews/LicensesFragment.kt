package com.simon.harmonichackernews

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.simon.harmonichackernews.ui.licenses.LicensesScreen
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.utils.Utils

class LicensesFragment : Fragment() {

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
                    LicensesScreen(
                        onBack = ::navigateBack,
                        onOpenLicense = { url ->
                            Utils.launchCustomTab(requireActivity(), url)
                        },
                        singlePane = !isSettingsTwoPane,
                    )
                }
            }
        }
    }

    private fun navigateBack() {
        val settingsActivity = activity as? SettingsActivity
        if (settingsActivity?.isTwoPane == true) {
            settingsActivity.showAbout()
        } else {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }
    }
}
