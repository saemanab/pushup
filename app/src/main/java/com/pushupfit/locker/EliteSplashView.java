package com.pushupfit.locker;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * EliteSplashView — Custom Canvas view for the "Elite Performance" splash screen.
 *
 * Renders (back to front):
 *   1. Matte-black vignette overlay
 *   2. Volumetric light cone radiating from top-center
 *   3. GOLD PULSE LINE — flat EKG line on left/right, morphs through
 *      a sharp geometric push-up figure silhouette in the center
 *   4. Multiple glow layers around the pulse line
 *   5. Horizontal scan-line effect (animated)
 *   6. "PUSHUP TIME" in electric-gold metallic gradient
 *   7. "ELITE PERFORMANCE" subtitle
 *   8. Small accent lines / corner marks
 *
 * All animations are driven externally via setDrawProgress() and setScanY().
 */
public class EliteSplashView extends View {

    // ── Electric Gold palette ────────────────────────────────────────────────
    private static final int GOLD_BRIGHT  = 0xFFFFCC00;    // #FFCC00
    private static final int GOLD_MID     = 0xFFE6A800;    // #E6A800
    private static final int GOLD_DARK    = 0xFF7A5500;    // #7A5500
    private static final int GOLD_DIM     = 0x44FFB800;    // 27% gold
    private static final int MATTE_BLACK  = 0xFF0C0C0C;
    private static final int BG_COLOR     = 0xFF0E0E0F;

    // ── Draw progress (0 → 1): how much of the pulse path is visible ─────────
    private float drawProgress = 0f;

    // ── Scan-line Y position (0 → height) ────────────────────────────────────
    private float scanY = 0f;
    private float scanAlpha = 0f;

    // ── Text alpha (0 → 1) ───────────────────────────────────────────────────
    private float textAlpha = 0f;
    private float subtitleAlpha = 0f;

    // ── Cached geometry ───────────────────────────────────────────────────────
    private Path   pulsePath;
    private float  pathLength = 0f;

    // ── Paints ────────────────────────────────────────────────────────────────
    private Paint bgPaint, vignettePaint, volumePaint;
    private Paint glowWide, glowMid, lineSharp, lineCore;
    private Paint scanPaint;
    private Paint titlePaint, subtitlePaint, accentPaint;
    private Paint cornerPaint;

    private boolean geometryReady = false;

    // ── Density ───────────────────────────────────────────────────────────────
    private float dp;

    public EliteSplashView(Context context) { super(context); init(); }
    public EliteSplashView(Context ctx, AttributeSet a) { super(ctx, a); init(); }
    public EliteSplashView(Context ctx, AttributeSet a, int s) { super(ctx, a, s); init(); }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);  // BlurMaskFilter needs SW
        dp = getResources().getDisplayMetrics().density;

        // Background
        bgPaint = new Paint();
        bgPaint.setColor(BG_COLOR);

        // Volumetric light cone
        volumePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Glow — widest, most blurred
        glowWide = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowWide.setStyle(Paint.Style.STROKE);
        glowWide.setStrokeWidth(dp * 22f);
        glowWide.setColor(0x22FFCC00);
        glowWide.setMaskFilter(new BlurMaskFilter(dp * 32f, BlurMaskFilter.Blur.NORMAL));

        // Glow — mid
        glowMid = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowMid.setStyle(Paint.Style.STROKE);
        glowMid.setStrokeWidth(dp * 8f);
        glowMid.setColor(0x55FFB800);
        glowMid.setMaskFilter(new BlurMaskFilter(dp * 14f, BlurMaskFilter.Blur.NORMAL));

        // Sharp mid-line
        lineSharp = new Paint(Paint.ANTI_ALIAS_FLAG);
        lineSharp.setStyle(Paint.Style.STROKE);
        lineSharp.setStrokeWidth(dp * 2.5f);
        lineSharp.setColor(GOLD_MID);
        lineSharp.setStrokeCap(Paint.Cap.ROUND);
        lineSharp.setStrokeJoin(Paint.Join.ROUND);

        // Core bright line
        lineCore = new Paint(Paint.ANTI_ALIAS_FLAG);
        lineCore.setStyle(Paint.Style.STROKE);
        lineCore.setStrokeWidth(dp * 1.2f);
        lineCore.setColor(0xFFFFEE55);
        lineCore.setStrokeCap(Paint.Cap.ROUND);
        lineCore.setStrokeJoin(Paint.Join.ROUND);

        // Scan line
        scanPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scanPaint.setStyle(Paint.Style.STROKE);
        scanPaint.setStrokeWidth(dp * 1f);

        // Title text
        titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setFakeBoldText(true);
        titlePaint.setLetterSpacing(0.28f);

        // Subtitle text
        subtitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subtitlePaint.setTextAlign(Paint.Align.CENTER);
        subtitlePaint.setColor(0xCCE6A800);
        subtitlePaint.setLetterSpacing(0.42f);

        // Accent divider / corner marks
        accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        accentPaint.setStyle(Paint.Style.STROKE);
        accentPaint.setStrokeWidth(dp * 1f);
        accentPaint.setColor(0x99FFCC00);

        cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(dp * 2f);
        cornerPaint.setColor(0x66FFCC00);

        vignettePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        buildPulsePath(w, h);
        buildShaders(w, h);
        geometryReady = true;
    }

    /**
     * Builds the geometric pulse-line path that morphs into a push-up figure.
     *
     * Reading left → right (normalized coords then scaled):
     *
     *  SEGMENT 0: flat baseline from left edge to 20%w            (EKG lead-in)
     *  SEGMENT 1: small tick down then sharp UP spike             (heartbeat tick)
     *  SEGMENT 2: angled down to ground — RIGHT HAND on floor     (arm plant)
     *  SEGMENT 3: diagonal up-right — FOREARM + upper arm          (arm rise)
     *  SEGMENT 4: curve-less sharp angle — SHOULDER                (shoulder)
     *  SEGMENT 5: long diagonal line from shoulder → hips          (torso / back)
     *  SEGMENT 6: slightly angling line hips → knees              (thigh)
     *  SEGMENT 7: angled down to heels — leg extension             (shin + foot)
     *  SEGMENT 8: short vertical up — toes on ground              (toe plant)
     *  SEGMENT 9: flat baseline from ~85%w to right edge           (EKG lead-out)
     *
     * Figure is sized to fill roughly 55% of the screen width, centered 40% down.
     */
    private void buildPulsePath(int w, int h) {
        float cx = w * 0.5f;
        float cy = h * 0.40f;      // vertical centre of figure

        // Figure extents (half-widths in px)
        float figW  = w * 0.30f;   // half-width of figure zone
        float unit  = figW / 5.5f; // one "unit" of geometric scale

        // Key landmarks (all relative to cx, cy)
        // Ground level for hands and feet
        float groundY = cy + unit * 2.4f;

        // RIGHT HAND (left side of figure on screen — leading edge)
        float rhX = cx - figW;
        float rhY = groundY;

        // ELBOW (bent arm: about 1 unit right of hand, raised)
        float elX = rhX + unit * 1.1f;
        float elY = rhY - unit * 1.5f;

        // SHOULDER (top of arm, ~ 2 units from hand)
        float shX = rhX + unit * 1.8f;
        float shY = cy - unit * 0.4f;

        // HIP (body horizontal in push-up, so hip is same level as shoulder, shifted right)
        float hipX = shX + unit * 3.2f;
        float hipY = shY + unit * 0.35f;

        // KNEE
        float knX  = hipX + unit * 1.4f;
        float knY  = hipY + unit * 0.3f;

        // FOOT / HEEL
        float footX = knX  + unit * 1.5f;
        float footY = groundY;

        // TOE (vertical, short rise above heel — ball of foot on floor)
        float toeX  = footX - unit * 0.4f;
        float toeY  = groundY - unit * 0.55f;

        // ── Build path ────────────────────────────────────────────────────────
        pulsePath = new Path();

        // SEGMENT 0: flat lead-in from left edge
        float leadInStartX = 0f;
        float baseline = groundY + unit * 0.3f;
        pulsePath.moveTo(leadInStartX, baseline);
        pulsePath.lineTo(rhX - unit * 0.8f, baseline);

        // SEGMENT 1: small downward tick then sharp up spike (EKG heartbeat)
        pulsePath.lineTo(rhX - unit * 0.5f, baseline + unit * 0.35f);
        pulsePath.lineTo(rhX - unit * 0.2f, baseline - unit * 1.6f);  // spike UP
        pulsePath.lineTo(rhX              , baseline + unit * 0.2f);

        // SEGMENT 2: from baseline to right-hand position (arm descends to floor)
        pulsePath.lineTo(rhX, rhY);   // hand on ground

        // SEGMENT 3: elbow — forearm rising
        pulsePath.lineTo(elX, elY);

        // SEGMENT 4: upper arm to shoulder
        pulsePath.lineTo(shX, shY);

        // SEGMENT 5: chest/torso to hip (long diagonal — the body)
        pulsePath.lineTo(hipX, hipY);

        // SEGMENT 6: thigh
        pulsePath.lineTo(knX, knY);

        // SEGMENT 7: shin to heel
        pulsePath.lineTo(footX, footY);

        // SEGMENT 8: toes
        pulsePath.lineTo(toeX, toeY);

        // Extra: short line back down to ground (toe tip)
        pulsePath.lineTo(toeX + unit * 0.15f, footY);

        // SEGMENT 9: flat lead-out to right edge
        pulsePath.lineTo(w * 0.88f, baseline);
        pulsePath.lineTo((float) w, baseline);

        // Measure total path length for draw-on animation
        PathMeasure pm = new PathMeasure(pulsePath, false);
        pathLength = pm.getLength();
    }

    private void buildShaders(int w, int h) {
        // Title gradient — vertical gold metallic
        float titleY = h * 0.76f;
        float titleSize = dp * 46f;
        titlePaint.setTextSize(titleSize);
        titlePaint.setShader(new LinearGradient(
                0, titleY - titleSize,
                0, titleY,
                new int[]{ 0xFFFFEE88, 0xFFFFCC00, 0xFFAA7700, 0xFFFFCC00, 0xFFFFEE88 },
                new float[]{ 0f, 0.2f, 0.5f, 0.8f, 1f },
                Shader.TileMode.CLAMP));

        subtitlePaint.setTextSize(dp * 12f);

        // Volumetric light: radial gradient from top-center downward
        volumePaint.setShader(new RadialGradient(
                w * 0.5f, 0,
                h * 0.65f,
                new int[]{ 0x0FFFCC00, 0x07FFCC00, 0x00000000 },
                new float[]{ 0f, 0.4f, 1f },
                Shader.TileMode.CLAMP));

        // Vignette: radial black from edges
        vignettePaint.setShader(new RadialGradient(
                w * 0.5f, h * 0.5f,
                Math.max(w, h) * 0.72f,
                new int[]{ 0x00000000, 0x00000000, 0xCC000000 },
                new float[]{ 0f, 0.55f, 1f },
                Shader.TileMode.CLAMP));
    }

    // ── Public setters for animation ──────────────────────────────────────────

    public float getDrawProgress() { return drawProgress; }
    public void setDrawProgress(float p) {
        drawProgress = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    public float getScanY() { return scanY; }
    public void setScanY(float y) { scanY = y; invalidate(); }

    public float getScanAlpha() { return scanAlpha; }
    public void setScanAlpha(float a) { scanAlpha = a; invalidate(); }

    public float getTextAlpha() { return textAlpha; }
    public void setTextAlpha(float a) { textAlpha = a; invalidate(); }

    public float getSubtitleAlpha() { return subtitleAlpha; }
    public void setSubtitleAlpha(float a) { subtitleAlpha = a; invalidate(); }

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas) {
        if (!geometryReady) return;
        int w = getWidth();
        int h = getHeight();
        float cx = w / 2f;

        // 1. Matte black background
        canvas.drawColor(BG_COLOR);

        // 2. Volumetric light cone from top
        canvas.drawRect(0, 0, w, h, volumePaint);

        // 3. Draw the pulse line up to drawProgress ───────────────────────────
        if (drawProgress > 0f && pathLength > 0f) {
            // Build a trimmed path for the "draw-on" effect via DashPathEffect
            float drawn = pathLength * drawProgress;
            float gap   = pathLength - drawn;
            if (gap < 0f) gap = 0f;

            DashPathEffect dpe = new DashPathEffect(new float[]{ drawn, gap + 1f }, 0);

            // Wide outer glow
            Paint pg = new Paint(glowWide);
            pg.setPathEffect(dpe);
            canvas.drawPath(pulsePath, pg);

            // Mid glow
            Paint pm = new Paint(glowMid);
            pm.setPathEffect(dpe);
            canvas.drawPath(pulsePath, pm);

            // Sharp base line
            Paint ps = new Paint(lineSharp);
            ps.setPathEffect(dpe);
            canvas.drawPath(pulsePath, ps);

            // Bright core
            Paint pc = new Paint(lineCore);
            pc.setPathEffect(dpe);
            canvas.drawPath(pulsePath, pc);

            // Leading dot (hot tip of drawing line)
            if (drawProgress < 0.99f) {
                PathMeasure pm2 = new PathMeasure(pulsePath, false);
                float[] pos = new float[2];
                pm2.getPosTan(drawn, pos, null);
                Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                dotPaint.setColor(0xFFFFFFDD);
                dotPaint.setMaskFilter(new BlurMaskFilter(dp * 12f, BlurMaskFilter.Blur.NORMAL));
                canvas.drawCircle(pos[0], pos[1], dp * 5f, dotPaint);
                dotPaint.setMaskFilter(null);
                dotPaint.setColor(0xFFFFFFFF);
                canvas.drawCircle(pos[0], pos[1], dp * 2.5f, dotPaint);
            }
        }

        // 4. Scan line ─────────────────────────────────────────────────────────
        if (scanAlpha > 0f) {
            scanPaint.setShader(new LinearGradient(
                    0, scanY, w, scanY,
                    new int[]{ 0x00FFCC00, 0x88FFCC00, 0xFFFFCC00, 0x88FFCC00, 0x00FFCC00 },
                    new float[]{ 0f, 0.25f, 0.5f, 0.75f, 1f },
                    Shader.TileMode.CLAMP));
            scanPaint.setAlpha((int)(scanAlpha * 255));
            canvas.drawLine(0, scanY, w, scanY, scanPaint);
        }

        // 5. "PUSHUP TIME" title ────────────────────────────────────────────────
        if (textAlpha > 0f) {
            titlePaint.setAlpha((int)(textAlpha * 255));
            float titleY = h * 0.76f;
            canvas.drawText("PUSHUP TIME", cx, titleY, titlePaint);

            // Thin gold divider line
            if (textAlpha > 0.3f) {
                float divAlpha = Math.min(1f, (textAlpha - 0.3f) / 0.7f);
                accentPaint.setAlpha((int)(divAlpha * 160));
                float divHW  = dp * 72f * divAlpha;
                float divY   = titleY + dp * 14f;
                canvas.drawLine(cx - divHW, divY, cx + divHW, divY, accentPaint);

                // Corner accent marks (top-left and top-right of title zone)
                float markLen = dp * 16f;
                float markX1  = cx - divHW - dp * 6f;
                float markX2  = cx + divHW + dp * 6f;
                cornerPaint.setAlpha((int)(divAlpha * 180));
                // Left mark
                canvas.drawLine(markX1, divY - dp * 2f, markX1 - markLen, divY - dp * 2f, cornerPaint);
                canvas.drawLine(markX1, divY - dp * 2f, markX1, divY - dp * 2f - markLen * 0.6f, cornerPaint);
                // Right mark
                canvas.drawLine(markX2, divY - dp * 2f, markX2 + markLen, divY - dp * 2f, cornerPaint);
                canvas.drawLine(markX2, divY - dp * 2f, markX2, divY - dp * 2f - markLen * 0.6f, cornerPaint);
            }
        }

        // 6. "ELITE PERFORMANCE" subtitle ───────────────────────────────────────
        if (subtitleAlpha > 0f) {
            subtitlePaint.setAlpha((int)(subtitleAlpha * 220));
            float subY = h * 0.76f + dp * 38f;
            canvas.drawText("ELITE  PERFORMANCE", cx, subY, subtitlePaint);
        }

        // 7. Corner frame marks (always visible, gold at 40% alpha) ─────────────
        float pad  = dp * 24f;
        float mark = dp * 28f;
        cornerPaint.setAlpha(100);
        // Top-left
        canvas.drawLine(pad, pad, pad + mark, pad, cornerPaint);
        canvas.drawLine(pad, pad, pad, pad + mark, cornerPaint);
        // Top-right
        canvas.drawLine(w - pad, pad, w - pad - mark, pad, cornerPaint);
        canvas.drawLine(w - pad, pad, w - pad, pad + mark, cornerPaint);
        // Bottom-left
        canvas.drawLine(pad, h - pad, pad + mark, h - pad, cornerPaint);
        canvas.drawLine(pad, h - pad, pad, h - pad - mark, cornerPaint);
        // Bottom-right
        canvas.drawLine(w - pad, h - pad, w - pad - mark, h - pad, cornerPaint);
        canvas.drawLine(w - pad, h - pad, w - pad, h - pad - mark, cornerPaint);

        // 8. Vignette overlay
        canvas.drawRect(0, 0, w, h, vignettePaint);
    }
}
