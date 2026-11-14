package org.hyfoxus.cobblemoncaughtbiome.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The online-player list (and the viewer's own pending invite, if any) for the not-in-a-run Invite view. */
public record SoulLinkOnlinePayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<SoulLinkOnlinePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cobblemoncaughtbiome", "soullink_online"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SoulLinkOnlinePayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeNbt(payload.data()),
            buffer -> {
                CompoundTag data = buffer.readNbt();
                if (data == null) throw new IllegalArgumentException("Missing online-list data");
                return new SoulLinkOnlinePayload(data);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
