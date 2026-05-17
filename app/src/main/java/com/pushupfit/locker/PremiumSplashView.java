package com.pushupfit.locker;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * PremiumSplashView — "POWER CORE" design.
 *
 * Renders (back → front):
 *   1. Near-black background
 *   2. Three concentric glowing orange rings (animated scale + alpha)
 *   3. Central radial-gradient circle (orange bloom)
 *   4. Sharp glowing circle border
 *   5. Skeletal push-up figure (bone sticks + joint dots) in white
 *   6. "PUSHUP LOCKER" title with white-to-amber gradient
 *   7. Orange divider line + subtitle
 *   8. Edge vignette
 *
 * Animated properties (driven by ObjectAnimator in SplashActivity):
 *   ringScale  0.5 → 1.0   rings pulse outward
 *   ringAlpha  0.0 → 1.0   rings fade in
 *   figAlpha   0.0 → 1.0   figure assembles
 *   textAlpha  0.0 → 1.0   title + subtitle appear
 */
public class PremiumSplashView extends View {

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final int BG       = 0xFF08080F;
    private static final int ORANGE   = 0xFFFF6B00;
    private static final int AMBER    = 0xFFFFB347;

    // ── Animated fields ───────────────────────────────────────────────────────
    private float ringScale  = 0.5f;
    private float ringAlpha  = 0f;
    private float figAlpha   = 0f;
    private float textAlpha  = 0f;

    // ── Geometry (set in onSizeChanged) ───────────────────────────────────────
    private float cx, cy, cr;   // circle centre + radius
    private boolean ready = false;

    // ── Paints ────────────────────────────────────────────────────────────────
    private final Paint bgPaint       = new Paint();
    private final Paint bloomPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderGlow    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderSharp   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bonePaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint jointPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint divPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vignettePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float dp;

    public PremiumSplashView(Context c)                          { super(c);       init(); }
    public PremiumSplashView(Context c, AttributeSet a)          { super(c, a);    init(); }
    public PremiumSplashView(Context c, AttributeSet a, int s)   { super(c, a, s); init(); }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        dp = getResources().getDisplayMetrics().density;

        bgPaint.setColor(BG);

        // Bone lines — thick, round-capped, white
        bonePaint.setStyle(Paint.Style.STROKE);
        bonePaint.setStrokeCap(Paint.Cap.ROUND);
        bonePaint.setStrokeJoin(Paint.Join.ROUND);
        bonePaint.setColor(Color.WHITE);
        bonePaint.setStrokeWidth(dp * 8f);

        // Joint dots
        jointPaint.setColor(Color.WHITE);
        jointPaint.setStyle(Paint.Style.FILL);

        headFillPaint.setColor(Color.WHITE);
        headFillPaint.setStyle(Paint.Style.FILL);

        // Ring
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp * 1.5f);

        // Border glow
        borderGlow.setStyle(Paint.Style.STROKE);
        borderGlow.setStrokeWidth(dp * 3f);
        borderGlow.setColor(ORANGE);

        borderSharp.setStyle(Paint.Style.STROKE);
        borderSharp.setStrokeWidth(dp * 1.5f);
        borderSharp.setColor(0xFFFFB347);

        // Text
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setFakeBoldText(true);
        titlePaint.setLetterSpacing(0.14f);

        subPaint.setTextAlign(Paint.Align.CENTER);
        subPaint.setLetterSpacing(0.38f);
        subPaint.setColor(0xCCFF8C3A);

        divPaint.setStyle(Paint.Style.STROKE);
        divPaint.setStrokeWidth(dp * 1f);
        divPaint.setColor(0xAAFF6B00);

        vignettePaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        cx = w * 0.5f;
        cy = h * 0.38f;
        cr = w * 0.30f;

        // Radial bloom inside circle
        bloomPaint.setShader(new RadialGradient(cx, cy, cr * 1.05f,
                new int[]{ 0xFFFF7020, 0xCCFF5500, 0x66CC4000, 0x00000000 },
                new float[]{ 0f, 0.35f, 0.65f, 1f },
                Shader.TileMode.CLAMP));

        // Outer border glow blur
        borderGlow.setMaskFilter(new BlurMaskFilter(dp * 18f, BlurMaskFilter.Blur.NORMAL));

        // Title gradient: white → amber → white
        float ts = dp * 42f;
        titlePaint.setTextSize(ts);
        float ty = h * 0.70f;
        titlePaint.setShader(new LinearGradient(
                cx - w * 0.38f, ty - ts,
                cx + w * 0.38f, ty,
                new int[]{ 0xFFFFFFFF, 0xFFFFB347, 0xFFFFFFFF },
                new float[]{ 0f, 0.5f, 1f },
                Shader.TileMode.CLAMP));

        subPaint.setTextSize(dp * 11.5f);

        // Vignette
        vignettePaint.setShader(new RadialGradient(
                cx, h * 0.5f,
                Math.max(w, h) * 0.76f,
                new int[]{ 0x00000000, 0x00000000, 0xEE000000 },
                new float[]{ 0f, 0.48f, 1f },
                Shader.TileMode.CLAMP));

        ready = true;
    }

    // ── Animated property accessors ──────────────────────────────────────────
    public float getRingScale()  { return ringScale; }
    public void  setRingScale(float v)  { ringScale = v; invalidate(); }

    public float getRingAlpha()  { return ringAlpha; }
    public void  setRingAlpha(float v)  { ringAlpha = v; invalidate(); }

    public float getFigAlpha()   { return figAlpha; }
    public void  setFigAlpha(float v)   { figAlpha = v; invalidate(); }

    public float getTextAlpha()  { return textAlpha; }
    public void  setTextAlpha(float v)  { textAlpha = v; invalidate(); }

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas) {
        if (!ready) return;
        int w = getWidth(), h = getHeight();

        // 1. Background
        canvas.drawColor(BG);

        // 2. Concentric rings
        drawRings(canvas);

        // 3. Central bloom
        canvas.drawCircle(cx, cy, cr * 1.05f, bloomPaint);

        // 4. Circle border glow + sharp line
        canvas.drawCircle(cx, cy, cr, borderGlow);
        canvas.drawCircle(cx, cy, cr, borderSharp);

        // 5. Push-up skeletal figure
        if (figAlpha > 0f) drawFigure(canvas, (int)(figAlpha * 255));

        // 6 & 7. Text
        if (textAlpha > 0f) drawText(canvas, h, (int)(textAlpha * 255));

        // 8. Vignette
        canvas.drawRect(0, 0, w, h, vignettePaint);
    }

    // ── Concentric pulse rings ────────────────────────────────────────────────
    private void drawRings(Canvas canvas) {
        if (ringAlpha <= 0f) return;
        float[] radMult = { 1.50f, 1.88f, 2.26f };
        float[] aMult   = { 0.65f, 0.38f, 0.18f };
        int[]   colors  = { 0x00FF5500, 0x00FF8020, 0x00FF9040 };

        for (int i = 0; i < 3; i++) {
            float r  = cr * radMult[i] * ringScale;
            int   a  = (int)(ringAlpha * aMult[i] * 255);
            int   col = (a << 24) | (colors[i] & 0x00FFFFFF) | 0x00FF5500;
            // just use orange with computed alpha
            ringPaint.setColor( (a << 24) | 0xFF5500 );
            ringPaint.setMaskFilter(new BlurMaskFilter(dp * 6f, BlurMaskFilter.Blur.NORMAL));
            canvas.drawCircle(cx, cy, r, ringPaint);
        }
    }

    // ── Skeletal push-up figure ───────────────────────────────────────────────
    /**
     * Draws a stick-figure in push-up DOWN position (side view, facing right).
     * All positions are computed relative to the circle centre (cx, cy)
     * using a scale factor s = cr * 0.82, so the figure fills the circle.
     */
    private void drawFigure(Canvas canvas, int alpha) {
        float s = cr * 0.82f;

        // ── Joint positions ──────────────────────────────────────────────────
        // HEAD
        float hx = cx + s * 0.74f,   hy = cy - s * 0.34f;
        float hr = s * 0.17f;

        // Neck base / Right shoulder
        float shx = cx + s * 0.50f,  shy = cy - s * 0.08f;

        // LEFT shoulder (far side, slight offset for depth)
        float lsx = cx + s * 0.38f,  lsy = cy + s * 0.04f;

        // Elbow (arm bent 90°, elbow points DOWN-BACK)
        float ex  = cx + s * 0.60f,  ey  = cy + s * 0.40f;

        // Hand / wrist at ground level inside the circle
        float wx  = cx + s * 0.44f,  wy  = cy + s * 0.60f;

        // Hip
        float hipx = cx - s * 0.48f, hipy = cy + s * 0.08f;

        // Knee
        float knx  = cx - s * 0.68f, kny  = cy + s * 0.28f;

        // Foot (on toes)
        float ftx  = cx - s * 0.84f, fty  = cy + s * 0.56f;

        // ── Set alpha ────────────────────────────────────────────────────────
        bonePaint.setAlpha(alpha);
        jointPaint.setAlpha(alpha);
        headFillPaint.setAlpha(alpha);

        float jr = bonePaint.getStrokeWidth() * 0.70f; // joint dot radius

        // ── Bones ────────────────────────────────────────────────────────────
        // Torso (spine)
        canvas.drawLine(shx, shy, hipx, hipy, bonePaint);
        // Neck to head
        canvas.drawLine(shx, shy, hx - hr * 0.55f, hy + hr * 0.72f, bonePaint);
        // Upper arm
        canvas.drawLine(shx, shy, ex, ey, bonePaint);
        // Forearm
        canvas.drawLine(ex, ey, wx, wy, bonePaint);

        // SECOND ARM (far side, slightly lighter/transparent for depth)
        bonePaint.setAlpha((int)(alpha * 0.45f));
        float ex2 = cx + s * 0.48f, ey2 = cy + s * 0.44f;
        float wx2 = cx + s * 0.32f, wy2 = cy + s * 0.62f;
        canvas.drawLine(lsx, lsy, ex2, ey2, bonePaint);
        canvas.drawLine(ex2, ey2, wx2, wy2, bonePaint);
        bonePaint.setAlpha(alpha);

        // Thigh
        canvas.drawLine(hipx, hipy, knx, kny, bonePaint);
        // Shin
        canvas.drawLine(knx, kny, ftx, fty, bonePaint);

        // SECOND LEG (far side, slight depth offset)
        bonePaint.setAlpha((int)(alpha * 0.45f));
        canvas.drawLine(hipx + dp*4, hipy + dp*5, knx + dp*4, kny + dp*5, bonePaint);
        canvas.drawLine(knx + dp*4,  kny + dp*5,  ftx + dp*4, fty + dp*5, bonePaint);
        bonePaint.setAlpha(alpha);

        // ── Joints ───────────────────────────────────────────────────────────
        canvas.drawCircle(shx, shy, jr, jointPaint);   // shoulder
        canvas.drawCircle(ex,  ey,  jr, jointPaint);   // elbow
        canvas.drawCircle(wx,  wy,  jr, jointPaint);   // wrist
        canvas.drawCircle(hipx, hipy, jr, jointPaint); // hip
        canvas.drawCircle(knx, kny,  jr, jointPaint);  // knee
        canvas.drawCircle(ftx, fty,  jr, jointPaint);  // foot
        canvas.drawCircle(lsx, lsy,  jr * 0.7f, jointPaint); // far shoulder (smaller)

        // ── Head ─────────────────────────────────────────────────────────────
        canvas.drawCircle(hx, hy, hr, headFillPaint);
    }

    // ── Title and subtitle text ───────────────────────────────────────────────
    private void drawText(Canvas canvas, int h, int alpha) {
        float ty = h * 0.70f;

        // Main title
        titlePaint.setAlpha(alpha);
        canvas.drawText("PUSHUP LOCKER", cx, ty, titlePaint);

        // Orange divider line
        float divY = ty + dp * 12f;
        float fade  = Math.min(1f, alpha / 255f);
        divPaint.setAlpha((int)(fade * 180));
        canvas.drawLine(cx - dp * 68f, divY, cx + dp * 68f, divY, divPaint);

        // Subtitle
        subPaint.setAlpha((int)(alpha * 0.82f));
        canvas.drawText("GET ACTIVE  ·  STAY LOCKED", cx, divY + dp * 24f, subPaint);
    }
}
