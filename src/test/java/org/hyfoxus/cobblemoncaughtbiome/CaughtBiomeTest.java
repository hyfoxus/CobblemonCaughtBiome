package org.hyfoxus.cobblemoncaughtbiome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class CaughtBiomeTest {

    @Test
    void storeThenReadRoundTrips() {
        CompoundTag tag = new CompoundTag();
        CaughtBiome.store(tag, ResourceLocation.parse("minecraft:plains"));
        assertEquals(Optional.of(ResourceLocation.parse("minecraft:plains")), CaughtBiome.read(tag));
    }

    @Test
    void readMissingIsEmpty() {
        assertEquals(Optional.empty(), CaughtBiome.read(new CompoundTag()));
    }

    @Test
    void readInvalidIsEmpty() {
        CompoundTag tag = new CompoundTag();
        tag.putString(CaughtBiome.KEY, "Not A Valid:ID!");
        assertEquals(Optional.empty(), CaughtBiome.read(tag));
    }

    @Test
    void displayWithoutBiomeIsUnknown() {
        assertEquals("Unknown", CaughtBiome.display(new CompoundTag()).getString());
    }

    @Test
    void displayWithBiomeStartsWithCaughtIn() {
        CompoundTag tag = new CompoundTag();
        CaughtBiome.store(tag, ResourceLocation.parse("minecraft:plains"));
        assertTrue(CaughtBiome.display(tag).getString().startsWith("Caught in: "));
    }

    @Test
    void displayUnknownNamespaceFallsBackToId() {
        CompoundTag tag = new CompoundTag();
        CaughtBiome.store(tag, ResourceLocation.parse("mymod:weird_place"));
        String shown = CaughtBiome.display(tag).getString();
        assertEquals("Caught in: mymod:weird_place", shown);
        assertFalse(shown.contains("biome."));
    }
}
