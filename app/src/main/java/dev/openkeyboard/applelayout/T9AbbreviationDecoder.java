package dev.openkeyboard.applelayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class T9AbbreviationDecoder {
    private static final int MAX_ABBREVIATION_KEYS = 96;

    private T9AbbreviationDecoder() {
    }

    static List<String> abbreviationKeysForDigits(String rawDigits) {
        String digits = normalizeDigits(rawDigits);
        if (digits.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        buildKeys(digits, 0, new StringBuilder(digits.length()), result);
        return Collections.unmodifiableList(result);
    }

    static String preferredDisplayForDigits(String rawDigits) {
        List<String> keys = abbreviationKeysForDigits(rawDigits);
        if (keys.isEmpty()) {
            return "";
        }
        String key = keys.get(0);
        StringBuilder out = new StringBuilder(key.length() * 2 - 1);
        for (int i = 0; i < key.length(); i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(key.charAt(i));
        }
        return out.toString();
    }

    static String digitsForAbbreviation(String abbreviation) {
        if (abbreviation == null || abbreviation.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(abbreviation.length());
        for (int i = 0; i < abbreviation.length(); i++) {
            char c = Character.toLowerCase(abbreviation.charAt(i));
            if (c >= 'a' && c <= 'c') {
                out.append('2');
            } else if (c >= 'd' && c <= 'f') {
                out.append('3');
            } else if (c >= 'g' && c <= 'i') {
                out.append('4');
            } else if (c >= 'j' && c <= 'l') {
                out.append('5');
            } else if (c >= 'm' && c <= 'o') {
                out.append('6');
            } else if (c >= 'p' && c <= 's') {
                out.append('7');
            } else if (c >= 't' && c <= 'v') {
                out.append('8');
            } else if (c >= 'w' && c <= 'z') {
                out.append('9');
            }
        }
        return out.toString();
    }

    private static void buildKeys(String digits, int index, StringBuilder current, List<String> out) {
        if (out.size() >= MAX_ABBREVIATION_KEYS) {
            return;
        }
        if (index >= digits.length()) {
            out.add(current.toString());
            return;
        }
        for (String initial : initialsForDigit(digits.charAt(index))) {
            current.append(initial);
            buildKeys(digits, index + 1, current, out);
            current.setLength(current.length() - initial.length());
            if (out.size() >= MAX_ABBREVIATION_KEYS) {
                return;
            }
        }
    }

    private static List<String> initialsForDigit(char digit) {
        switch (digit) {
            case '2':
                return Arrays.asList("a", "b", "c");
            case '3':
                return Arrays.asList("d", "e", "f");
            case '4':
                return Arrays.asList("h", "g");
            case '5':
                return Arrays.asList("j", "l", "k");
            case '6':
                return Arrays.asList("n", "m", "o");
            case '7':
                return Arrays.asList("s", "q", "r", "p");
            case '8':
                return Collections.singletonList("t");
            case '9':
                return Arrays.asList("w", "x", "y", "z");
            default:
                return Collections.emptyList();
        }
    }

    private static String normalizeDigits(String rawDigits) {
        if (rawDigits == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(rawDigits.length());
        for (int i = 0; i < rawDigits.length(); i++) {
            char c = rawDigits.charAt(i);
            if (c >= '2' && c <= '9') {
                out.append(c);
            }
        }
        return out.toString();
    }
}
