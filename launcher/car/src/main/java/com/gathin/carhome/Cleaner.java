package com.gathin.carhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.storage.StorageManager;
import android.util.Log;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The Clean button. RAM: asks Android to close apps sitting in the background (the same as swiping them
 * away), never the app on screen, music or navigation that is running (they have a foreground service),
 * nor anything the car needs ({@link Keep}). Cache: on Android 11+, shows the system's own "clear the
 * cache of all apps?" dialog; on older units (this A30 runs Android 9) it runs {@code pm trim-caches}
 * through the unit's debugging service ({@link LocalShell}). Both delete cache files only: no sign-ins,
 * settings or downloads.
 */
final class Cleaner {
    private static final String TAG = "CarHome.Clean";
    static final int REQUEST_CLEAR_CACHE = 7;
    private static volatile boolean running;

    /** Result of {@link #trimCaches}. */
    static final int TRIM_DONE = 0, TRIM_NOT_ALLOWED = 1, TRIM_NO_DEBUGGING = 2;
    private static volatile boolean trimming;

    interface Trimmed {
        void onTrimmed(int result, long freedBytes);
    }

    interface Done {
        void onDone(int closed, long freedBytes, float usedFraction);
    }

    private Cleaner() {
    }

    /** RAM in use, 0 to 1. */
    static float usedFraction(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.totalMem <= 0 ? 0f : 1f - mi.availMem / (float) mi.totalMem;
    }

    /**
     * Closes background apps on a worker thread and reports on the main thread. Only apps the person
     * installed or can open from the grid ({@code launchable}) are candidates; vendor system services
     * are never touched.
     */
    static void cleanRam(Context ctx, Set<String> launchable, Collection<String> keep, Done done) {
        if (running) return;
        running = true;
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            int closed = 0;
            long freed = 0;
            try {
                ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo before = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(before);
                PackageManager pm = app.getPackageManager();
                List<ApplicationInfo> apps = pm.getInstalledApplications(0);
                for (ApplicationInfo ai : apps) {
                    String pkg = ai.packageName;
                    if (pkg.equals(app.getPackageName())) continue;
                    if ((ai.flags & ApplicationInfo.FLAG_PERSISTENT) != 0) continue;
                    boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                            && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
                    if (system && !launchable.contains(pkg)) continue;
                    if (Keep.isProtected(pkg, keep)) continue;
                    try {
                        am.killBackgroundProcesses(pkg);
                        closed++;
                    } catch (SecurityException e) {
                        Log.w(TAG, "not allowed to close " + pkg);
                    }
                }
                // Our own leftovers too.
                deleteContents(app.getCacheDir());
                deleteContents(app.getExternalCacheDir());
                System.gc();
                try {
                    Thread.sleep(800);
                } catch (InterruptedException ignored) {
                }
                ActivityManager.MemoryInfo after = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(after);
                freed = Math.max(0, after.availMem - before.availMem);
                Log.i(TAG, "closed background apps: " + closed + " checked, " + (freed >> 20) + " MB freed");
            } catch (RuntimeException e) {
                Log.w(TAG, "clean failed: " + e);
            } finally {
                running = false;
            }
            final int c = closed;
            final long f = freed;
            new Handler(Looper.getMainLooper()).post(() -> done.onDone(c, f, usedFraction(app)));
        }, "car-clean").start();
    }

    /**
     * Shows Android's "clear all app caches?" dialog. Returns false when this head unit can't
     * (older than 11, or the one-time "All files access" was not granted), so the caller can explain.
     */
    static boolean askClearCaches(Activity a) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false;
        if (!Environment.isExternalStorageManager()) return false;
        try {
            a.startActivityForResult(new Intent(StorageManager.ACTION_CLEAR_APP_CACHE), REQUEST_CLEAR_CACHE);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "no clear-cache dialog on this firmware: " + e);
            return false;
        }
    }

    /**
     * Older Android: asks the unit's debugging service to trim every app's cache. {@code mayAsk} lets it
     * show "Allow USB debugging?" (only when someone tapped Clean, never on its own).
     */
    static void trimCaches(Context ctx, boolean mayAsk, Trimmed done) {
        if (trimming) return;
        trimming = true;
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            int result;
            long before = freeBytes();
            try {
                String cmd = "shell:pm trim-caches 999999999999";
                // A key allowed a moment ago is not always let through on the connection that asked.
                boolean ok = LocalShell.shell(app, false, cmd) || mayAsk && (LocalShell.shell(app, true, cmd)
                        || LocalShell.shell(app, false, cmd));
                result = ok ? TRIM_DONE : TRIM_NOT_ALLOWED;
            } catch (java.io.IOException e) {
                Log.i(TAG, "debugging service not reachable: " + e);
                result = TRIM_NO_DEBUGGING;
            } catch (Exception e) {
                Log.w(TAG, "trim-caches failed: " + e);
                result = TRIM_NO_DEBUGGING;
            } finally {
                trimming = false;
            }
            if (result == TRIM_DONE) Log.i(TAG, "cleared cached files");
            final int r = result;
            final long freed = Math.max(0, freeBytes() - before);
            new Handler(Looper.getMainLooper()).post(() -> done.onTrimmed(r, freed));
        }, "car-trim").start();
    }

    private static long freeBytes() {
        try {
            return new android.os.StatFs(Environment.getDataDirectory().getPath()).getAvailableBytes();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static void deleteContents(File dir) {
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) deleteContents(f);
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }
}
