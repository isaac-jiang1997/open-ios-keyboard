package dev.openkeyboard.applelayout;

import android.graphics.RectF;

/**
 * Pure-logic state machine driving the iOS-style key popup overlay.
 *
 * <p>The controller is intentionally framework-free: it consumes touch
 * events translated into semantic calls (press / move / release / cancel /
 * dismiss) and emits {@link Command} values describing what the popup view
 * should do. State transitions are defined by the decision table in
 * {@code design.md} (Components and Interfaces &rarr; KeyPopupController).
 *
 * <p>The controller tracks the popup's {@link State} and the currently
 * displayed label. Each public {@code onXxx} method computes the next
 * state and returns the {@link Command} that the view layer should apply.
 * Same-label re-entry while {@code SHOWING} collapses to {@code NOOP} so
 * the view does not redraw on every {@code ACTION_MOVE} that stays inside
 * one key.
 */
final class KeyPopupController {

    /** Visibility state of the popup as tracked by the controller. */
    enum State {
        HIDDEN,
        SHOWING
    }

    /**
     * Immutable description of a single popup-view operation produced by a
     * state transition. Constructed via the static factories below so that
     * the {@link #anchor} field is always a defensive copy of caller input.
     */
    static final class Command {

        /** Discriminator for the four kinds of popup operations. */
        enum Kind {
            NOOP,
            SHOW,
            UPDATE,
            HIDE
        }

        final Kind kind;
        final String label;
        final RectF anchor;

        private Command(Kind kind, String label, RectF anchor) {
            this.kind = kind;
            this.label = label;
            this.anchor = anchor;
        }

        static Command noop() {
            return new Command(Kind.NOOP, null, null);
        }

        static Command show(String label, RectF anchor) {
            return new Command(Kind.SHOW, label, new RectF(anchor));
        }

        static Command update(String label, RectF anchor) {
            return new Command(Kind.UPDATE, label, new RectF(anchor));
        }

        static Command hide() {
            return new Command(Kind.HIDE, null, null);
        }
    }

    private State state = State.HIDDEN;
    private String currentLabel = null;

    /**
     * Returns true when the given key in the given mode is eligible to show
     * a character popup: only character keys in LETTERS mode qualify.
     * iOS does not show popups for numbers, symbols, or emoji keys.
     */
    private boolean isCharacterKey(KeyboardKey key, KeyboardMode mode) {
        return key.action == KeyAction.CHARACTER && mode == KeyboardMode.LETTERS;
    }

    /**
     * Pressing a key. Behaves the same as {@link #onMoveTo}: a character
     * key (in non-EMOJI mode) shows or updates the popup; any other key
     * hides the popup if currently visible.
     */
    Command onPress(KeyboardKey key, KeyboardMode mode) {
        return onCharacterTouch(key, mode);
    }

    /**
     * The active pointer moved onto {@code key}. Same decision rules as
     * {@link #onPress}: character keys drive SHOW/UPDATE/NOOP, non-character
     * keys drive HIDE/NOOP.
     */
    Command onMoveTo(KeyboardKey key, KeyboardMode mode) {
        return onCharacterTouch(key, mode);
    }

    /**
     * Pointer moved off the keyboard or onto an empty area (no key under
     * the pointer). The popup must hide if it was showing; otherwise no-op.
     */
    Command onMoveOut() {
        return hideIfShowing();
    }

    /** Pointer released. Same semantics as {@link #onMoveOut}. */
    Command onRelease() {
        return hideIfShowing();
    }

    /** Touch stream cancelled by the system. Same semantics as {@link #onMoveOut}. */
    Command onCancel() {
        return hideIfShowing();
    }

    /**
     * External dismiss request (e.g. lifecycle teardown via
     * {@code onFinishInputView} / {@code onWindowHidden}). Same semantics as
     * {@link #onMoveOut}.
     */
    Command onDismiss() {
        return hideIfShowing();
    }

    /**
     * Shared decision logic for {@link #onPress} and {@link #onMoveTo}: a
     * character key shows/updates the popup; anything else hides it.
     */
    private Command onCharacterTouch(KeyboardKey key, KeyboardMode mode) {
        if (!isCharacterKey(key, mode)) {
            return hideIfShowing();
        }
        if (state == State.SHOWING && key.label.equals(currentLabel)) {
            return Command.noop();
        }
        if (state == State.HIDDEN) {
            state = State.SHOWING;
            currentLabel = key.label;
            return Command.show(key.label, key.bounds);
        }
        // SHOWING with a different label.
        currentLabel = key.label;
        return Command.update(key.label, key.bounds);
    }

    /** HIDE if currently SHOWING; NOOP otherwise. Resets {@link #currentLabel}. */
    private Command hideIfShowing() {
        if (state == State.HIDDEN) {
            return Command.noop();
        }
        state = State.HIDDEN;
        currentLabel = null;
        return Command.hide();
    }
}
