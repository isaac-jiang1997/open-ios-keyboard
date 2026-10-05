package dev.openkeyboard.ioskeyboard;

/**
 * Helpers for T9 selected-syllable history. After a character is chosen, backspace
 * should restore that syllable's digits instead of dropping both the character
 * and the pinyin.
 */
final class T9SelectionHistory {
    private T9SelectionHistory() {
    }

    static String lastSyllable(String history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        int lastSpace = history.lastIndexOf(' ');
        return lastSpace < 0 ? history : history.substring(lastSpace + 1);
    }

    static String dropLastSyllable(String history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        int lastSpace = history.lastIndexOf(' ');
        return lastSpace < 0 ? "" : history.substring(0, lastSpace);
    }

    static String prependDigits(String tokens, String pinyin) {
        String digits = T9PinyinDecoder.digitsForPinyin(pinyin);
        if (digits.isEmpty()) {
            return tokens == null ? "" : tokens;
        }
        if (tokens == null || tokens.isEmpty()) {
            return digits;
        }
        return digits + tokens;
    }
}
