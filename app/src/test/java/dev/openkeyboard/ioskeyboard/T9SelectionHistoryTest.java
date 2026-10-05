package dev.openkeyboard.ioskeyboard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class T9SelectionHistoryTest {

    @Test
    void popsLastSelectedSyllable() {
        assertEquals("hao", T9SelectionHistory.lastSyllable("ni hao"));
        assertEquals("ni", T9SelectionHistory.dropLastSyllable("ni hao"));
        assertEquals("ni", T9SelectionHistory.lastSyllable("ni"));
        assertEquals("", T9SelectionHistory.dropLastSyllable("ni"));
        assertEquals("", T9SelectionHistory.lastSyllable(""));
    }

    @Test
    void restoresDigitsToFrontOfRemainingTokens() {
        assertEquals("64", T9SelectionHistory.prependDigits("", "ni"));
        assertEquals("64426", T9SelectionHistory.prependDigits("426", "ni"));
        assertEquals("426", T9SelectionHistory.prependDigits("426", ""));
    }
}
