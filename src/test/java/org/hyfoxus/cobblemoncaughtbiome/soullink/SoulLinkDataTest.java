package org.hyfoxus.cobblemoncaughtbiome.soullink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.hyfoxus.cobblemoncaughtbiome.soullink.SoulLinkData.Run;
import org.junit.jupiter.api.Test;

class SoulLinkDataTest {
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @Test
    void oldSaveLoadsWithIdAndBothMembersSelected() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        CompoundTag run = new CompoundTag();
        run.putUUID("first", first);
        run.putString("firstName", "Player483");
        run.putUUID("second", second);
        run.putString("secondName", "SoulLinkTester");
        ListTag runs = new ListTag();
        runs.add(run);
        CompoundTag root = new CompoundTag();
        root.put("runs", runs);

        SoulLinkData data = SoulLinkData.load(root, null);

        assertEquals(1, data.runs.size());
        Run loaded = data.runs.get(0);
        assertNotNull(loaded.id);
        assertEquals("Player483 & SoulLinkTester", loaded.title);
        assertSame(loaded, data.activeRun(first));
        assertSame(loaded, data.activeRun(second));
    }

    @Test
    void saveThenLoadRoundTripsIdsTitlesSoloAndSelection() {
        SoulLinkData data = new SoulLinkData();
        Run solo = data.createSolo(a, "Alex");
        Run shared = data.createShared(a, "Alex", b, "Sam");
        // Persisted selection (shared) differs from the load-time default (first run in list = solo).
        data.select(a, shared);
        assertEquals(shared.id, data.selected.get(a));

        SoulLinkData loaded = SoulLinkData.load(data.save(new CompoundTag(), null), null);

        assertEquals(2, loaded.runs.size());
        for (Run original : data.runs) {
            Run copy = loaded.run(original.id);
            assertNotNull(copy);
            assertEquals(original.title, copy.title);
            assertEquals(original.solo(), copy.solo());
        }
        assertTrue(loaded.run(solo.id).solo());
        assertTrue(!loaded.run(shared.id).solo());
        assertEquals(data.selected, loaded.selected);
        assertEquals(shared.id, loaded.selected.get(a));
        assertNotEquals(solo.id, loaded.selected.get(a));
        assertEquals(shared.id, loaded.selected.get(b));
    }

    @Test
    void freePlaySurvivesSaveAndLoad() {
        SoulLinkData data = new SoulLinkData();
        Run run = data.createShared(a, "Alex", b, "Sam");
        data.clearSelection(a);
        assertNull(data.activeRun(a));

        SoulLinkData loaded = SoulLinkData.load(data.save(new CompoundTag(), null), null);

        assertNotNull(loaded.run(run.id));
        assertNull(loaded.activeRun(a));
        assertSame(loaded.run(run.id), loaded.activeRun(b));
    }

    @Test
    void repeatMemberSetGetsNumberedTitle() {
        SoulLinkData data = new SoulLinkData();
        assertEquals("Alex & Sam", data.createShared(a, "Alex", b, "Sam").title);
        assertEquals("Alex & Sam #2", data.createShared(a, "Alex", b, "Sam").title);
        assertEquals("Solo", data.createSolo(a, "Alex").title);
        assertEquals("Solo #2", data.createSolo(a, "Alex").title);
    }

    @Test
    void createSelectsNewRunReplacingPreviousSelection() {
        SoulLinkData data = new SoulLinkData();
        Run first = data.createSolo(a, "Alex");
        assertSame(first, data.activeRun(a));
        Run second = data.createShared(a, "Alex", b, "Sam");
        assertSame(second, data.activeRun(a));
        assertSame(second, data.activeRun(b));
        assertEquals(2, data.runsOf(a).size());
        assertTrue(data.runsOf(a).contains(first));
        assertTrue(data.runsOf(a).contains(second));
        assertEquals(1, data.runsOf(b).size());
    }

    @Test
    void selectRejectsNonMember() {
        SoulLinkData data = new SoulLinkData();
        Run run = data.createSolo(a, "Alex");
        UUID stranger = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> data.select(stranger, run));
        assertNull(data.activeRun(stranger));
    }

    @Test
    void removeClearsSelectionOnlyWhenSelected() {
        SoulLinkData data = new SoulLinkData();
        Run runA = data.createSolo(a, "Alex");
        Run runB = data.createShared(a, "Alex", b, "Sam");
        data.select(a, runA);

        data.remove(runB);
        assertSame(runA, data.activeRun(a));
        assertNull(data.run(runB.id));

        data.remove(runA);
        assertNull(data.activeRun(a));
        assertTrue(data.runsOf(a).isEmpty());
    }

    @Test
    void parseRunIdRejectsGarbage() {
        assertNull(SoulLinkData.parseRunId("nope"));
        assertNull(SoulLinkData.parseRunId(""));
        assertNull(SoulLinkData.parseRunId(null));
        UUID id = UUID.randomUUID();
        assertEquals(id, SoulLinkData.parseRunId(id.toString()));
    }

    @Test
    void soloRunMembersHasOneEntry() {
        SoulLinkData data = new SoulLinkData();
        Run solo = data.createSolo(a, "Alex");
        Run shared = data.createShared(a, "Alex", b, "Sam");
        assertEquals(1, solo.members().size());
        assertEquals(a, solo.members().get(0));
        assertEquals(2, shared.members().size());
    }
}
