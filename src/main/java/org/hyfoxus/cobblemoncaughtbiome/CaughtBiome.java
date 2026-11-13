package org.hyfoxus.cobblemoncaughtbiome;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class CaughtBiome {
    public static final String KEY = "cobblemoncaughtbiome:biome";

    private CaughtBiome() {
    }

    public static void store(CompoundTag data, ResourceLocation biomeId) {
        data.putString(KEY, biomeId.toString());
    }

    public static Optional<ResourceLocation> read(CompoundTag data) {
        String raw = data.getString(KEY);
        return raw.isEmpty() ? Optional.empty() : Optional.ofNullable(ResourceLocation.tryParse(raw));
    }

    public static Component display(CompoundTag data) {
        return read(data)
                .<Component>map(id -> Component.translatableWithFallback(
                        "cobblemoncaughtbiome.caught_in",
                        "Caught in: %s",
                        BiomeNames.display(id.toString())))
                .orElseGet(() -> Component.translatableWithFallback("cobblemoncaughtbiome.unknown", "Unknown"));
    }
}
