package com.example.zomboidmod;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Раны и болезни игроков хранятся в мире. */
public class HealthState extends PersistentState {
    public final Map<UUID, PlayerHealth> players = new HashMap<>();

    private static final PersistentState.Type<HealthState> TYPE =
            new PersistentState.Type<>(HealthState::new, HealthState::fromNbt, null);

    public static HealthState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "zomboidmod_health");
    }

    public PlayerHealth of(UUID id) {
        return players.computeIfAbsent(id, k -> new PlayerHealth());
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtCompound all = new NbtCompound();
        players.forEach((id, h) -> all.put(id.toString(), h.toNbt()));
        nbt.put("players", all);
        return nbt;
    }

    public static HealthState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        HealthState st = new HealthState();
        NbtCompound all = nbt.getCompound("players");
        for (String key : all.getKeys()) {
            st.players.put(UUID.fromString(key), PlayerHealth.fromNbt(all.getCompound(key)));
        }
        return st;
    }
}
