package com.example.animeresolver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DiagnosticStoreTest {
    @Test public void sanitizeRemovesUrlsAndSecrets() {
        String result = DiagnosticStore.sanitize(
                "request https://video.example/a.m3u8?sign=secret Cookie: abc token=xyz");
        assertTrue(result.contains("[url]"));
        assertTrue(result.contains("Cookie=[redacted]"));
        assertTrue(result.contains("token=[redacted]"));
        assertFalse(result.contains("secret"));
        assertFalse(result.contains("abc"));
        assertFalse(result.contains("xyz"));
    }

    @Test public void sanitizeUrlDropsQueryAndFragment() {
        assertEquals("https://example.com/video/play",
                DiagnosticStore.sanitizeUrl("https://example.com/video/play?sign=secret#part"));
    }
}
