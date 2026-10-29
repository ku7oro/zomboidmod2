package com.example.zomboidmod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** part - номер части тела, action - номер MedItem.Kind. */
public record TreatPayload(int part, int action) implements CustomPayload {
    public static final CustomPayload.Id<TreatPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ZomboidMod.MOD_ID, "treat"));

    public static final PacketCodec<RegistryByteBuf, TreatPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, TreatPayload::part,
            PacketCodecs.INTEGER, TreatPayload::action,
            TreatPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
