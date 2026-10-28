package com.example.zomboidmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ZomboidMod implements ModInitializer {
    public static final String MOD_ID = "zomboidmod";
    public static final int MAX_THIRST = 100;

    // Настройки (в секундах)
    private static final int SECONDS_PER_THIRST_POINT = 12; // -1% жажды раз в 12 сек (бег - в 2 раза быстрее)

    private static final Map<UUID, Integer> THIRST = new HashMap<>();
    private static final Map<UUID, Integer> TIMER = new HashMap<>();

    public static Item CLEAN_WATER;

    public static int getThirst(ServerPlayerEntity p) {
        return THIRST.getOrDefault(p.getUuid(), MAX_THIRST);
    }

    public static void addThirst(ServerPlayerEntity p, int amount) {
        int value = Math.max(0, Math.min(MAX_THIRST, getThirst(p) + amount));
        THIRST.put(p.getUuid(), value);
    }

    @Override
    public void onInitialize() {
        CLEAN_WATER = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "clean_water"),
                new CleanWaterItem(new Item.Settings().maxCount(16)));

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK)
                .register(entries -> entries.add(CLEAN_WATER));

        // Раз в секунду: уменьшаем жажду и показываем её над хотбаром
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) return;

            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;

                int t = TIMER.merge(p.getUuid(), p.isSprinting() ? 2 : 1, Integer::sum);
                if (t >= SECONDS_PER_THIRST_POINT) {
                    TIMER.put(p.getUuid(), 0);
                    addThirst(p, -1);
                }

                int thirst = getThirst(p);

                if (thirst <= 0 && server.getTicks() % 80 == 0) {
                    p.damage(p.getDamageSources().starve(), 1.0f);
                }
                if (thirst <= 20) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 0, true, false));
                }

                p.sendMessage(Text.literal("Жажда: " + thirst + "%"), true);
            }
        });

        // После смерти жажда сбрасывается
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            THIRST.remove(newPlayer.getUuid());
            TIMER.remove(newPlayer.getUuid());
        });
    }
}
