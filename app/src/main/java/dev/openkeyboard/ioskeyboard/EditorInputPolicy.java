package dev.openkeyboard.ioskeyboard;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;

/**
 * Maps an {@link EditorInfo} to keyboard layout, learning, and enter-key policy.
 */
final class EditorInputPolicy {
    final boolean forceNumberPad;
    final boolean forceEnglish;
    final boolean suppressCandidates;
    final boolean allowLearning;
    final boolean performEditorAction;
    final int editorAction;
    final boolean sendNewline;

    private EditorInputPolicy(
            boolean forceNumberPad,
            boolean forceEnglish,
            boolean suppressCandidates,
            boolean allowLearning,
            boolean performEditorAction,
            int editorAction,
            boolean sendNewline
    ) {
        this.forceNumberPad = forceNumberPad;
        this.forceEnglish = forceEnglish;
        this.suppressCandidates = suppressCandidates;
        this.allowLearning = allowLearning;
        this.performEditorAction = performEditorAction;
        this.editorAction = editorAction;
        this.sendNewline = sendNewline;
    }

    static EditorInputPolicy from(EditorInfo info) {
        if (info == null) {
            return new EditorInputPolicy(false, false, false, false, false, EditorInfo.IME_ACTION_NONE, true);
        }
        int inputType = info.inputType;
        int clazz = inputType & InputType.TYPE_MASK_CLASS;
        int variation = inputType & InputType.TYPE_MASK_VARIATION;
        int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean noEnterAction = (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
        boolean noPersonalizedLearning =
                (info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0;

        boolean password = isPassword(clazz, variation);
        boolean email = isEmail(clazz, variation);
        boolean uri = isUri(clazz, variation);
        boolean numberPad = clazz == InputType.TYPE_CLASS_NUMBER
                || clazz == InputType.TYPE_CLASS_PHONE
                || clazz == InputType.TYPE_CLASS_DATETIME;
        boolean sensitiveText = password || email || uri
                || (clazz == InputType.TYPE_CLASS_TEXT && (
                variation == InputType.TYPE_TEXT_VARIATION_PERSON_NAME
                        || variation == InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS));

        boolean allowLearning = !noPersonalizedLearning && !sensitiveText && !numberPad;
        boolean forceEnglish = password || email || uri;
        boolean suppressCandidates = password;

        boolean isAction = action == EditorInfo.IME_ACTION_GO
                || action == EditorInfo.IME_ACTION_SEARCH
                || action == EditorInfo.IME_ACTION_SEND
                || action == EditorInfo.IME_ACTION_NEXT
                || action == EditorInfo.IME_ACTION_DONE;
        boolean performEditorAction = isAction && !noEnterAction;
        boolean sendNewline = !performEditorAction;

        return new EditorInputPolicy(
                numberPad,
                forceEnglish,
                suppressCandidates,
                allowLearning,
                performEditorAction,
                action,
                sendNewline);
    }

    private static boolean isPassword(int clazz, int variation) {
        if (clazz == InputType.TYPE_CLASS_TEXT) {
            return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
        }
        return clazz == InputType.TYPE_CLASS_NUMBER
                && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    }

    private static boolean isEmail(int clazz, int variation) {
        return clazz == InputType.TYPE_CLASS_TEXT
                && (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS);
    }

    private static boolean isUri(int clazz, int variation) {
        return clazz == InputType.TYPE_CLASS_TEXT
                && variation == InputType.TYPE_TEXT_VARIATION_URI;
    }
}
