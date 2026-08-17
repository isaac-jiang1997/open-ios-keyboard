package dev.openkeyboard.ioskeyboard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Smoke test verifying the JVM unit-test source set, JUnit 5 runner, and
 * {@code useJUnitPlatform()} wiring are functional before adding real tests.
 *
 * <p>This test exists solely to prove that {@code app/src/test/java} compiles
 * against {@code testImplementation} dependencies and that the JUnit Platform
 * picks up annotated methods. It must not be expanded with real assertions
 * about production code; dedicated test classes should be added as needed.
 */
class SmokeTest {

    @Test
    void onePlusOneEqualsTwo() {
        assertEquals(2, 1 + 1);
    }
}
