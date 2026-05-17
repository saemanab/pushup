package com.pushupfit.locker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

/**
 * PoseOverlayView — transparent View layered on top of the camera PreviewView.
 *
 * Draws the full Human Pose Estimation skeleton:
 *   • White filled circles  at each detected keypoint
 *   • Cyan lines            connecting them (arms, torso, legs)
 *
 * Coordinate mapping:
 *   ML Kit returns landmark positions in IMAGE pixel coordinates.
 *   We scale them to VIEW coordinates using the image's width/height and
 *   mirror the X axis because we are using the FRONT camera.
 *
 * Thread safety:
 *   Call {@link #setPose(Pose, int, int)} from any thread — it posts
 *   invalidate() to the main thread internally.
 */
public class PoseOverlayView extends View {

    // ── Visual style ──────────────────────────────────────────────────────────
    private static final float DOT_RADIUS       = 14f;   // keypoint circle radius (px)
    private static final float DOT_STROKE_WIDTH = 4f;
    private static final float LINE_STROKE_WIDTH = 6f;

    // Cyan for lines  (#00E5FF neon cyan)
    private static final int LINE_COLOR    = Color.parseColor("#00E5FF");
    // White filled dot
    private static final int DOT_FILL      = Color.WHITE;
    // Cyan border on dot
    private static final int DOT_STROKE    = Color.parseColor("#00E5FF");

    // Minimum in-frame confidence for a landmark to be drawn
    private static final float MIN_CONFIDENCE = 0.45f;

    // ── Skeleton connections ──────────────────────────────────────────────────
    // Each pair of PoseLandmark type constants defines one bone segment.
    private static final int[][] CONNECTIONS = {
            // ── Face ──
            { PoseLandmark.NOSE, PoseLandmark.LEFT_EYE_INNER },
            { PoseLandmark.NOSE, PoseLandmark.RIGHT_EYE_INNER },
            { PoseLandmark.LEFT_EYE_INNER,  PoseLandmark.LEFT_EYE },
            { PoseLandmark.LEFT_EYE,        PoseLandmark.LEFT_EYE_OUTER },
            { PoseLandmark.RIGHT_EYE_INNER, PoseLandmark.RIGHT_EYE },
            { PoseLandmark.RIGHT_EYE,       PoseLandmark.RIGHT_EYE_OUTER },
            { PoseLandmark.LEFT_EAR,        PoseLandmark.LEFT_EYE_OUTER },
            { PoseLandmark.RIGHT_EAR,       PoseLandmark.RIGHT_EYE_OUTER },
            { PoseLandmark.NOSE,            PoseLandmark.LEFT_EAR },
            { PoseLandmark.NOSE,            PoseLandmark.RIGHT_EAR },

            // ── Shoulders ──
            { PoseLandmark.LEFT_SHOULDER,   PoseLandmark.RIGHT_SHOULDER },

            // ── Left arm ──
            { PoseLandmark.LEFT_SHOULDER,   PoseLandmark.LEFT_ELBOW },
            { PoseLandmark.LEFT_ELBOW,      PoseLandmark.LEFT_WRIST },
            { PoseLandmark.LEFT_WRIST,      PoseLandmark.LEFT_INDEX },
            { PoseLandmark.LEFT_WRIST,      PoseLandmark.LEFT_PINKY },
            { PoseLandmark.LEFT_WRIST,      PoseLandmark.LEFT_THUMB },

            // ── Right arm ──
            { PoseLandmark.RIGHT_SHOULDER,  PoseLandmark.RIGHT_ELBOW },
            { PoseLandmark.RIGHT_ELBOW,     PoseLandmark.RIGHT_WRIST },
            { PoseLandmark.RIGHT_WRIST,     PoseLandmark.RIGHT_INDEX },
            { PoseLandmark.RIGHT_WRIST,     PoseLandmark.RIGHT_PINKY },
            { PoseLandmark.RIGHT_WRIST,     PoseLandmark.RIGHT_THUMB },

            // ── Torso ──
            { PoseLandmark.LEFT_SHOULDER,   PoseLandmark.LEFT_HIP },
            { PoseLandmark.RIGHT_SHOULDER,  PoseLandmark.RIGHT_HIP },
            { PoseLandmark.LEFT_HIP,        PoseLandmark.RIGHT_HIP },

            // ── Left leg ──
            { PoseLandmark.LEFT_HIP,        PoseLandmark.LEFT_KNEE },
            { PoseLandmark.LEFT_KNEE,       PoseLandmark.LEFT_ANKLE },
            { PoseLandmark.LEFT_ANKLE,      PoseLandmark.LEFT_HEEL },
            { PoseLandmark.LEFT_HEEL,       PoseLandmark.LEFT_FOOT_INDEX },

            // ── Right leg ──
            { PoseLandmark.RIGHT_HIP,       PoseLandmark.RIGHT_KNEE },
            { PoseLandmark.RIGHT_KNEE,      PoseLandmark.RIGHT_ANKLE },
            { PoseLandmark.RIGHT_ANKLE,     PoseLandmark.RIGHT_HEEL },
            { PoseLandmark.RIGHT_HEEL,      PoseLandmark.RIGHT_FOOT_INDEX },
    };

    // ── Paint objects ─────────────────────────────────────────────────────────
    private final Paint linePaint;
    private final Paint dotFillPaint;
    private final Paint dotStrokePaint;

    // ── Data ──────────────────────────────────────────────────────────────────
    private volatile Pose   pose;
    private volatile int    imageWidth  = 1;
    private volatile int    imageHeight = 1;

    // ── Constructors ──────────────────────────────────────────────────────────
    public PoseOverlayView(Context context) {
        this(context, null);
    }

    public PoseOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(LINE_COLOR);
        linePaint.setStrokeWidth(LINE_STROKE_WIDTH);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setAlpha(220);

        dotFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotFillPaint.setColor(DOT_FILL);
        dotFillPaint.setStyle(Paint.Style.FILL);

        dotStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotStrokePaint.setColor(DOT_STROKE);
        dotStrokePaint.setStrokeWidth(DOT_STROKE_WIDTH);
        dotStrokePaint.setStyle(Paint.Style.STROKE);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Feed a new detected pose and the image dimensions it was detected from.
     * Safe to call from a background thread.
     */
    public void setPose(Pose pose, int imageWidth, int imageHeight) {
        this.pose        = pose;
        this.imageWidth  = Math.max(1, imageWidth);
        this.imageHeight = Math.max(1, imageHeight);
        postInvalidate(); // triggers onDraw on the UI thread
    }

    /** Call to clear the overlay (e.g. when session stops). */
    public void clearPose() {
        this.pose = null;
        postInvalidate();
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Pose currentPose = pose;
        if (currentPose == null) return;

        int viewW = getWidth();
        int viewH = getHeight();
        if (viewW == 0 || viewH == 0) return;

        // Scale factors: map image pixel → view pixel
        // We use FILL-CENTER scaling (same as PreviewView default), which
        // means we scale uniformly and may crop one axis.
        float scaleX = (float) viewW / imageWidth;
        float scaleY = (float) viewH / imageHeight;
        float scale  = Math.max(scaleX, scaleY);          // fill (crop) strategy

        // Offset to centre the scaled image inside the view
        float offsetX = (viewW - imageWidth  * scale) / 2f;
        float offsetY = (viewH - imageHeight * scale) / 2f;

        // ── Draw connections first (underneath dots) ──────────────────────────
        for (int[] conn : CONNECTIONS) {
            PoseLandmark lmA = currentPose.getPoseLandmark(conn[0]);
            PoseLandmark lmB = currentPose.getPoseLandmark(conn[1]);
            if (!isVisible(lmA) || !isVisible(lmB)) continue;

            float ax = toViewX(lmA.getPosition().x, scale, offsetX, viewW);
            float ay = toViewY(lmA.getPosition().y, scale, offsetY);
            float bx = toViewX(lmB.getPosition().x, scale, offsetX, viewW);
            float by = toViewY(lmB.getPosition().y, scale, offsetY);

            canvas.drawLine(ax, ay, bx, by, linePaint);
        }

        // ── Draw keypoint dots ─────────────────────────────────────────────────
        for (PoseLandmark lm : currentPose.getAllPoseLandmarks()) {
            if (!isVisible(lm)) continue;

            float x = toViewX(lm.getPosition().x, scale, offsetX, viewW);
            float y = toViewY(lm.getPosition().y, scale, offsetY);

            canvas.drawCircle(x, y, DOT_RADIUS,       dotFillPaint);
            canvas.drawCircle(x, y, DOT_RADIUS,       dotStrokePaint);
        }
    }

    // ── Coordinate helpers ────────────────────────────────────────────────────

    /**
     * Converts an image-space X coordinate to view-space X,
     * mirroring horizontally for the front camera.
     */
    private float toViewX(float imgX, float scale, float offsetX, int viewW) {
        float scaled = imgX * scale + offsetX;
        // Mirror: front camera image is not yet mirrored by CameraX
        return viewW - scaled;
    }

    private float toViewY(float imgY, float scale, float offsetY) {
        return imgY * scale + offsetY;
    }

    private boolean isVisible(@Nullable PoseLandmark lm) {
        return lm != null && lm.getInFrameLikelihood() >= MIN_CONFIDENCE;
    }
}
