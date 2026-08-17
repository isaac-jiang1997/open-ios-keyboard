package dev.openkeyboard.applelayout;

import android.graphics.Color;

final class IosKeyboardTheme {
    static final float REFERENCE_WIDTH = 1320f;

    static final int KEYBOARD_BG = Color.rgb(209, 213, 219);
    static final int ACCESSORY_BG = Color.rgb(246, 247, 249);
    static final int KEY_BG = Color.WHITE;
    static final int SPECIAL_KEY_BG = Color.rgb(172, 177, 185);

    /** Press fill for {@code CHARACTER} keys: matches the default function key gray. */
    static final int CHARACTER_KEY_PRESSED_BG = SPECIAL_KEY_BG;
    /** Press fill for non-{@code RETURN} function keys: matches the default character key white. */
    static final int FUNCTION_KEY_PRESSED_BG = KEY_BG;
    /** Press fill for the {@code RETURN} action key: a deeper blue than {@link #ACTION_BLUE}. */
    static final int ACTION_KEY_PRESSED_BG = Color.rgb(0, 90, 195);

    /** Shadow color for {@code CHARACTER} keys: ≈30% black ({@code 0x4D000000}). */
    static final int CHARACTER_KEY_SHADOW = 0x4D000000;
    /** Shadow color for function keys: ≈25% black ({@code 0x40000000}). */
    static final int FUNCTION_KEY_SHADOW = 0x40000000;
    /** Vertical offset (in dp) for the alpha-based key shadow. */
    static final float KEY_SHADOW_OFFSET_DP = 1f;

    // ===== Key Popup geometry =====
    // Single source of truth for KeyPopupView. Do not inline these values elsewhere.
    /** Popup total height as a multiple of the anchored key's height. */
    static final float POPUP_HEIGHT_MULTIPLIER = 1.55f;
    /** Horizontal expansion (in dp) on each side beyond the anchored key's width. */
    static final float POPUP_SIDE_EXPAND_DP = 6f;
    /** Vertical tuck (in dp) by which the popup's bottom overlaps the key top, hiding the seam. */
    static final float POPUP_BOTTOM_TUCK_DP = 2f;
    /** Top-corner radius (in dp) of the popup bubble; must be greater than the bottom radius. */
    static final float POPUP_TOP_RADIUS_DP = 14f;
    /** Bottom-corner radius (in dp) of the popup bubble. */
    static final float POPUP_BOTTOM_RADIUS_DP = 6f;
    /** Popup label text size as a fraction of the popup bubble height. */
    static final float POPUP_TEXT_SCALE = 0.85f;

    static final int ACTION_BLUE = Color.rgb(0, 122, 255);
    static final int TEXT_PRIMARY = Color.BLACK;
    static final int TEXT_SECONDARY = Color.rgb(81, 86, 96);

    private IosKeyboardTheme() {
    }

    static float scale(int width) {
        return width > 0 ? width / REFERENCE_WIDTH : 1f;
    }
}
