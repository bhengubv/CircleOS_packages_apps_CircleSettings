/*
 * Copyright (C) 2024 CircleOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.circleos.settings.privacy;

import android.circleos.AppPrivacyPolicy;
import android.circleos.ICirclePrivacyManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.util.Slog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import com.circleos.settings.R;

import java.util.List;

/**
 * ListView adapter for the Privacy Dashboard app list.
 * Each row shows: app icon, name, privacy score, and a network toggle.
 */
public class AppPrivacyAdapter extends ArrayAdapter<PrivacyDashboardActivity.AppPrivacySummary> {

    private static final String TAG = "CirclePrivacyAdapter";

    private final ICirclePrivacyManager mManager;

    public AppPrivacyAdapter(Context context,
                             List<PrivacyDashboardActivity.AppPrivacySummary> items,
                             ICirclePrivacyManager manager) {
        super(context, 0, items);
        mManager = manager;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_app_privacy, parent, false);
        }

        PrivacyDashboardActivity.AppPrivacySummary item = getItem(position);
        if (item == null) return convertView;

        ImageView icon    = convertView.findViewById(R.id.app_icon);
        TextView  name    = convertView.findViewById(R.id.app_name);
        TextView  score   = convertView.findViewById(R.id.privacy_score);
        Switch    network = convertView.findViewById(R.id.network_toggle);

        // App icon
        try {
            PackageManager pm = getContext().getPackageManager();
            icon.setImageDrawable(pm.getApplicationIcon(item.app));
            name.setText(pm.getApplicationLabel(item.app));
        } catch (Exception ignored) {
            name.setText(item.app.packageName);
        }

        // Privacy score badge
        score.setText(item.score + "/100");
        score.setTextColor(scoreColor(item.score));

        // Network toggle — no listener during bind to avoid spurious callbacks
        network.setOnCheckedChangeListener(null);
        network.setChecked(item.policy != null && item.policy.networkAllowed);
        network.setOnCheckedChangeListener((btn, checked) -> {
            try {
                AppPrivacyPolicy policy = mManager.getPolicy(item.app.packageName);
                policy.networkAllowed = checked;
                mManager.setPolicy(item.app.packageName, policy);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to update network policy", e);
                btn.setChecked(!checked); // revert
            }
        });

        // Tap row → full per-app settings
        convertView.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), AppPrivacyDetailActivity.class);
            intent.putExtra(AppPrivacyDetailActivity.EXTRA_PACKAGE, item.app.packageName);
            getContext().startActivity(intent);
        });

        return convertView;
    }

    private static int scoreColor(int score) {
        if (score >= 80) return 0xFF4CAF50; // green
        if (score >= 50) return 0xFFFFC107; // amber
        return 0xFFF44336;                   // red
    }
}
