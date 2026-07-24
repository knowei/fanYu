package com.example.animeresolver;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UpdateCheckerTest {
    @Test public void comparesSemanticVersions() {
        assertTrue(UpdateChecker.isNewer("v1.0.9", "1.0.8"));
        assertTrue(UpdateChecker.isNewer("2.0", "1.9.9"));
        assertFalse(UpdateChecker.isNewer("v1.0.8", "1.0.8"));
        assertFalse(UpdateChecker.isNewer("1.0.7", "1.0.8"));
    }
}
