/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package com.circleos.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.circleos.settings.mesh.MeshSettingsActivity;
import com.circleos.settings.privacy.PrivacyDashboardActivity;
import com.circleos.settings.update.UpdateSettingsActivity;

/**
 * Top-level hub for CircleOS settings.
 *
 * Shows three cards:
 *   - Privacy      → PrivacyDashboardActivity
 *   - Mesh Network → MeshSettingsActivity
 *   - System Update → UpdateSettingsActivity
 */
public class CircleSettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildLayout());
    }

    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF2F2F7);

        // Title bar
        TextView title = new TextView(this);
        title.setText("CircleOS Settings");
        title.setTextSize(22);
        title.setTextColor(0xFF1A1A2E);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dp(20), dp(24), dp(20), dp(16));
        root.addView(title);

        // Cards
        root.addView(buildCard(
                "Privacy",
                "App permissions, network isolation, audit log",
                0xFF1A1A2E,
                v -> startActivity(new Intent(this, PrivacyDashboardActivity.class))));

        root.addView(buildCard(
                "Mesh Network",
                "P2P discovery, peer list, device identity",
                0xFF2E4057,
                v -> startActivity(new Intent(this, MeshSettingsActivity.class))));

        root.addView(buildCard(
                "System Update",
                "OTA channel, check for updates, download status",
                0xFF048A81,
                v -> startActivity(new Intent(this, UpdateSettingsActivity.class))));

        return root;
    }

    private View buildCard(String title, String subtitle, int color, View.OnClickListener click) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(color);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(16), dp(8), dp(16), dp(8));
        card.setLayoutParams(params);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(18);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText(subtitle);
        tvSub.setTextSize(13);
        tvSub.setTextColor(0xCCFFFFFF);
        tvSub.setPadding(0, dp(4), 0, 0);
        card.addView(tvSub);

        card.setOnClickListener(click);
        return card;
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
