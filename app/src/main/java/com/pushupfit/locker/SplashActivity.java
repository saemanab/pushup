package com.pushupfit.locker;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/**
 * SplashActivity — shown once on first install. Icon only, then opens onboarding.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long HOLD_AFTER_ICON_MS = 600;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SessionManager session = new SessionManager(this);
        if (session.isSplashShown()) {
            navigateNext(session);
            return;
        }

        int barColor = ContextCompat.getColor(this, R.color.splash_gradient_bottom);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
        getWindow().setStatusBarColor(barColor);
        getWindow().setNavigationBarColor(barColor);

        setContentView(R.layout.activity_splash);

        View iconBadge = findViewById(R.id.iconBadge);
        iconBadge.setScaleX(0.88f);
        iconBadge.setScaleY(0.88f);

        AnimatorSet iconSet = new AnimatorSet();
        iconSet.playTogether(
                ObjectAnimator.ofFloat(iconBadge, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(iconBadge, "scaleX", 0.88f, 1f),
                ObjectAnimator.ofFloat(iconBadge, "scaleY", 0.88f, 1f));
        iconSet.setDuration(650);
        iconSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                handler.postDelayed(() -> {
                    SessionManager s = new SessionManager(SplashActivity.this);
                    s.setSplashShown(true);
                    goToOnboarding();
                }, HOLD_AFTER_ICON_MS);
            }
        });
        iconSet.start();
    }

    private void navigateNext(SessionManager session) {
        Intent intent = session.isOnboardingDone()
                ? new Intent(this, MainActivity.class)
                : new Intent(this, OnboardingWelcomeActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void goToOnboarding() {
        startActivity(new Intent(this, OnboardingWelcomeActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
