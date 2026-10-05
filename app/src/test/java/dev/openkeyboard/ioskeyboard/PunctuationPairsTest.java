package dev.openkeyboard.ioskeyboard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PunctuationPairsTest {

    @Test
    void detectsWrapPairs() {
        assertTrue(PunctuationPairs.isWrapPair("“”"));
        assertTrue(PunctuationPairs.isWrapPair("()"));
        assertTrue(PunctuationPairs.isWrapPair("《》"));
        assertFalse(PunctuationPairs.isWrapPair("，"));
        assertFalse(PunctuationPairs.isWrapPair(""));
        assertFalse(PunctuationPairs.isWrapPair(null));
    }
}
