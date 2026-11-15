package org.hyfoxus.cobblemoncaughtbiome.soullink;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.hyfoxus.cobblemoncaughtbiome.BiomeNames;
import org.hyfoxus.cobblemoncaughtbiome.network.SoulLinkInvitePayload;
import org.hyfoxus.cobblemoncaughtbiome.soullink.SoulLinkData.Capture;
import org.hyfoxus.cobblemoncaughtbiome.soullink.SoulLinkData.Run;

public final class SoulLinkManager {
    private static final Map<UUID, Invitation> INVITATIONS = new HashMap<>();

    private SoulLinkManager() { }

    public static void initialize() {
        SoulLinkCommands.register();
        SoulLinkJournal.initialize();
        SoulLinkMenu.initialize();
        PayloadTypeRegistry.playS2C().register(SoulLinkInvitePayload.TYPE, SoulLinkInvitePayload.CODEC);
        SoulLinkCaptureRules.initialize();
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            INVITATIONS.clear();
            SoulLinkCaptureRules.reset();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                SoulLinkCaptureRules.clearReminder(handler.getPlayer().getUUID()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            SoulLinkData data = SoulLinkData.get(server);
            Run run = data.findRun(player.getUUID());
            if (run != null) {
                if (run.first.equals(player.getUUID())) run.firstName = player.getGameProfile().getName();
                else run.secondName = player.getGameProfile().getName();
                data.setDirty();
            }
            List<String> pending = data.notifications.remove(player.getUUID());
            if (pending != null) {
                pending.forEach(message -> player.sendSystemMessage(restoreMessage(server, message)));
                data.setDirty();
            }
        });
        CobblemonEvents.POKEMON_CAPTURED.subscribe(event -> {
            ServerPlayer player = event.getPlayer();
            SoulLinkCaptureRules.finishCapture(player, event.getPokemon(), event.getPokeBallEntity())
                    .ifPresent(biome -> recordCapture(player, event.getPokemon(), biome));
        });
        CobblemonEvents.POKEMON_FAINTED.subscribe(event -> {
            Pokemon pokemon = event.getPokemon();
            if (pokemon.getOwnerEntity() instanceof ServerPlayer owner) {
                owner.server.execute(() -> notifyFainted(owner.server, pokemon));
            }
        });
    }

    static boolean invite(ServerPlayer inviter, ServerPlayer target) {
        SoulLinkData data = SoulLinkData.get(inviter.server);
        if (inviter.getUUID().equals(target.getUUID())) return error(inviter, "You cannot invite yourself.");
        if (data.findRun(inviter.getUUID()) != null || data.findRun(target.getUUID()) != null)
            return error(inviter, "One of you is already in a Soul Link run.");
        Invitation existing = INVITATIONS.get(target.getUUID());
        if (existing != null && existing.expiresAt() > inviter.server.getTickCount())
            return error(inviter, "That player already has a pending invitation.");
        String token = UUID.randomUUID().toString();
        INVITATIONS.put(target.getUUID(), new Invitation(inviter.getUUID(), inviter.server.getTickCount() + 1200L, token));
        message(inviter, "Invitation sent to " + target.getGameProfile().getName() + ".");
        target.sendSystemMessage(SoulLinkMessages.line(SoulLinkMessages.highlight(inviter.getGameProfile().getName())
                .append(SoulLinkMessages.text(" invited you to start a run.\n"))
                .append(SoulLinkMessages.button("Accept", "/soullink accept " + token, SoulLinkMessages.TEAL, "Start a run together"))
                .append(SoulLinkMessages.text("   "))
                .append(SoulLinkMessages.button("Decline", "/soullink decline " + token, SoulLinkMessages.WARNING, "Decline this invitation"))
                .append(SoulLinkMessages.text("  ·  Expires in 60 seconds"))));
        if (ServerPlayNetworking.canSend(target, SoulLinkInvitePayload.TYPE)) {
            CompoundTag invitePayload = new CompoundTag();
            invitePayload.putString("inviter", inviter.getGameProfile().getName());
            invitePayload.putString("token", token);
            invitePayload.putInt("ticksRemaining", 1200);
            ServerPlayNetworking.send(target, new SoulLinkInvitePayload(invitePayload));
        }
        SoulLinkMenu.openOnline(target, false);
        return true;
    }

    static boolean accept(ServerPlayer player) {
        return accept(player, null);
    }

    static boolean accept(ServerPlayer player, String token) {
        Invitation invite = INVITATIONS.get(player.getUUID());
        if (invite != null && token != null && !invite.token().equals(token))
            return error(player, "This invitation is no longer active.");
        INVITATIONS.remove(player.getUUID());
        if (invite == null || invite.expiresAt() <= player.server.getTickCount())
            return error(player, "You have no active invitation.");
        ServerPlayer inviter = player.server.getPlayerList().getPlayer(invite.inviter());
        if (inviter == null) return error(player, "The inviting player must be online.");
        SoulLinkData data = SoulLinkData.get(player.server);
        if (data.findRun(player.getUUID()) != null || data.findRun(inviter.getUUID()) != null)
            return error(player, "One of you is already in a Soul Link run.");
        Run run = new Run(inviter.getUUID(), inviter.getGameProfile().getName(),
                player.getUUID(), player.getGameProfile().getName());
        data.runs.add(run);
        run.log.add(formatLogEntry(inviter.getGameProfile().getName() + " and "
                + player.getGameProfile().getName() + " started a Soul Link run."));
        data.setDirty();
        INVITATIONS.entrySet().removeIf(entry -> entry.getKey().equals(player.getUUID())
                || entry.getKey().equals(inviter.getUUID()) || entry.getValue().inviter().equals(player.getUUID())
                || entry.getValue().inviter().equals(inviter.getUUID()));
        message(player, "Soul Link started with " + inviter.getGameProfile().getName() + ".");
        message(inviter, "Soul Link started with " + player.getGameProfile().getName() + ".");
        pushRefresh(player);
        pushRefresh(inviter);
        return true;
    }

    static boolean decline(ServerPlayer player, String token) {
        Invitation invite = INVITATIONS.get(player.getUUID());
        if (invite == null || invite.expiresAt() <= player.server.getTickCount()
                || (token != null && !invite.token().equals(token)))
            return error(player, "This invitation is no longer active.");
        INVITATIONS.remove(player.getUUID());
        message(player, "Invitation declined.");
        ServerPlayer inviter = player.server.getPlayerList().getPlayer(invite.inviter());
        if (inviter != null) message(inviter, player.getGameProfile().getName() + " declined your invitation.");
        return true;
    }

    private static void recordCapture(ServerPlayer player, Pokemon pokemon, ResourceLocation biome) {
        SoulLinkData data = SoulLinkData.get(player.server);
        Run run = data.findRun(player.getUUID());
        if (run == null || run.paused || pokemon.getShiny()
                || run.capture(player.getUUID(), biome.toString()) != null) return;
        // A released and recaptured Pokémon must not become its own partner.
        if (run.captures.stream().anyMatch(capture -> capture.pokemon().equals(pokemon.getUuid()))) return;
        Capture capture = new Capture(player.getUUID(), pokemon.getUuid(), pokemon.getDisplayName(false).getString(), biome.toString(),
                pokemon.getSpecies().getResourceIdentifier().toString(), pokemon.getAspects());
        run.captures.add(capture);
        run.log.add(formatLogEntry(capture.name() + " was caught in "
                + BiomeNames.display(biome.toString()).getString() + "."));
        data.setDirty();
        Capture other = run.capture(run.partner(player.getUUID()), biome.toString());
        MutableComponent catchDetails = SoulLinkMessages.text("You caught ")
                .append(SoulLinkMessages.highlight(capture.name())).append(SoulLinkMessages.text(" in "))
                .append(BiomeNames.display(biome.toString()).copy().withStyle(style -> style.withColor(SoulLinkMessages.ACCENT)))
                .append(SoulLinkMessages.text("!"));
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        player.connection.send(new ClientboundSetSubtitleTextPacket(catchDetails));
        player.connection.send(new ClientboundSetTitleTextPacket(SoulLinkMessages.highlight("Congratulations!")));
        MutableComponent partnerMessage = SoulLinkMessages.highlight(run.name(player.getUUID()))
                .append(SoulLinkMessages.text(" has caught ")).append(SoulLinkMessages.highlight(capture.name()))
                .append(SoulLinkMessages.text(" in "))
                .append(BiomeNames.display(biome.toString()).copy().withStyle(style -> style.withColor(SoulLinkMessages.ACCENT)))
                .append(SoulLinkMessages.text("!"));
        if (other == null) {
            partnerMessage.append(SoulLinkMessages.text(" Catch up and catch your own Link!"));
            deliver(player.server, data, run.partner(player.getUUID()), SoulLinkMessages.line(partnerMessage));
        } else {
            deliver(player.server, data, run.partner(player.getUUID()), SoulLinkMessages.line(partnerMessage));
            run.log.add(formatLogEntry(capture.name() + " paired with " + other.name() + "."));
            Component text = SoulLinkMessages.line(SoulLinkMessages.text("Pair formed  ·  ")
                    .append(SoulLinkMessages.highlight(capture.name()))
                    .append(SoulLinkMessages.text(" ↔ ")).append(SoulLinkMessages.highlight(other.name()))
                    .append(SoulLinkMessages.text("  ·  ")).append(BiomeNames.display(biome.toString())));
            deliver(player.server, data, run.first, text);
            deliver(player.server, data, run.second, text);
        }
        pushRefresh(player);
        pushRefresh(player.server, run.partner(player.getUUID()));
    }

    private static void notifyFainted(MinecraftServer server, Pokemon pokemon) {
        SoulLinkData data = SoulLinkData.get(server);
        for (Run run : data.runs) {
            if (run.paused) continue;
            for (Capture capture : run.captures) {
                if (!capture.pokemon().equals(pokemon.getUuid())) continue;
                Capture other = run.capture(run.partner(capture.player()), capture.biome());
                if (other != null) {
                    run.log.add(formatLogEntry(run.name(capture.player()) + "'s "
                            + pokemon.getDisplayName(false).getString() + " fainted."));
                    data.setDirty();
                    deliver(server, data, other.player(), SoulLinkMessages.line(
                            SoulLinkMessages.highlight(run.name(capture.player()) + "'s " + pokemon.getDisplayName(false).getString())
                                    .append(SoulLinkMessages.text(" fainted!").withStyle(style -> style.withColor(SoulLinkMessages.WARNING)))
                                    .append(SoulLinkMessages.text(" Linked to your "))
                                    .append(SoulLinkMessages.highlight(other.name()))
                                    .append(SoulLinkMessages.text("  ·  ")).append(BiomeNames.display(capture.biome()))));
                    pushRefresh(server, capture.player());
                    pushRefresh(server, other.player());
                }
                return;
            }
        }
    }

    private static void deliver(MinecraftServer server, SoulLinkData data, UUID recipient, String message) {
        deliver(server, data, recipient, SoulLinkMessages.line(SoulLinkMessages.text(message.replaceFirst("^\\[Soul Link] ", ""))));
    }

    private static void deliver(MinecraftServer server, SoulLinkData data, UUID recipient, Component message) {
        ServerPlayer player = server.getPlayerList().getPlayer(recipient);
        if (player != null) player.sendSystemMessage(message);
        else {
            data.notifications.computeIfAbsent(recipient, ignored -> new ArrayList<>())
                    .add("json:" + Component.Serializer.toJson(message, server.registryAccess()));
            data.setDirty();
        }
    }

    static void end(ServerPlayer player) {
        SoulLinkData data = SoulLinkData.get(player.server);
        Run run = data.findRun(player.getUUID());
        if (run == null) { error(player, "You are not in a Soul Link run."); return; }
        run.log.add(formatLogEntry("Run ended."));
        data.runs.remove(run);
        SoulLinkCaptureRules.clearRun(run);
        data.setDirty();
        String text = "[Soul Link] " + player.getGameProfile().getName() + " ended your Soul Link run.";
        deliver(player.server, data, run.first, text);
        deliver(player.server, data, run.second, text);
        pushRefresh(player.server, run.first);
        pushRefresh(player.server, run.second);
    }

    static boolean setPaused(ServerPlayer player, boolean paused) {
        SoulLinkData data = SoulLinkData.get(player.server);
        Run run = data.findRun(player.getUUID());
        if (run == null) return error(player, "You are not in a Soul Link run.");
        if (run.paused == paused) return error(player, "Your Soul Link run is already "
                + (paused ? "paused." : "active."));
        run.paused = paused;
        run.log.add(formatLogEntry(paused ? "Run paused." : "Run resumed."));
        SoulLinkCaptureRules.clearRun(run);
        data.setDirty();
        String text = "[Soul Link] " + player.getGameProfile().getName()
                + (paused ? " paused your run. Captures are unrestricted and will not count toward progress."
                : " resumed your run. Biome capture limits are active again.");
        deliver(player.server, data, run.first, text);
        deliver(player.server, data, run.second, text);
        pushRefresh(player.server, run.first);
        pushRefresh(player.server, run.second);
        return true;
    }

    private static void pushRefresh(ServerPlayer player) {
        SoulLinkData data = SoulLinkData.get(player.server);
        if (data.findRun(player.getUUID()) != null) {
            SoulLinkJournal.open(player, "all", 1, false);
            SoulLinkJournal.openLogs(player);
        } else {
            SoulLinkMenu.openOnline(player, false);
        }
    }

    private static void pushRefresh(MinecraftServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) pushRefresh(player);
    }

    static void message(ServerPlayer player, String text) {
        player.sendSystemMessage(SoulLinkMessages.line(SoulLinkMessages.text(text)));
    }

    private static boolean error(ServerPlayer player, String text) {
        player.sendSystemMessage(SoulLinkMessages.line(SoulLinkMessages.text(text)
                .withStyle(style -> style.withColor(SoulLinkMessages.WARNING))));
        return false;
    }

    private static Component restoreMessage(MinecraftServer server, String saved) {
        if (saved.startsWith("json:")) {
            try {
                Component message = Component.Serializer.fromJson(saved.substring(5), server.registryAccess());
                if (message != null) return message;
            } catch (RuntimeException ignored) {
                return SoulLinkMessages.line(SoulLinkMessages.text("A saved notification could not be read."));
            }
        }
        return SoulLinkMessages.line(SoulLinkMessages.text(saved.replaceFirst("^\\[Soul Link] ", "")));
    }

    static String formatLogEntry(String text) {
        return "[" + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) + "] " + text;
    }

    static Invitation pendingInvitation(UUID player, MinecraftServer server) {
        Invitation invite = INVITATIONS.get(player);
        return invite != null && invite.expiresAt() > server.getTickCount() ? invite : null;
    }

    record Invitation(UUID inviter, long expiresAt, String token) { }
}
