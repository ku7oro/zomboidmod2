package com.example.zomboidmod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ThirstPayload(int thirst) implements CustomPayload {
    public static final CustomPayload.Id<ThirstPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ZomboidMod.MOD_ID, "thirst"));

    public static final PacketCodec<RegistryByteBuf, ThirstPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.INTEGER, ThirstPayload::thirst, ThirstPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
