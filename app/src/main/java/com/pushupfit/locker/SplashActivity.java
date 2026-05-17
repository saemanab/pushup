package com.pushupfit.locker;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * SplashActivity — Professional Purple Splash with user's icon.
 *
 * Animation timeline:
 *   0 ms  → Icon badge drops in from above + fades        (650 ms)
 *   280ms → App name scales up + fades                    (550 ms)
 *   480ms → Tagline fades in                              (450 ms)
 *   620ms → Dots fade in                                  (350 ms)
 *   800ms → GET STARTED slides up + fades                 (500 ms)
 */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── Skip splash on every launch after the first install ───────────
        SessionManager session = new SessionManager(this);
        if (session.isSplashShown()) {
            navigateNext();
            return;
        }

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
        getWindow().setStatusBarColor(0xFF1A1C2E);
        getWindow().setNavigationBarColor(0xFF1A1C2E);

        setContentView(R.layout.activity_splash);

        View     iconBadge = findViewById(R.id.iconBadge);
        TextView tvTitle   = findViewById(R.id.tvTitle);
        TextView tvTagline = findViewById(R.id.tvTagline);
        TextView tvDots    = findViewById(R.id.tvDots);
        TextView btnStart  = findViewById(R.id.btnGetStarted);

        // ── Initial states ────────────────────────────────────────────────
        iconBadge.setTranslationY(-60f);
        tvTitle.setScaleX(0.90f);
        tvTitle.setScaleY(0.90f);
        btnStart.setTranslationY(55f);

        // ── 1. Badge drops in from above ──────────────────────────────────
        AnimatorSet badgeSet = new AnimatorSet();
        badgeSet.playTogether(
                ObjectAnimator.ofFloat(iconBadge, "alpha",        0f, 1f),
                ObjectAnimator.ofFloat(iconBadge, "translationY", -60f, 0f));
        badgeSet.setDuration(700);
        badgeSet.setInterpolator(new OvershootInterpolator(1.2f));

        // ── 2. Title scales up + fades ────────────────────────────────────
        ObjectAnimator titleA  = anim(tvTitle, "alpha",  0f, 1f, 550, 280);
        ObjectAnimator titleSX = anim(tvTitle, "scaleX", 0.90f, 1f, 550, 280);
        ObjectAnimator titleSY = anim(tvTitle, "scaleY", 0.90f, 1f, 550, 280);
        titleSX.setInterpolator(new OvershootInterpolator(1.3f));
        titleSY.setInterpolator(new OvershootInterpolator(1.3f));

        // ── 3. Tagline fades ──────────────────────────────────────────────
        ObjectAnimator tagA = anim(tvTagline, "alpha", 0f, 1f, 450, 480);

        // ── 4. Dots fade ──────────────────────────────────────────────────
        ObjectAnimator dotsA = anim(tvDots, "alpha", 0f, 1f, 350, 620);

        // ── 5. Button slides up ───────────────────────────────────────────
        ObjectAnimator btnA  = anim(btnStart, "alpha",        0f, 1f,  500, 800);
        ObjectAnimator btnTY = anim(btnStart, "translationY", 55f, 0f, 500, 800);
        btnTY.setInterpolator(new OvershootInterpolator(1.1f));

        // ── Fire all ──────────────────────────────────────────────────────
        AnimatorSet master = new AnimatorSet();
        master.playTogether(badgeSet, titleA, titleSX, titleSY, tagA, dotsA, btnA, btnTY);
        master.start();

        // ── Button tap → navigate ─────────────────────────────────────────
        btnStart.setOnClickListener(v -> {
            v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80)
                    .withEndAction(() ->
                            v.animate().scaleX(1f).scaleY(1f).setDuration(80)
                                    .withEndAction(this::navigateNext).start())
                    .start();
        });
    }

    private ObjectAnimator anim(View v, String prop, float from, float to,
                                long dur, long delay) {
        ObjectAnimator a = ObjectAnimator.ofFloat(v, prop, from, to);
        a.setDuration(dur);
        a.setStartDelay(delay);
        a.setInterpolator(new DecelerateInterpolator(1.6f));
        return a;
    }

    private void navigateNext() {
        SessionManager session = new SessionManager(this);
        // Mark splash as seen — never show it again after this first launch
        session.setSplashShown(true);
        Intent intent = session.isOnboardingDone()
                ? new Intent(this, MainActivity.class)
                : new Intent(this, QuizActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
