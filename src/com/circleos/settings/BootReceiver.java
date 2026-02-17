/*
 * Copyright (C) 2024 CircleOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.circleos.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Slog;

/**
 * Triggered on BOOT_COMPLETED to:
 *   1. Show setup wizard if this is the first boot.
 *   2. Schedule the threat intel update job.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG  = "CircleBootReceiver";
    private static final String PREF = "circle_setup";
    private static final String KEY_DONE = "wizard_done";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        Slog.i(TAG, "BOOT_COMPLETED received");

        // Schedule recurring jobs
        ThreatIntelUpdater.schedule(context);

        // Launch setup wizard on first boot
        boolean wizardDone = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getBoolean(KEY_DONE, false);
        if (!wizardDone) {
            Intent wizard = new Intent(context, SetupWizardActivity.class);
            wizard.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(wizard);
            Slog.i(TAG, "Launched setup wizard (first boot)");
        }
    }
}
