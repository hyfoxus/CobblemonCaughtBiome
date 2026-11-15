package org.hyfoxus.cobblemoncaughtbiome;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import net.fabricmc.api.ModInitializer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.hyfoxus.cobblemoncaughtbiome.soullink.SoulLinkManager;

public class Cobblemoncaughtbiome implements ModInitializer {

    @Override
    public void onInitialize() {
        SoulLinkManager.initialize();
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(event -> {
            Entity ball = event.getPokeBall();
            if (!(ball.level() instanceof ServerLevel level)) {
                return;
            }
            level.getBiome(ball.blockPosition()).unwrapKey().ifPresent(key ->
                    CaughtBiome.store(event.getPokemon().getPokemon().getPersistentData(), key.location()));
        });
    }
}
