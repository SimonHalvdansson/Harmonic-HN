package com.simon.harmonichackernews.ui.debug;

import android.content.Context;
import android.content.Intent;

import com.simon.harmonichackernews.MainActivity;

public final class CoulombGasContract {
    public static final String ACTION_OPEN =
            "com.simon.harmonichackernews.action.OPEN_COULOMB_GAS";

    private CoulombGasContract() {
    }

    public static Intent createIntent(Context context) {
        return new Intent(context, MainActivity.class)
                .setAction(ACTION_OPEN)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
}
