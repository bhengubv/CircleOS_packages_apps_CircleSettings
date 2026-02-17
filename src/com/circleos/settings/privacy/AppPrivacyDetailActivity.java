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
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Slog;
import android.widget.Switch;
import android.widget.TextView;

import com.circleos.settings.R;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Per-app privacy settings screen.
 *
 * Shows and allows editing of:
 *  - Network permission (toggle + domain allow-list)
 *  - Sensor permissions (one toggle per sensor)
 *  - Contacts access toggle
 *  - Storage access toggle
 *  - Lobby mode toggle
 *  - Last 20 audit log entries for this app
 */
public class AppPrivacyDetailActivity extends Activity {

    private static final String TAG = "CircleAppPrivacyDetail";
    public  static final String EXTRA_PACKAGE = "circle.extra.PACKAGE";

    private static final String[] SENSORS = {
        "ACCELEROMETER", "GYROSCOPE", "BAROMETER", "MAGNETOMETER"
    };

    private ICirclePrivacyManager mManager;
    private String                mPackageName;
    private AppPrivacyPolicy      mPolicy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_privacy_detail);

        mPackageName = getIntent().getStringExtra(EXTRA_PACKAGE);
        if (mPackageName == null) { finish(); return; }

        IBinder b = ServiceManager.getService("circle.privacy");
        if (b == null) { finish(); return; }
        mManager = ICirclePrivacyManager.Stub.asInterface(b);

        loadAndBindPolicy();
        loadAuditLog();
    }

    private void loadAndBindPolicy() {
        try {
            mPolicy = mManager.getPolicy(mPackageName);
        } catch (RemoteException e) {
            mPolicy = new AppPrivacyPolicy();
        }

        bindSwitch(R.id.switch_network,  mPolicy.networkAllowed,  v -> {
            mPolicy.networkAllowed = v; savePolicy();
        });
        bindSwitch(R.id.switch_contacts, mPolicy.contactsAllowed, v -> {
            mPolicy.contactsAllowed = v; savePolicy();
        });
        bindSwitch(R.id.switch_storage,  mPolicy.storageAllowed,  v -> {
            mPolicy.storageAllowed = v; savePolicy();
        });
        bindSwitch(R.id.switch_lobby,    mPolicy.lobbyMode,       v -> {
            mPolicy.lobbyMode = v; savePolicy();
        });

        // Sensor toggles
        int[] sensorIds = {
            R.id.switch_accelerometer, R.id.switch_gyroscope,
            R.id.switch_barometer, R.id.switch_magnetometer
        };
        for (int i = 0; i < SENSORS.length; i++) {
            final String sensor = SENSORS[i];
            bindSwitch(sensorIds[i], mPolicy.allowedSensors.contains(sensor), v -> {
                if (v) { if (!mPolicy.allowedSensors.contains(sensor)) mPolicy.allowedSensors.add(sensor); }
                else   { mPolicy.allowedSensors.remove(sensor); }
                savePolicy();
            });
        }
    }

    private void loadAuditLog() {
        try {
            long since = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000); // 7 days
            List<PermissionUsageRecord> records = mManager.getUsageLog(mPackageName, since);
            StringBuilder sb = new StringBuilder();
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm", Locale.US);
            int count = Math.min(records.size(), 20);
            for (int i = 0; i < count; i++) {
                PermissionUsageRecord r = records.get(i);
                sb.append(sdf.format(new Date(r.timestamp)))
                  .append("  ").append(r.action)
                  .append("  ").append(shortPermission(r.permission));
                if (r.extra != null) sb.append(" (").append(r.extra).append(")");
                sb.append("\n");
            }
            TextView log = findViewById(R.id.audit_log);
            log.setText(sb.length() > 0 ? sb.toString() : "No recent activity");
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to load audit log", e);
        }
    }

    private void savePolicy() {
        try {
            mManager.setPolicy(mPackageName, mPolicy);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to save policy", e);
        }
    }

    private void bindSwitch(int viewId, boolean initial,
                            java.util.function.Consumer<Boolean> onChange) {
        Switch sw = findViewById(viewId);
        if (sw == null) return;
        sw.setOnCheckedChangeListener(null);
        sw.setChecked(initial);
        sw.setOnCheckedChangeListener((btn, checked) -> onChange.accept(checked));
    }

    private static String shortPermission(String perm) {
        if (perm == null) return "";
        int dot = perm.lastIndexOf('.');
        return dot >= 0 ? perm.substring(dot + 1) : perm;
    }
}
