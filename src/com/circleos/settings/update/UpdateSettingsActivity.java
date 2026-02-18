/*
 * Copyright (C) 2024 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 */
package com.circleos.settings.update;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import za.co.circleos.update.ICircleUpdateService;

/**
 * OTA update settings screen.
 *
 * Shows:
 *  - Current update state (IDLE / CHECKING / DOWNLOADING / READY / INSTALLING / FAILED)
 *  - Available version (if found)
 *  - Download progress (if downloading)
 *  - Last check time
 *  - Channel picker: stable / beta / nightly
 *  - "Check Now" button
 *  - "Install Now" button (shown when READY_TO_INSTALL)
 */
public class UpdateSettingsActivity extends Activity {

    private static final int STATE_IDLE             = 0;
    private static final int STATE_CHECKING         = 1;
    private static final int STATE_DOWNLOADING      = 2;
    private static final int STATE_READY_TO_INSTALL = 3;
    private static final int STATE_INSTALLING       = 4;
    private static final int STATE_FAILED           = 5;

    private ICircleUpdateService mUpdate;
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());

    private TextView    mTvState;
    private TextView    mTvVersion;
    private TextView    mTvProgress;
    private TextView    mTvLastCheck;
    private RadioGroup  mRgChannel;
    private RadioButton mRbStable;
    private RadioButton mRbBeta;
    private RadioButton mRbNightly;
    private Button      mBtnCheckNow;
    private Button      mBtnInstall;

    private final Runnable mRefreshRunnable = new Runnable() {
        @Override public void run() {
            if (!isFinishing()) {
                refreshStatus();
                mUiHandler.postDelayed(this, 10_000);
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
            IBinder binder = ServiceManager.getService("circle.update");
            if (binder != null) {
                mUpdate = ICircleUpdateService.Stub.asInterface(binder);
            }
            mUiHandler.post(this::refreshStatus);
        }).start();
    }

    private void refreshStatus() {
        if (mUpdate == null) {
            mTvState.setText("Service unavailable");
            return;
        }
        new Thread(() -> {
            try {
                int    state     = mUpdate.getState();
                String version   = mUpdate.getAvailableVersion();
                int    progress  = mUpdate.getDownloadProgress();
                long   lastCheck = mUpdate.getLastCheckTime();
                String channel   = mUpdate.getChannel();

                mUiHandler.post(() -> {
                    mTvState.setText(stateName(state));
                    mTvState.setTextColor(stateColor(state));

                    mTvVersion.setText(version != null ? version : "—");

                    if (state == STATE_DOWNLOADING && progress >= 0) {
                        mTvProgress.setText("Downloading: " + progress + "%");
                        mTvProgress.setVisibility(View.VISIBLE);
                    } else {
                        mTvProgress.setVisibility(View.GONE);
                    }

                    if (lastCheck > 0) {
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault());
                        mTvLastCheck.setText("Last check: " + sdf.format(new Date(lastCheck)));
                    } else {
                        mTvLastCheck.setText("Never checked");
                    }

                    // Set channel radio
                    if ("beta".equals(channel))         mRbBeta.setChecked(true);
                    else if ("nightly".equals(channel)) mRbNightly.setChecked(true);
                    else                                 mRbStable.setChecked(true);

                    // Show Install button only when ready
                    mBtnInstall.setVisibility(
                            state == STATE_READY_TO_INSTALL ? View.VISIBLE : View.GONE);

                    // Disable Check Now during active states
                    mBtnCheckNow.setEnabled(
                            state == STATE_IDLE || state == STATE_FAILED);
                });
            } catch (RemoteException e) {
                mUiHandler.post(() -> mTvState.setText("Error: " + e.getMessage()));
            }
        }).start();
    }

    private void onChannelSelected(String channel) {
        if (mUpdate == null) return;
        new Thread(() -> {
            try { mUpdate.setChannel(channel); }
            catch (RemoteException e) { /* ignore */ }
        }).start();
    }

    private void onCheckNow() {
        if (mUpdate == null) return;
        mBtnCheckNow.setEnabled(false);
        new Thread(() -> {
            try { mUpdate.checkNow(); }
            catch (RemoteException e) { /* ignore */ }
            mUiHandler.postDelayed(this::refreshStatus, 2_000);
        }).start();
    }

    private void onInstall() {
        if (mUpdate == null) return;
        mBtnInstall.setEnabled(false);
        new Thread(() -> {
            try { mUpdate.applyUpdate(); }
            catch (RemoteException e) { /* ignore */ }
        }).start();
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF2F2F7);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        root.addView(makeTitle("System Update"));

        // Status card
        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setBackgroundColor(0xFFFFFFFF);
        statusCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        statusCard.setLayoutParams(cardParams());

        mTvState = makeLabel("Connecting…", 16, 0xFF888888);
        mTvState.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        statusCard.addView(mTvState);

        LinearLayout verRow = makeRow();
        verRow.addView(makeLabel("Available version", 14, 0xFF666666));
        verRow.addView(spacer());
        mTvVersion = makeLabel("—", 14, 0xFF1A1A2E);
        verRow.addView(mTvVersion);
        statusCard.addView(verRow);

        mTvProgress = makeLabel("", 13, 0xFF048A81);
        mTvProgress.setVisibility(View.GONE);
        statusCard.addView(mTvProgress);

        mTvLastCheck = makeLabel("", 12, 0xFF888888);
        mTvLastCheck.setPadding(0, dp(4), 0, 0);
        statusCard.addView(mTvLastCheck);

        root.addView(statusCard);

        // Channel card
        LinearLayout channelCard = new LinearLayout(this);
        channelCard.setOrientation(LinearLayout.VERTICAL);
        channelCard.setBackgroundColor(0xFFFFFFFF);
        channelCard.setPadding(dp(16), dp(12), dp(16), dp(12));
        channelCard.setLayoutParams(cardParams());
        channelCard.addView(makeLabel("Update Channel", 15, 0xFF1A1A2E));

        mRgChannel = new RadioGroup(this);
        mRgChannel.setOrientation(RadioGroup.VERTICAL);

        mRbStable  = makeRadio("Stable  — recommended");
        mRbBeta    = makeRadio("Beta    — early features");
        mRbNightly = makeRadio("Nightly — latest builds");

        mRgChannel.addView(mRbStable);
        mRgChannel.addView(mRbBeta);
        mRgChannel.addView(mRbNightly);

        mRgChannel.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == mRbBeta.getId())         onChannelSelected("beta");
            else if (checkedId == mRbNightly.getId()) onChannelSelected("nightly");
            else                                       onChannelSelected("stable");
        });

        channelCard.addView(mRgChannel);
        root.addView(channelCard);

        // Action buttons
        mBtnCheckNow = new Button(this);
        mBtnCheckNow.setText("Check Now");
        mBtnCheckNow.setTextColor(0xFFFFFFFF);
        mBtnCheckNow.setBackgroundColor(0xFF1A1A2E);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(0, 0, 0, dp(8));
        mBtnCheckNow.setLayoutParams(btnLp);
        mBtnCheckNow.setOnClickListener(v -> onCheckNow());
        root.addView(mBtnCheckNow);

        mBtnInstall = new Button(this);
        mBtnInstall.setText("Install Now");
        mBtnInstall.setTextColor(0xFFFFFFFF);
        mBtnInstall.setBackgroundColor(0xFF048A81);
        mBtnInstall.setLayoutParams(btnLp);
        mBtnInstall.setVisibility(View.GONE);
        mBtnInstall.setOnClickListener(v -> onInstall());
        root.addView(mBtnInstall);

        return root;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String stateName(int state) {
        switch (state) {
            case STATE_IDLE:             return "Up to date";
            case STATE_CHECKING:         return "Checking…";
            case STATE_DOWNLOADING:      return "Downloading…";
            case STATE_READY_TO_INSTALL: return "Ready to install";
            case STATE_INSTALLING:       return "Installing…";
            case STATE_FAILED:           return "Failed";
            default:                     return "Unknown";
        }
    }

    private static int stateColor(int state) {
        switch (state) {
            case STATE_READY_TO_INSTALL: return 0xFF048A81;
            case STATE_FAILED:           return 0xFFCC0000;
            case STATE_IDLE:             return 0xFF006600;
            default:                     return 0xFF444444;
        }
    }

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

    private RadioButton makeRadio(String label) {
        RadioButton rb = new RadioButton(this);
        rb.setText(label);
        rb.setTextSize(14);
        rb.setTextColor(0xFF1A1A2E);
        return rb;
    }

    private LinearLayout makeRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, dp(2));
        return row;
    }

    private View spacer() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return v;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        return lp;
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
