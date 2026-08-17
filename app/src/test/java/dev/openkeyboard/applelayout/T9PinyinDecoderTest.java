package dev.openkeyboard.applelayout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class T9PinyinDecoderTest {

    @Test
    void decodesNiHaoSequenceWithClickableFirstSyllableAlternatives() {
        T9PinyinDecoder.Result result = new T9PinyinDecoder().decode("64426");

        assertEquals("ni hao", result.bestDisplayText);
        assertTrue(result.pinyinCandidates.contains("ni"));
        assertTrue(result.pinyinCandidates.contains("mi"));
        assertTrue(result.pinyinCandidates.stream().noneMatch("ni hao"::equals));
        assertTrue(result.pinyinCandidates.stream().noneMatch("hao"::equals));
    }

    @Test
    void mapsPinyinToNineKeyDigitsForSegmentSelection() {
        assertEquals("64", T9PinyinDecoder.digitsForPinyin("ni"));
        assertEquals("426", T9PinyinDecoder.digitsForPinyin("hao"));
        assertEquals("9426", T9PinyinDecoder.digitsForPinyin("xian"));
    }

    @Test
    void singleDigitFallsBackToPinyinInitialInsteadOfRawNumber() {
        T9PinyinDecoder.Result result = new T9PinyinDecoder().decode("7");

        assertEquals("s", result.bestDisplayText);
        assertTrue(result.pinyinCandidates.contains("s"));
        assertTrue(result.pinyinCandidates.contains("q"));
    }
}
