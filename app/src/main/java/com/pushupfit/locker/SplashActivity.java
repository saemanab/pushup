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
import androidx.core.content.ContextCompat;

/**
 * SplashActivity — first launch only. Shows the app icon on the same
 * blue gradient as the launcher icon, with a short modern entrance animation.
 */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SessionManager session = new SessionManager(this);
        if (session.isSplashShown()) {
            navigateNext();
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
        TextView tvTitle = findViewById(R.id.tvTitle);
        TextView tvTagline = findViewById(R.id.tvTagline);
        TextView btnStart = findViewById(R.id.btnGetStarted);

        iconBadge.setScaleX(0.88f);
        iconBadge.setScaleY(0.88f);
        btnStart.setTranslationY(40f);

        AnimatorSet iconSet = new AnimatorSet();
        iconSet.playTogether(
                ObjectAnimator.ofFloat(iconBadge, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(iconBadge, "scaleX", 0.88f, 1f),
                ObjectAnimator.ofFloat(iconBadge, "scaleY", 0.88f, 1f));
        iconSet.setDuration(650);
        iconSet.setInterpolator(new OvershootInterpolator(1.05f));

        ObjectAnimator titleA = anim(tvTitle, "alpha", 0f, 1f, 450, 320);
        ObjectAnimator tagA = anim(tvTagline, "alpha", 0f, 1f, 400, 480);
        ObjectAnimator btnA = anim(btnStart, "alpha", 0f, 1f, 450, 620);
        ObjectAnimator btnTY = anim(btnStart, "translationY", 40f, 0f, 450, 620);
        btnTY.setInterpolator(new DecelerateInterpolator(1.4f));

        AnimatorSet master = new AnimatorSet();
        master.playTogether(iconSet, titleA, tagA, btnA, btnTY);
        master.start();

        btnStart.setOnClickListener(v -> {
            v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80)
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
        session.setSplashShown(true);
        Intent intent = session.isOnboardingDone()
                ? new Intent(this, MainActivity.class)
                : new Intent(this, QuizActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
