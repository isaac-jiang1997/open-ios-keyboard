package dev.openkeyboard.ioskeyboard;

import java.util.List;
import java.util.Locale;

/**
 * Pure hit-testing for keyboard keys. Fast taps often land in the visual gap
 * between keys or roll onto a neighbor on finger-up; this resolver fills gaps,
 * applies hysteresis so a pressed key is sticky, and classifies taps vs slides.
 */
final class KeyHitResolver {
    static final class KeyRef {
        final int id;
        final float left;
        final float top;
        final float right;
        final float bottom;
        final boolean stickyFunction;

        KeyRef(int id, float left, float top, float right, float bottom, boolean stickyFunction) {
            this.id = id;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.stickyFunction = stickyFunction;
        }

        boolean contains(float x, float y) {
            return x >= left && x < right && y >= top && y < bottom;
        }

        boolean containsInflated(float x, float y, float pad) {
            return x >= left - pad && x < right + pad && y >= top - pad && y < bottom + pad;
        }

        float distance(float x, float y) {
            float dx = 0f;
            if (x < left) {
                dx = left - x;
            } else if (x > right) {
                dx = x - right;
            }
            float dy = 0f;
            if (y < top) {
                dy = top - y;
            } else if (y > bottom) {
                dy = y - bottom;
            }
            return (float) Math.hypot(dx, dy);
        }

        float centerDistance(float x, float y) {
            return (float) Math.hypot(x - (left + right) * 0.5f, y - (top + bottom) * 0.5f);
        }

        float minSide() {
            return Math.min(Math.max(1f, right - left), Math.max(1f, bottom - top));
        }
    }

    private KeyHitResolver() {
    }

    static int hit(
            List<KeyRef> keys,
            float x,
            float y,
            int stickyId,
            float hysteresisPx,
            float snapPx
    ) {
        if (keys == null || keys.isEmpty()) {
            return -1;
        }
        if (stickyId >= 0) {
            KeyRef sticky = findById(keys, stickyId);
            if (sticky != null && sticky.containsInflated(x, y, hysteresisPx)) {
                return stickyId;
            }
        }
        for (int i = keys.size() - 1; i >= 0; i--) {
            KeyRef key = keys.get(i);
            if (key.contains(x, y)) {
                return key.id;
            }
        }
        int bestId = -1;
        float bestDistance = Float.MAX_VALUE;
        float bestCenter = Float.MAX_VALUE;
        for (int i = 0; i < keys.size(); i++) {
            KeyRef key = keys.get(i);
            float distance = key.distance(x, y);
            if (distance > snapPx) {
                continue;
            }
            float center = key.centerDistance(x, y);
            if (distance < bestDistance - 0.01f
                    || (Math.abs(distance - bestDistance) <= 0.01f && center < bestCenter)) {
                bestDistance = distance;
                bestCenter = center;
                bestId = key.id;
            }
        }
        return bestId;
    }

    static boolean isTap(float downX, float downY, float x, float y, float slopPx) {
        float dx = x - downX;
        float dy = y - downY;
        return dx * dx + dy * dy <= slopPx * slopPx;
    }

    /**
     * Thumbs land below the visual key. Shift the sample up unless the raw
     * point is already in the upper portion of a key.
     */
    static float correctY(List<KeyRef> keys, float x, float y, float offsetPx) {
        if (offsetPx <= 0f || keys == null || keys.isEmpty()) {
            return y;
        }
        for (int i = keys.size() - 1; i >= 0; i--) {
            KeyRef key = keys.get(i);
            if (!key.contains(x, y)) {
                continue;
            }
            float height = key.bottom - key.top;
            if (height > 1f && y <= key.top + height * 0.72f) {
                return y;
            }
            break;
        }
        return y - offsetPx;
    }

    /**
     * Fast two-thumb typing presses the next key before the previous finger
     * lifts. Commit the held letter/space immediately so finger-up roll cannot
     * rewrite it, and so the next key is not dropped.
     */
    static boolean shouldRolloverCommit(KeyAction heldAction) {
        return heldAction == KeyAction.CHARACTER || heldAction == KeyAction.SPACE;
    }

    static String applyLetterCase(String output, boolean uppercase) {
        if (output == null || output.length() != 1) {
            return output;
        }
        char c = output.charAt(0);
        if ((c < 'A' || c > 'Z') && (c < 'a' || c > 'z')) {
            return output;
        }
        return uppercase
                ? output.toUpperCase(Locale.ROOT)
                : output.toLowerCase(Locale.ROOT);
    }

    static boolean isStickyFunction(KeyAction action) {
        if (action == null) {
            return false;
        }
        switch (action) {
            case CHARACTER:
            case SPACE:
            case SHIFT:
                return false;
            default:
                return true;
        }
    }

    /**
     * Decide which key a pointer-up should fire.
     *
     * @return the up key, the down key, or {@code null} to fire nothing
     */
    static Integer resolveRelease(int downId, int upId, boolean isTap, boolean downIsStickyFunction) {
        if (downId < 0 && upId < 0) {
            return null;
        }
        if (isTap) {
            return downId >= 0 ? downId : upId;
        }
        if (downIsStickyFunction && upId != downId) {
            return null;
        }
        if (upId >= 0) {
            return upId;
        }
        return downId >= 0 ? downId : null;
    }

    private static KeyRef findById(List<KeyRef> keys, int id) {
        for (int i = 0; i < keys.size(); i++) {
            KeyRef key = keys.get(i);
            if (key.id == id) {
                return key;
            }
        }
        return null;
    }
}
