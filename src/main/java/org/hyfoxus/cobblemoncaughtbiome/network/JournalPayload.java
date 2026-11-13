package org.hyfoxus.cobblemoncaughtbiome.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A bounded chunk of a server-authorized journal snapshot. */
public record JournalPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<JournalPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cobblemoncaughtbiome", "journal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JournalPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeNbt(payload.data()),
            buffer -> {
                CompoundTag data = buffer.readNbt();
                if (data == null) throw new IllegalArgumentException("Missing journal snapshot");
                return new JournalPayload(data);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
