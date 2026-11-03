package dev.umbra.qr.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import dev.umbra.qr.R;

/**
 * Camera overlay shaped like a gothic lancet window: everything outside the arch is veiled,
 * the arch is outlined with a gilded double rule, and a crimson line sweeps through it while
 * scanning.
 */
public final class GothicViewfinderView extends View {

    /** Height of an equilateral (lancet) arch relative to its width: sin(60°). */
    private static final float ARCH_RISE = 0.866f;
    /** Height of the straight jambs below the arch, relative to its width. */
    private static final float JAMB_RATIO = 0.58f;
    private static final long SWEEP_DURATION_MS = 2600;

    private final float dp;
    private final int goldColor;
    private final int crimsonColor;
    private final int veilColor;

    private final Paint outerRule = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerRule = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ornament = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint jewel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweep = new Paint();

    private final Path arch = new Path();
    private final Path innerArch = new Path();
    private final RectF bounds = new RectF();
    private float springY;

    private final ValueAnimator sweepAnimator = ValueAnimator.ofFloat(0.04f, 0.96f);
    private float sweepProgress = 0.5f;
    private boolean scanning = true;

    public GothicViewfinderView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        goldColor = ContextCompat.getColor(context, R.color.tarnished_gold);
        crimsonColor = ContextCompat.getColor(context, R.color.ember_crimson);
        veilColor = ContextCompat.getColor(context, R.color.veil);

        outerRule.setStyle(Paint.Style.STROKE);
        outerRule.setStrokeWidth(2.5f * dp);
        outerRule.setStrokeJoin(Paint.Join.MITER);
        outerRule.setColor(goldColor);

        innerRule.setStyle(Paint.Style.STROKE);
        innerRule.setStrokeWidth(1f * dp);
        innerRule.setColor(ContextCompat.getColor(context, R.color.tarnished_gold_dim));

        ornament.setStyle(Paint.Style.STROKE);
        ornament.setStrokeWidth(1.5f * dp);
        ornament.setColor(goldColor);

        jewel.setStyle(Paint.Style.FILL);
        jewel.setColor(ContextCompat.getColor(context, R.color.blood_crimson));

        sweepAnimator.setDuration(SWEEP_DURATION_MS);
        sweepAnimator.setRepeatMode(ValueAnimator.REVERSE);
        sweepAnimator.setRepeatCount(ValueAnimator.INFINITE);
        sweepAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        sweepAnimator.addUpdateListener(
                animation -> {
                    sweepProgress = (float) animation.getAnimatedValue();
                    invalidate();
                });
    }

    /** Shows the sweeping line while scanning; turns the arch crimson once a code is found. */
    public void setScanning(boolean scanning) {
        if (this.scanning == scanning) {
            return;
        }
        this.scanning = scanning;
        outerRule.setColor(scanning ? goldColor : crimsonColor);
        updateAnimation();
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        sweepAnimator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateAnimation();
    }

    private void updateAnimation() {
        // Respect the system "remove animations" setting: keep a static line instead.
        boolean animate =
                scanning
                        && isAttachedToWindow()
                        && isShown()
                        && ValueAnimator.areAnimatorsEnabled();
        if (animate && !sweepAnimator.isStarted()) {
            sweepAnimator.start();
        } else if (!animate && sweepAnimator.isStarted()) {
            sweepAnimator.cancel();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float width = Math.min(w * 0.68f, h * 0.52f / (ARCH_RISE + JAMB_RATIO));
        float rise = width * ARCH_RISE;
        float total = rise + width * JAMB_RATIO;
        float left = (w - width) / 2f;
        float top = h * 0.47f - total / 2f;

        bounds.set(left, top, left + width, top + total);
        springY = top + rise;
        buildArch(arch, bounds.left, bounds.right, springY, bounds.bottom);
        float inset = 6f * dp;
        buildArch(innerArch, bounds.left + inset, bounds.right - inset, springY, bounds.bottom - inset);

        float glow = 28f * dp;
        sweep.setShader(
                new LinearGradient(
                        0, 0, 0, glow,
                        new int[] {0x00000000, withAlpha(crimsonColor, 0x55), crimsonColor, withAlpha(crimsonColor, 0x55), 0x00000000},
                        new float[] {0f, 0.4f, 0.5f, 0.6f, 1f},
                        Shader.TileMode.CLAMP));
    }

    /**
     * Equilateral pointed arch: each side is an arc of radius {@code width} centred on the
     * opposite springing point, meeting at the apex.
     */
    private static void buildArch(Path path, float left, float right, float springY, float bottom) {
        float width = right - left;
        path.reset();
        path.moveTo(left, bottom);
        path.lineTo(left, springY);
        path.arcTo(right - width, springY - width, right + width, springY + width, 180f, 60f, false);
        path.arcTo(left - width, springY - width, left + width, springY + width, 300f, 60f, false);
        path.lineTo(right, bottom);
        path.close();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bounds.isEmpty()) {
            return;
        }

        canvas.save();
        canvas.clipOutPath(arch);
        canvas.drawColor(veilColor);
        canvas.restore();

        if (scanning) {
            float glow = 28f * dp;
            float y = bounds.top + sweepProgress * bounds.height();
            canvas.save();
            canvas.clipPath(innerArch);
            canvas.translate(0, y - glow / 2f);
            canvas.drawRect(bounds.left, 0, bounds.right, glow, sweep);
            canvas.restore();
        }

        canvas.drawPath(arch, outerRule);
        canvas.drawPath(innerArch, innerRule);
        drawFinial(canvas, bounds.centerX(), bounds.top);
        drawCapital(canvas, bounds.left, springY, -1);
        drawCapital(canvas, bounds.right, springY, 1);
        drawQuatrefoil(canvas, bounds.left, bounds.bottom);
        drawQuatrefoil(canvas, bounds.right, bounds.bottom);
    }

    /** Cross-shaped finial rising from the apex, crowned by a crimson jewel. */
    private void drawFinial(Canvas canvas, float x, float apexY) {
        float height = 22f * dp;
        float crossY = apexY - height * 0.62f;
        canvas.drawLine(x, apexY, x, apexY - height, ornament);
        canvas.drawLine(x - 7f * dp, crossY, x + 7f * dp, crossY, ornament);
        canvas.drawCircle(x, apexY - height - 3f * dp, 3f * dp, jewel);
    }

    /** Short moulding marking where the arch springs from the jamb. */
    private void drawCapital(Canvas canvas, float x, float y, int direction) {
        float reach = 10f * dp * direction;
        canvas.drawLine(x, y, x + reach, y, ornament);
        canvas.drawLine(x, y + 4f * dp, x + reach * 0.6f, y + 4f * dp, ornament);
    }

    /** Four-lobed gothic tracery with a crimson centre. */
    private void drawQuatrefoil(Canvas canvas, float x, float y) {
        float lobe = 4f * dp;
        canvas.drawCircle(x - lobe, y, lobe, ornament);
        canvas.drawCircle(x + lobe, y, lobe, ornament);
        canvas.drawCircle(x, y - lobe, lobe, ornament);
        canvas.drawCircle(x, y + lobe, lobe, ornament);
        canvas.drawCircle(x, y, 2f * dp, jewel);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
