package com.pushupfit.locker;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ViewFlipper;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.google.android.material.snackbar.Snackbar;

/**
 * QuizActivity — Screen 1: Multi-step "Personalize Your Goal" onboarding quiz.
 *
 * 4 steps displayed via a ViewFlipper (slide-in/out):
 * ┌─────────────────────────────────────────────────────────────┐
 * │ Step 1 — What is your MAIN FITNESS GOAL? │
 * │ [Strength] [Endurance] [Weight Loss] [Cardio Mix] │
 * │ [Flexibility] │
 * │ │
 * │ Step 2 — What is your CURRENT FITNESS LEVEL? │
 * │ [Beginner] [Intermediate] [Advanced] │
 * │ │
 * │ Step 3 — HOW OFTEN do you work out per week? │
 * │ [1-2 days] [3-4 days] [5+ days] [Every day] │
 * │ │
 * │ Step 4 — What MOTIVATES you most? │
 * │ [Look Better] [Feel Healthier] [Build Discipline] │
 * │ [Compete] [Reduce Stress] │
 * └─────────────────────────────────────────────────────────────┘
 *
 * After Step 4 → AppSelectionActivity (choose which apps to block)
 */
public class QuizActivity extends AppCompatActivity {

    // ── Step answers ──────────────────────────────────────────────────────────
    private String selectedCategory = null;
    private String selectedLevel = null;
    private String selectedFrequency = null;
    private String selectedMotivation = null;

    // Currently highlighted card per step
    private CardView selectedCard1 = null;
    private CardView selectedCard2 = null;
    private CardView selectedCard3 = null;
    private CardView selectedCard4 = null;

    // ── Views ─────────────────────────────────────────────────────────────────
    private ViewFlipper viewFlipper;
    private ProgressBar stepProgress;
    private TextView tvStepLabel;
    private Button btnNext;
    private Button btnBack;

    private int currentStep = 0; // 0 = Step 1, 3 = Step 4

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);

        viewFlipper = findViewById(R.id.viewFlipper);
        stepProgress = findViewById(R.id.stepProgress);
        tvStepLabel = findViewById(R.id.tvStepLabel);
        btnNext = findViewById(R.id.btnNext);
        btnBack = findViewById(R.id.btnBack);

        stepProgress.setMax(4);
        stepProgress.setProgress(1);

        bindStep1();
        bindStep2();
        bindStep3();
        bindStep4();
        updateStepIndicator();

        // ── Navigation ────────────────────────────────────────────────────────
        btnNext.setOnClickListener(v -> {
            if (!validateCurrentStep())
                return;
            saveCurrentStep();

            if (currentStep < 3) {
                currentStep++;
                viewFlipper.setInAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_right));
                viewFlipper.setOutAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_out_left));
                viewFlipper.showNext();
                updateStepIndicator();
            } else {
                // All 4 steps done — save everything and go to app selection
                finishQuiz();
            }
        });

        btnBack.setOnClickListener(v -> {
            if (currentStep > 0) {
                currentStep--;
                viewFlipper.setInAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_left));
                viewFlipper.setOutAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_out_right));
                viewFlipper.showPrevious();
                updateStepIndicator();
            }
        });
    }

    // ── Step 1: Fitness Goal ──────────────────────────────────────────────────
    private void bindStep1() {
        CardView c1 = findViewById(R.id.card1_Strength);
        CardView c2 = findViewById(R.id.card1_Endurance);
        CardView c3 = findViewById(R.id.card1_WeightLoss);
        CardView c4 = findViewById(R.id.card1_Cardio);
        CardView c5 = findViewById(R.id.card1_Flex);

        c1.setOnClickListener(v -> selectStep1(c1, "Strength"));
        c2.setOnClickListener(v -> selectStep1(c2, "Endurance"));
        c3.setOnClickListener(v -> selectStep1(c3, "Weight Loss"));
        c4.setOnClickListener(v -> selectStep1(c4, "Cardio Mix"));
        c5.setOnClickListener(v -> selectStep1(c5, "Flexibility"));
    }

    private void selectStep1(CardView card, String value) {
        if (selectedCard1 != null)
            deselect(selectedCard1);
        select(card);
        selectedCard1 = card;
        selectedCategory = value;
    }

    // ── Step 2: Fitness Level ─────────────────────────────────────────────────
    private void bindStep2() {
        CardView c1 = findViewById(R.id.card2_Beginner);
        CardView c2 = findViewById(R.id.card2_Intermediate);
        CardView c3 = findViewById(R.id.card2_Advanced);

        c1.setOnClickListener(v -> selectStep2(c1, "Beginner"));
        c2.setOnClickListener(v -> selectStep2(c2, "Intermediate"));
        c3.setOnClickListener(v -> selectStep2(c3, "Advanced"));
    }

    private void selectStep2(CardView card, String value) {
        if (selectedCard2 != null)
            deselect(selectedCard2);
        select(card);
        selectedCard2 = card;
        selectedLevel = value;
    }

    // ── Step 3: Workout Frequency ─────────────────────────────────────────────
    private void bindStep3() {
        CardView c1 = findViewById(R.id.card3_1_2);
        CardView c2 = findViewById(R.id.card3_3_4);
        CardView c3 = findViewById(R.id.card3_5plus);
        CardView c4 = findViewById(R.id.card3_Daily);

        c1.setOnClickListener(v -> selectStep3(c1, "1-2 days/week"));
        c2.setOnClickListener(v -> selectStep3(c2, "3-4 days/week"));
        c3.setOnClickListener(v -> selectStep3(c3, "5+ days/week"));
        c4.setOnClickListener(v -> selectStep3(c4, "Every day"));
    }

    private void selectStep3(CardView card, String value) {
        if (selectedCard3 != null)
            deselect(selectedCard3);
        select(card);
        selectedCard3 = card;
        selectedFrequency = value;
    }

    // ── Step 4: Motivation ────────────────────────────────────────────────────
    private void bindStep4() {
        CardView c1 = findViewById(R.id.card4_LookBetter);
        CardView c2 = findViewById(R.id.card4_FeeHealthy);
        CardView c3 = findViewById(R.id.card4_Discipline);
        CardView c4 = findViewById(R.id.card4_Compete);
        CardView c5 = findViewById(R.id.card4_Stress);

        c1.setOnClickListener(v -> selectStep4(c1, "Look Better"));
        c2.setOnClickListener(v -> selectStep4(c2, "Feel Healthier"));
        c3.setOnClickListener(v -> selectStep4(c3, "Build Discipline"));
        c4.setOnClickListener(v -> selectStep4(c4, "Compete & Win"));
        c5.setOnClickListener(v -> selectStep4(c5, "Reduce Stress"));
    }

    private void selectStep4(CardView card, String value) {
        if (selectedCard4 != null)
            deselect(selectedCard4);
        select(card);
        selectedCard4 = card;
        selectedMotivation = value;
    }

    // ── Card helpers ──────────────────────────────────────────────────────────
    private void select(CardView card) {
        card.setCardBackgroundColor(getColor(R.color.card_selected));
        card.setCardElevation(16f);
    }

    private void deselect(CardView card) {
        card.setCardBackgroundColor(getColor(R.color.card_default));
        card.setCardElevation(4f);
    }

    // ── Validation ────────────────────────────────────────────────────────────
    private boolean validateCurrentStep() {
        switch (currentStep) {
            case 0:
                if (selectedCategory == null) {
                    toast("Select a fitness goal 💪");
                    return false;
                }
                break;
            case 1:
                if (selectedLevel == null) {
                    toast("Tell us your fitness level 🏅");
                    return false;
                }
                break;
            case 2:
                if (selectedFrequency == null) {
                    toast("How often do you train? 📅");
                    return false;
                }
                break;
            case 3:
                if (selectedMotivation == null) {
                    toast("What drives you? 🔥");
                    return false;
                }
                break;
        }
        return true;
    }

    private void saveCurrentStep() {
        /* saved all at once in finishQuiz */ }

    // ── Finish quiz ───────────────────────────────────────────────────────────
    private void finishQuiz() {
        SessionManager session = new SessionManager(this);
        session.setCategory(selectedCategory);
        session.setFitnessLevel(selectedLevel);
        session.setFrequency(selectedFrequency);
        session.setMotivation(selectedMotivation);

        // Compute a personalised pushup goal from category × level
        int goal = SessionManager.computeGoal(selectedCategory, selectedLevel);
        session.setGoal(goal);
        session.setOnboardingDone(true);

        // Go to loading / calibration screen first, then app selection
        startActivity(new Intent(this, LoadingActivity.class));
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
        finish();
    }

    // ── Step indicator ────────────────────────────────────────────────────────
    private void updateStepIndicator() {
        stepProgress.setProgress(currentStep + 1);
        String[] labels = {
                "Step 1 of 4 — Fitness Goal",
                "Step 2 of 4 — Fitness Level",
                "Step 3 of 4 — Workout Frequency",
                "Step 4 of 4 — Your Motivation"
        };
        tvStepLabel.setText(labels[currentStep]);
        btnBack.setVisibility(currentStep > 0 ? View.VISIBLE : View.GONE);
        btnNext.setText(currentStep == 3 ? "See My Plan →" : "Next →");
    }

    private void toast(String msg) {
        showSnackbar(msg);
    }

    private void showSnackbar(String msg) {
        View rootView = findViewById(android.R.id.content);
        com.google.android.material.snackbar.Snackbar snack =
                com.google.android.material.snackbar.Snackbar.make(rootView, msg,
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT);
        snack.setBackgroundTint(0xFF1C1B22);
        snack.setTextColor(0xFFFFFFFF);
        snack.setActionTextColor(0xFF9B59FF);
        snack.getView().setElevation(16f);
        snack.show();
    }
}
