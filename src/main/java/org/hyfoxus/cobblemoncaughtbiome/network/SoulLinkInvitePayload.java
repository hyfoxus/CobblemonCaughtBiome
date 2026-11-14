package org.hyfoxus.cobblemoncaughtbiome.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Pushed to a player the moment they receive a Soul Link invitation. */
public record SoulLinkInvitePayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<SoulLinkInvitePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cobblemoncaughtbiome", "soullink_invite"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SoulLinkInvitePayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeNbt(payload.data()),
            buffer -> {
                CompoundTag data = buffer.readNbt();
                if (data == null) throw new IllegalArgumentException("Missing invite data");
                return new SoulLinkInvitePayload(data);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
