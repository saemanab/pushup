package com.pushupfit.locker;

import android.graphics.PointF;

import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

/**
 * PushupDetector
 *
 * Production-ready push-up rep counter powered by ML Kit Human Pose Estimation.
 *
 * ── Algorithm Overview ───────────────────────────────────────────────────────
 *
 *  Step 1 │ Extract LEFT and RIGHT (shoulder, elbow, wrist) landmarks.
 *  Step 2 │ Compute elbow angle for each arm using the law of cosines.
 *  Step 3 │ Average the two arm angles (use one if only one is visible).
 *  Step 4 │ Feed the raw angle into a 5-frame moving average buffer.
 *  Step 5 │ Run the smoothed angle through the UP/DOWN state machine.
 *  Step 6 │ (Optional) Check body-horizontal posture and fire a warning.
 *
 * ── State Machine ────────────────────────────────────────────────────────────
 *
 *  The machine starts in the UP position (arms straight).
 *
 *  UP   → smoothedAngle < DOWN_THRESHOLD (90°) → set isDown = true
 *  DOWN → smoothedAngle > UP_THRESHOLD  (160°) → repCount++, isDown = false
 *
 *  Only one rep is counted per complete UP → DOWN → UP cycle.
 *
 * ── Thresholds (adjustable) ──────────────────────────────────────────────────
 *
 *  UP_THRESHOLD   = 160°  arm is straight  (top of push-up)
 *  DOWN_THRESHOLD =  90°  arm is bent      (bottom of push-up)
 *
 * ── False-Count Prevention ───────────────────────────────────────────────────
 *
 *  • 5-frame moving average smooths per-frame noise so jitters near a
 *    threshold boundary never flip the state machine.
 *  • isDown flag ensures the DOWN phase MUST be observed before a rep
 *    can be counted on the next UP phase — partial movements are ignored.
 *  • 700 ms debounce after each counted rep prevents double-counting.
 *  • Landmark confidence gate (MIN_CONFIDENCE = 0.4) rejects unreliable
 *    detections caused by occlusion or motion blur.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class PushupDetector {

    // =========================================================================
    // Adjustable thresholds
    // =========================================================================

    /**
     * Elbow angle (degrees) above which the arm is considered STRAIGHT (UP).
     * Increase this value to require a more fully-locked-out arm at the top.
     * Default: 160°
     */
    public static final float UP_THRESHOLD = 160f;

    /**
     * Elbow angle (degrees) below which the arm is considered BENT (DOWN).
     * Decrease this value to require a deeper push-up before DOWN is detected.
     * Default: 90°
     */
    public static final float DOWN_THRESHOLD = 90f;

    /**
     * Minimum in-frame likelihood score (0–1) for a pose landmark to be used.
     * Lower values accept more landmarks but increase noise risk.
     * Default: 0.4
     */
    public static final float MIN_CONFIDENCE = 0.4f;

    /**
     * Number of frames included in the moving-average smoothing window.
     * Higher = smoother but slightly more lag. 5 is optimal at 30 fps.
     */
    public static final int SMOOTH_FRAMES = 5;

    /**
     * Minimum milliseconds that must pass between two counted reps.
     * Guards against a momentary angle wobble at the top being miscounted.
     * Default: 700 ms
     */
    public static final long DEBOUNCE_MS = 700L;

    // =========================================================================
    // Optional posture warning
    // =========================================================================

    /**
     * When true, the detector will check whether the body is roughly
     * horizontal and fire {@link PostureListener#onPostureWarning(String)}
     * if the body appears to be too vertical.
     */
    private boolean postureCheckEnabled = false;

    /**
     * Optional callback to notify the UI about posture issues.
     */
    public interface PostureListener {
        /**
         * Called (on the thread that calls {@link #processPose}) when the
         * user's body posture does not look like a push-up position.
         *
         * @param message Human-readable warning, e.g. "Stand horizontally for push-ups"
         */
        void onPostureWarning(String message);
    }

    private PostureListener postureListener;

    // =========================================================================
    // Rep-completion callback
    // =========================================================================

    /**
     * Callback fired every time a complete UP → DOWN → UP rep is detected.
     */
    public interface RepListener {
        /**
         * @param totalReps Running total of reps counted this session.
         */
        void onRepCounted(int totalReps);
    }

    private RepListener repListener;

    // =========================================================================
    // Internal state
    // =========================================================================

    /**
     * True while the arm is in the DOWN phase (waiting to return UP).
     * Reset to false once the rep is counted.
     */
    private boolean isDown = false;

    /** Running rep count for this session. */
    private int repCount = 0;

    /** Timestamp of the last successfully counted rep (for debounce). */
    private long lastRepTimestamp = 0L;

    /**
     * Most recently computed smoothed angle.
     * Exposed via {@link #getCurrentAngle()} for the UI debug overlay.
     */
    private float currentAngle = -1f;

    // ── 5-frame moving average buffer ─────────────────────────────────────────
    private final float[] angleBuffer = new float[SMOOTH_FRAMES];
    private int  bufferHead   = 0;   // next write position (circular)
    private int  bufferFilled = 0;   // number of valid frames in buffer

    // =========================================================================
    // Public API
    // =========================================================================

    /** Register the rep completion callback. */
    public void setRepListener(RepListener listener) {
        this.repListener = listener;
    }

    /**
     * Enable or disable the optional body-horizontal posture check.
     * When enabled, set a {@link PostureListener} to receive warnings.
     *
     * @param enabled  Whether to perform the check.
     * @param listener Callback for posture warnings (may be null to disable).
     */
    public void setPostureCheck(boolean enabled, PostureListener listener) {
        this.postureCheckEnabled = enabled;
        this.postureListener     = listener;
    }

    // =========================================================================
    // Core processing — call this once per camera frame
    // =========================================================================

    /**
     * Process one ML Kit {@link Pose} frame.
     *
     * <p>May be called from any thread (camera background executor).
     * The {@link RepListener} is fired on the same calling thread —
     * post to the main thread in MainActivity if needed for UI updates.</p>
     *
     * @param pose The pose detected by ML Kit for this frame.
     */
    public void processPose(Pose pose) {
        if (pose == null) return;

        // ------------------------------------------------------------------
        // STEP 1 & 2 — Extract landmarks and compute per-arm elbow angles
        // ------------------------------------------------------------------

        // Left arm landmarks
        PoseLandmark leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark leftElbow    = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW);
        PoseLandmark leftWrist    = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST);

        // Right arm landmarks
        PoseLandmark rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
        PoseLandmark rightElbow    = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW);
        PoseLandmark rightWrist    = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST);

        // Calculate angle for each arm independently (NaN if landmarks missing)
        float angleLeft  = Float.NaN;
        float angleRight = Float.NaN;

        if (isConfident(leftShoulder) && isConfident(leftElbow) && isConfident(leftWrist)) {
            // Angle at the LEFT elbow vertex (shoulder–elbow–wrist)
            angleLeft = computeAngle(
                    leftShoulder.getPosition(),
                    leftElbow.getPosition(),
                    leftWrist.getPosition());
        }

        if (isConfident(rightShoulder) && isConfident(rightElbow) && isConfident(rightWrist)) {
            // Angle at the RIGHT elbow vertex (shoulder–elbow–wrist)
            angleRight = computeAngle(
                    rightShoulder.getPosition(),
                    rightElbow.getPosition(),
                    rightWrist.getPosition());
        }

        // ------------------------------------------------------------------
        // STEP 3 — Average both arms for higher accuracy
        // ------------------------------------------------------------------
        float rawAngle;
        if (!Float.isNaN(angleLeft) && !Float.isNaN(angleRight)) {
            rawAngle = (angleLeft + angleRight) / 2f;   // Both arms visible
        } else if (!Float.isNaN(angleLeft)) {
            rawAngle = angleLeft;                        // Only left arm visible
        } else if (!Float.isNaN(angleRight)) {
            rawAngle = angleRight;                       // Only right arm visible
        } else {
            return; // No usable landmark data this frame — skip
        }

        // ------------------------------------------------------------------
        // STEP 4 — 5-frame moving average to smooth out noise / jitter
        // ------------------------------------------------------------------
        angleBuffer[bufferHead] = rawAngle;
        bufferHead = (bufferHead + 1) % SMOOTH_FRAMES;   // advance circular pointer
        if (bufferFilled < SMOOTH_FRAMES) bufferFilled++;

        // Wait until we have a full window before acting (avoids a false
        // trigger from the very first frame before enough data is seen)
        if (bufferFilled < SMOOTH_FRAMES) return;

        float smoothedAngle = movingAverage();
        currentAngle = smoothedAngle;

        // ------------------------------------------------------------------
        // STEP 6 (optional) — Posture check: is the body roughly horizontal?
        // ------------------------------------------------------------------
        if (postureCheckEnabled && postureListener != null) {
            checkPosture(pose);
        }

        // ------------------------------------------------------------------
        // STEP 5 — UP / DOWN state machine
        //
        //  UP   → smoothedAngle < DOWN_THRESHOLD  →  isDown = true
        //  DOWN → smoothedAngle > UP_THRESHOLD    →  COUNT + isDown = false
        // ------------------------------------------------------------------
        if (!isDown) {
            // Waiting to see the DOWN phase
            if (smoothedAngle < DOWN_THRESHOLD) {
                // Arm is bent → entered DOWN position
                isDown = true;
            }
        } else {
            // isDown == true: waiting for the arm to extend back UP
            if (smoothedAngle > UP_THRESHOLD) {
                // Arm is straight again → full cycle complete
                long now = System.currentTimeMillis();
                if (now - lastRepTimestamp >= DEBOUNCE_MS) {
                    lastRepTimestamp = now;
                    isDown = false;
                    repCount++;
                    if (repListener != null) {
                        repListener.onRepCounted(repCount);
                    }
                }
            }
        }
    }

    // =========================================================================
    // Posture helper — optional body-horizontal check
    // =========================================================================

    /**
     * Checks whether the user is in a plank / push-up horizontal position.
     *
     * Method: the midpoint of the shoulders and the midpoint of the hips
     * should be at similar Y values in the image (both near the same
     * horizontal level). If the shoulders are significantly higher than
     * the hips (i.e. the user is standing), fire a warning.
     *
     * The check is intentionally lenient: if the required landmarks are
     * not visible, no warning is fired (benefit of the doubt).
     *
     * @param pose Current frame pose.
     */
    private void checkPosture(Pose pose) {
        PoseLandmark lShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark rShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
        PoseLandmark lHip      = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
        PoseLandmark rHip      = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP);

        // Need at least one shoulder and one hip to reason about posture
        if (!isConfident(lShoulder) && !isConfident(rShoulder)) return;
        if (!isConfident(lHip)      && !isConfident(rHip))      return;

        // Mid-point Y values (Y increases downward in image coordinates)
        float shoulderMidY = midY(lShoulder, rShoulder);
        float hipMidY      = midY(lHip,      rHip);

        // Use shoulder-to-shoulder span as a body-scale normaliser so the
        // check is independent of how far the user is from the camera
        float shoulderSpan = isConfident(lShoulder) && isConfident(rShoulder)
                ? Math.abs(lShoulder.getPosition().x - rShoulder.getPosition().x)
                : 100f; // fallback if only one shoulder visible

        if (shoulderSpan < 10f) return; // too close together to interpret

        // Normalised vertical separation: large → body is more vertical
        float verticalRatio = Math.abs(shoulderMidY - hipMidY) / shoulderSpan;

        // Threshold: if the hips are more than 2× shoulder-width above/below
        // the shoulders, the body is probably not horizontal
        if (verticalRatio > 2.0f && postureListener != null) {
            postureListener.onPostureWarning(
                    "Keep your body horizontal for push-ups!");
        }
    }

    // =========================================================================
    // Geometry helpers
    // =========================================================================

    /**
     * Computes the angle (in degrees) at vertex {@code b} formed by the
     * three points {@code a}–{@code b}–{@code c}, using the dot-product
     * formula (equivalent to the law of cosines for 2-D vectors).
     *
     * @param a First point  (shoulder)
     * @param b Vertex point (elbow)
     * @param c Third point  (wrist)
     * @return Angle in degrees in the range [0°, 180°].
     */
    private float computeAngle(PointF a, PointF b, PointF c) {
        // Vectors from elbow to shoulder and elbow to wrist
        double baX = a.x - b.x,  baY = a.y - b.y;
        double bcX = c.x - b.x,  bcY = c.y - b.y;

        double dot    = baX * bcX + baY * bcY;
        double magBA  = Math.sqrt(baX * baX + baY * baY);
        double magBC  = Math.sqrt(bcX * bcX + bcY * bcY);

        if (magBA == 0 || magBC == 0) return 180f; // degenerate → treat as straight

        // Clamp to [-1, 1] to guard against floating-point precision drift
        double cosAngle = Math.max(-1.0, Math.min(1.0, dot / (magBA * magBC)));
        return (float) Math.toDegrees(Math.acos(cosAngle));
    }

    /**
     * Returns the current moving-average from the circular angle buffer.
     */
    private float movingAverage() {
        float sum = 0f;
        for (int i = 0; i < bufferFilled; i++) sum += angleBuffer[i];
        return sum / bufferFilled;
    }

    /**
     * Average Y of two landmarks (uses one if the other is missing/low confidence).
     */
    private float midY(PoseLandmark a, PoseLandmark b) {
        boolean ga = isConfident(a), gb = isConfident(b);
        if (ga && gb) return (a.getPosition().y + b.getPosition().y) / 2f;
        if (ga) return a.getPosition().y;
        if (gb) return b.getPosition().y;
        return Float.NaN;
    }

    /**
     * Returns true when the landmark exists and its in-frame likelihood
     * is at or above {@link #MIN_CONFIDENCE}.
     */
    private boolean isConfident(PoseLandmark lm) {
        return lm != null && lm.getInFrameLikelihood() >= MIN_CONFIDENCE;
    }

    // =========================================================================
    // Public getters
    // =========================================================================

    /** @return Total reps counted since the last {@link #reset()}. */
    public int getRepCount() { return repCount; }

    /**
     * @return Smoothed elbow angle in degrees (average of last 5 frames).
     *         Returns {@code -1} if not yet computed (buffer not yet full).
     */
    public float getCurrentAngle() { return currentAngle; }

    /**
     * @return True while the arm is in the DOWN phase (elbow < 90°),
     *         waiting to come back UP. Useful for HUD state display.
     */
    public boolean isInDownPhase() { return isDown; }

    /**
     * Resets all state for a new session.
     * Call this when the user starts a new workout.
     */
    public void reset() {
        isDown           = false;
        repCount         = 0;
        lastRepTimestamp = 0L;
        currentAngle     = -1f;
        bufferHead       = 0;
        bufferFilled     = 0;
        for (int i = 0; i < SMOOTH_FRAMES; i++) angleBuffer[i] = 0f;
    }
}
