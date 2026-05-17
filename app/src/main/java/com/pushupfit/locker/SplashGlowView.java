package com.pushupfit.locker;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * SplashGlowView — A lightweight custom View that renders:
 *
 *  1. A soft neon-blue radial glow behind the glass card (ambient bloom)
 *  2. A frosted-glass rounded rectangle (glassmorphism card)
 *  3. A neon-blue border with blur/soft-glow edges
 *  4. "PUSHUP TIME" text with a metallic silver linear gradient
 *  5. A soft divider line and tagline below the title
 *
 * The push-up silhouette itself is placed with an ImageView above this
 * view in the layout — this view handles all the paint effects.
 */
public class SplashGlowView extends View {

    // ── Paints ──────────────────────────────────────────────────────
    private final Paint ambientGlowPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassFillPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderGlowPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderSharpPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint         = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint taglinePaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ── Card geometry (set in onSizeChanged) ─────────────────────────
    private RectF cardRect    = new RectF();
    private float cardRadius  = 0f;

    // ── Corner radius ────────────────────────────────────────────────
    private static final float CORNER_DP = 32f;

    public SplashGlowView(Context context) {
        super(context);
        init();
    }

    public SplashGlowView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        init();
    }

    public SplashGlowView(Context ctx, AttributeSet attrs, int defStyle) {
        super(ctx, attrs, defStyle);
        init();
    }

    private void init() {
        // Allow setLayerType for hardware-accelerated blur
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);

        float density = getResources().getDisplayMetrics().density;
        cardRadius    = CORNER_DP * density;

        // Card occupies the whole view (outer padding handled by XML layout margins)
        cardRect.set(0, 0, w, h);

        // ── Ambient radial glow behind card ──────────────────────────
        RadialGradient ambientGrad = new RadialGradient(
                w / 2f, h / 2f,
                Math.max(w, h) * 0.72f,
                new int[]{ 0x4A1A6FFF, 0x2A1A6FFF, 0x00000000 },
                new float[]{ 0f, 0.45f, 1f },
                Shader.TileMode.CLAMP);
        ambientGlowPaint.setShader(ambientGrad);

        // ── Glass fill — semi-transparent white ──────────────────────
        glassFillPaint.setColor(0x1AFFFFFF);   // ~10% white

        // ── Border soft glow (blurred) ───────────────────────────────
        borderGlowPaint.setColor(0xCC1A8EFF);
        borderGlowPaint.setStyle(Paint.Style.STROKE);
        borderGlowPaint.setStrokeWidth(10f * density);
        borderGlowPaint.setMaskFilter(new BlurMaskFilter(24f * density, BlurMaskFilter.Blur.NORMAL));

        // ── Border sharp inner line ───────────────────────────────────
        borderSharpPaint.setColor(0x994FC3FF);  // 60% neon blue
        borderSharpPaint.setStyle(Paint.Style.STROKE);
        borderSharpPaint.setStrokeWidth(1.5f * density);

        // ── Title "PUSHUP TIME" — metallic silver LinearGradient ─────
        titlePaint.setTextSize(42f * density);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setFakeBoldText(true);
        titlePaint.setLetterSpacing(0.18f);

        // ── Tagline ──────────────────────────────────────────────────
        taglinePaint.setTextSize(13f * density);
        taglinePaint.setTextAlign(Paint.Align.CENTER);
        taglinePaint.setColor(0xCC4FC3FF);   // neon blue 80%
        taglinePaint.setLetterSpacing(0.22f);

        // ── Divider ──────────────────────────────────────────────────
        dividerPaint.setColor(0x554FC3FF);
        dividerPaint.setStrokeWidth(1f * density);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float density = getResources().getDisplayMetrics().density;

        // 1 ── Ambient glow splash
        canvas.drawRect(0, 0, w, h, ambientGlowPaint);

        // 2 ── Frosted glass card fill
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, glassFillPaint);

        // 3 ── Neon border glow (blurred)
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, borderGlowPaint);

        // 4 ── Sharp border line
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, borderSharpPaint);

        // 5 ── Metallic "PUSHUP TIME" text
        float titleY = h * 0.72f;

        // Build metallic silver gradient each draw (width-dependent)
        LinearGradient metalGrad = new LinearGradient(
                0, titleY - 50f * density,
                0, titleY,
                new int[]{ 0xFFFFFFFF, 0xFFD0D4E8, 0xFFA8AEC8, 0xFFD0D4E8, 0xFFFFFFFF },
                new float[]  { 0f, 0.25f, 0.5f, 0.75f, 1f },
                Shader.TileMode.CLAMP);
        titlePaint.setShader(metalGrad);
        canvas.drawText("PUSHUP TIME", cx, titleY, titlePaint);

        // 6 ── Thin divider line
        float divY = titleY + 18f * density;
        float divHalfW = 80f * density;
        canvas.drawLine(cx - divHalfW, divY, cx + divHalfW, divY, dividerPaint);

        // 7 ── Tagline
        canvas.drawText("EARN YOUR SCREEN TIME", cx, divY + 26f * density, taglinePaint);
    }
}
