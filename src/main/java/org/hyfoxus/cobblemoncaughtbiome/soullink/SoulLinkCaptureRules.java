package org.hyfoxus.cobblemoncaughtbiome.soullink;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokeball.catching.CaptureContext;
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.hyfoxus.cobblemoncaughtbiome.soullink.SoulLinkData.Capture;
import org.hyfoxus.cobblemoncaughtbiome.soullink.SoulLinkData.Run;

final class SoulLinkCaptureRules {
    private static final Map<UUID, Reservation> RESERVATIONS = new HashMap<>();
    private static final Map<UUID, Integer> LAST_REMINDERS = new HashMap<>();
    private static final int REMINDER_COOLDOWN_TICKS = 60;

    private SoulLinkCaptureRules() { }

    static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server ->
                RESERVATIONS.values().removeIf(reservation -> reservation.ball().isRemoved()));
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(event -> {
            if (event.getPokeBall().getOwner() instanceof ServerPlayer player
                    && !event.isCanceled()
                    && !allowCapture(player, event.getPokemon().getPokemon(), event.getPokeBall())) {
                event.cancel();
            }
        });
        CobblemonEvents.POKE_BALL_CAPTURE_CALCULATED.subscribe(event -> {
            if (!(event.getThrower() instanceof ServerPlayer player)) return;
            EmptyPokeBallEntity ball = event.getPokeBallEntity();
            if (!allowCapture(player, event.getPokemonEntity().getPokemon(), ball)) {
                event.setCaptureResult(new CaptureContext(0, false, false));
            }
            if (!event.getCaptureResult().isSuccessfulCapture()) RESERVATIONS.remove(ball.getUUID());
        });
    }

    private static boolean allowCapture(ServerPlayer player, Pokemon pokemon, EmptyPokeBallEntity ball) {
        Run run = SoulLinkData.get(player.server).findRun(player.getUUID());
        if (run == null || run.paused || pokemon.getShiny()) {
            RESERVATIONS.remove(ball.getUUID());
            return true;
        }
        Reservation existing = RESERVATIONS.get(ball.getUUID());
        ResourceLocation biome = existing != null && existing.run() == run ? existing.biome()
                : player.serverLevel().getBiome(ball.blockPosition()).unwrapKey().orElseThrow().location();
        Capture caught = run.capture(player.getUUID(), biome.toString());
        if (caught != null) {
            remind(player, "Already caught here", caught.name());
            RESERVATIONS.remove(ball.getUUID());
            return false;
        }
        // Only one ordinary capture per player/biome may be in flight at a time.
        for (Reservation other : RESERVATIONS.values()) {
            if (other.run() == run && other.player().equals(player.getUUID())
                    && other.biome().equals(biome) && !other.ball().isRemoved()
                    && !other.ball().getUUID().equals(ball.getUUID())) {
                remind(player, "Capture in progress", other.pokemonName());
                return false;
            }
        }
        RESERVATIONS.put(ball.getUUID(), new Reservation(run, player.getUUID(), biome,
                pokemon.getDisplayName(false).getString(), ball));
        return true;
    }

    static Optional<ResourceLocation> finishCapture(ServerPlayer player, Pokemon pokemon, EmptyPokeBallEntity ball) {
        Reservation reservation = RESERVATIONS.remove(ball.getUUID());
        Run run = SoulLinkData.get(player.server).findRun(player.getUUID());
        if (run == null || run.paused || pokemon.getShiny() || reservation == null
                || reservation.run() != run || !reservation.player().equals(player.getUUID())) {
            return Optional.empty();
        }
        return Optional.of(reservation.biome());
    }

    static void clearRun(Run run) {
        RESERVATIONS.values().removeIf(reservation -> reservation.run() == run);
    }

    static void clearReminder(UUID player) {
        LAST_REMINDERS.remove(player);
    }

    static void reset() {
        RESERVATIONS.clear();
        LAST_REMINDERS.clear();
    }

    private static void remind(ServerPlayer player, String explanation, String pokemonName) {
        int now = player.server.getTickCount();
        Integer last = LAST_REMINDERS.get(player.getUUID());
        if (last != null && now - last < REMINDER_COOLDOWN_TICKS) return;
        LAST_REMINDERS.put(player.getUUID(), now);
        Component text = Component.literal(explanation + " · ")
                .withStyle(style -> style.withColor(0xAEB6C3))
                .append(Component.literal(pokemonName).withStyle(style -> style.withColor(0xE7C97B)));
        player.displayClientMessage(text, true);
    }

    private record Reservation(Run run, UUID player, ResourceLocation biome,
                               String pokemonName, EmptyPokeBallEntity ball) { }
}
