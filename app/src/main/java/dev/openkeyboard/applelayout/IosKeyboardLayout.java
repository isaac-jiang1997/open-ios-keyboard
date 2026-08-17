package dev.openkeyboard.applelayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class IosKeyboardLayout {
    static final float IOS_ROW_3_SIDE_KEY_UNITS = 1.40f;
    static final float IOS_BOTTOM_SMALL_KEY_UNITS = 1.25f;
    static final float IOS_RETURN_KEY_UNITS = 1.90f;
    static final String ACTION_SEARCH = "search";
    static final String ACTION_SEARCH_ZH = "搜索";

    private IosKeyboardLayout() {
    }

    static List<List<KeyboardKey>> rows(
            KeyboardMode mode,
            boolean shifted,
            boolean capsLocked,
            InputLanguage language,
            ChineseKeyboardLayout chineseKeyboardLayout,
            boolean composing
    ) {
        return rows(mode, shifted, capsLocked, language, chineseKeyboardLayout, composing, false);
    }

    static List<List<KeyboardKey>> rows(
            KeyboardMode mode,
            boolean shifted,
            boolean capsLocked,
            InputLanguage language,
            ChineseKeyboardLayout chineseKeyboardLayout,
            boolean composing,
            boolean punctuationStrip
    ) {
        if (mode == KeyboardMode.NUMBERS) {
            if (language == InputLanguage.CHINESE && chineseKeyboardLayout == ChineseKeyboardLayout.NINE_KEY) {
                return nineKeyNumberRows();
            }
            return numberRows(language);
        }
        if (mode == KeyboardMode.SYMBOLS) {
            if (language == InputLanguage.CHINESE && chineseKeyboardLayout == ChineseKeyboardLayout.NINE_KEY) {
                return nineKeySymbolRows();
            }
            return symbolRows(language);
        }
        if (mode == KeyboardMode.SYMBOLS_MORE) {
            if (language == InputLanguage.CHINESE && chineseKeyboardLayout == ChineseKeyboardLayout.NINE_KEY) {
                return nineKeyMoreSymbolRows();
            }
            return symbolRows(language);
        }
        if (mode == KeyboardMode.EMOJI) {
            return emojiRows(language);
        }
        if (language == InputLanguage.CHINESE && chineseKeyboardLayout == ChineseKeyboardLayout.NINE_KEY) {
            if (punctuationStrip) {
                return nineKeyRowsWithPunctuationStrip(composing);
            }
            return nineKeyRows(composing);
        }
        return letterRows(shifted || capsLocked, language);
    }

    private static List<List<KeyboardKey>> letterRows(boolean uppercase, InputLanguage language) {
        List<List<KeyboardKey>> rows = new ArrayList<>();
        rows.add(chars("qwertyuiop", uppercase));
        rows.add(chars("asdfghjkl", uppercase));

        List<KeyboardKey> third = new ArrayList<>();
        third.add(new KeyboardKey("shift", null, KeyAction.SHIFT, IOS_ROW_3_SIDE_KEY_UNITS));
        addChars(third, "zxcvbnm", uppercase);
        third.add(new KeyboardKey("delete", null, KeyAction.BACKSPACE, IOS_ROW_3_SIDE_KEY_UNITS));
        rows.add(third);

        List<KeyboardKey> bottom = new ArrayList<>();
        bottom.add(new KeyboardKey("123", null, KeyAction.MODE_123, IOS_BOTTOM_SMALL_KEY_UNITS));
        bottom.add(emojiKey());
        bottom.add(new KeyboardKey(language == InputLanguage.CHINESE ? "空格" : "space", " ", KeyAction.SPACE,
                5.45f));
        bottom.add(searchKey(language));
        rows.add(bottom);
        return Collections.unmodifiableList(rows);
    }

    private static List<List<KeyboardKey>> nineKeyRows(boolean composing) {
        List<List<KeyboardKey>> rows = new ArrayList<>();
        rows.add(row(
                new KeyboardKey("123", null, KeyAction.MODE_123, 1.0f),
                new KeyboardKey("，。?!", null, KeyAction.PUNCTUATION_PICKER, 1.0f),
                new KeyboardKey("ABC", "2", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("DEF", "3", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("delete", null, KeyAction.BACKSPACE, 1.0f)));
        rows.add(row(
                new KeyboardKey("#@¥", null, KeyAction.MODE_SYMBOLS, 1.0f),
                new KeyboardKey("GHI", "4", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("JKL", "5", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("MNO", "6", KeyAction.CHARACTER, 1.0f),
                composing
                        ? new KeyboardKey("分隔", null, KeyAction.SEPARATOR, 1.0f)
                        : new KeyboardKey("更多", null, KeyAction.MODE_SYMBOLS, 1.0f)));
        rows.add(row(
                new KeyboardKey("ABC", null, KeyAction.SWITCH_LANGUAGE, 1.0f),
                new KeyboardKey("PQRS", "7", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("TUV", "8", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("WXYZ", "9", KeyAction.CHARACTER, 1.0f),
                composing ? confirmKey() : searchKey(InputLanguage.CHINESE)));
        rows.add(row(
                emojiKey(),
                new KeyboardKey("选拼音", null, KeyAction.SWITCH_CHINESE_LAYOUT, 1.0f),
                composing
                        ? new KeyboardKey("选词", null, KeyAction.SELECT_WORDS, 2.08f)
                        : new KeyboardKey("空格", " ", KeyAction.SPACE, 2.08f)));
        return Collections.unmodifiableList(rows);
    }

    private static List<List<KeyboardKey>> nineKeyRowsWithPunctuationStrip(boolean composing) {
        List<List<KeyboardKey>> rows = new ArrayList<>(nineKeyRows(composing));
        rows.add(row(
                new KeyboardKey("，", "，", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("。", "。", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("？", "？", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("！", "！", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("、", "、", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("；", "；", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("：", "：", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("…", "…", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("—", "—", KeyAction.CHARACTER, 1.0f)));
        return Collections.unmodifiableList(rows);
    }

    private static List<List<KeyboardKey>> nineKeyNumberRows() {
        List<List<KeyboardKey>> rows = new ArrayList<>();
        rows.add(row(
                new KeyboardKey("拼音", null, KeyAction.MODE_ABC, 1.0f),
                new KeyboardKey("1", "1", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("2", "2", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("3", "3", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("delete", null, KeyAction.BACKSPACE, 1.0f)));
        rows.add(row(
                new KeyboardKey("#@¥", null, KeyAction.MODE_SYMBOLS, 1.0f),
                new KeyboardKey("4", "4", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("5", "5", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("6", "6", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("更多", null, KeyAction.MODE_SYMBOLS, 1.0f)));
        rows.add(row(
                new KeyboardKey("ABC", null, KeyAction.SWITCH_LANGUAGE, 1.0f),
                new KeyboardKey("7", "7", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("8", "8", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("9", "9", KeyAction.CHARACTER, 1.0f),
                searchKey(InputLanguage.CHINESE)));
        rows.add(row(
                emojiKey(),
                new KeyboardKey(".,;", null, KeyAction.PUNCTUATION_PICKER, 1.0f),
                new KeyboardKey("0", "0", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("空格", " ", KeyAction.SPACE, 1.0f)));
        return Collections.unmodifiableList(rows);
    }

    private static List<List<KeyboardKey>> nineKeySymbolRows() {
        List<List<KeyboardKey>> rows = new ArrayList<>();
        rows.add(raw("1234567890"));
        rows.add(raw("-/:;()¥@“”"));
        rows.add(row(
                new KeyboardKey("#+=", null, KeyAction.MODE_SYMBOLS, IOS_ROW_3_SIDE_KEY_UNITS),
                new KeyboardKey("。", "。", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("，", "，", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("、", "、", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("?", "?", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("!", "!", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey(".", ".", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("delete", null, KeyAction.BACKSPACE, IOS_ROW_3_SIDE_KEY_UNITS)));
        rows.add(row(
                new KeyboardKey("拼音", null, KeyAction.MODE_ABC, 2.0f),
                new KeyboardKey("空格", " ", KeyAction.SPACE, 4.1f),
                searchKey(InputLanguage.CHINESE)));
        return Collections.unmodifiableList(rows);
    }

    private static List<List<KeyboardKey>> nineKeyMoreSymbolRows() {
        List<List<KeyboardKey>> rows = new ArrayList<>();
        rows.add(raw("【】{}#%^*+="));
        rows.add(row(
                new KeyboardKey("_", "_", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("—", "—", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("\\", "\\", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("|", "|", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("~", "~", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("«", "«", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("»", "»", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("$", "$", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("&", "&", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("·", "·", KeyAction.CHARACTER, 1.0f)));
        rows.add(row(
                new KeyboardKey("123", null, KeyAction.MODE_123, IOS_ROW_3_SIDE_KEY_UNITS),
                new KeyboardKey("…", "…", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("，", "，", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("^^_", "^^_", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("?", "?", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("!", "!", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("’", "’", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("delete", null, KeyAction.BACKSPACE, IOS_ROW_3_SIDE_KEY_UNITS)));
        rows.add(row(
                new KeyboardKey("拼音", null, KeyAction.MODE_ABC, 2.0f),
                new KeyboardKey("空格", " ", KeyAction.SPACE, 4.1f),
                searchKey(InputLanguage.CHINESE)));
        return Collections.unmodifiableList(rows);
    }

    private static List<List<KeyboardKey>> numberRows(InputLanguage language) {
        List<List<KeyboardKey>> rows = new ArrayList<>();
        rows.add(raw("1234567890"));
        rows.add(raw("-/:;()$&@\""));

        rows.add(row(
                new KeyboardKey("#+=", null, KeyAction.MODE_SYMBOLS, IOS_ROW_3_SIDE_KEY_UNITS),
                new KeyboardKey(".", ".", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey(",", ",", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("?", "?", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("!", "!", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("'", "'", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("delete", null, KeyAction.BACKSPACE, IOS_ROW_3_SIDE_KEY_UNITS)));

        rows.add(modeBottom("ABC", KeyAction.MODE_ABC, language));
        return Collections.unmodifiableList(rows);
    }

    private static List<List<KeyboardKey>> symbolRows(InputLanguage language) {
        List<List<KeyboardKey>> rows = new ArrayList<>();
        rows.add(raw("[]{}#%^*+="));
        rows.add(raw("_\\|~<>€£¥•"));

        rows.add(row(
                new KeyboardKey("123", null, KeyAction.MODE_123, IOS_ROW_3_SIDE_KEY_UNITS),
                new KeyboardKey(".", ".", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey(",", ",", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("?", "?", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("!", "!", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("'", "'", KeyAction.CHARACTER, 1.0f),
                new KeyboardKey("delete", null, KeyAction.BACKSPACE, IOS_ROW_3_SIDE_KEY_UNITS)));

        rows.add(modeBottom("ABC", KeyAction.MODE_ABC, language));
        return Collections.unmodifiableList(rows);
    }

    private static List<List<KeyboardKey>> emojiRows(InputLanguage language) {
        List<List<KeyboardKey>> rows = new ArrayList<>();
        rows.add(emojiRow("⌛", "😔", "🐎", "🀄", "😦", "🈚", "🍹", "👀"));
        rows.add(emojiRow("👏", "😅", "😞", "🎊", "🎧", "😐", "🔢", "得"));
        rows.add(emojiRow("🛞", "+", "💩", "👂", "👌", "☺", "🤔", "🐷"));
        rows.add(emojiRow("😰", "😂", "🐻", "🐴", "😡", "😉", "✖"));
        rows.add(emojiRow("😀", "😃", "😄", "😁", "😆", "🥹", "😇", "🙂"));
        rows.add(emojiRow("🙃", "😉", "😊", "😋", "😎", "😍", "😘", "🥰"));
        rows.add(emojiRow("😗", "😙", "😚", "🤗", "🤩", "🥳", "😏", "😌"));
        rows.add(emojiRow("😔", "😪", "🤤", "😴", "😷", "🤒", "🤕", "🤧"));
        rows.add(emojiRow("🥵", "🥶", "🥴", "😵", "🤯", "🤠", "🥸", "😈"));
        rows.add(emojiRow("👋", "🤚", "🖐", "✋", "🖖", "👌", "🤌", "🤏"));
        rows.add(emojiRow("✌️", "🤞", "🫰", "🤟", "🤘", "🤙", "👈", "👉"));
        rows.add(emojiRow("👆", "👇", "☝️", "👍", "👎", "✊", "👊", "👏"));
        rows.add(emojiRow("🙌", "🫶", "🙏", "💪", "🦾", "🧠", "👀", "👂"));
        rows.add(emojiRow("🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼"));
        rows.add(emojiRow("🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓"));
        rows.add(emojiRow("⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🎱", "🏓"));
        rows.add(emojiRow("🚗", "🚕", "🚙", "🚌", "🚎", "🏎", "🚓", "🚑"));
        rows.add(emojiRow("❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍"));
        rows.add(row(
                new KeyboardKey("ABC", null, KeyAction.MODE_ABC, 1.0f),
                new KeyboardKey("delete", null, KeyAction.BACKSPACE, 1.0f)));
        return Collections.unmodifiableList(rows);
    }

    private static List<KeyboardKey> emojiRow(String... emojis) {
        List<KeyboardKey> row = new ArrayList<>();
        for (String emoji : emojis) {
            row.add(new KeyboardKey(emoji, emoji, KeyAction.CHARACTER, 1.0f));
        }
        return row;
    }

    private static List<KeyboardKey> modeBottom(String modeLabel, KeyAction modeAction, InputLanguage language) {
        List<KeyboardKey> bottom = new ArrayList<>();
        bottom.add(new KeyboardKey(modeLabel, null, modeAction, IOS_BOTTOM_SMALL_KEY_UNITS));
        bottom.add(emojiKey());
        bottom.add(new KeyboardKey(language == InputLanguage.CHINESE ? "空格" : "space", " ", KeyAction.SPACE, 5.45f));
        bottom.add(searchKey(language));
        return bottom;
    }

    private static KeyboardKey emojiKey() {
        return new KeyboardKey("😊", null, KeyAction.MODE_EMOJI, IOS_BOTTOM_SMALL_KEY_UNITS);
    }

    private static KeyboardKey searchKey(InputLanguage language) {
        return new KeyboardKey(language == InputLanguage.CHINESE ? ACTION_SEARCH_ZH : ACTION_SEARCH, "\n",
                KeyAction.RETURN, IOS_RETURN_KEY_UNITS);
    }

    private static KeyboardKey confirmKey() {
        return new KeyboardKey("选定", null, KeyAction.CONFIRM_COMPOSITION, IOS_RETURN_KEY_UNITS);
    }

    private static List<KeyboardKey> row(KeyboardKey... keys) {
        List<KeyboardKey> row = new ArrayList<>();
        Collections.addAll(row, keys);
        return row;
    }

    private static List<KeyboardKey> chars(String chars, boolean uppercase) {
        List<KeyboardKey> keys = new ArrayList<>();
        addChars(keys, chars, uppercase);
        return keys;
    }

    private static void addChars(List<KeyboardKey> keys, String chars, boolean uppercase) {
        for (int i = 0; i < chars.length(); i++) {
            String lower = String.valueOf(chars.charAt(i));
            String label = uppercase ? lower.toUpperCase() : lower;
            keys.add(new KeyboardKey(label, label, KeyAction.CHARACTER, 1.0f));
        }
    }

    private static List<KeyboardKey> raw(String chars) {
        List<KeyboardKey> keys = new ArrayList<>();
        addRaw(keys, chars);
        return keys;
    }

    private static void addRaw(List<KeyboardKey> keys, String chars) {
        for (int i = 0; i < chars.length(); ) {
            int codePoint = chars.codePointAt(i);
            String value = new String(Character.toChars(codePoint));
            keys.add(new KeyboardKey(value, value, KeyAction.CHARACTER, 1.0f));
            i += Character.charCount(codePoint);
        }
    }
}
