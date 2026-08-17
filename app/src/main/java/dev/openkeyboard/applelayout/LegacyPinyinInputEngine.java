package dev.openkeyboard.applelayout;

import android.content.Context;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class LegacyPinyinInputEngine implements ChineseInputEngine {
    private final PinyinEngine pinyinEngine;
    private final StringBuilder composing = new StringBuilder();
    private ChineseKeyboardLayout layout = ChineseKeyboardLayout.NINE_KEY;
    private boolean learningAllowed = true;

    LegacyPinyinInputEngine(Context context) {
        pinyinEngine = new PinyinEngine(context);
    }

    @Override
    public void setLayout(ChineseKeyboardLayout layout) {
        this.layout = layout == null ? ChineseKeyboardLayout.NINE_KEY : layout;
    }

    @Override
    public void setLearningAllowed(boolean allowed) {
        learningAllowed = allowed;
    }

    @Override
    public void reset() {
        composing.setLength(0);
    }

    @Override
    public boolean hasComposition() {
        return composing.length() > 0;
    }

    @Override
    public String preedit() {
        return composing.toString();
    }

    @Override
    public List<String> candidates() {
        if (composing.length() == 0) {
            return Collections.emptyList();
        }
        if (layout == ChineseKeyboardLayout.NINE_KEY) {
            return pinyinEngine.candidatesForNineKey(composing.toString(), learningAllowed);
        }
        return pinyinEngine.candidates(composing.toString(), learningAllowed);
    }

    @Override
    public List<String> candidateAnnotations() {
        return Collections.emptyList();
    }

    @Override
    public void appendAsciiLetter(String letter) {
        if (letter == null || letter.length() != 1) {
            return;
        }
        composing.append(letter.toLowerCase(Locale.US));
    }

    @Override
    public void appendNineKeyDigit(String digit) {
        if (digit == null || digit.length() != 1) {
            return;
        }
        char c = digit.charAt(0);
        if (c >= '2' && c <= '9') {
            composing.append(c);
        }
    }

    @Override
    public String consumePendingCommit() {
        return "";
    }

    @Override
    public boolean backspace() {
        if (composing.length() == 0) {
            return false;
        }
        composing.deleteCharAt(composing.length() - 1);
        return true;
    }

    @Override
    public String commitCandidate(String text) {
        if (text == null || text.isEmpty()) {
            return commitRaw();
        }
        if (learningAllowed) {
            pinyinEngine.recordSelection(composing.toString(), text);
        }
        reset();
        return text;
    }

    @Override
    public String commitBestCandidateOrRaw() {
        if (composing.length() == 0) {
            return "";
        }
        List<String> currentCandidates = candidates();
        if (currentCandidates.isEmpty()) {
            return commitRaw();
        }
        return commitCandidate(currentCandidates.get(0));
    }

    @Override
    public String commitRaw() {
        String raw = composing.toString();
        reset();
        return raw;
    }
}
