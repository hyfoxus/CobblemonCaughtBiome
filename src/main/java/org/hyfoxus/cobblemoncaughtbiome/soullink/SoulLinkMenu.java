package org.hyfoxus.cobblemoncaughtbiome.soullink;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import org.hyfoxus.cobblemoncaughtbiome.network.SoulLinkOnlinePayload;

final class SoulLinkMenu {

    private SoulLinkMenu() { }

    static void initialize() {
        PayloadTypeRegistry.playS2C().register(SoulLinkOnlinePayload.TYPE, SoulLinkOnlinePayload.CODEC);
    }

    static int openOnline(ServerPlayer player, boolean forceOpen) {
        if (!ServerPlayNetworking.canSend(player, SoulLinkOnlinePayload.TYPE)) {
            if (forceOpen) SoulLinkManager.message(player, "Install the matching client mod to open the Soul Link menu.");
            return 0;
        }
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("forceOpen", forceOpen);
        ListTag players = new ListTag();
        player.server.getPlayerList().getPlayers().stream()
                .filter(online -> online != player)
                .forEach(online -> players.add(StringTag.valueOf(online.getGameProfile().getName())));
        tag.put("players", players);
        SoulLinkData data = SoulLinkData.get(player.server);
        SoulLinkData.Run active = data.activeRun(player.getUUID());
        ListTag runs = new ListTag();
        for (SoulLinkData.Run run : data.runsOf(player.getUUID())) {
            CompoundTag row = new CompoundTag();
            row.putString("id", run.id.toString());
            row.putString("title", run.title);
            row.putString("members", run.solo() ? run.firstName : run.firstName + " & " + run.secondName);
            row.putBoolean("solo", run.solo());
            row.putBoolean("paused", run.paused);
            row.putBoolean("selected", run == active);
            int caught = 0;
            int linked = 0;
            for (SoulLinkData.Capture capture : run.captures) {
                if (capture.player().equals(player.getUUID())) caught++;
                if (!run.solo() && capture.player().equals(run.first)
                        && run.capture(run.second, capture.biome()) != null) linked++;
            }
            row.putInt("caught", caught);
            row.putInt("linked", linked);
            runs.add(row);
        }
        tag.put("runs", runs);
        if (active != null) tag.putString("selected", active.id.toString());
        SoulLinkManager.Invitation invite = SoulLinkManager.pendingInvitation(player.getUUID(), player.server);
        if (invite != null) {
            ServerPlayer inviter = player.server.getPlayerList().getPlayer(invite.inviter());
            tag.putString("pendingInviter", inviter != null ? inviter.getGameProfile().getName() : "");
            tag.putString("pendingToken", invite.token());
            tag.putInt("pendingTicksRemaining", (int) (invite.expiresAt() - player.server.getTickCount()));
        }
        ServerPlayNetworking.send(player, new SoulLinkOnlinePayload(tag));
        return 1;
    }
}
