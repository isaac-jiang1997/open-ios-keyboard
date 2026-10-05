package dev.openkeyboard.ioskeyboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class KeyHitResolverTest {

    private static List<KeyHitResolver.KeyRef> qwertyRow() {
        return Arrays.asList(
                new KeyHitResolver.KeyRef(0, 0, 0, 80, 80, false),
                new KeyHitResolver.KeyRef(1, 90, 0, 170, 80, false),
                new KeyHitResolver.KeyRef(2, 180, 0, 260, 80, true)
        );
    }

    @Test
    void fillsGapBetweenKeysWithNearestSnap() {
        int hit = KeyHitResolver.hit(qwertyRow(), 85, 40, -1, 12, 20);
        assertEquals(0, hit);
    }

    @Test
    void hysteresisKeepsPressedLetterOnFingerRoll() {
        int hit = KeyHitResolver.hit(qwertyRow(), 88, 40, 0, 20, 20);
        assertEquals(0, hit);
    }

    @Test
    void exactHitPrefersLaterOverlappingKey() {
        List<KeyHitResolver.KeyRef> overlapping = Arrays.asList(
                new KeyHitResolver.KeyRef(0, 0, 0, 100, 200, false),
                new KeyHitResolver.KeyRef(1, 0, 100, 100, 200, false)
        );
        assertEquals(1, KeyHitResolver.hit(overlapping, 50, 150, -1, 0, 0));
    }

    @Test
    void tapUsesDownKeyEvenIfFingerRolls() {
        assertTrue(KeyHitResolver.isTap(40, 40, 52, 48, 20));
        Integer fired = KeyHitResolver.resolveRelease(0, 1, true, false);
        assertEquals(0, fired);
    }

    @Test
    void slideToNeighborLetterFiresTheNeighbor() {
        Integer fired = KeyHitResolver.resolveRelease(0, 1, false, false);
        assertEquals(1, fired);
    }

    @Test
    void slideOffStickyFunctionCancels() {
        assertNull(KeyHitResolver.resolveRelease(2, 1, false, true));
    }

    @Test
    void tapOnStickyFunctionFiresIt() {
        assertEquals(2, KeyHitResolver.resolveRelease(2, 2, true, true));
    }

    @Test
    void farAwayPointDoesNotSnap() {
        assertEquals(-1, KeyHitResolver.hit(qwertyRow(), 800, 800, -1, 12, 20));
    }

    @Test
    void functionKeysAreStickyExceptLettersSpaceAndShift() {
        assertFalse(KeyHitResolver.isStickyFunction(KeyAction.CHARACTER));
        assertFalse(KeyHitResolver.isStickyFunction(KeyAction.SPACE));
        assertFalse(KeyHitResolver.isStickyFunction(KeyAction.SHIFT));
        assertTrue(KeyHitResolver.isStickyFunction(KeyAction.BACKSPACE));
        assertTrue(KeyHitResolver.isStickyFunction(KeyAction.RETURN));
        assertTrue(KeyHitResolver.isStickyFunction(KeyAction.MODE_123));
    }

    @Test
    void releaseHitWithoutHysteresisPicksNeighbor() {
        int withHysteresis = KeyHitResolver.hit(qwertyRow(), 95, 40, 0, 20, 20);
        assertEquals(0, withHysteresis);
        int onRelease = KeyHitResolver.hit(qwertyRow(), 95, 40, -1, 0, 20);
        assertEquals(1, onRelease);
    }

    @Test
    void correctYShiftsGapHitsUpAndLeavesCenteredHitsAlone() {
        List<KeyHitResolver.KeyRef> keys = qwertyRow();
        assertEquals(40f, KeyHitResolver.correctY(keys, 40, 40, 12), 0.01f);
        assertEquals(68f, KeyHitResolver.correctY(keys, 40, 80, 12), 0.01f);
    }

    @Test
    void rolloverCommitsLettersAndSpaceOnly() {
        assertTrue(KeyHitResolver.shouldRolloverCommit(KeyAction.CHARACTER));
        assertTrue(KeyHitResolver.shouldRolloverCommit(KeyAction.SPACE));
        assertFalse(KeyHitResolver.shouldRolloverCommit(KeyAction.BACKSPACE));
        assertFalse(KeyHitResolver.shouldRolloverCommit(KeyAction.SHIFT));
        assertFalse(KeyHitResolver.shouldRolloverCommit(KeyAction.RETURN));
        assertFalse(KeyHitResolver.shouldRolloverCommit(null));
    }

    @Test
    void letterCaseFollowsLiveShiftNotLayoutSnapshot() {
        assertEquals("h", KeyHitResolver.applyLetterCase("H", false));
        assertEquals("H", KeyHitResolver.applyLetterCase("h", true));
        assertEquals("2", KeyHitResolver.applyLetterCase("2", true));
        assertEquals("，", KeyHitResolver.applyLetterCase("，", true));
    }
}
