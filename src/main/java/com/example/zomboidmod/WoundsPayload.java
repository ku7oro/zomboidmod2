package com.example.zomboidmod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** data: "код_части0,...,код_части5,вирус(0/1),обезболивающее(0/1)" */
public record WoundsPayload(String data) implements CustomPayload {
    public static final CustomPayload.Id<WoundsPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ZomboidMod.MOD_ID, "wounds"));

    public static final PacketCodec<RegistryByteBuf, WoundsPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, WoundsPayload::data, WoundsPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
