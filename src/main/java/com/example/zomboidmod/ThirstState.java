package com.example.zomboidmod;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Хранит жажду игроков в файле мира, чтобы она не сбрасывалась после выхода. */
public class ThirstState extends PersistentState {
    public final Map<UUID, Integer> thirst = new HashMap<>();

    private static final PersistentState.Type<ThirstState> TYPE =
            new PersistentState.Type<>(ThirstState::new, ThirstState::fromNbt, null);

    public static ThirstState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "zomboidmod_thirst");
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtCompound all = new NbtCompound();
        thirst.forEach((id, value) -> all.putInt(id.toString(), value));
        nbt.put("thirst", all);
        return nbt;
    }

    public static ThirstState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        ThirstState state = new ThirstState();
        NbtCompound all = nbt.getCompound("thirst");
        for (String key : all.getKeys()) {
            state.thirst.put(UUID.fromString(key), all.getInt(key));
        }
        return state;
    }
}
