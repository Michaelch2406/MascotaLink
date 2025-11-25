package com.mjc.mascotalink.ui.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.mjc.mascotalink.R;

/**
 * Overlay that dims the screen, draws an oval cutout, and switches color based on readiness.
 */
public class OverlayView extends View {

    private final Paint dimPaint = new Paint();
    private final Paint clearPaint = new Paint();
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shapePath = new Path();
    private final RectF ovalRect = new RectF();
    private final RectF progressRect = new RectF();
    private int progress = 0;

    public OverlayView(Context context) {
        super(context);
        init(context);
    }

    public OverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public OverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        dimPaint.setColor(Color.parseColor("#80000000"));

        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        clearPaint.setAntiAlias(true);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dpToPx(context, 3));
        strokePaint.setColor(ContextCompat.getColor(context, R.color.red_error));

        progressBgPaint.setStyle(Paint.Style.STROKE);
        progressBgPaint.setStrokeWidth(dpToPx(context, 6));
        progressBgPaint.setColor(Color.parseColor("#50FFFFFF"));

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStrokeWidth(dpToPx(context, 6));
        progressPaint.setColor(ContextCompat.getColor(context, R.color.green_accent));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float horizontalPadding = w * 0.15f;
        float verticalPadding = h * 0.22f;
        ovalRect.set(horizontalPadding, verticalPadding, w - horizontalPadding, h - verticalPadding);
        float extra = dpToPx(getContext(), 10);
        progressRect.set(ovalRect.left - extra, ovalRect.top - extra, ovalRect.right + extra, ovalRect.bottom + extra);
        shapePath.reset();
        shapePath.addOval(ovalRect, Path.Direction.CW);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int checkpoint = canvas.saveLayer(null, null);
        canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);
        canvas.drawPath(shapePath, clearPaint);
        canvas.drawOval(ovalRect, strokePaint);
        canvas.drawOval(progressRect, progressBgPaint);
        float sweep = 360f * (progress / 100f);
        canvas.drawArc(progressRect, -90, sweep, false, progressPaint);
        canvas.restoreToCount(checkpoint);
    }

    public void setReady(boolean ready) {
        int colorRes = ready ? R.color.green_success : R.color.red_error;
        strokePaint.setColor(ContextCompat.getColor(getContext(), colorRes));
        postInvalidateOnAnimation();
    }

    public void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(100, progress));
        postInvalidateOnAnimation();
    }

    private float dpToPx(Context context, float dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }
}
