/*
 * Copyright (C) 2024 CircleOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.circleos.settings.privacy;

import android.app.Activity;
import android.circleos.AppPrivacyPolicy;
import android.circleos.ICirclePrivacyManager;
import android.circleos.PermissionUsageRecord;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Slog;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Main Privacy Dashboard — entry point for CircleSettings.
 *
 * Shows:
 *  - Per-app privacy scores (ICirclePrivacyManager.getPrivacyScore)
 *  - Quick network toggle per app
 *  - Link to full per-app settings
 *  - Recent audit log entries
 *
 * Each list item is an AppPrivacySummaryItem showing:
 *   [App icon] [App name] [Score badge] [Network toggle]
 */
public class PrivacyDashboardActivity extends Activity {

    private static final String TAG = "CirclePrivacyDashboard";

    private ICirclePrivacyManager mPrivacyManager;
    private ListView              mAppList;
    private TextView              mHeaderScore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_dashboard);

        mAppList     = findViewById(R.id.app_list);
        mHeaderScore = findViewById(R.id.header_score);

        connectToPrivacyManager();
        if (mPrivacyManager != null) {
            loadApps();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mPrivacyManager != null) loadApps();
    }

    private void connectToPrivacyManager() {
        IBinder b = ServiceManager.getService("circle.privacy");
        if (b == null) {
            Slog.e(TAG, "circle.privacy service not found");
            return;
        }
        mPrivacyManager = ICirclePrivacyManager.Stub.asInterface(b);
    }

    private void loadApps() {
        List<ApplicationInfo> apps = getPackageManager()
                .getInstalledApplications(PackageManager.GET_META_DATA);

        List<AppPrivacySummary> summaries = new ArrayList<>();
        int totalScore = 0;

        for (ApplicationInfo app : apps) {
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            try {
                int score = mPrivacyManager.getPrivacyScore(app.packageName);
                AppPrivacyPolicy policy = mPrivacyManager.getPolicy(app.packageName);
                summaries.add(new AppPrivacySummary(app, score, policy));
                totalScore += score;
            } catch (RemoteException e) {
                Slog.w(TAG, "Failed to get score for " + app.packageName);
            }
        }

        // Sort by score ascending (most risky first)
        summaries.sort((a, b) -> Integer.compare(a.score, b.score));

        int avg = summaries.isEmpty() ? 100 : totalScore / summaries.size();
        mHeaderScore.setText("Device Privacy Score: " + avg + "/100");

        mAppList.setAdapter(new AppPrivacyAdapter(this, summaries, mPrivacyManager));
    }

    /** Simple data holder for dashboard list items. */
    static class AppPrivacySummary {
        final ApplicationInfo app;
        final int             score;
        final AppPrivacyPolicy policy;

        AppPrivacySummary(ApplicationInfo app, int score, AppPrivacyPolicy policy) {
            this.app    = app;
            this.score  = score;
            this.policy = policy;
        }
    }
}
