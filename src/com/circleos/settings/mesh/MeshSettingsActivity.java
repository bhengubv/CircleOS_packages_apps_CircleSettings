/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package com.circleos.settings.mesh;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import za.co.circleos.mesh.ICircleMeshService;

/**
 * Mesh Network settings screen.
 *
 * Shows:
 *  - Mesh running status + toggle (start/stop)
 *  - Local rotating device ID (16-char hex)
 *  - Live peer count (refreshes every 5 s)
 *  - Peer list placeholder (future: per-peer detail)
 */
public class MeshSettingsActivity extends Activity {

    private ICircleMeshService mMesh;
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());

    private TextView mTvDeviceId;
    private TextView mTvPeerCount;
    private TextView mTvStatus;
    private Switch   mSwMesh;

    private final Runnable mRefreshRunnable = new Runnable() {
        @Override public void run() {
            if (!isFinishing()) {
                refreshStatus();
                mUiHandler.postDelayed(this, 5_000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildLayout());
        connectService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mUiHandler.post(mRefreshRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mUiHandler.removeCallbacks(mRefreshRunnable);
    }

    private void connectService() {
        new Thread(() -> {
            IBinder binder = ServiceManager.getService("circle.mesh");
            if (binder != null) {
                mMesh = ICircleMeshService.Stub.asInterface(binder);
            }
            mUiHandler.post(this::refreshStatus);
        }).start();
    }

    private void refreshStatus() {
        if (mMesh == null) {
            mTvStatus.setText("Service unavailable");
            return;
        }
        new Thread(() -> {
            try {
                boolean running  = mMesh.isRunning();
                int     peers    = mMesh.getPeerCount();
                String  deviceId = mMesh.getDeviceId();
                mUiHandler.post(() -> {
                    mTvStatus.setText(running ? "● Running" : "● Stopped");
                    mTvStatus.setTextColor(running ? 0xFF006600 : 0xFFCC0000);
                    mSwMesh.setChecked(running);
                    mTvPeerCount.setText(String.valueOf(peers));
                    if (deviceId != null) mTvDeviceId.setText(deviceId);
                });
            } catch (RemoteException e) {
                mUiHandler.post(() -> mTvStatus.setText("Error: " + e.getMessage()));
            }
        }).start();
    }

    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF2F2F7);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        // Title
        root.addView(makeTitle("Mesh Network"));

        // Status row
        LinearLayout statusRow = makeRow();
        mTvStatus = makeLabel("Connecting…", 15, 0xFF888888);
        mSwMesh = new Switch(this);
        mSwMesh.setOnCheckedChangeListener((btn, checked) -> {
            // Mesh lifecycle is managed by the system service; toggle is informational only
            // (start/stop API not exposed over binder in v1)
        });
        statusRow.addView(makeLabel("Mesh Stack", 15, 0xFF1A1A2E));
        statusRow.addView(spacer());
        statusRow.addView(mTvStatus);
        root.addView(wrapCard(statusRow));

        // Device ID
        LinearLayout idRow = makeRow();
        mTvDeviceId = makeLabel("—", 13, 0xFF444444);
        mTvDeviceId.setTypeface(android.graphics.Typeface.MONOSPACE);
        idRow.addView(makeLabel("Device ID", 15, 0xFF1A1A2E));
        idRow.addView(spacer());
        idRow.addView(mTvDeviceId);
        root.addView(wrapCard(idRow));

        // Peer count
        LinearLayout peerRow = makeRow();
        mTvPeerCount = makeLabel("—", 24, 0xFF1A1A2E);
        mTvPeerCount.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        peerRow.addView(makeLabel("Visible Peers", 15, 0xFF1A1A2E));
        peerRow.addView(spacer());
        peerRow.addView(mTvPeerCount);
        root.addView(wrapCard(peerRow));

        // Info note
        TextView note = makeLabel(
                "Device ID rotates every 24 hours for privacy.\n"
              + "Peer count updates every 5 seconds.", 13, 0xFF888888);
        note.setPadding(dp(4), dp(12), dp(4), 0);
        root.addView(note);

        return root;
    }

    // ── Layout helpers ────────────────────────────────────────────────────────

    private TextView makeTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(22);
        tv.setTextColor(0xFF1A1A2E);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(4), dp(8), dp(4), dp(16));
        return tv;
    }

    private TextView makeLabel(String text, int sp, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        return tv;
    }

    private LinearLayout makeRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        return row;
    }

    private View wrapCard(View inner) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(0xFFFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(lp);
        card.addView(inner);
        return card;
    }

    private View spacer() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return v;
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
