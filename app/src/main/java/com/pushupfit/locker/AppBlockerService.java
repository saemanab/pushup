package com.pushupfit.locker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.core.app.NotificationCompat;

import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * AppBlockerService — Foreground service that monitors the foreground app.
 *
 * Now respects the user-selected blocked package list from SessionManager.
 * Only draws the overlay when:
 * 1. The session is locked (isLocked == true)
 * 2. The foreground app is in the user's chosen blocked-packages set
 * 3. The foreground app is not PushUp Locker itself
 *
 * If no packages were selected, ALL non-launcher apps are blocked (same as v1).
 */
public class AppBlockerService extends Service {

    private static final String CHANNEL_ID = "pushup_blocker_channel";
    private static final int NOTIF_ID = 1;
    private static final long POLL_INTERVAL_MS = 800L;

    private WindowManager windowManager;
    private View overlayView;
    private boolean overlayShowing = false;

    private final Handler handler = new Handler();
    private SessionManager session;
    private String myPackage;

    // ── Polling runnable ───────────────────────────────────────────────────────
    private final Runnable pollForeground = new Runnable() {
        @Override
        public void run() {
            // Stop if session unlocked OR if user never selected any apps to block
            if (!session.isLocked() || !session.hasBlockedPackages()) {
                if (overlayShowing)
                    hideOverlay();
                stopSelf();
                return;
            }

            String foreground = getForegroundPackage();
            boolean shouldBlock = foreground != null
                    && !foreground.equals(myPackage)
                    && !isLauncher(foreground)
                    && isInBlockList(foreground);

            if (shouldBlock && !overlayShowing)
                showOverlay(foreground);
            else if (!shouldBlock && overlayShowing)
                hideOverlay();

            handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    // ══════════════════════════════════════════════════════════════════════════
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        session = new SessionManager(this);
        myPackage = getPackageName();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        startForeground(NOTIF_ID, buildNotification());
        handler.post(pollForeground);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (overlayShowing)
            hideOverlay();
    }

    // ── Check if the foreground package should be blocked ─────────────────────
    private boolean isInBlockList(String pkg) {
        Set<String> blocked = session.getBlockedPackages();
        if (blocked.isEmpty()) {
            // No apps selected → block nothing
            return false;
        }
        return blocked.contains(pkg);
    }

    private boolean isLauncher(String pkg) {
        return pkg.equals("android")
                || pkg.contains("launcher")
                || pkg.contains("home")
                || pkg.equals("com.android.systemui");
    }

    // ── Foreground app detection via UsageStatsManager ────────────────────────
    private String getForegroundPackage() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();

        List<UsageStats> stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, now - 2000, now);

        if (stats == null || stats.isEmpty())
            return null;

        SortedMap<Long, UsageStats> sorted = new TreeMap<>();
        for (UsageStats us : stats)
            sorted.put(us.getLastTimeUsed(), us);

        if (sorted.isEmpty())
            return null;
        return sorted.get(sorted.lastKey()).getPackageName();
    }

    // ── Overlay management ────────────────────────────────────────────────────
    private void showOverlay(String blockedPkg) {
        if (!Settings.canDrawOverlays(this) || overlayShowing)
            return;

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;

        overlayView = LayoutInflater.from(this)
                .inflate(R.layout.overlay_blocked, null);

        TextView tvMsg = overlayView.findViewById(R.id.tvOverlayMessage);
        int remaining = session.getGoal() - session.getReps();
        tvMsg.setText("Complete " + remaining + " more pushups to unlock " +
                blockedPkg.substring(blockedPkg.lastIndexOf('.') + 1) + "!");

        overlayView.setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
        });

        windowManager.addView(overlayView, params);
        overlayShowing = true;
    }

    private void hideOverlay() {
        if (overlayView != null && overlayShowing) {
            windowManager.removeView(overlayView);
            overlayShowing = false;
            overlayView = null;
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────
    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "PushUp Locker Active",
                NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Monitors app usage during your workout session");
        nm.createNotificationChannel(ch);

        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("💪 PushUp Locker — Session Active")
                .setContentText("Complete your pushup goal to unlock apps")
                .setSmallIcon(R.drawable.ic_lock_small)
                .setColor(0xFF9B59FF) // Premium purple accent
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }
}
