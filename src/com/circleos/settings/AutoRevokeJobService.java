/*
 * Copyright (C) 2024 CircleOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.circleos.settings;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.circleos.ICirclePrivacyManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Slog;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * JobService that revokes Circle OS permissions for apps that haven't
 * used them in 90+ days. Scheduled every 7 days by CirclePermissionService.
 *
 * Calls ICirclePrivacyManager.revokeUnusedPermissions() which triggers
 * the server-side scan and revocation logic.
 */
public class AutoRevokeJobService extends JobService {

    private static final String TAG = "CircleAutoRevoke";

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    @Override
    public boolean onStartJob(JobParameters params) {
        Slog.i(TAG, "Auto-revoke job started");
        mExecutor.execute(() -> {
            try {
                IBinder b = ServiceManager.getService("circle.privacy");
                if (b == null) {
                    Slog.w(TAG, "circle.privacy service not found");
                    jobFinished(params, true /* reschedule */);
                    return;
                }
                ICirclePrivacyManager manager = ICirclePrivacyManager.Stub.asInterface(b);
                manager.revokeUnusedPermissions();
                Slog.i(TAG, "Auto-revoke scan complete");
                jobFinished(params, false);
            } catch (RemoteException e) {
                Slog.e(TAG, "Auto-revoke failed", e);
                jobFinished(params, true /* reschedule */);
            }
        });
        return true; // job running asynchronously
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        mExecutor.shutdownNow();
        return true; // reschedule
    }
}
