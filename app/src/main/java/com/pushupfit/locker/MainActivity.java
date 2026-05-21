package com.pushupfit.locker;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.provider.Settings;
import androidx.cardview.widget.CardView;
import androidx.core.app.NotificationCompat;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import com.google.android.material.button.MaterialButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions;

import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MainActivity — Workout session screen.
 *
 * Full-screen camera preview (background) with a HUD overlay:
 *   • Session timer counting up (mm:ss)
 *   • Rep counter  "X / GOAL"  derived from quiz answers
 *   • ML Kit Pose Detection → PushupDetector counts elbow-angle reps
 *   • Elbow angle + phase indicator
 *   • Horizontal progress bar
 *   • Start / Pause / Resume button
 *
 * Falls back to proximity + accelerometer if camera permission is denied.
 */
public class MainActivity extends AppCompatActivity
        implements android.hardware.SensorEventListener {

    // ── Permission codes ───────────────────────────────────────────────────────
    private static final int REQ_CAMERA     = 100;
    private static final int REQ_OVERLAY    = 101;
    private static final int REQ_USAGE_STATS = 102;

    // ── Sensor fallback thresholds ─────────────────────────────────────────────
    private static final float PROX_THRESHOLD_CM = 3.0f;
    private static final float ACCEL_Z_THRESHOLD = 12.0f;
    private static final long  DEBOUNCE_MS       = 500L;

    // ── Session state ─────────────────────────────────────────────────────────
    private int     repCount     = 0;
    private int     repGoal      = SessionManager.DEFAULT_GOAL;
    private boolean sessionActive = false;
    private boolean isCameraWarmup = false;

    // ── Timer ─────────────────────────────────────────────────────────────────
    private long    sessionStartMs = 0L;
    private long    elapsedMs      = 0L;   // accumulates across pause/resume
    private boolean timerRunning   = false;

    private final Runnable tickTimer = new Runnable() {
        @Override
        public void run() {
            if (timerRunning) {
                long currentSessionTotal = elapsedMs + (System.currentTimeMillis() - sessionStartMs);
                
                // Show today's cumulative time on the Home Screen card
                long dailyMs = sessionManager.getTodayTotalTimeMs() + currentSessionTotal;
                long dailyMins = (dailyMs / 1000) / 60;
                long dailySecs = (dailyMs / 1000) % 60;
                tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", dailyMins, dailySecs));
                
                // Show current session breakdown on HUD overlay
                long sessionMins = (currentSessionTotal / 1000) / 60;
                long sessionSecs = (currentSessionTotal / 1000) % 60;
                if (tvHudTimer != null) {
                    tvHudTimer.setText(String.format(Locale.getDefault(), "⏱  %02d:%02d", sessionMins, sessionSecs));
                }
                
                mainHandler.postDelayed(this, 500);
            }
        }
    };

    // Ticks the home-screen time card every second while NOT in a session,
    // so the card always shows the up-to-date today total time.
    private boolean homeTimerRunning = false;
    private final Runnable tickHomeTimer = new Runnable() {
        @Override
        public void run() {
            if (!homeTimerRunning) return;
            if (!timerRunning && tvTimer != null) {
                long tMs = sessionManager.getTodayTotalTimeMs();
                tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d",
                        (tMs / 1000) / 60, (tMs / 1000) % 60));
            }
            mainHandler.postDelayed(this, 1000);
        }
    };

    // ── Camera & ML Kit ───────────────────────────────────────────────────────
    private PreviewView       cameraPreview;
    private ExecutorService   cameraExecutor;
    private PoseDetector      poseDetector;
    private PushupDetector    pushupDetector;
    private ProcessCameraProvider cameraProvider;
    private boolean           cameraRunning  = false;

    // ── Sensor fallback ───────────────────────────────────────────────────────
    private android.hardware.SensorManager sensorManager;
    private android.hardware.Sensor        proximitySensor;
    private android.hardware.Sensor        accelerometer;
    private boolean isChestDown        = false;
    private long    lastRepTimestamp   = 0L;
    private boolean usingCameraMode    = true;

    // ── UI ────────────────────────────────────────────────────────────────────
    private TextView         tvRepCounter;
    private TextView         tvTimer;
    private TextView         tvStatus;
    private TextView         tvGoalLabel;
    private TextView         tvRepsRemaining;
    private TextView         tvAngleDebug;
    private MaterialButton    btnStartSession;
    private ProgressBar      progressBar;
    private ProgressBar      dailyProgressBar;
    private TextView         tvDailyProgressPct;
    private TextView         tvBlockedAppsCount;
    private View             btnSettings;
    private ConstraintLayout layoutBlocked;
    private ConstraintLayout layoutSessionHud;
    private View             scrollContent;

    private View             layoutWorkoutControls;
    private MaterialButton   btnCancelWorkout;
    private MaterialButton   btnPauseWorkout;
    private TextView         tvHudReps;
    private TextView         tvHudTimer;
    private TextView         tvHudAngle;
    private View             tvCameraWarmupHint;
    private ProgressBar      hudProgressBar;
    private TextView         tvBlockedMessage;
    private Button           btnUnlock;
    private Button           btnTimeRemaining;
    private View             viewDarkBg;
    private View             viewTopScrim;
    private View             viewBottomScrim;
    private PoseOverlayView  poseOverlay;
    private View             btnDayStreak;
    private TextView         tvDayStreakLabel;

    // ── Break timer UI ────────────────────────────────────────────────────────
    private CardView         cardBreakTimer;
    private TextView         tvBreakCountdown;
    private TextView         btnCancelBreak;

    // ── Break timer state ─────────────────────────────────────────────────────
    private static final String BREAK_CHANNEL_ID  = "break_timer_channel";
    private static final int    BREAK_NOTIF_ID    = 42;
    private boolean breakActive      = false;
    private long    breakEndMs       = 0L;

    private final Runnable tickBreak = new Runnable() {
        @Override
        public void run() {
            if (!breakActive) return;
            long remaining = breakEndMs - System.currentTimeMillis();
            if (remaining <= 0) {
                stopBreakTimer(true); // true = finished naturally
                return;
            }
            long secs = (remaining / 1000) % 60;
            long mins = (remaining / 1000) / 60;
            if (tvBreakCountdown != null)
                tvBreakCountdown.setText(String.format(Locale.getDefault(), "%02d:%02d", mins, secs));
                
            // When paused in the HUD, inform the user about the remaining break time
            if (!sessionActive && tvStatus != null) {
                tvStatus.setText("Paused — tap Resume to continue. Break: " + String.format(Locale.getDefault(), "%02d:%02d", mins, secs));
            }
                
            mainHandler.postDelayed(this, 500);
        }
    };

    // ── Helpers ───────────────────────────────────────────────────────────────
    private SessionManager sessionManager;
    private Vibrator       vibrator;
    private final Handler  mainHandler = new Handler(Looper.getMainLooper());

    // Status messages cycle while session is running
    private static final String[] STATUS_MSGS = {
            "Analyzing Form... 🔍",
            "Tracking Pose... 🤖",
            "Counting Reps... 💪",
            "Keep Going! 🔥",
            "Great Form! ✅"
    };
    private int statusIndex = 0;

    private final Runnable cycleStatus = new Runnable() {
        @Override
        public void run() {
            if (sessionActive) {
                tvStatus.setText(STATUS_MSGS[statusIndex % STATUS_MSGS.length]);
                statusIndex++;
                mainHandler.postDelayed(this, 3000);
            }
        }
    };

    // ══════════════════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on during workout
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        sessionManager = new SessionManager(this);
        vibrator       = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        repGoal = sessionManager.getGoal();

        initViews();
        initSensorFallback();
        checkSpecialPermissions();
        updateUI();
    }

    // ── View binding ──────────────────────────────────────────────────────────
    private void initViews() {
        cameraPreview      = findViewById(R.id.cameraPreview);
        tvRepCounter       = findViewById(R.id.tvRepCounter);
        tvTimer            = findViewById(R.id.tvTimer);
        tvStatus           = findViewById(R.id.tvStatus);
        tvGoalLabel        = findViewById(R.id.tvGoalLabel);
        tvRepsRemaining    = findViewById(R.id.tvRepsRemaining);
        tvAngleDebug       = findViewById(R.id.tvAngleDebug);
        btnStartSession    = findViewById(R.id.btnStartSession);
        progressBar        = findViewById(R.id.circularProgress);
        dailyProgressBar   = findViewById(R.id.dailyProgressBar);
        tvDailyProgressPct = findViewById(R.id.tvDailyProgressPct);
        tvBlockedAppsCount = findViewById(R.id.tvBlockedAppsCount);
        btnSettings        = findViewById(R.id.btnSettings);
        layoutBlocked      = findViewById(R.id.layoutBlocked);
        layoutSessionHud   = findViewById(R.id.layoutSessionHud);
        scrollContent      = findViewById(R.id.scrollContent);

        layoutWorkoutControls = findViewById(R.id.layoutWorkoutControls);
        btnCancelWorkout   = findViewById(R.id.btnCancelWorkout);
        btnPauseWorkout    = findViewById(R.id.btnPauseWorkout);
        tvHudReps          = findViewById(R.id.tvHudReps);
        tvHudTimer         = findViewById(R.id.tvHudTimer);
        tvHudAngle         = findViewById(R.id.tvHudAngle);
        tvCameraWarmupHint = findViewById(R.id.tvCameraWarmupHint);
        hudProgressBar     = findViewById(R.id.hudProgressBar);
        tvBlockedMessage   = findViewById(R.id.tvBlockedMessage);
        btnUnlock          = findViewById(R.id.btnUnlock);
        btnTimeRemaining   = findViewById(R.id.btnTimeRemaining);
        viewDarkBg         = findViewById(R.id.viewDarkBg);
        viewTopScrim       = findViewById(R.id.viewTopScrim);
        viewBottomScrim    = findViewById(R.id.viewBottomScrim);
        poseOverlay        = findViewById(R.id.poseOverlay);
        btnDayStreak       = findViewById(R.id.btnDayStreak);
        tvDayStreakLabel   = findViewById(R.id.tvDayStreakLabel);
        cardBreakTimer     = findViewById(R.id.cardBreakTimer);
        tvBreakCountdown   = findViewById(R.id.tvBreakCountdown);
        btnCancelBreak     = findViewById(R.id.btnCancelBreak);

        if (btnCancelBreak != null) {
            btnCancelBreak.setOnClickListener(v -> stopBreakTimer(false));
        }

        if (btnDayStreak != null) {
            btnDayStreak.setOnClickListener(v -> showStatsBottomSheet());
        }

        if (btnCancelWorkout != null) {
            btnCancelWorkout.setOnClickListener(v -> cancelWorkout());
        }
        
        if (btnPauseWorkout != null) {
            btnPauseWorkout.setOnClickListener(v -> {
                if (sessionActive) {
                    pauseSession();
                } else {
                    beginWorkout();
                }
            });
        }

        // Populate quiz-driven labels
        tvGoalLabel.setText("Goal: " + repGoal + " Reps");

        tvBlockedMessage.setText(
                "🔒 Focus on your workout!\n" +
                "All other apps are locked until you complete " + repGoal + " push-ups.");

        // Show how many apps are currently set to be blocked
        int blockedCount = sessionManager.getBlockedPackages().size();
        tvBlockedAppsCount.setText("🔒  " + blockedCount + " app" + (blockedCount == 1 ? "" : "s") + " will be blocked");

        // Highlight today's bar in the weekly chart
        highlightTodayBar();

        // Settings button → open Settings screen
        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });

        btnStartSession.setOnClickListener(v -> {
            if (sessionActive) pauseSession();
            else if (isCameraWarmup) beginWorkout();
            else startWarmup();
        });

        btnUnlock.setOnClickListener(v -> {
            if (repCount >= repGoal) {
                showGoalComplete();
            } else {
                int left = repGoal - repCount;
                showSnackbar(left + " more push-ups to unlock! 💪");
                layoutBlocked.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake));
            }
        });

        btnTimeRemaining.setOnClickListener(v -> showBlockedState(false));

        // Only restore the blocked banner if the user actually selected apps to block
        if (sessionManager.isLocked() && sessionManager.hasBlockedPackages())
            showBlockedState(true);
    }

    // ── Session lifecycle ─────────────────────────────────────────────────────
    private void startWarmup() {
        isCameraWarmup = true;
        sessionActive = false;

        // Only lock + start the blocker service if the user has actually chosen apps to block
        if (sessionManager.hasBlockedPackages()) {
            sessionManager.setLocked(true);
            startService(new Intent(this, AppBlockerService.class));
        }

        btnStartSession.setText("START");
        btnStartSession.setCornerRadius((int) (50 * getResources().getDisplayMetrics().density));
        
        // Hide home screen cards, show warm-up HUD
        if (scrollContent      != null) scrollContent.setVisibility(View.GONE);
        if (layoutSessionHud   != null) layoutSessionHud.setVisibility(View.VISIBLE);
        if (btnDayStreak       != null) btnDayStreak.setVisibility(View.GONE);

        if (tvCameraWarmupHint != null) tvCameraWarmupHint.setVisibility(View.VISIBLE);
        if (tvHudReps          != null) tvHudReps.setVisibility(View.GONE);
        if (tvHudTimer         != null) tvHudTimer.setVisibility(View.GONE);


        if (hasCameraPermission()) {
            usingCameraMode = true;
            startCamera();
        } else {
            usingCameraMode = false;
            ActivityCompat.requestPermissions(this,
                    new String[]{ Manifest.permission.CAMERA }, REQ_CAMERA);
            startSensorSession();
        }
    }

    private void beginWorkout() {
        isCameraWarmup = false;
        sessionActive = true;
        
        btnStartSession.setVisibility(View.GONE);
        if (layoutWorkoutControls != null) layoutWorkoutControls.setVisibility(View.VISIBLE);
        if (btnPauseWorkout != null) {
            btnPauseWorkout.setText("PAUSE");
            btnPauseWorkout.setIconResource(R.drawable.ic_pause);
            btnPauseWorkout.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#80000000")));
        }

        // Hide home screen cards, show session HUD
        if (scrollContent      != null) scrollContent.setVisibility(View.GONE);
        if (layoutSessionHud   != null) layoutSessionHud.setVisibility(View.VISIBLE);
        if (btnDayStreak       != null) btnDayStreak.setVisibility(View.GONE);

        if (tvCameraWarmupHint != null) tvCameraWarmupHint.setVisibility(View.GONE);
        if (tvHudReps          != null) tvHudReps.setVisibility(View.VISIBLE);
        if (tvHudTimer         != null) tvHudTimer.setVisibility(View.VISIBLE);




        // Start the timer — stop home ticker first to avoid conflict
        homeTimerRunning = false;
        mainHandler.removeCallbacks(tickHomeTimer);
        sessionStartMs = System.currentTimeMillis() - elapsedMs;
        timerRunning   = true;
        mainHandler.post(tickTimer);

        // Track session as 'opened'
        sessionManager.setSessionsCompleted(sessionManager.getSessionsCompleted() + 1);

        mainHandler.post(cycleStatus);

        // Cancel any running break timer when workout begins
        stopBreakTimer(false);

        updateUI();
    }



    private void cancelWorkout() {
        sessionActive = false;
        timerRunning = false;
        mainHandler.removeCallbacks(tickTimer);

        stopCamera();
        stopSensorSession();
        mainHandler.removeCallbacks(cycleStatus);

        sessionManager.setLocked(false);
        sessionManager.resetReps();
        // Only stop the service if it was started (i.e. user had selected apps to block)
        if (sessionManager.hasBlockedPackages()) {
            stopService(new Intent(this, AppBlockerService.class));
        }
        showBlockedState(false);
        stopBreakTimer(false);

        // Save the time they spent working out before canceling
        sessionManager.addTodayTotalTimeMs(elapsedMs);

        // Save any reps done before canceling
        if (repCount > 0) {
            saveWorkoutProgress();
        }

        repCount = 0;
        elapsedMs = 0;
        long totalMs = sessionManager.getTodayTotalTimeMs();
        tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", (totalMs / 1000) / 60, (totalMs / 1000) % 60));
        if (pushupDetector != null) pushupDetector.reset();
        tvStatus.setText("Workout cancelled.");
        
        btnStartSession.setVisibility(View.VISIBLE);
        if (layoutWorkoutControls != null) layoutWorkoutControls.setVisibility(View.GONE);
        btnStartSession.setText("START SESSION");
        btnStartSession.setCornerRadius((int) (50 * getResources().getDisplayMetrics().density));
        
        if (layoutSessionHud != null) layoutSessionHud.setVisibility(View.GONE);
        if (scrollContent    != null) scrollContent.setVisibility(View.VISIBLE);
        if (btnDayStreak     != null) btnDayStreak.setVisibility(View.VISIBLE);
        if (tvCameraWarmupHint != null) tvCameraWarmupHint.setVisibility(View.GONE);

        // Restart home-screen time ticker
        homeTimerRunning = true;
        mainHandler.removeCallbacks(tickHomeTimer);
        mainHandler.post(tickHomeTimer);
        
        updateUI();
    }

    private void pauseSession() {
        sessionActive = false;

        // Freeze timer
        elapsedMs   += System.currentTimeMillis() - sessionStartMs;
        timerRunning = false;
        mainHandler.removeCallbacks(tickTimer);

        // DO NOT stop camera here; let the user remain in the HUD view
        mainHandler.removeCallbacks(cycleStatus);
        
        if (btnPauseWorkout != null) {
            btnPauseWorkout.setText("START");
            btnPauseWorkout.setIconResource(R.drawable.ic_play);
            // Use the app's dark violet theme color since they requested "keep the colors in my app" 
            btnPauseWorkout.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#004E9A")));
        }
        
        tvStatus.setText("Paused — tap Start to continue");



        // Start the break timer
        startBreakTimer();
    }

    private void showGoalComplete() {
        sessionActive = false;

        // Stop timer
        elapsedMs   += System.currentTimeMillis() - sessionStartMs;
        timerRunning = false;
        mainHandler.removeCallbacks(tickTimer);

        stopCamera();
        stopSensorSession();
        mainHandler.removeCallbacks(cycleStatus);

        sessionManager.setLocked(false);
        sessionManager.resetReps();
        // Only stop the service if it was started (i.e. user had selected apps to block)
        if (sessionManager.hasBlockedPackages()) {
            stopService(new Intent(this, AppBlockerService.class));
        }
        showBlockedState(false);

        // Cancel break if a session completes
        stopBreakTimer(false);

        saveWorkoutProgress();
        sessionManager.addTodayTotalTimeMs(elapsedMs);

        long totalSecs = elapsedMs / 1000;
        long m = totalSecs / 60, s = totalSecs % 60;
        String timeStr = String.format(Locale.getDefault(), "%02d:%02d", m, s);

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_goal_complete, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvDesc = dialogView.findViewById(R.id.tvDialogDesc);
        tvDesc.setText("Amazing! You crushed " + repGoal + " push-ups in " + timeStr +
                ".\n\nAll apps are now unlocked!");

        android.widget.Button btnAwesome = dialogView.findViewById(R.id.btnAwesome);
        btnAwesome.setOnClickListener(v -> {
            repCount      = 0;
            elapsedMs     = 0;
            isCameraWarmup = false;
            long tMs = sessionManager.getTodayTotalTimeMs();
            tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", (tMs / 1000) / 60, (tMs / 1000) % 60));
            if (pushupDetector != null) pushupDetector.reset();
            updateUI();
            updateStreakUI();
            highlightTodayBar();
            tvStatus.setText("Great job! Start a new session.");
            
            btnStartSession.setVisibility(View.VISIBLE);
            if (layoutWorkoutControls != null) layoutWorkoutControls.setVisibility(View.GONE);
            
            btnStartSession.setText("START SESSION");
            btnStartSession.setCornerRadius((int) (50 * getResources().getDisplayMetrics().density));
            
            if (layoutSessionHud != null) layoutSessionHud.setVisibility(View.GONE);
            if (scrollContent    != null) scrollContent.setVisibility(View.VISIBLE);
            if (btnDayStreak     != null) btnDayStreak.setVisibility(View.VISIBLE);

            // Restart home-screen time ticker
            homeTimerRunning = true;
            mainHandler.removeCallbacks(tickHomeTimer);
            mainHandler.post(tickHomeTimer);

            dialog.dismiss();

            // One-shot rating prompt — only show on very first goal completion
            if (!sessionManager.hasSeenRatingPopup()) {
                mainHandler.postDelayed(() -> showRatingDialog(), 600);
            }
        });

        // Glowing button press animation
        btnAwesome.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                    break;
            }
            return false;
        });

        // Intro animation
        dialog.setOnShowListener(d -> {
            android.view.animation.ScaleAnimation scaleAnim = new android.view.animation.ScaleAnimation(0.85f, 1.0f, 0.85f, 1.0f,
                    android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                    android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f);
            scaleAnim.setDuration(350);
            scaleAnim.setInterpolator(new android.view.animation.OvershootInterpolator(1.2f));
            android.view.animation.AlphaAnimation alphaAnim = new android.view.animation.AlphaAnimation(0.0f, 1.0f);
            alphaAnim.setDuration(250);
            android.view.animation.AnimationSet animSet = new android.view.animation.AnimationSet(true);
            animSet.addAnimation(scaleAnim);
            animSet.addAnimation(alphaAnim);
            dialogView.startAnimation(animSet);
        });

        dialog.show();

        if (vibrator != null)
            vibrator.vibrate(VibrationEffect.createWaveform(
                    new long[]{ 0, 100, 50, 100, 50, 200 }, -1));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Rating Dialog  (one-shot — fires on first ever goal completion)
    // ══════════════════════════════════════════════════════════════════════════

    private void showRatingDialog() {
        if (isFinishing() || isDestroyed()) return;

        // -- Immediately persist the flag so it NEVER shows again --
        sessionManager.markRatingPopupSeen();

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_rating, null);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setDimAmount(0.65f);
        }

        // ── View refs ──────────────────────────────────────────────────────────
        ImageView[] stars = {
            dialogView.findViewById(R.id.star1),
            dialogView.findViewById(R.id.star2),
            dialogView.findViewById(R.id.star3),
            dialogView.findViewById(R.id.star4),
            dialogView.findViewById(R.id.star5)
        };
        View[] containers = {
            dialogView.findViewById(R.id.containerStar1),
            dialogView.findViewById(R.id.containerStar2),
            dialogView.findViewById(R.id.containerStar3),
            dialogView.findViewById(R.id.containerStar4),
            dialogView.findViewById(R.id.containerStar5)
        };
        TextView tvLabel    = dialogView.findViewById(R.id.tvRatingLabel);
        EditText etFeedback = dialogView.findViewById(R.id.etFeedback);
        Button   btnSubmit  = dialogView.findViewById(R.id.btnSubmitRating);
        TextView tvNotNow   = dialogView.findViewById(R.id.tvNotNow);

        final int[] selectedRating = { 0 };

        // Label text + color per rating (1-5)
        final String[] labels     = { "", "\ud83d\ude15 Not great", "\ud83d\ude10 It was ok", "\ud83d\ude42 Pretty good", "\ud83d\ude00 Really liked it", "\ud83e\udd29 Loved it!" };
        final int[]    labelColors = { 0, 0xFFE53935, 0xFFFF8F00, 0xFFFFC107, 0xFF66BB6A, 0xFF00D2FF };

        // ── Star + container interaction ────────────────────────────────────────
        for (int i = 0; i < containers.length; i++) {
            final int rating = i + 1;
            containers[i].setOnClickListener(v -> {
                selectedRating[0] = rating;

                for (int j = 0; j < stars.length; j++) {
                    boolean filled    = (j < rating);
                    final ImageView star      = stars[j];
                    final View      container = containers[j];

                    // ── Container glow toggle (staggered wave: 0, 30, 60 … ms) ──
                    final int delay = filled ? j * 30 : 0;
                    container.postDelayed(() -> {
                        container.setBackgroundResource(
                                filled ? R.drawable.bg_star_container_selected
                                       : R.drawable.bg_star_container_default);
                        if (filled) {
                            container.animate()
                                    .scaleX(1.12f).scaleY(1.12f).setDuration(90)
                                    .withEndAction(() -> container.animate()
                                            .scaleX(1f).scaleY(1f).setDuration(110)
                                            .setInterpolator(new android.view.animation.OvershootInterpolator(2f))
                                            .start())
                                    .start();
                        }
                    }, delay);

                    // ── Star icon swap with bouncy pop ──────────────────────────
                    star.setImageResource(filled ? R.drawable.ic_star_yellow
                                                 : R.drawable.ic_star_outline);
                    if (filled) {
                        star.animate().scaleX(1.25f).scaleY(1.25f).setDuration(100)
                                .withEndAction(() -> star.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
                                .start();
                    }
                }

                // ── Rating label pill (color-coded) ────────────────────────────
                tvLabel.setVisibility(View.VISIBLE);
                tvLabel.setText(labels[rating]);
                tvLabel.setTextColor(labelColors[rating]);
                tvLabel.setAlpha(0f);
                tvLabel.setScaleX(0.85f);
                tvLabel.setScaleY(0.85f);
                tvLabel.animate()
                        .alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(220)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2f))
                        .start();

                // ── Reveal feedback field (slide up) ────────────────────────────
                if (etFeedback.getVisibility() != View.VISIBLE) {
                    etFeedback.setVisibility(View.VISIBLE);
                    etFeedback.setAlpha(0f);
                    etFeedback.setTranslationY(12f);
                    etFeedback.animate().alpha(1f).translationY(0f).setDuration(260).start();
                }

                // ── Enable submit ────────────────────────────────────────────────
                btnSubmit.setEnabled(true);
                btnSubmit.animate().alpha(1f).setDuration(200).start();

                // Light haptic per tap
                if (vibrator != null)
                    vibrator.vibrate(VibrationEffect.createOneShot(28, VibrationEffect.DEFAULT_AMPLITUDE));
            });
        }

        // ── Submit ─────────────────────────────────────────────────────────────
        btnSubmit.setOnClickListener(v -> {
            String feedback = etFeedback.getText() != null ? etFeedback.getText().toString().trim() : "";
            submitRatingToBackend(selectedRating[0], feedback);
            dialog.dismiss();
            // Success toast styled like existing snackbar
            showSnackbar("Thanks for your feedback! ⭐");
        });

        // ── Not now ────────────────────────────────────────────────────────────
        tvNotNow.setOnClickListener(v -> dialog.dismiss());

        // ── Entrance animation: slide-up + fade (matches notification style) ──
        dialog.setOnShowListener(d -> {
            android.view.animation.TranslateAnimation slideUp =
                    new android.view.animation.TranslateAnimation(
                            android.view.animation.Animation.RELATIVE_TO_SELF, 0f,
                            android.view.animation.Animation.RELATIVE_TO_SELF, 0f,
                            android.view.animation.Animation.RELATIVE_TO_SELF, 0.12f,
                            android.view.animation.Animation.RELATIVE_TO_SELF, 0f);
            slideUp.setDuration(380);
            slideUp.setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f));

            android.view.animation.AlphaAnimation fadeIn =
                    new android.view.animation.AlphaAnimation(0f, 1f);
            fadeIn.setDuration(280);

            android.view.animation.AnimationSet animSet = new android.view.animation.AnimationSet(true);
            animSet.addAnimation(slideUp);
            animSet.addAnimation(fadeIn);
            dialogView.startAnimation(animSet);
        });

        // Light haptic on dialog appear
        if (vibrator != null)
            vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE));

        dialog.show();
    }

    /**
     * Mock backend submission — replace with real API call (Retrofit, etc.).
     * Separated from UI so the business logic stays clean.
     */
    private void submitRatingToBackend(int rating, String feedback) {
        // TODO: replace with real network call
        android.util.Log.d("RatingDialog", "Rating submitted: " + rating + " | Feedback: " + feedback);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CameraX  +  ML Kit Pose Detection
    // ══════════════════════════════════════════════════════════════════════════

    private void startCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor();
        pushupDetector = new PushupDetector();

        pushupDetector.setRepListener(totalReps -> mainHandler.post(() -> {
            repCount = totalReps;
            onRepCounted();
        }));

        AccuratePoseDetectorOptions options = new AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
                .build();
        poseDetector = PoseDetection.getClient(options);

        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                showSnackbar("Camera error: " + e.getMessage());
                usingCameraMode = false;
                startSensorSession();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        cameraProvider.unbindAll();

        // Full-screen preview
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

        // Frame analysis for pose detection
        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysis.setAnalyzer(cameraExecutor, imageProxy -> {
            if (!sessionActive) { imageProxy.close(); return; }
            analyzeFrame(imageProxy);
        });

        // Use FRONT camera so the user sees themselves
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        cameraProvider.bindToLifecycle(this, cameraSelector, preview, analysis);
        cameraRunning = true;

        // Show camera + skeleton overlay, hide static dark background, show scrims
        mainHandler.post(() -> {
            cameraPreview.setVisibility(View.VISIBLE);
            poseOverlay.setVisibility(View.VISIBLE);
            viewDarkBg.setVisibility(View.GONE);
            viewTopScrim.setVisibility(View.VISIBLE);
            viewBottomScrim.setVisibility(View.VISIBLE);
        });
    }

    @androidx.camera.core.ExperimentalGetImage
    private void analyzeFrame(ImageProxy imageProxy) {
        try {
            if (imageProxy.getImage() == null) {
                imageProxy.close();
                return;
            }

            // ── Capture ALL proxy metadata BEFORE any async call ──────────────
            // imageProxy must NOT be accessed inside listener lambdas because
            // addOnCompleteListener closes it and the scheduler order is not
            // guaranteed across threads.
            final int imgW   = imageProxy.getWidth();
            final int imgH   = imageProxy.getHeight();
            final int rotDeg = imageProxy.getImageInfo().getRotationDegrees();

            // Swap width/height for 90° / 270° rotations (portrait phones)
            final int overlayW = (rotDeg == 90 || rotDeg == 270) ? imgH : imgW;
            final int overlayH = (rotDeg == 90 || rotDeg == 270) ? imgW : imgH;

            // ── Guard against race: stopCamera() on main thread ───────────────
            PoseDetector detector = poseDetector;
            if (detector == null) {
                imageProxy.close();
                return;
            }

            InputImage inputImage = InputImage.fromMediaImage(
                    imageProxy.getImage(), rotDeg);

            detector.process(inputImage)
                    .addOnSuccessListener(pose -> {
                        // Local copies avoid NPE if session stops mid-frame
                        PushupDetector pd = pushupDetector;
                        PoseOverlayView ov = poseOverlay;

                        if (pd != null) {
                            pd.processPose(pose);
                            float angle = pd.getCurrentAngle();

                            // Draw skeleton overlay
                            if (ov != null) {
                                ov.setPose(pose, overlayW, overlayH);
                            }

                            // Update angle label on main thread
                            mainHandler.post(() -> {
                                if (pd.getCurrentAngle() >= 0) {
                                    String phase = pd.isInDownPhase() ? "↓ DOWN" : "↑ UP";
                                    String angleStr = String.format(Locale.getDefault(),
                                            "Elbow: %.0f°  %s", angle, phase);
                                    if (tvAngleDebug != null) tvAngleDebug.setText(angleStr);
                                    if (tvHudAngle  != null) tvHudAngle.setText(angleStr);
                                }
                            });
                        }
                    })
                    .addOnFailureListener(e -> { /* ignore single-frame failures */ })
                    .addOnCompleteListener(t -> imageProxy.close());

        } catch (Exception e) {
            // Safety net — close the proxy so the next frame is not blocked
            try { imageProxy.close(); } catch (Exception ignored) {}
        }
    }

    private void stopCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraRunning = false;
        }
        if (cameraExecutor != null && !cameraExecutor.isShutdown()) {
            cameraExecutor.shutdown();
        }
        if (poseDetector != null) {
            poseDetector.close();
            poseDetector = null;
        }
        // Restore dark background when camera is off, clear skeleton overlay
        mainHandler.post(() -> {
            if (cameraPreview   != null) cameraPreview.setVisibility(View.GONE);
            if (poseOverlay     != null) { poseOverlay.clearPose(); poseOverlay.setVisibility(View.GONE); }
            if (viewDarkBg      != null) viewDarkBg.setVisibility(View.VISIBLE);
            if (viewTopScrim    != null) viewTopScrim.setVisibility(View.GONE);
            if (viewBottomScrim != null) viewBottomScrim.setVisibility(View.GONE);
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Sensor Fallback (proximity + accelerometer)
    // ══════════════════════════════════════════════════════════════════════════

    private void initSensorFallback() {
        sensorManager   = (android.hardware.SensorManager) getSystemService(SENSOR_SERVICE);
        proximitySensor = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_PROXIMITY);
        accelerometer   = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER);
    }

    private void startSensorSession() {
        if (proximitySensor != null)
            sensorManager.registerListener(this, proximitySensor,
                    android.hardware.SensorManager.SENSOR_DELAY_FASTEST);
        if (accelerometer != null)
            sensorManager.registerListener(this, accelerometer,
                    android.hardware.SensorManager.SENSOR_DELAY_GAME);
        tvStatus.setText("Sensor mode — place phone below chest");
    }

    private void stopSensorSession() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(android.hardware.SensorEvent event) {
        if (!sessionActive || usingCameraMode) return;

        if (event.sensor.getType() == android.hardware.Sensor.TYPE_PROXIMITY) {
            float dist = event.values[0];
            if (dist < PROX_THRESHOLD_CM) {
                isChestDown = true;
            } else if (isChestDown) {
                isChestDown = false;
                long now = System.currentTimeMillis();
                if (now - lastRepTimestamp > DEBOUNCE_MS) {
                    lastRepTimestamp = now;
                    repCount++;
                    mainHandler.post(this::onRepCounted);
                }
            }
        } else if (event.sensor.getType() == android.hardware.Sensor.TYPE_ACCELEROMETER) {
            if (proximitySensor != null) return;
            float az = event.values[2];
            if (isChestDown && az > ACCEL_Z_THRESHOLD) {
                long now = System.currentTimeMillis();
                if (now - lastRepTimestamp > DEBOUNCE_MS) {
                    lastRepTimestamp = now;
                    isChestDown = false;
                    repCount++;
                    mainHandler.post(this::onRepCounted);
                }
            } else if (az < -2f) {
                isChestDown = true;
            }
        }
    }

    @Override
    public void onAccuracyChanged(android.hardware.Sensor sensor, int accuracy) {}

    // ── Rep counted ───────────────────────────────────────────────────────────
    private void onRepCounted() {
        try {
            // Haptic pulse — use modern VibrationEffect (API 26+)
            if (vibrator != null) {
                vibrator.vibrate(
                        VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE));
            }
            // Animate the counter
            try {
                tvRepCounter.startAnimation(
                        AnimationUtils.loadAnimation(this, R.anim.pop_in));
            } catch (Exception ignored) { /* animation missing — non-fatal */ }

            updateUI();
            sessionManager.setReps(repCount);
            if (repCount >= repGoal) showGoalComplete();
        } catch (Exception e) {
            // Never let a rep-count callback crash the UI thread
            android.util.Log.e("MainActivity", "onRepCounted error", e);
        }
    }

    // ── UI refresh ────────────────────────────────────────────────────────────
    private void updateUI() {
        // Rep counter shows just the count number (not "X / goal")
        tvRepCounter.setText(String.valueOf(repCount));

        // When workout is not actively running, show today's accumulated time instead of 00:00
        if (!timerRunning && !sessionActive) {
            long tMs = sessionManager.getTodayTotalTimeMs();
            if (tvTimer != null) {
                tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", (tMs / 1000) / 60, (tMs / 1000) % 60));
            }
        }

        int rem = repGoal - repCount;
        if (tvRepsRemaining != null)
            tvRepsRemaining.setText("Reps Remaining: " + Math.max(0, rem));

        // Session progress bar
        progressBar.setMax(repGoal);
        progressBar.setProgress(repCount);

        // Daily progress bar (0–100% of goal)
        if (dailyProgressBar != null) {
            int pct = repGoal > 0 ? (int) ((repCount * 100f) / repGoal) : 0;
            pct = Math.min(pct, 100);
            dailyProgressBar.setMax(100);
            dailyProgressBar.setProgress(pct);
            if (tvDailyProgressPct != null)
                tvDailyProgressPct.setText(pct + "%");
        }

        // HUD overlay reps + progress
        if (tvHudReps != null)      tvHudReps.setText(String.valueOf(repCount));
        if (hudProgressBar != null) { hudProgressBar.setMax(repGoal); hudProgressBar.setProgress(repCount); }

        sessionManager.setReps(repCount);
    }

    /** Highlights the current weekday bar in the weekly chart. */
    private void highlightTodayBar() {
        // Map: Calendar.SUNDAY=1 … Calendar.SATURDAY=7
        int day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK);

        int[] barIds   = { R.id.barSun, R.id.barMon, R.id.barTue, R.id.barWed,
                           R.id.barThu, R.id.barFri, R.id.barSat };
        int[] labelIds = { R.id.labelSun, R.id.labelMon, R.id.labelTue, R.id.labelWed,
                           R.id.labelThu, R.id.labelFri, R.id.labelSat };

        // day is 1-indexed (Sunday=1), array is 0-indexed from Sunday
        int todayIdx = day - 1; // 0=Sun … 6=Sat

        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.DAY_OF_YEAR, -todayIdx);
        
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
        int[] weekHistory = new int[7];
        int maxReps = 1; 
        for (int i = 0; i < 7; i++) {
            String dStr = sdf.format(cal.getTime());
            int r = sessionManager.getDailyHistory(dStr);
            if (i == todayIdx) {
                // Keep today's realtime status even if history hasn't been written yet
                r = Math.max(r, sessionManager.getTodayTotalReps());
            }
            weekHistory[i] = r;
            if (r > maxReps) maxReps = r;
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
        }

        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < barIds.length; i++) {
            android.view.View bar   = findViewById(barIds[i]);
            android.widget.TextView lbl = findViewById(labelIds[i]);
            if (bar == null || lbl == null) continue;

            int dpHeight = 16 + (int) (((float) weekHistory[i] / maxReps) * 60);
            android.view.ViewGroup.LayoutParams lp = bar.getLayoutParams();
            lp.height = (int) (dpHeight * density);
            bar.setLayoutParams(lp);

            if (i == todayIdx) {
                bar.setBackgroundResource(R.drawable.bg_bar_active);
                lbl.setTextColor(0xFF00D2FF);   // purple
                lbl.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                bar.setBackgroundResource(R.drawable.bg_bar_default);
                lbl.setTextColor(0x66FFFFFF);   // dim white
                lbl.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        }
    }

    private void showBlockedState(boolean show) {
        layoutBlocked.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            layoutBlocked.setAlpha(0f);
            layoutBlocked.animate().alpha(1f).setDuration(400).start();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Permissions
    // ══════════════════════════════════════════════════════════════════════════

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_CAMERA) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                usingCameraMode = true;
                stopSensorSession();
                startCamera();
            } else {
                showSnackbar("Camera denied — using sensor fallback");
            }
        }
    }

    private void checkSpecialPermissions() {
        boolean needOverlay    = !Settings.canDrawOverlays(this);
        boolean needUsageStats = !hasUsageStatsPermission();
        if (!needOverlay && !needUsageStats) return;

        // Inflate custom premium dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_permissions, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        // Make standard dialog background transparent to show our premium background
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvOverlay = dialogView.findViewById(R.id.tvBulletOverlay);
        TextView tvUsage = dialogView.findViewById(R.id.tvBulletUsage);
        android.widget.Button btnSettings = dialogView.findViewById(R.id.btnOpenSettings);
        TextView btnLater = dialogView.findViewById(R.id.btnLater);

        if (needOverlay) tvOverlay.setVisibility(View.VISIBLE);
        if (needUsageStats) tvUsage.setVisibility(View.VISIBLE);

        btnSettings.setOnClickListener(v -> {
            if (needOverlay) {
                startActivityForResult(
                        new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())), REQ_OVERLAY);
            } else {
                startActivityForResult(
                        new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), REQ_USAGE_STATS);
            }
            dialog.dismiss();
        });

        btnLater.setOnClickListener(v -> dialog.dismiss());

        // Glowing button hover/press animation effect
        btnSettings.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                    break;
            }
            return false; // return false so onClick still fires
        });

        // Dialog animation: subtle scale-up and fade-in
        dialog.setOnShowListener(d -> {
            android.view.animation.ScaleAnimation scaleAnim = new android.view.animation.ScaleAnimation(0.85f, 1.0f, 0.85f, 1.0f,
                    android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                    android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f);
            scaleAnim.setDuration(350);
            scaleAnim.setInterpolator(new android.view.animation.OvershootInterpolator(1.2f));
            android.view.animation.AlphaAnimation alphaAnim = new android.view.animation.AlphaAnimation(0.0f, 1.0f);
            alphaAnim.setDuration(250);
            android.view.animation.AnimationSet animSet = new android.view.animation.AnimationSet(true);
            animSet.addAnimation(scaleAnim);
            animSet.addAnimation(alphaAnim);
            dialogView.startAnimation(animSet);
        });

        dialog.show();
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager aom = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        int mode = aom.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY && !hasUsageStatsPermission()) {
            startActivityForResult(
                    new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), REQ_USAGE_STATS);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected void onResume() {
        super.onResume();
        
        repGoal = sessionManager.getGoal();
        
        if (tvGoalLabel != null) {
            tvGoalLabel.setText("Goal: " + repGoal + " Reps");
        }
        if (tvBlockedMessage != null) {
            tvBlockedMessage.setText(
                    "🔒 Focus on your workout!\n" +
                    "All other apps are locked until you complete " + repGoal + " push-ups.");
        }
        if (tvBlockedAppsCount != null) {
            int blockedCount = sessionManager.getBlockedPackages().size();
            tvBlockedAppsCount.setText("🔒  " + blockedCount + " app" + (blockedCount == 1 ? "" : "s") + " will be blocked");
        }
        
        updateUI();
        updateStreakUI();
        highlightTodayBar();

        if (sessionActive) {
            if (usingCameraMode && hasCameraPermission() && !cameraRunning) startCamera();
            else if (!usingCameraMode) startSensorSession();
        }

        // Re-sync break timer if one was running before user navigated away
        if (breakActive) {
            mainHandler.removeCallbacks(tickBreak); // prevent double-posting
            if (breakEndMs > System.currentTimeMillis()) {
                mainHandler.post(tickBreak);
            } else {
                stopBreakTimer(true); // it expired while user was away
            }
        }

        // Start the home-screen time ticker if not in an active session
        if (!timerRunning) {
            homeTimerRunning = true;
            mainHandler.removeCallbacks(tickHomeTimer);
            mainHandler.post(tickHomeTimer);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!usingCameraMode) stopSensorSession();
        // Stop home ticker to avoid leaking while app is in background
        homeTimerRunning = false;
        mainHandler.removeCallbacks(tickHomeTimer);
        // Camera stays running (reps counted even when overlay briefly shown)
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCamera();
        stopSensorSession();
        mainHandler.removeCallbacksAndMessages(null);
    }

    private void updateStreakUI() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String today = sdf.format(new java.util.Date());
        String lastDate = sessionManager.getLastWorkoutDate();
        int currentStreak = sessionManager.getDayStreak();

        if (!lastDate.isEmpty() && !today.equals(lastDate)) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1);
            String yesterday = sdf.format(cal.getTime());

            if (!lastDate.equals(yesterday)) {
                currentStreak = 0;
                sessionManager.setDayStreak(0);
            }
        }
        
        if (tvDayStreakLabel != null) {
            tvDayStreakLabel.setText(currentStreak + " DAY STREAK");
        }
    }

    private void saveWorkoutProgress() {
        int repsDone = repCount; 
        
        sessionManager.setLifetimeReps(sessionManager.getLifetimeReps() + repsDone);
        sessionManager.addTodayTotalReps(repsDone);

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String today = sdf.format(new java.util.Date());
        String lastDate = sessionManager.getLastWorkoutDate();
        int currentStreak = sessionManager.getDayStreak();

        if (lastDate.isEmpty() || !today.equals(lastDate)) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1);
            String yesterday = sdf.format(cal.getTime());

            if (lastDate.equals(yesterday)) {
                currentStreak++;
            } else {
                currentStreak = 1;
            }

            sessionManager.setDayStreak(currentStreak);
            sessionManager.setLastWorkoutDate(today);
        }

        // Save Historic stats
        int todayTotal = sessionManager.getTodayTotalReps();
        sessionManager.saveDailyHistory(today, todayTotal);

        // Track best daily performance in best_streak
        if (todayTotal > sessionManager.getBestStreak()) {
            sessionManager.setBestStreak(todayTotal);
        }

        updateStreakUI();
        highlightTodayBar();
    }

    private void showStatsBottomSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog = 
                new com.google.android.material.bottomsheet.BottomSheetDialog(this, com.google.android.material.R.style.Theme_Design_BottomSheetDialog);
        bottomSheetDialog.setContentView(R.layout.dialog_stats);

        View bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            bottomSheet.setBackgroundResource(android.R.color.transparent);
        }

        TextView tvBsDayStreakVal = bottomSheetDialog.findViewById(R.id.tvBsDayStreakVal);
        TextView tvBsBestStreak = bottomSheetDialog.findViewById(R.id.tvBsBestStreak);
        TextView tvBsToday = bottomSheetDialog.findViewById(R.id.tvBsToday);
        TextView tvBsLifetime = bottomSheetDialog.findViewById(R.id.tvBsLifetime);
        TextView tvBsSessions = bottomSheetDialog.findViewById(R.id.tvBsSessions);

        if (tvBsDayStreakVal != null) tvBsDayStreakVal.setText(String.valueOf(sessionManager.getDayStreak()));
        if (tvBsBestStreak != null) tvBsBestStreak.setText(String.valueOf(sessionManager.getBestStreak()));
        if (tvBsToday != null) tvBsToday.setText(String.valueOf(sessionManager.getTodayTotalReps()));
        if (tvBsLifetime != null) tvBsLifetime.setText(String.valueOf(sessionManager.getLifetimeReps()));
        if (tvBsSessions != null) tvBsSessions.setText(String.valueOf(sessionManager.getSessionsCompleted()));

        bottomSheetDialog.show();
    }

    private void showSnackbar(String msg) {
        View rootView = findViewById(android.R.id.content);
        if (rootView == null) return;
        com.google.android.material.snackbar.Snackbar snack =
                com.google.android.material.snackbar.Snackbar.make(rootView, msg,
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT);
        snack.setBackgroundTint(0xFF003D7A);
        snack.setTextColor(0xFFFFFFFF);
        snack.setActionTextColor(0xFF00D2FF);
        snack.getView().setElevation(16f);
        snack.show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Break Timer
    // ══════════════════════════════════════════════════════════════════════════

    /** Start a countdown using the break duration saved in Settings. */
    private void startBreakTimer() {
        int breakMins = sessionManager.getBreakTime();
        if (breakMins <= 0) return; // nothing to count down

        breakEndMs  = System.currentTimeMillis() + (long) breakMins * 60 * 1000;
        breakActive = true;

        // Initialise label and show card with a fade-in animation
        if (tvBreakCountdown != null)
            tvBreakCountdown.setText(String.format(Locale.getDefault(), "%02d:00", breakMins));

        if (cardBreakTimer != null) {
            cardBreakTimer.setAlpha(0f);
            cardBreakTimer.setVisibility(View.VISIBLE);
            cardBreakTimer.animate().alpha(1f).setDuration(350).start();
        }

        mainHandler.post(tickBreak);
    }

    /**
     * Stop the break countdown.
     *
     * @param finished {@code true}  if the break ended naturally (time ran out) →
     *                              vibrate + send notification.
     *                 {@code false} if manually cancelled by the user resuming
     *                              or tapping ✕.
     */
    private void stopBreakTimer(boolean finished) {
        breakActive = false;
        mainHandler.removeCallbacks(tickBreak);

        // Hide the card with a fade-out
        if (cardBreakTimer != null) {
            cardBreakTimer.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                if (cardBreakTimer != null)
                    cardBreakTimer.setVisibility(View.GONE);
            }).start();
        }

        if (finished) {
            // Vibrate pattern: short-short-long
            if (vibrator != null)
                vibrator.vibrate(VibrationEffect.createWaveform(
                        new long[]{ 0, 120, 80, 120, 80, 300 }, -1));

            // Send break-end notification
            sendBreakEndNotification();
        }
    }

    /** Posts a premium-styled "Break Over" notification to the status bar. */
    private void sendBreakEndNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;

        // Create / ensure notification channel
        NotificationChannel ch = new NotificationChannel(
                BREAK_CHANNEL_ID, "Break Timer",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Notifies when your break is over");
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{ 0, 150, 100, 150 });
        ch.enableLights(true);
        ch.setLightColor(0xFF00D2FF);          // purple LED
        ch.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);

        // Tap notification → open app
        PendingIntent pi = PendingIntent.getActivity(
                this, 0,
                new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // "Let's Go" action taps into the app
        PendingIntent actionPi = PendingIntent.getActivity(
                this, 1,
                new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        android.app.Notification notif = new NotificationCompat.Builder(this, BREAK_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_lock_small)       // same icon as service notif
                .setContentTitle("💪 Break Over — Let's Go!")
                .setContentText("Your rest is done. Time to crush more push-ups!")
                // Dark violet background (app's purple_mid = #2D1B69) with colorize
                .setColor(0xFF0078FF)
                .setColorized(true)
                // Expanded view with extra detail
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("☕ Rest is over!\n\nGet back into position and crush your push-up goal. You've got this! 🔥")
                        .setBigContentTitle("💪 Break Over — Let's Go!"))
                // Action button with purple accent label
                .addAction(R.drawable.ic_fire, "▶  Resume Workout", actionPi)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build();

        nm.notify(BREAK_NOTIF_ID, notif);
    }
}
