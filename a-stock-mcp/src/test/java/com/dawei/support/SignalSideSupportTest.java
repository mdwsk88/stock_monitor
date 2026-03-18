package com.dawei.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignalSideSupportTest {

    @Test
    void normalizeShouldMapChineseAndEnglishValuesToCanonicalSides() {
        assertEquals(SignalSideSupport.BUY, SignalSideSupport.normalize("利多"));
        assertEquals(SignalSideSupport.SELL, SignalSideSupport.normalize("SELL"));
        assertEquals(SignalSideSupport.NEUTRAL, SignalSideSupport.normalize("中性"));
        assertEquals(SignalSideSupport.NEUTRAL, SignalSideSupport.normalize("unknown"));
    }

    @Test
    void helpersShouldExposeStableLabelsAndCompatibilityChecks() {
        assertEquals("利多", SignalSideSupport.toLabel("BUY"));
        assertEquals("利空", SignalSideSupport.toLabel("利空"));
        assertEquals("中性", SignalSideSupport.toLabel(null));
        assertTrue(SignalSideSupport.isCompatible("利多", "BUY"));
        assertTrue(SignalSideSupport.isCompatible("中性", "SELL"));
    }
}
