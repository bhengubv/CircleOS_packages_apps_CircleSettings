/*
 * Copyright (C) 2024 CircleOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.circleos.settings;

import android.app.Activity;
import android.circleos.AppPrivacyPolicy;
import android.circleos.ICirclePrivacyManager;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Slog;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ViewFlipper;

import java.util.List;

/**
 * First-boot privacy setup wizard.
 *
 * Shown once on first boot after AOSP SetupWizard completes.
 * Screens (controlled by ViewFlipper):
 *   0 — Welcome to Circle OS
 *   1 — Privacy Philosophy (default-deny explained)
 *   2 — Network defaults (deny-all with user confirmation)
 *   3 — Sensor defaults
 *   4 — Done
 *
 * On completion, writes a default-deny AppPrivacyPolicy for all
 * installed non-system apps and sets a shared preference flag so
 * the wizard never shows again.
 */
public class SetupWizardActivity extends Activity {

    private static final String TAG   = "CircleSetupWizard";
    private static final String PREF  = "circle_setup";
    private static final String KEY_DONE = "wizard_done";

    private ViewFlipper mFlipper;
    private int         mCurrentScreen = 0;
    private static final int TOTAL_SCREENS = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Skip if already completed
        if (getSharedPreferences(PREF, MODE_PRIVATE).getBoolean(KEY_DONE, false)) {
            startMainSettings();
            return;
        }

        setContentView(R.layout.activity_setup_wizard);
        mFlipper = findViewById(R.id.view_flipper);

        Button btnNext = findViewById(R.id.btn_next);
        Button btnBack = findViewById(R.id.btn_back);

        btnNext.setOnClickListener(v -> {
            if (mCurrentScreen < TOTAL_SCREENS - 1) {
                mCurrentScreen++;
                mFlipper.showNext();
                btnBack.setVisibility(mCurrentScreen > 0 ? View.VISIBLE : View.GONE);
                btnNext.setText(mCurrentScreen == TOTAL_SCREENS - 1 ? "Finish" : "Next");
            } else {
                onWizardComplete();
            }
        });

        btnBack.setOnClickListener(v -> {
            if (mCurrentScreen > 0) {
                mCurrentScreen--;
                mFlipper.showPrevious();
                btnBack.setVisibility(mCurrentScreen > 0 ? View.VISIBLE : View.GONE);
                btnNext.setText("Next");
            }
        });
    }

    private void onWizardComplete() {
        applyDefaultPolicies();
        getSharedPreferences(PREF, MODE_PRIVATE)
                .edit().putBoolean(KEY_DONE, true).apply();
        Slog.i(TAG, "Setup wizard completed — default policies applied");
        startMainSettings();
    }

    /**
     * Applies a default-deny policy to all user-installed apps.
     * System apps retain their existing permissions.
     */
    private void applyDefaultPolicies() {
        try {
            IBinder b = ServiceManager.getService("circle.privacy");
            if (b == null) { Slog.w(TAG, "circle.privacy not available"); return; }
            ICirclePrivacyManager manager = ICirclePrivacyManager.Stub.asInterface(b);

            List<PackageInfo> packages = getPackageManager()
                    .getInstalledPackages(PackageManager.GET_PERMISSIONS);

            for (PackageInfo pkg : packages) {
                if ((pkg.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) {
                    continue; // skip system apps
                }
                // Default policy: deny-all (all fields false = deny)
                AppPrivacyPolicy policy = new AppPrivacyPolicy();
                policy.networkAllowed  = false;
                policy.contactsAllowed = false;
                policy.storageAllowed  = false;
                manager.setPolicy(pkg.packageName, policy);
            }
            Slog.i(TAG, "Default-deny policies applied to user apps");
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to apply default policies", e);
        }
    }

    private void startMainSettings() {
        startActivity(new Intent(this, PrivacyDashboardActivity.class));
        finish();
    }
}
