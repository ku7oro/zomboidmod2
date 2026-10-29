package com.example.zomboidmod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record DrinkPayload() implements CustomPayload {
    public static final CustomPayload.Id<DrinkPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ZomboidMod.MOD_ID, "drink"));

    public static final PacketCodec<RegistryByteBuf, DrinkPayload> CODEC =
            PacketCodec.unit(new DrinkPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
