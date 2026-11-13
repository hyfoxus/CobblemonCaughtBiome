package org.hyfoxus.cobblemoncaughtbiome;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class BiomeNames {
    private BiomeNames() { }

    public static Component display(String biome) {
        ResourceLocation id = ResourceLocation.tryParse(biome);
        if (id == null) return Component.literal("Unknown biome");
        String fallback = Arrays.stream(id.getPath().split("[_/.-]+"))
                .filter(word -> !word.isEmpty())
                .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1))
                .collect(Collectors.joining(" "));
        return Component.translatableWithFallback(id.toLanguageKey("biome"), fallback);
    }
}
