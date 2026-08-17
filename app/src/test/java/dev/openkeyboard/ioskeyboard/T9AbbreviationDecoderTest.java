package dev.openkeyboard.ioskeyboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class T9AbbreviationDecoderTest {

    @Test
    void repeatedGhiPrefersHInitialsForColloquialChinese() {
        List<String> keys = T9AbbreviationDecoder.abbreviationKeysForDigits("4444");

        assertEquals("hhhh", keys.get(0));
        assertTrue(keys.contains("gggg"));
        assertEquals("h h h h", T9AbbreviationDecoder.preferredDisplayForDigits("4444"));
    }

    @Test
    void mapsAbbreviationBackToNineKeyDigits() {
        assertEquals("4444", T9AbbreviationDecoder.digitsForAbbreviation("hhhh"));
        assertEquals("64426", T9AbbreviationDecoder.digitsForAbbreviation("nihao"));
    }
}
