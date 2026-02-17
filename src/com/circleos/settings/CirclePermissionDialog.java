/*
 * Copyright (C) 2024 CircleOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.circleos.settings;

import android.app.Activity;
import android.circleos.AppPrivacyPolicy;
import android.circleos.ICirclePrivacyManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.util.Slog;

/**
 * Permission dialog shown when PrivacyRulesEngine returns Action.ASK.
 *
 * Started via an Intent from the permission grant hook in system_server.
 * Intent extras:
 *   EXTRA_PACKAGE_NAME — the requesting app
 *   EXTRA_PERMISSION   — the Circle OS permission being requested
 *   EXTRA_CONTEXT      — optional context string (domain, sensor type, etc.)
 *
 * The user's choice is written back to CirclePrivacyManagerService and
 * optionally persisted ("remember this choice").
 */
public class CirclePermissionDialog extends Activity {

    private static final String TAG = "CirclePermDialog";

    public static final String EXTRA_PACKAGE_NAME = "circle.extra.PACKAGE_NAME";
    public static final String EXTRA_PERMISSION   = "circle.extra.PERMISSION";
    public static final String EXTRA_CONTEXT      = "circle.extra.CONTEXT";

    public static final int RESULT_GRANTED = RESULT_OK;
    public static final int RESULT_DENIED  = RESULT_CANCELED;

    private String mPackageName;
    private String mPermission;
    private String mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_permission);

        mPackageName = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        mPermission  = getIntent().getStringExtra(EXTRA_PERMISSION);
        mContext     = getIntent().getStringExtra(EXTRA_CONTEXT);

        if (mPackageName == null || mPermission == null) {
            finish();
            return;
        }

        setupViews();
    }

    private void setupViews() {
        // App icon
        ImageView icon = findViewById(R.id.app_icon);
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(mPackageName, 0);
            icon.setImageDrawable(pm.getApplicationIcon(info));
        } catch (PackageManager.NameNotFoundException ignored) {}

        // App name + permission description
        TextView title = findViewById(R.id.dialog_title);
        TextView desc  = findViewById(R.id.dialog_description);
        title.setText(getAppName(mPackageName) + " wants " + describePermission(mPermission));
        if (mContext != null) {
            desc.setText("Context: " + mContext);
        }

        CheckBox remember = findViewById(R.id.remember_choice);

        // Allow button
        Button allow = findViewById(R.id.btn_allow);
        allow.setOnClickListener(v -> {
            applyChoice(true, remember.isChecked());
            setResult(RESULT_GRANTED);
            finish();
        });

        // Deny button
        Button deny = findViewById(R.id.btn_deny);
        deny.setOnClickListener(v -> {
            applyChoice(false, remember.isChecked());
            setResult(RESULT_DENIED);
            finish();
        });
    }

    private void applyChoice(boolean granted, boolean persist) {
        if (!persist) return; // one-shot: system handles the immediate grant; don't persist
        try {
            IBinder b = ServiceManager.getService("circle.privacy");
            if (b == null) return;
            ICirclePrivacyManager manager = ICirclePrivacyManager.Stub.asInterface(b);
            AppPrivacyPolicy policy = manager.getPolicy(mPackageName);
            if ("com.circleos.permission.NETWORK".equals(mPermission)) {
                policy.networkAllowed = granted;
            } else if (mPermission != null && mPermission.startsWith("com.circleos.permission.")) {
                String sensor = mPermission.substring("com.circleos.permission.".length());
                if (granted) {
                    if (!policy.allowedSensors.contains(sensor)) policy.allowedSensors.add(sensor);
                } else {
                    policy.allowedSensors.remove(sensor);
                }
            }
            manager.setPolicy(mPackageName, policy);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to persist permission choice", e);
        }
    }

    private String getAppName(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return (String) pm.getApplicationLabel(info);
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static String describePermission(String permission) {
        if (permission == null) return "a permission";
        switch (permission) {
            case "com.circleos.permission.NETWORK":       return "internet access";
            case "com.circleos.permission.ACCELEROMETER": return "accelerometer access";
            case "com.circleos.permission.GYROSCOPE":     return "gyroscope access";
            case "com.circleos.permission.BAROMETER":     return "barometer access";
            case "com.circleos.permission.MAGNETOMETER":  return "magnetometer access";
            default: return permission;
        }
    }
}
