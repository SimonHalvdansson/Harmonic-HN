package com.simon.harmonichackernews.ui.debug

import android.content.Context
import android.content.Intent
import com.simon.harmonichackernews.MainActivity

object CoulombGasContract {
    const val ACTION_OPEN: kotlin.String = "com.simon.harmonichackernews.action.OPEN_COULOMB_GAS"

    fun createIntent(context: android.content.Context?): Intent {
        return Intent(context, MainActivity::class.java)
            .setAction(CoulombGasContract.ACTION_OPEN)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
