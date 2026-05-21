package com.pushupfit.locker

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class OnboardingWelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val barColor = ContextCompat.getColor(this, R.color.splash_gradient_bottom)
        window.statusBarColor = barColor
        window.navigationBarColor = barColor

        setContentView(R.layout.activity_onboarding_welcome)

        val bubbleTopRight = findViewById<View>(R.id.bubbleTopRight)
        val bubbleMidLeft = findViewById<View>(R.id.bubbleMidLeft)
        val bubbleBottom = findViewById<View>(R.id.bubbleBottom)
        val glassHeroCard = findViewById<View>(R.id.glassHeroCard)
        val featuresContainer = findViewById<View>(R.id.featuresContainer)
        val pageIndicator = findViewById<View>(R.id.pageIndicator)
        val featureRow1 = findViewById<View>(R.id.featureRow1)
        val featureRow2 = findViewById<View>(R.id.featureRow2)
        val featureRow3 = findViewById<View>(R.id.featureRow3)
        val btnContinue = findViewById<TextView>(R.id.btnContinue)

        glassHeroCard.translationY = 48f
        featuresContainer.translationY = 24f
        btnContinue.translationY = 32f

        fadeIn(bubbleTopRight, 500, 0)
        fadeIn(bubbleMidLeft, 550, 80)
        fadeIn(bubbleBottom, 600, 160)
        startFloat(bubbleTopRight, -10f, 10f, 2800)
        startFloat(bubbleMidLeft, 8f, -8f, 3200)
        startFloat(bubbleBottom, -6f, 6f, 3600)

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(glassHeroCard, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(glassHeroCard, "translationY", 48f, 0f)
            )
            duration = 700
            startDelay = 200
            interpolator = OvershootInterpolator(0.9f)
            start()
        }

        fadeIn(featuresContainer, 500, 450)
        ObjectAnimator.ofFloat(featuresContainer, "translationY", 24f, 0f).apply {
            duration = 500
            startDelay = 450
            interpolator = DecelerateInterpolator(1.4f)
            start()
        }

        staggerRow(featureRow1, 520)
        staggerRow(featureRow2, 600)
        staggerRow(featureRow3, 680)

        fadeIn(pageIndicator, 400, 720)
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(btnContinue, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(btnContinue, "translationY", 32f, 0f)
            )
            duration = 500
            startDelay = 780
            interpolator = DecelerateInterpolator(1.5f)
            start()
        }

        btnContinue.setOnClickListener { view ->
            view.animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(80)
                .withEndAction {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(80)
                        .withEndAction { goToQuiz() }
                        .start()
                }
                .start()
        }
    }

    private fun fadeIn(view: View, durationMs: Long, delayMs: Long) {
        ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
            duration = durationMs
            startDelay = delayMs
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun staggerRow(row: View, delayMs: Long) {
        row.alpha = 0f
        row.translationX = -20f

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(row, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(row, "translationX", -20f, 0f)
            )
            duration = 400
            startDelay = delayMs
            interpolator = DecelerateInterpolator(1.3f)
            start()
        }
    }

    private fun startFloat(view: View, from: Float, to: Float, durationMs: Long) {
        ValueAnimator.ofFloat(from, to).apply {
            duration = durationMs
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                view.translationY = animation.animatedValue as Float
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    view.translationY = from
                }
            })
            startDelay = 400
            start()
        }
    }

    private fun goToQuiz() {
        startActivity(Intent(this, QuizActivity::class.java))
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        finish()
    }
}
