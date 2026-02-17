/*
 * Copyright (C) 2024 CircleOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.circleos.settings;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Slog;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * JobService that fetches updated threat intelligence and merges it into
 * /data/circle/threat_intel.db. Runs every 24 hours on Wi-Fi.
 *
 * Sources (public block lists):
 *   - Steven Black unified hosts list (trackers + malware)
 *   - abuse.ch URLhaus (malware URLs)
 *
 * Merges new entries without disrupting the running CircleDomainFilterService;
 * the filter service reloads the DB on its next scan cycle.
 */
public class ThreatIntelUpdater extends JobService {

    private static final String TAG    = "CircleThreatIntel";
    private static final int    JOB_ID = 0xC1C1E002;
    private static final String DB_PATH = "/data/circle/threat_intel.db";

    // Public block list URLs (hosts format: "0.0.0.0 domain.com")
    private static final String[] BLOCK_LIST_URLS = {
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
    };

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    /** Call this from CirclePermissionService.onBootPhase to register the job. */
    public static void schedule(Context context) {
        JobScheduler js = context.getSystemService(JobScheduler.class);
        if (js == null) return;
        ComponentName component = new ComponentName(
                "com.circleos.settings",
                "com.circleos.settings.ThreatIntelUpdater");
        JobInfo job = new JobInfo.Builder(JOB_ID, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED) // Wi-Fi only
                .setRequiresCharging(false)
                .setPeriodic(24 * 60 * 60 * 1000L) // 24 hours
                .setPersisted(true)
                .build();
        js.schedule(job);
        Slog.i(TAG, "Threat intel update job scheduled");
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        mExecutor.execute(() -> {
            try {
                int added = fetchAndMerge();
                Slog.i(TAG, "Threat intel update complete: " + added + " new entries");
            } catch (Exception e) {
                Slog.e(TAG, "Threat intel update failed", e);
            } finally {
                jobFinished(params, false);
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        mExecutor.shutdownNow();
        return true;
    }

    private int fetchAndMerge() throws Exception {
        List<String> domains = new ArrayList<>();

        for (String urlStr : BLOCK_LIST_URLS) {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(30_000);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("#") || line.isEmpty()) continue;
                    // Hosts format: "0.0.0.0 tracker.com" or "127.0.0.1 tracker.com"
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2 &&
                            (parts[0].equals("0.0.0.0") || parts[0].equals("127.0.0.1"))) {
                        String domain = parts[1].toLowerCase().trim();
                        if (!domain.equals("localhost") && domain.contains(".")) {
                            domains.add(domain);
                        }
                    }
                }
            } finally {
                conn.disconnect();
            }
        }

        return mergeIntoDB(domains);
    }

    private int mergeIntoDB(List<String> domains) {
        int added = 0;
        try {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(
                    DB_PATH, null, SQLiteDatabase.OPEN_READWRITE);
            db.beginTransaction();
            try {
                long now = System.currentTimeMillis() / 1000;
                for (String domain : domains) {
                    ContentValues cv = new ContentValues(4);
                    cv.put("domain",   domain);
                    cv.put("category", "TRACKER");
                    cv.put("severity", 1);
                    cv.put("added_at", now);
                    cv.put("source",   "StevenBlack/hosts");
                    long result = db.insertWithOnConflict(
                            "threat_domains", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
                    if (result != -1) added++;
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            db.close();
        } catch (Exception e) {
            Slog.e(TAG, "DB merge failed", e);
        }
        return added;
    }
}
