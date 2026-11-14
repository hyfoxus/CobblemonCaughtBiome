package org.hyfoxus.cobblemoncaughtbiome.soullink;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class SoulLinkManagerLogTest {
    @Test
    void formatLogEntryPrefixesATimestamp() {
        String entry = SoulLinkManager.formatLogEntry("Run started.");
        assertTrue(entry.matches("\\[\\d{2}:\\d{2}] Run started\\."));
    }
}
