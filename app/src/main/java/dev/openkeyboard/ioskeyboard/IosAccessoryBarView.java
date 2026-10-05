package dev.openkeyboard.ioskeyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

public final class IosAccessoryBarView extends View {
    private static final float IOS_ACCESSORY_HEIGHT = 112f;

    interface Listener {
        void onGlobe();

        void onGlobeLongPress();
    }

    private static final long GLOBE_LONG_PRESS_MS = 420;

    private final Drawable globeIcon;
    private final Rect iconBounds = new Rect();
    private final RectF globeBounds = new RectF();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Listener listener;
    private boolean pointerDownInGlobe;
    private boolean longPressFired;
    private final Runnable longPress = () -> {
        if (!pointerDownInGlobe || listener == null) {
            return;
        }
        longPressFired = true;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        listener.onGlobeLongPress();
    };

    public IosAccessoryBarView(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(IosKeyboardTheme.KEYBOARD_BG);
        setFocusable(false);
        setFocusableInTouchMode(false);
        globeIcon = context.getDrawable(R.drawable.ic_keyboard_globe_ios_style);
        setContentDescription("Switch keyboard");
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
        float iconCenterX = 126f * scale;
        float hitWidth = Math.max(dp(48), 96f * scale);
        globeBounds.set(Math.max(0f, iconCenterX - hitWidth / 2f), 0, iconCenterX + hitWidth / 2f, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(IosKeyboardTheme.KEYBOARD_BG);
        float scale = IosKeyboardTheme.scale(getWidth());
        int centerY = Math.round(getHeight() * 0.48f);
        int iconSize = dp(28);

        drawIcon(canvas, globeIcon, Math.round(126f * scale), centerY, iconSize);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pointerDownInGlobe = globeBounds.contains(x, y);
                longPressFired = false;
                if (pointerDownInGlobe) {
                    handler.postDelayed(longPress, GLOBE_LONG_PRESS_MS);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pointerDownInGlobe && !globeBounds.contains(x, y)) {
                    pointerDownInGlobe = false;
                    handler.removeCallbacks(longPress);
                }
                return true;
            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longPress);
                if (pointerDownInGlobe && !longPressFired && listener != null && globeBounds.contains(x, y)) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    listener.onGlobe();
                }
                pointerDownInGlobe = false;
                longPressFired = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPress);
                pointerDownInGlobe = false;
                longPressFired = false;
                return true;
            default:
                return true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(longPress);
        pointerDownInGlobe = false;
        longPressFired = false;
        super.onDetachedFromWindow();
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
