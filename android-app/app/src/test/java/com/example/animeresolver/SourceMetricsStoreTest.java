package com.example.animeresolver;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SourceMetricsStoreTest {
    @Test public void baseNameRemovesChannelSuffix() {
        assertEquals("橘子动漫", SourceMetricsStore.baseName("橘子动漫 · 高清线路2"));
        assertEquals("源 A", SourceMetricsStore.baseName("源 A | 线路 1"));
    }

    @Test public void inferHeightUsesHighestResolution() {
        assertEquals(1080, SourceMetricsStore.inferHeight(
                "https://example/video/720p/master.m3u8 default=1080P"));
        assertEquals(0, SourceMetricsStore.inferHeight("https://example/master.m3u8"));
    }
}
