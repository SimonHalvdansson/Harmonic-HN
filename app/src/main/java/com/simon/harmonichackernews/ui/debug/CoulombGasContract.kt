package com.simon.harmonichackernews.ui.debug

import android.content.Context
import android.content.Intent
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.navigation.AppDestinationCodec
import com.simon.harmonichackernews.navigation.CoulombGasDestination

object CoulombGasContract {
    const val ACTION_OPEN = "com.simon.harmonichackernews.action.OPEN_COULOMB_GAS"

    fun createIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .setAction(ACTION_OPEN)
        .putExtra(
            AppDestinationCodec.ANDROID_PAYLOAD_EXTRA,
            AppDestinationCodec.encode(CoulombGasDestination),
        )
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
