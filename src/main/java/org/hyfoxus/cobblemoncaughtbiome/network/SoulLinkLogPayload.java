package org.hyfoxus.cobblemoncaughtbiome.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SoulLinkLogPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<SoulLinkLogPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cobblemoncaughtbiome", "soullink_log"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SoulLinkLogPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeNbt(payload.data()),
            buffer -> {
                CompoundTag data = buffer.readNbt();
                if (data == null) throw new IllegalArgumentException("Missing log snapshot");
                return new SoulLinkLogPayload(data);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
