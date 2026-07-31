package com.simon.harmonichackernews

import androidx.compose.runtime.Composable
import com.simon.harmonichackernews.ui.common.HarmonicComposeActivity
import com.simon.harmonichackernews.ui.licenses.LicensesScreen
import com.simon.harmonichackernews.utils.Utils

class LicensesActivity : HarmonicComposeActivity() {

    @Composable
    override fun HarmonicContent() {
        LicensesScreen(
            onBack = onBackPressedDispatcher::onBackPressed,
            onOpenLicense = { url ->
                Utils.launchCustomTab(this, url)
            },
            singlePane = true,
        )
    }
}
