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

    static int openOnline(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, SoulLinkOnlinePayload.TYPE)) {
            SoulLinkManager.message(player, "Install the matching client mod to open the Soul Link menu.");
            return 0;
        }
        CompoundTag tag = new CompoundTag();
        ListTag players = new ListTag();
        player.server.getPlayerList().getPlayers().stream()
                .filter(online -> online != player)
                .forEach(online -> players.add(StringTag.valueOf(online.getGameProfile().getName())));
        tag.put("players", players);
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
