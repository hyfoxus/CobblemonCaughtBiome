package org.hyfoxus.cobblemoncaughtbiome;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.hyfoxus.cobblemoncaughtbiome.soullink.SoulLinkManager;

public class Cobblemoncaughtbiome implements ModInitializer {

    @Override
    public void onInitialize() {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            ServerLifecycleEvents.SERVER_STARTED.register(server -> {
                if (!server.isDedicatedServer()) {
                    server.setUsesAuthentication(false);
                }
            });
        }
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
