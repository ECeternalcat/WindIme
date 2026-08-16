package com.garaho.ime.settings.update;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VersionUtilTest {

    @Test
    public void equalVersions() {
        assertEquals(0, VersionUtil.compare("0.5.6", "0.5.6"));
        assertEquals(0, VersionUtil.compare("v0.5.6", "0.5.6"));
        assertEquals(0, VersionUtil.compare("V0.5.6", "v0.5.6"));
    }

    @Test
    public void patchOrdering() {
        assertTrue(VersionUtil.isNewer("0.5.7", "0.5.6"));
        assertTrue(VersionUtil.isNewer("0.6.0", "0.5.9"));
        assertTrue(VersionUtil.isNewer("1.0.0", "0.9.9"));
        assertFalse(VersionUtil.isNewer("0.5.6", "0.5.7"));
        assertFalse(VersionUtil.isNewer("0.5.6", "0.5.6"));
    }

    @Test
    public void numericNotLexicographic() {
        // String comparison would call "0.5.10" smaller than "0.5.9".
        assertTrue(VersionUtil.isNewer("0.5.10", "0.5.9"));
        assertTrue(VersionUtil.isNewer("0.10.0", "0.9.0"));
    }

    @Test
    public void missingPartsCountAsZero() {
        assertTrue(VersionUtil.isNewer("0.5.1", "0.5"));
        assertFalse(VersionUtil.isNewer("0.5", "0.5.0"));
        assertTrue(VersionUtil.isNewer("1", "0.9.9"));
    }

    @Test
    public void suffixesAndPrefixesIgnored() {
        assertEquals(0, VersionUtil.compare("0.5.6-beta", "0.5.6"));
        assertTrue(VersionUtil.isNewer("v0.5.7-rc1", "0.5.6"));
        assertEquals("0.5.6", VersionUtil.stripPrefix("v0.5.6"));
        assertEquals("", VersionUtil.stripPrefix(null));
    }

    @Test
    public void junkInputBehavesAsZero() {
        assertEquals(0, VersionUtil.compare("", ""));
        assertEquals(0, VersionUtil.compare(null, null));
        assertTrue(VersionUtil.isNewer("0.0.1", ""));
    }
}
