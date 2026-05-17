package com.pushupfit.locker;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * SessionManager — single source of truth for all persisted state.
 *
 * Stored data:
 * • pushup_goal – target rep count for the session
 * • fitness_category – chosen fitness goal (Strength, Endurance, …)
 * • fitness_level – Beginner / Intermediate / Advanced
 * • workout_frequency – Days per week (1-2, 3-4, 5+, Every day)
 * • motivation – user's "why" answer
 * • current_reps – real-time rep counter (read by the blocker service)
 * • is_locked – whether the session is currently active/locked
 * • onboarding_done – skips quiz on re-launch
 * • blocked_packages – Set<String> of package names chosen by the user
 */
public class SessionManager {

    private static final String PREF_NAME = "PushUpLockerPrefs";

    // Keys
    private static final String KEY_GOAL = "pushup_goal";
    private static final String KEY_CATEGORY = "fitness_category";
    private static final String KEY_LEVEL = "fitness_level";
    private static final String KEY_FREQUENCY = "workout_frequency";
    private static final String KEY_MOTIVATION = "motivation";
    private static final String KEY_REPS = "current_reps";
    private static final String KEY_LOCKED = "is_locked";
    private static final String KEY_ONBOARDED = "onboarding_done";
    private static final String KEY_BLOCKED_PKG = "blocked_packages";
    private static final String KEY_SPLASH_SHOWN = "splash_shown";
    private static final String KEY_BREAK_TIME = "break_time";
    private static final String KEY_HAPTIC = "haptic_enabled";
    private static final String KEY_SOUND = "sound_enabled";

    // Stats keys
    private static final String KEY_DAY_STREAK = "day_streak";
    private static final String KEY_BEST_STREAK = "best_streak";
    private static final String KEY_LIFETIME_REPS = "lifetime_reps";
    private static final String KEY_SESSIONS = "sessions_completed";
    private static final String KEY_LAST_DATE = "last_workout_date";
    private static final String KEY_TODAY_REPS = "today_total_reps";
    private static final String KEY_TODAY_DATE = "today_date";
    private static final String KEY_TODAY_TIME_MS = "today_total_time_ms";
    private static final String KEY_SEEN_RATING   = "has_seen_rating_popup";

    /** Default goal (used when no quiz has been completed) */
    public static final int DEFAULT_GOAL = 20;

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ── Goal ───────────────────────────────────────────────────────────────────
    public void setGoal(int goal) {
        prefs.edit().putInt(KEY_GOAL, goal).apply();
    }

    public int getGoal() {
        return prefs.getInt(KEY_GOAL, DEFAULT_GOAL);
    }

    // ── Break Time ─────────────────────────────────────────────────────────────
    public void setBreakTime(int mins) {
        prefs.edit().putInt(KEY_BREAK_TIME, mins).apply();
    }

    public int getBreakTime() {
        return prefs.getInt(KEY_BREAK_TIME, 15);
    }

    // ── Haptic & Sound ─────────────────────────────────────────────────────────
    public void setHapticEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_HAPTIC, enabled).apply();
    }

    public boolean isHapticEnabled() {
        return prefs.getBoolean(KEY_HAPTIC, true);
    }

    public void setSoundEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply();
    }

    public boolean isSoundEnabled() {
        return prefs.getBoolean(KEY_SOUND, true);
    }

    // ── Category (Strength, Endurance, Weight Loss…) ───────────────────────────
    public void setCategory(String cat) {
        prefs.edit().putString(KEY_CATEGORY, cat).apply();
    }

    public String getCategory() {
        return prefs.getString(KEY_CATEGORY, "Strength");
    }

    // ── Fitness level ─────────────────────────────────────────────────────────
    public void setFitnessLevel(String level) {
        prefs.edit().putString(KEY_LEVEL, level).apply();
    }

    public String getFitnessLevel() {
        return prefs.getString(KEY_LEVEL, "Beginner");
    }

    // ── Workout frequency ─────────────────────────────────────────────────────
    public void setFrequency(String freq) {
        prefs.edit().putString(KEY_FREQUENCY, freq).apply();
    }

    public String getFrequency() {
        return prefs.getString(KEY_FREQUENCY, "3-4 days");
    }

    // ── Motivation / "why" ────────────────────────────────────────────────────
    public void setMotivation(String m) {
        prefs.edit().putString(KEY_MOTIVATION, m).apply();
    }

    public String getMotivation() {
        return prefs.getString(KEY_MOTIVATION, "Stay Healthy");
    }

    // ── Rep count ─────────────────────────────────────────────────────────────
    public void setReps(int reps) {
        prefs.edit().putInt(KEY_REPS, reps).apply();
    }

    public int getReps() {
        return prefs.getInt(KEY_REPS, 0);
    }

    public void resetReps() {
        prefs.edit().putInt(KEY_REPS, 0).apply();
    }

    // ── Lock state ────────────────────────────────────────────────────────────
    public void setLocked(boolean locked) {
        prefs.edit().putBoolean(KEY_LOCKED, locked).apply();
    }

    public boolean isLocked() {
        return prefs.getBoolean(KEY_LOCKED, false);
    }

    // ── Onboarding ────────────────────────────────────────────────────────────
    public void setOnboardingDone(boolean done) {
        prefs.edit().putBoolean(KEY_ONBOARDED, done).apply();
    }

    public boolean isOnboardingDone() {
        return prefs.getBoolean(KEY_ONBOARDED, false);
    }

    // ── Splash (shown only on first install) ──────────────────────────────────
    public void setSplashShown(boolean shown) {
        prefs.edit().putBoolean(KEY_SPLASH_SHOWN, shown).apply();
    }

    public boolean isSplashShown() {
        return prefs.getBoolean(KEY_SPLASH_SHOWN, false);
    }

    // ── Blocked apps (Set of package names) ───────────────────────────────────
    public void saveBlockedPackages(Set<String> packages) {
        prefs.edit().putStringSet(KEY_BLOCKED_PKG, packages).apply();
    }

    public Set<String> getBlockedPackages() {
        Set<String> saved = prefs.getStringSet(KEY_BLOCKED_PKG, null);
        if (saved == null)
            return new HashSet<>();
        // SharedPreferences returns a live reference — copy it for safety
        return new HashSet<>(saved);
    }

    public boolean hasBlockedPackages() {
        Set<String> s = prefs.getStringSet(KEY_BLOCKED_PKG, null);
        return s != null && !s.isEmpty();
    }

    // ── Helper: compute goal from category + level ────────────────────────────
    /**
     * Returns a suggested rep goal based on the chosen category and fitness level.
     * Called after quiz completion to set a balanced pushup target.
     */
    public static int computeGoal(String category, String level) {
        // User requested override: fixed reps based on Level only
        switch (level) {
            case "Beginner":
                return 10;
            case "Intermediate":
                return 25;
            case "Advanced":
                return 50;
            default:
                return 20; // Default fallback
        }
    }

    // ── Stats ─────────────────────────────────────────────────────────────────
    public int getDayStreak() {
        return prefs.getInt(KEY_DAY_STREAK, 0);
    }
    public void setDayStreak(int val) {
        prefs.edit().putInt(KEY_DAY_STREAK, val).apply();
    }

    public int getBestStreak() {
        return prefs.getInt(KEY_BEST_STREAK, 0);
    }
    public void setBestStreak(int val) {
        prefs.edit().putInt(KEY_BEST_STREAK, val).apply();
    }

    public int getLifetimeReps() {
        return prefs.getInt(KEY_LIFETIME_REPS, 0);
    }
    public void setLifetimeReps(int val) {
        prefs.edit().putInt(KEY_LIFETIME_REPS, val).apply();
    }

    public int getSessionsCompleted() {
        return prefs.getInt(KEY_SESSIONS, 0);
    }
    public void setSessionsCompleted(int val) {
        prefs.edit().putInt(KEY_SESSIONS, val).apply();
    }

    public String getLastWorkoutDate() {
        return prefs.getString(KEY_LAST_DATE, "");
    }
    public void setLastWorkoutDate(String date) {
        prefs.edit().putString(KEY_LAST_DATE, date).apply();
    }

    public int getTodayTotalReps() {
        String todayDate = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
        if (!todayDate.equals(prefs.getString(KEY_TODAY_DATE, ""))) {
            return 0;
        }
        return prefs.getInt(KEY_TODAY_REPS, 0);
    }

    public void addTodayTotalReps(int reps) {
        String todayDate = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
        int current = getTodayTotalReps();
        prefs.edit()
                .putString(KEY_TODAY_DATE, todayDate)
                .putInt(KEY_TODAY_REPS, current + reps)
                .apply();
    }

    public long getTodayTotalTimeMs() {
        String todayDate = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
        if (!todayDate.equals(prefs.getString(KEY_TODAY_DATE, ""))) {
            return 0L;
        }
        return prefs.getLong(KEY_TODAY_TIME_MS, 0L);
    }

    public void addTodayTotalTimeMs(long timeMs) {
        String todayDate = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
        long current = getTodayTotalTimeMs();
        prefs.edit()
                .putString(KEY_TODAY_DATE, todayDate)
                .putLong(KEY_TODAY_TIME_MS, current + timeMs)
                .apply();
    }

    // ── Historic Data ─────────────────────────────────────────────────────────

    /** Saves historic data (reps) for a specific date (yyyy-MM-dd) */
    public void saveDailyHistory(String date, int reps) {
        prefs.edit().putInt("history_" + date, reps).apply();
    }

    /** Gets historic data (reps) for a specific date (yyyy-MM-dd) */
    public int getDailyHistory(String date) {
        return prefs.getInt("history_" + date, 0);
    }

    // ── Rating popup (one-shot) ───────────────────────────────────────────────

    /** Returns true if the user has already seen the rating popup. */
    public boolean hasSeenRatingPopup() {
        return prefs.getBoolean(KEY_SEEN_RATING, false);
    }

    /** Call this to permanently mark the rating popup as seen. */
    public void markRatingPopupSeen() {
        prefs.edit().putBoolean(KEY_SEEN_RATING, true).apply();
    }
}
