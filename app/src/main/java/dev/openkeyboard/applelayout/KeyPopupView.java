package dev.openkeyboard.applelayout;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Transparent overlay view that renders the iOS-style key preview "bubble" when a
 * {@code CHARACTER} key is pressed.
 *
 * <p>The view sits on top of the keyboard column inside a {@code FrameLayout} and must
 * never consume touch events: the keyboard itself owns the touch stream. To stay
 * touch-transparent, the view is configured with {@code clickable=false},
 * {@code focusable=false}, and a {@code null} background, and it does not override
 * {@code onTouchEvent} or {@code dispatchTouchEvent}.
 *
 * <p>The bubble has the iOS-style "上宽下窄、上圆下方" silhouette: the top corners use
 * {@link IosKeyboardTheme#POPUP_TOP_RADIUS_DP} (larger) and the bottom corners use
 * {@link IosKeyboardTheme#POPUP_BOTTOM_RADIUS_DP} (smaller). It is drawn as a single
 * {@link Path} with eight corner radii, filled with {@link IosKeyboardTheme#KEY_BG},
 * with a soft alpha-based shadow offset slightly downward to match the key shadows
 * rendered by {@code IosKeyboardView}.
 */
public final class KeyPopupView extends View {

    /** Whether the bubble should be rendered. When {@code false}, {@link #onDraw} is a no-op. */
    private boolean visible;

    /** Label drawn inside the bubble (e.g. "A", "ABC"). {@code null} when hidden. */
    private String label;

    /**
     * The pressed key's bounds in the keyboard's local coordinate space, copied
     * defensively so callers may reuse their own {@link RectF} instances.
     */
    private final RectF anchorInKeyboard = new RectF();

    /**
     * The keyboard view's top edge expressed in this overlay's coordinate space.
     * Used by {@link #onDraw} to translate the anchor rect into popup-view
     * coordinates without holding a reference to the keyboard view.
     */
    private int keyboardTopInPopupCoords;

    // Reusable draw resources. Allocated once to keep onDraw allocation-free.
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bubblePath = new Path();
    private final RectF popupRect = new RectF();
    /** [topL_x, topL_y, topR_x, topR_y, botR_x, botR_y, botL_x, botL_y] in px. */
    private final float[] cornerRadii = new float[8];

    public KeyPopupView(Context context) {
        super(context);
        // Render via onDraw rather than skip drawing entirely.
        setWillNotDraw(false);
        // Stay transparent to touches; the keyboard view owns the touch stream.
        setClickable(false);
        setFocusable(false);
        setBackground(null);

        bubblePaint.setStyle(Paint.Style.FILL);
        shadowPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * Report zero intrinsic height so that the parent FrameLayout's size is
     * determined entirely by the keyboard column, not by this overlay.
     * FrameLayout will re-measure MATCH_PARENT children with the final height
     * in its second pass, so the overlay still covers the full keyboard area.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        if (heightMode == MeasureSpec.EXACTLY) {
            // Second pass from FrameLayout: accept the assigned height.
            setMeasuredDimension(width, MeasureSpec.getSize(heightMeasureSpec));
        } else {
            // First pass: report zero so we don't inflate the parent.
            setMeasuredDimension(width, 0);
        }
    }

    /**
     * Show the bubble for the given key.
     *
     * @param label the text to render inside the bubble (typically the key's label)
     * @param anchorInKeyboard the pressed key's bounds in keyboard-local coordinates;
     *     copied defensively so the caller may mutate or reuse the original
     * @param keyboardTopInPopupCoords the keyboard view's top edge in this view's
     *     coordinate space (used to translate {@code anchorInKeyboard} into
     *     popup-view coordinates at draw time)
     */
    public void show(String label, RectF anchorInKeyboard, int keyboardTopInPopupCoords) {
        this.label = label;
        this.anchorInKeyboard.set(anchorInKeyboard);
        this.keyboardTopInPopupCoords = keyboardTopInPopupCoords;
        this.visible = true;
        invalidate();
    }

    /**
     * Update the bubble while it is already visible. Behaves identically to
     * {@link #show} but is named separately to make call sites self-documenting
     * (PRESS vs MOVE_TO transitions).
     */
    public void update(String label, RectF anchorInKeyboard, int keyboardTopInPopupCoords) {
        this.label = label;
        this.anchorInKeyboard.set(anchorInKeyboard);
        this.keyboardTopInPopupCoords = keyboardTopInPopupCoords;
        this.visible = true;
        invalidate();
    }

    /** Hide the bubble. Idempotent. */
    public void hide() {
        this.visible = false;
        this.label = null;
        invalidate();
    }

    /** @return whether the bubble is currently visible. */
    public boolean isVisible() {
        return visible;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!visible || label == null) {
            return;
        }

        final float density = getResources().getDisplayMetrics().density;

        // Anchor rect translated into this view's coordinate space.
        final float anchorLeft = anchorInKeyboard.left;
        final float anchorRight = anchorInKeyboard.right;
        final float anchorTop = anchorInKeyboard.top + keyboardTopInPopupCoords;
        final float anchorCenterX = (anchorLeft + anchorRight) * 0.5f;
        final float anchorWidth = anchorRight - anchorLeft;
        final float anchorHeight = anchorInKeyboard.height();

        // Geometry constants (dp -> px).
        final float sideExpandPx = IosKeyboardTheme.POPUP_SIDE_EXPAND_DP * density;
        final float bottomTuckPx = IosKeyboardTheme.POPUP_BOTTOM_TUCK_DP * density;
        final float topRadiusPx = IosKeyboardTheme.POPUP_TOP_RADIUS_DP * density;
        final float bottomRadiusPx = IosKeyboardTheme.POPUP_BOTTOM_RADIUS_DP * density;
        // Match IosKeyboardView's per-key shadow offset rule: never less than 1px.
        final float shadowOffsetPx = Math.max(1f, IosKeyboardTheme.KEY_SHADOW_OFFSET_DP * density);

        // Popup rect: width = anchor.width + 2 * side-expand, height = anchor.height * multiplier,
        // bottom tucked just below the anchor's top edge so the bubble base hides the seam,
        // horizontally centered on the anchor.
        final float popupWidth = anchorWidth + 2f * sideExpandPx;
        final float popupHeight = anchorHeight * IosKeyboardTheme.POPUP_HEIGHT_MULTIPLIER;
        final float popupBottom = anchorTop + bottomTuckPx;
        final float popupTop = popupBottom - popupHeight;
        final float popupLeft = anchorCenterX - popupWidth * 0.5f;
        final float popupRight = anchorCenterX + popupWidth * 0.5f;

        popupRect.set(popupLeft, popupTop, popupRight, popupBottom);

        // Asymmetric corner radii: top corners larger than bottom corners,
        // producing the iOS "上宽下窄、上圆下方" silhouette via a single rounded rect.
        cornerRadii[0] = topRadiusPx;    // top-left x
        cornerRadii[1] = topRadiusPx;    // top-left y
        cornerRadii[2] = topRadiusPx;    // top-right x
        cornerRadii[3] = topRadiusPx;    // top-right y
        cornerRadii[4] = bottomRadiusPx; // bottom-right x
        cornerRadii[5] = bottomRadiusPx; // bottom-right y
        cornerRadii[6] = bottomRadiusPx; // bottom-left x
        cornerRadii[7] = bottomRadiusPx; // bottom-left y

        bubblePath.rewind();
        bubblePath.addRoundRect(popupRect, cornerRadii, Path.Direction.CW);

        // Soft shadow: the same path translated downward by shadowOffsetPx, filled
        // with the alpha-based character-key shadow color so it visually matches the
        // shadows drawn by IosKeyboardView for keys.
        shadowPaint.setColor(IosKeyboardTheme.CHARACTER_KEY_SHADOW);
        canvas.save();
        canvas.translate(0f, shadowOffsetPx);
        canvas.drawPath(bubblePath, shadowPaint);
        canvas.restore();

        // Bubble fill.
        bubblePaint.setColor(IosKeyboardTheme.KEY_BG);
        canvas.drawPath(bubblePath, bubblePaint);

        // Label: centered horizontally; vertically biased toward the upper half of the
        // bubble (the lower portion is the narrow neck that tucks into the key, so the
        // text reads better when it sits above the geometric center).
        textPaint.setColor(IosKeyboardTheme.TEXT_PRIMARY);
        textPaint.setTextSize(anchorHeight * IosKeyboardTheme.POPUP_TEXT_SCALE);
        final Paint.FontMetrics fm = textPaint.getFontMetrics();
        // 0.40 places the text center at 40% from the top of the bubble.
        final float textCenterY = popupRect.top + popupRect.height() * 0.40f;
        final float baseline = textCenterY - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(label, popupRect.centerX(), baseline, textPaint);
    }
}
