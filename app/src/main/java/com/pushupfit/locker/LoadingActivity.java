package com.pushupfit.locker;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * LoadingActivity — Premium "Building Your Plan" screen shown after quiz.
 *
 * Features:
 *  • Pulsing glow icon (scale + alpha loop)
 *  • Determinate progress bar that animates from 0 → 100 over ~3 s
 *  • Live percentage counter next to the progress bar
 *  • Step checklist (4 rows) that activate + tick off with staggered timing
 *  • Each step fades in, highlights, then shows a checkmark ✓ when done
 *  • Smooth cross-fade on the status label
 */
public class LoadingActivity extends AppCompatActivity {

    // ── Step timing (ms from start) ───────────────────────────────────────────
    // Total session: 3 400 ms before navigating away
    private static final int TOTAL_MS    = 3400;
    private static final int STEP1_START = 200;
    private static final int STEP1_DONE  = 900;
    private static final int STEP2_START = 950;
    private static final int STEP2_DONE  = 1700;
    private static final int STEP3_START = 1750;
    private static final int STEP3_DONE  = 2500;
    private static final int STEP4_START = 2550;
    private static final int STEP4_DONE  = 3200;

    // ── Status labels ──────────────────────────────────────────────────────────
    private static final String[] STATUS = {
            "Analyzing your fitness profile…",
            "Configuring push-up counter…",
            "Calibrating pose detection…",
            "Finalising your custom plan…",
            "Ready! 🚀"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());

    // ── Views ──────────────────────────────────────────────────────────────────
    private TextView    tvStatus;
    private TextView    tvProgressPct;
    private ProgressBar progressBar;
    private View        glowRing;
    private View        ivLoadingIcon;

    private View     step1, step2, step3, step4;
    private TextView tvStep1Check, tvStep2Check, tvStep3Check, tvStep4Check;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);

        // Bind views
        tvStatus       = findViewById(R.id.tvCalibrationStatus);
        tvProgressPct  = findViewById(R.id.tvProgressPct);
        progressBar    = findViewById(R.id.loadingProgressBar);
        glowRing       = findViewById(R.id.glowRing);
        ivLoadingIcon  = findViewById(R.id.ivLoadingIcon);

        step1 = findViewById(R.id.step1);
        step2 = findViewById(R.id.step2);
        step3 = findViewById(R.id.step3);
        step4 = findViewById(R.id.step4);

        tvStep1Check = findViewById(R.id.tvStep1Check);
        tvStep2Check = findViewById(R.id.tvStep2Check);
        tvStep3Check = findViewById(R.id.tvStep3Check);
        tvStep4Check = findViewById(R.id.tvStep4Check);

        startPulseAnimation();
        startProgressAnimation();
        scheduleSteps();

        // Navigate away after TOTAL_MS
        handler.postDelayed(() -> {
            startActivity(new Intent(this, AppSelectionActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, TOTAL_MS + 200);
    }

    // ── Pulsing icon + glow ────────────────────────────────────────────────────
    private void startPulseAnimation() {
        // Scale pulse: 1.0 → 1.08 → 1.0 forever
        ValueAnimator pulse = ValueAnimator.ofFloat(1f, 1.10f, 1f);
        pulse.setDuration(1600);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        pulse.addUpdateListener(anim -> {
            float v = (float) anim.getAnimatedValue();
            if (ivLoadingIcon != null) {
                ivLoadingIcon.setScaleX(v);
                ivLoadingIcon.setScaleY(v);
            }
        });
        pulse.start();

        // Glow ring alpha pulse: 0.3 → 0.75 → 0.3
        ValueAnimator glow = ValueAnimator.ofFloat(0.3f, 0.75f, 0.3f);
        glow.setDuration(1600);
        glow.setRepeatCount(ValueAnimator.INFINITE);
        glow.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        glow.addUpdateListener(anim -> {
            if (glowRing != null)
                glowRing.setAlpha((float) anim.getAnimatedValue());
        });
        glow.start();
    }

    // ── Smooth progress bar (0 → 100 over TOTAL_MS) ───────────────────────────
    private void startProgressAnimation() {
        ValueAnimator anim = ValueAnimator.ofInt(0, 100);
        anim.setDuration(TOTAL_MS);
        anim.setInterpolator(new DecelerateInterpolator(1.2f));
        anim.addUpdateListener(a -> {
            int val = (int) a.getAnimatedValue();
            if (progressBar != null) progressBar.setProgress(val);
            if (tvProgressPct != null) tvProgressPct.setText(val + "%");
        });
        anim.start();
    }

    // ── Step checklist sequencing ──────────────────────────────────────────────
    private void scheduleSteps() {
        // Step 1
        handler.postDelayed(() -> activateStep(step1, tvStep1Check, STATUS[0]), STEP1_START);
        handler.postDelayed(() -> completeStep(step1, tvStep1Check, STATUS[1]), STEP1_DONE);

        // Step 2
        handler.postDelayed(() -> activateStep(step2, tvStep2Check, null), STEP2_START);
        handler.postDelayed(() -> completeStep(step2, tvStep2Check, STATUS[2]), STEP2_DONE);

        // Step 3
        handler.postDelayed(() -> activateStep(step3, tvStep3Check, null), STEP3_START);
        handler.postDelayed(() -> completeStep(step3, tvStep3Check, STATUS[3]), STEP3_DONE);

        // Step 4
        handler.postDelayed(() -> activateStep(step4, tvStep4Check, null), STEP4_START);
        handler.postDelayed(() -> completeStep(step4, tvStep4Check, STATUS[4]), STEP4_DONE);
    }

    /**
     * Animate a step row into "active" state:
     *  • Fade + slide in to full opacity
     *  • Change circle indicator to a spinning dot
     *  • Optionally update the status label with a cross-fade
     */
    private void activateStep(View row, TextView check, String statusText) {
        if (row == null) return;

        // Slide-in + fade
        row.setTranslationX(-20f);
        row.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // Animate circle → spinner look (just color change here; real spinner via text anim)
        if (check != null) {
            check.setText("◉");
            check.setTextColor(0xFF00D2FF);
            check.animate().scaleX(1.3f).scaleY(1.3f).setDuration(150)
                    .withEndAction(() -> check.animate().scaleX(1f).scaleY(1f).setDuration(150).start())
                    .start();
        }

        if (statusText != null) crossFadeStatus(statusText);
    }

    /**
     * Mark a step row as completed:
     *  • Swap circle → bold checkmark ✓ with a pop
     *  • Tint the row a subtle green-ish glow
     *  • Update status label
     */
    private void completeStep(View row, TextView check, String nextStatusText) {
        if (check == null) return;

        // Pop-in checkmark
        check.setScaleX(0f);
        check.setScaleY(0f);
        check.setText("✓");
        check.setTextColor(0xFF66BB6A);   // green tick
        check.animate()
                .scaleX(1f).scaleY(1f)
                .setDuration(280)
                .setInterpolator(new OvershootInterpolator(2.5f))
                .start();

        if (nextStatusText != null) crossFadeStatus(nextStatusText);
    }

    /** Cross-fade the status label to new text. */
    private void crossFadeStatus(String text) {
        if (tvStatus == null) return;
        tvStatus.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            tvStatus.setText(text);
            tvStatus.animate().alpha(1f).setDuration(220).start();
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
