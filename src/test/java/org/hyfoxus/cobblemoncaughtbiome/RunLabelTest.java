package org.hyfoxus.cobblemoncaughtbiome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class RunLabelTest {

    @Test
    void storeThenReadRoundTrips() {
        CompoundTag tag = new CompoundTag();
        UUID id = UUID.randomUUID();
        RunLabel.store(tag, id, "Alex & Sam");
        assertEquals(Optional.of("Alex & Sam"), RunLabel.read(tag));
        assertEquals(id.toString(), tag.getString(RunLabel.ID_KEY));
    }

    @Test
    void readEmptyWhenAbsent() {
        CompoundTag tag = new CompoundTag();
        assertTrue(RunLabel.read(tag).isEmpty());
        assertTrue(RunLabel.display(tag).isEmpty());
    }

    @Test
    void readEmptyWhenTitleBlank() {
        CompoundTag tag = new CompoundTag();
        tag.putString(RunLabel.ID_KEY, UUID.randomUUID().toString());
        tag.putString(RunLabel.TITLE_KEY, "");
        assertTrue(RunLabel.read(tag).isEmpty());
    }

    @Test
    void readReturnsTitleVerbatim() {
        for (String title : new String[] {"100%", "A & B %s"}) {
            CompoundTag tag = new CompoundTag();
            RunLabel.store(tag, UUID.randomUUID(), title);
            assertEquals(Optional.of(title), RunLabel.read(tag));
        }
    }

    @Test
    void stampIfAbsentDoesNotOverwrite() {
        CompoundTag tag = new CompoundTag();
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        assertTrue(RunLabel.stampIfAbsent(tag, idA, "One"));
        assertFalse(RunLabel.stampIfAbsent(tag, idB, "Two"));
        assertEquals(Optional.of("One"), RunLabel.read(tag));
        assertEquals(idA.toString(), tag.getString(RunLabel.ID_KEY));
    }

    @Test
    void stampIfAbsentFillsWhenTitleBlank() {
        CompoundTag tag = new CompoundTag();
        tag.putString(RunLabel.ID_KEY, UUID.randomUUID().toString());
        tag.putString(RunLabel.TITLE_KEY, "");
        assertTrue(RunLabel.stampIfAbsent(tag, UUID.randomUUID(), "Two"));
        assertEquals(Optional.of("Two"), RunLabel.read(tag));
    }

    @Test
    void shortenTruncatesLongTitles() {
        String longTitle = "abcdefghij".repeat(5);
        String shortened = RunLabel.shorten(longTitle);
        assertEquals(30, shortened.length());
        assertTrue(shortened.endsWith("…"));
        assertTrue(shortened.startsWith(longTitle.substring(0, 29)));
        String exact = "x".repeat(30);
        assertEquals(exact, RunLabel.shorten(exact));
        assertEquals("Solo", RunLabel.shorten("Solo"));
    }
}
