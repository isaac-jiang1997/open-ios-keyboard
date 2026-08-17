package dev.openkeyboard.applelayout;

import android.graphics.RectF;

final class KeyboardKey {
    final String label;
    final String output;
    final KeyAction action;
    final float widthUnits;
    final RectF bounds = new RectF();

    KeyboardKey(String label, String output, KeyAction action, float widthUnits) {
        this.label = label;
        this.output = output;
        this.action = action;
        this.widthUnits = widthUnits;
    }

    boolean hit(float x, float y) {
        return bounds.contains(x, y);
    }
}
