package dev.openkeyboard.applelayout;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

public final class IosAccessoryBarView extends View {
    private static final float IOS_ACCESSORY_HEIGHT = 112f;

    interface Listener {
        void onGlobe();
        void onVoiceInput();
    }

    private final Drawable globeIcon;
    private final Drawable micIcon;
    private final Rect iconBounds = new Rect();
    private final RectF globeBounds = new RectF();
    private final RectF micBounds = new RectF();
    private Listener listener;

    public IosAccessoryBarView(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(IosKeyboardTheme.KEYBOARD_BG);
        setFocusable(false);
        setFocusableInTouchMode(false);
        globeIcon = context.getDrawable(R.drawable.ic_keyboard_globe_ios_style);
        micIcon = context.getDrawable(R.drawable.ic_keyboard_mic_ios_style);
        setContentDescription("Switch keyboard / Dictation");
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int desiredHeight = Math.max(dp(46), Math.round(width * IOS_ACCESSORY_HEIGHT / IosKeyboardTheme.REFERENCE_WIDTH));
        setMeasuredDimension(width, resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float scale = IosKeyboardTheme.scale(w);
        float hitWidth = Math.max(dp(56), 230f * scale);
        globeBounds.set(0, 0, hitWidth, h);
        micBounds.set(w - hitWidth, 0, w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(IosKeyboardTheme.KEYBOARD_BG);
        float scale = IosKeyboardTheme.scale(getWidth());
        int centerY = Math.round(getHeight() * 0.48f);
        int iconSize = dp(28);

        drawIcon(canvas, globeIcon, Math.round(126f * scale), centerY, iconSize);
        drawIcon(canvas, micIcon, Math.round(getWidth() - 129f * scale), centerY, iconSize);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_UP || listener == null) {
            return true;
        }
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        float x = event.getX();
        float y = event.getY();
        if (globeBounds.contains(x, y)) {
            listener.onGlobe();
        } else if (micBounds.contains(x, y)) {
            listener.onVoiceInput();
        }
        return true;
    }

    private void drawIcon(Canvas canvas, Drawable drawable, int centerX, int centerY, int size) {
        if (drawable == null) {
            return;
        }
        int half = size / 2;
        iconBounds.set(centerX - half, centerY - half, centerX + half, centerY + half);
        drawable.setBounds(iconBounds);
        drawable.draw(canvas);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
