package dev.openkeyboard.ioskeyboard;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class PunctuationPairs {
    private static final Set<String> PAIRS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "“”", "\"\"", "()", "[]", "{}", "<>", "《》", "〈〉", "「」", "『』", "〔〕", "【】"
    )));

    private PunctuationPairs() {
    }

    static boolean isWrapPair(String text) {
        return text != null && PAIRS.contains(text);
    }
}
