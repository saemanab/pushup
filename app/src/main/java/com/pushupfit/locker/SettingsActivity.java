package com.pushupfit.locker;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import com.google.android.material.snackbar.Snackbar;

import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private TextView tvTargetRepsValue;
    private TextView tvBreakTimeValue;
    private TextView tvBlockedAppsStatus;

    private int targetReps = 24;
    private int breakTime = 15;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sessionManager = new SessionManager(this);

        // Views
        View cardShare    = findViewById(R.id.cardShare);
        View cardRateUs   = findViewById(R.id.cardRateUs);
        View cardFeedback = findViewById(R.id.cardFeedback);

        ImageButton btnRepMinus = findViewById(R.id.btnRepMinus);
        ImageButton btnRepPlus = findViewById(R.id.btnRepPlus);
        tvTargetRepsValue = findViewById(R.id.tvTargetRepsValue);

        ImageButton btnBreakMinus = findViewById(R.id.btnBreakMinus);
        ImageButton btnBreakPlus = findViewById(R.id.btnBreakPlus);
        tvBreakTimeValue = findViewById(R.id.tvBreakTimeValue);

        LinearLayout rowManageApps = findViewById(R.id.rowManageApps);
        tvBlockedAppsStatus = findViewById(R.id.tvBlockedAppsStatus);

        SwitchCompat switchHaptic = findViewById(R.id.switchHaptic);
        SwitchCompat switchSound = findViewById(R.id.switchSound);

        TextView tvPrivacyPolicy = findViewById(R.id.tvPrivacyPolicy);
        TextView tvTerms = findViewById(R.id.tvTerms);

        // Load values
        targetReps = sessionManager.getGoal();
        breakTime = sessionManager.getBreakTime();
        
        updateRepsText();
        updateBreakTimeText();

        // Section 1: Actions
        cardShare.setOnClickListener(v -> {
            playClickEffect(v);
            animatePress(v);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, "Check out PushUp Time, the best app to stay focused while working out! https://play.google.com/store/apps/details?id=" + getPackageName());
            startActivity(Intent.createChooser(intent, "Share App"));
        });

        cardRateUs.setOnClickListener(v -> {
            playClickEffect(v);
            animatePress(v);
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName())));
            } catch (android.content.ActivityNotFoundException e) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
            }
        });

        cardFeedback.setOnClickListener(v -> {
            playClickEffect(v);
            animatePress(v);
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:support@pushupfit.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Feedback for PushUp Time");
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                showSnackbar("No email client installed.");
            }
        });

        // Section 2: Adjustments
        btnRepMinus.setOnClickListener(v -> {
            playClickEffect(v);
            if (targetReps > 1) {
                targetReps--;
                sessionManager.setGoal(targetReps);
                updateRepsText();
            }
        });

        btnRepPlus.setOnClickListener(v -> {
            playClickEffect(v);
            if (targetReps < 999) {
                targetReps++;
                sessionManager.setGoal(targetReps);
                updateRepsText();
            }
        });

        btnBreakMinus.setOnClickListener(v -> {
            playClickEffect(v);
            if (breakTime > 1) {
                breakTime--;
                sessionManager.setBreakTime(breakTime);
                updateBreakTimeText();
            }
        });

        btnBreakPlus.setOnClickListener(v -> {
            playClickEffect(v);
            if (breakTime < 120) {
                breakTime++;
                sessionManager.setBreakTime(breakTime);
                updateBreakTimeText();
            }
        });

        // Section 3: Manage
        rowManageApps.setOnClickListener(v -> {
            playClickEffect(v);
            startActivity(new Intent(this, AppSelectionActivity.class));
        });

        // Section 4: Switches
        switchHaptic.setChecked(sessionManager.isHapticEnabled());
        switchHaptic.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setHapticEnabled(isChecked);
            if (isChecked) playClickEffect(buttonView);
        });

        switchSound.setChecked(sessionManager.isSoundEnabled());
        switchSound.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setSoundEnabled(isChecked);
            if (isChecked) playClickEffect(buttonView);
        });

        // Footer
        tvPrivacyPolicy.setOnClickListener(v -> {
            playClickEffect(v);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/mohamedabiaba/home")));
        });

        tvTerms.setOnClickListener(v -> {
            playClickEffect(v);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/pushuptime-replift/home")));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update manage apps string
        Set<String> blocked = sessionManager.getBlockedPackages();
        if (blocked.isEmpty()) {
            tvBlockedAppsStatus.setText("No apps selected");
            tvBlockedAppsStatus.setTextColor(0xFF888888); // gray
        } else {
            int count = blocked.size();
            tvBlockedAppsStatus.setText(count + " app" + (count == 1 ? "" : "s") + " selected");
            tvBlockedAppsStatus.setTextColor(0xFF9B59FF); // purple
        }
    }

    private void updateRepsText() {
        tvTargetRepsValue.setText(String.valueOf(targetReps));
    }

    private void updateBreakTimeText() {
        tvBreakTimeValue.setText(String.valueOf(breakTime));
    }

    private void playClickEffect(android.view.View v) {
        if (sessionManager.isHapticEnabled()) {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        }
        if (sessionManager.isSoundEnabled()) {
            v.playSoundEffect(android.view.SoundEffectConstants.CLICK);
        }
    }

    /** Spring press animation for circular quick-action buttons. */
    private void animatePress(android.view.View v) {
        v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(100)
                .withEndAction(() -> v.animate()
                        .scaleX(1f).scaleY(1f).setDuration(150)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2f))
                        .start())
                .start();
    }

    private void showSnackbar(String msg) {
        android.view.View rootView = findViewById(android.R.id.content);
        Snackbar snack = Snackbar.make(rootView, msg, Snackbar.LENGTH_SHORT);
        snack.setBackgroundTint(0xFF1C1B22);
        snack.setTextColor(0xFFFFFFFF);
        snack.setActionTextColor(0xFF9B59FF);
        snack.getView().setElevation(16f);
        snack.show();
    }
}
